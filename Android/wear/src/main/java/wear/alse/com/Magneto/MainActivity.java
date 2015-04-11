package wear.alse.com.Magneto;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static wear.alse.com.Magneto.Common.TIME_DELAY_SPEECH_RECOGNIZER;
import static wear.alse.com.Magneto.Common.matrixMultiplication;

public class MainActivity extends Activity implements SensorEventListener,
        AngularVelocityListener {

    public static final float EPSILON = 0.000000001f;

    private static final String tag = MainActivity.class.getSimpleName();
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final int MEAN_FILTER_WINDOW = 10;
    private static final int MIN_SAMPLE_COUNT = 30;

    private int accelerationSampleCount = 0;
    private int magneticSampleCount = 0;
    private boolean hasInitialOrientation = false;
    private boolean stateInitializedCalibrated = false;
    private boolean stateInitializedRaw = false;

    private boolean useFusedEstimation = false;

    private DecimalFormat df;

    // Calibrated maths.
    private float[] currentRotationMatrixCalibrated;
    private float[] deltaRotationMatrixCalibrated;
    private float[] deltaRotationVectorCalibrated;
    private float[] gyroscopeOrientationCalibrated;
    private float[] currentGyroscopeOrientationCalibrated;

    // Uncalibrated maths
    private float[] currentRotationMatrixRaw;
    private float[] deltaRotationMatrixRaw;
    private float[] deltaRotationVectorRaw;
    private float[] gyroscopeOrientationRaw;

    // accelerometer and magnetometer based rotation matrix
    private float[] initialRotationMatrix;
    private float[] acceleration;
    private float[] magnetic;

    private FusedGyroscopeSensor fusedGyroscopeSensor;


    private long timestampOldCalibrated = 0;
    private long timestampOldRaw = 0;

    private MeanFilter accelerationFilter;
    private MeanFilter magneticFilter;

    // We need the SensorManager to register for Sensor Events.
    private SensorManager sensorManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 01, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(getApplicationContext());
        builder.setContentTitle("Magneto");
        builder.setOngoing(true);
        builder.setContentIntent(pendingIntent);
        Notification notification = builder.build();
        NotificationManager notificationManger =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManger.notify(01, notification);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                Button calibrate_button = (Button) stub.findViewById(R.id.calibrate);
                calibrate_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Init();
                    }
                });

                Button command = (Button) stub.findViewById(R.id.command);
                command.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SpeechRecognizerInit();
                    }
                });
            }
        });

        Init();


    }

    private void Init() {
        initMaths();
        initSensors();
        initFilters();
    }

    @Override
    public void onResume() {
        super.onResume();
        restart();
    }

    @Override
    public void onPause() {
        super.onPause();

        reset();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reset();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            onAccelerationSensorChanged(event.values, event.timestamp);
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            onMagneticSensorChanged(event.values, event.timestamp);
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            onGyroscopeSensorChanged(event.values, event.timestamp);
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
            onGyroscopeSensorUncalibratedChanged(event.values, event.timestamp);
        }
    }

    @Override
    public void onAngularVelocitySensorChanged(float[] angularVelocity,
                                               long timeStamp) {
        //xAxisCalibrated.setText(df.format(Math
        //.toDegrees(angularVelocity[0])));
        //yAxisCalibrated.setText(df.format(Math
        //.toDegrees(angularVelocity[1])));
        //zAxisCalibrated.setText(df.format(Math
        //.toDegrees(angularVelocity[2])));
    }

    public void onAccelerationSensorChanged(float[] acceleration, long timeStamp) {
        // Get a local copy of the raw magnetic values from the device sensor.
        System.arraycopy(acceleration, 0, this.acceleration, 0,
                acceleration.length);

        // Use a mean filter to smooth the sensor inputs
        this.acceleration = accelerationFilter.filterFloat(this.acceleration);

        // Count the number of samples received.
        accelerationSampleCount++;

        // Only determine the initial orientation after the acceleration sensor
        // and magnetic sensor have had enough time to be smoothed by the mean
        // filters. Also, only do this if the orientation hasn't already been
        // determined since we only need it once.
        if (accelerationSampleCount > MIN_SAMPLE_COUNT
                && magneticSampleCount > MIN_SAMPLE_COUNT
                && !hasInitialOrientation) {
            calculateOrientation();
        }
    }

    public void onMagneticSensorChanged(float[] magnetic, long timeStamp) {
        // Get a local copy of the raw magnetic values from the device sensor.
        System.arraycopy(magnetic, 0, this.magnetic, 0, magnetic.length);

        // Use a mean filter to smooth the sensor inputs
        this.magnetic = magneticFilter.filterFloat(this.magnetic);

        // Count the number of samples received.
        magneticSampleCount++;
    }


    private void calculateOrientation() {
        hasInitialOrientation = SensorManager.getRotationMatrix(
                initialRotationMatrix, null, acceleration, magnetic);

        // Remove the sensor observers since they are no longer required.
        if (hasInitialOrientation) {
            sensorManager.unregisterListener(this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
            sensorManager.unregisterListener(this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
        }

    }

    public void onGyroscopeSensorChanged(float[] gyroscope, long timestamp) {
        // don't start until first accelerometer/magnetometer orientation has
        // been acquired
        if (!hasInitialOrientation) {
            return;
        }

        // Initialization of the gyroscope based rotation matrix
        if (!stateInitializedCalibrated) {
            currentRotationMatrixCalibrated = matrixMultiplication(
                    currentRotationMatrixCalibrated, initialRotationMatrix);

            stateInitializedCalibrated = true;
        }

        // This timestep's delta rotation to be multiplied by the current
        // rotation after computing it from the gyro sample data.
        if (timestampOldCalibrated != 0 && stateInitializedCalibrated) {
            final float dT = (timestamp - timestampOldCalibrated) * NS2S;

            // Axis of the rotation sample, not normalized yet.
            float axisX = gyroscope[0];
            float axisY = gyroscope[1];
            float axisZ = gyroscope[2];

            // Calculate the angular speed of the sample
            float omegaMagnitude = (float) Math.sqrt(axisX * axisX + axisY
                    * axisY + axisZ * axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            if (omegaMagnitude > EPSILON) {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }

            // Integrate around this axis with the angular speed by the timestep
            // in order to get a delta rotation from this sample over the
            // timestep. We will convert this axis-angle representation of the
            // delta rotation into a quaternion before turning it into the
            // rotation matrix.
            float thetaOverTwo = omegaMagnitude * dT / 2.0f;

            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

            deltaRotationVectorCalibrated[0] = sinThetaOverTwo * axisX;
            deltaRotationVectorCalibrated[1] = sinThetaOverTwo * axisY;
            deltaRotationVectorCalibrated[2] = sinThetaOverTwo * axisZ;
            deltaRotationVectorCalibrated[3] = cosThetaOverTwo;

            SensorManager.getRotationMatrixFromVector(
                    deltaRotationMatrixCalibrated,
                    deltaRotationVectorCalibrated);

            currentRotationMatrixCalibrated = matrixMultiplication(
                    currentRotationMatrixCalibrated,
                    deltaRotationMatrixCalibrated);

            SensorManager.getOrientation(currentRotationMatrixCalibrated,
                    gyroscopeOrientationCalibrated);
        }

        timestampOldCalibrated = timestamp;

        processOrientation(
                (int) Math.toDegrees(gyroscopeOrientationCalibrated[0]),
                (int) Math.toDegrees(gyroscopeOrientationCalibrated[1]),
                (int) Math.toDegrees(gyroscopeOrientationCalibrated[2])
        );

    }

    // store or discard orientations
    public void processOrientation(int x, int y, int z) {
        int X = (int) currentGyroscopeOrientationCalibrated[0];
        int Y = (int) currentGyroscopeOrientationCalibrated[1];
        int Z = (int) currentGyroscopeOrientationCalibrated[2];
        // set this to true if the new point is different from the current point
        Boolean shouldSave = false;

        // if one is greater than 360 that means that it is the first time
        // so set the current value but don't save it to the file
        if (X > 360) {
            currentGyroscopeOrientationCalibrated[0] = x;
            currentGyroscopeOrientationCalibrated[1] = y;
            currentGyroscopeOrientationCalibrated[2] = z;
            return;
        }
        if ((Math.abs(X - x) >= Common.THRESHOLD_ANGLE) || (((360 - (Math.abs(X) - Math.abs(x))) % 360) >= Common.THRESHOLD_ANGLE)) {
            shouldSave = true;
            currentGyroscopeOrientationCalibrated[0] = x;
        }
        if ((Math.abs(Y - y) >= Common.THRESHOLD_ANGLE) || (((360 - (Math.abs(Y) - Math.abs(y))) % 360) >= Common.THRESHOLD_ANGLE)) {
            shouldSave = true;
            currentGyroscopeOrientationCalibrated[1] = y;
        }
        if ((Math.abs(Z - z) >= Common.THRESHOLD_ANGLE) || (((360 - (Math.abs(Z) - Math.abs(z))) % 360) >= Common.THRESHOLD_ANGLE)) {
            shouldSave = true;
            currentGyroscopeOrientationCalibrated[2] = z;
        }

        // if it is a new gesture then save it to the file
        if (shouldSave) {
            Commands.write_new_gesture((int) x, (int) y, (int) z);
        }
    }

    public void onGyroscopeSensorUncalibratedChanged(float[] gyroscope,
                                                     long timestamp) {
        // don't start until first accelerometer/magnetometer orientation has
        // been acquired
        if (!hasInitialOrientation) {
            return;
        }

        // Initialization of the gyroscope based rotation matrix
        if (!stateInitializedRaw) {
            currentRotationMatrixRaw = matrixMultiplication(
                    currentRotationMatrixRaw, initialRotationMatrix);

            stateInitializedRaw = true;

        }

        // This timestep's delta rotation to be multiplied by the current
        // rotation after computing it from the gyro sample data.
        if (timestampOldRaw != 0 && stateInitializedRaw) {
            final float dT = (timestamp - timestampOldRaw) * NS2S;

            // Axis of the rotation sample, not normalized yet.
            float axisX = gyroscope[0];
            float axisY = gyroscope[1];
            float axisZ = gyroscope[2];

            // Calculate the angular speed of the sample
            float omegaMagnitude = (float) Math.sqrt(axisX * axisX + axisY
                    * axisY + axisZ * axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            if (omegaMagnitude > EPSILON) {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }

            // Integrate around this axis with the angular speed by the timestep
            // in order to get a delta rotation from this sample over the
            // timestep. We will convert this axis-angle representation of the
            // delta rotation into a quaternion before turning it into the
            // rotation matrix.
            float thetaOverTwo = omegaMagnitude * dT / 2.0f;

            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

            deltaRotationVectorRaw[0] = sinThetaOverTwo * axisX;
            deltaRotationVectorRaw[1] = sinThetaOverTwo * axisY;
            deltaRotationVectorRaw[2] = sinThetaOverTwo * axisZ;
            deltaRotationVectorRaw[3] = cosThetaOverTwo;

            SensorManager.getRotationMatrixFromVector(deltaRotationMatrixRaw,
                    deltaRotationVectorRaw);

            currentRotationMatrixRaw = matrixMultiplication(
                    currentRotationMatrixRaw, deltaRotationMatrixRaw);

            SensorManager.getOrientation(currentRotationMatrixRaw,
                    gyroscopeOrientationRaw);
        }

        timestampOldRaw = timestamp;

    }

    /**
     * Initialize the mean filters.
     */
    private void initFilters() {
        accelerationFilter = new MeanFilter();
        accelerationFilter.setWindowSize(MEAN_FILTER_WINDOW);

        magneticFilter = new MeanFilter();
        magneticFilter.setWindowSize(MEAN_FILTER_WINDOW);
    }

    /**
     * Initialize the data structures required for the maths.
     */
    private void initMaths() {
        initialRotationMatrix = new float[9];
        acceleration = new float[3];
        magnetic = new float[3];

        deltaRotationVectorCalibrated = new float[4];
        deltaRotationMatrixCalibrated = new float[9];
        currentRotationMatrixCalibrated = new float[9];
        gyroscopeOrientationCalibrated = new float[3];
        currentGyroscopeOrientationCalibrated = new float[3];

        // Initialize the current rotation matrix as an identity matrix...
        currentRotationMatrixCalibrated[0] = 1.0f;
        currentRotationMatrixCalibrated[4] = 1.0f;
        currentRotationMatrixCalibrated[8] = 1.0f;

        //Initialize the current rotation angles to 361 which is improbable
        currentGyroscopeOrientationCalibrated[0] = 361;
        currentGyroscopeOrientationCalibrated[1] = 361;
        currentGyroscopeOrientationCalibrated[2] = 361;

        deltaRotationVectorRaw = new float[4];
        deltaRotationMatrixRaw = new float[9];
        currentRotationMatrixRaw = new float[9];
        gyroscopeOrientationRaw = new float[3];

        // Initialize the current rotation matrix as an identity matrix...
        currentRotationMatrixRaw[0] = 1.0f;
        currentRotationMatrixRaw[4] = 1.0f;
        currentRotationMatrixRaw[8] = 1.0f;
    }

    /**
     * Initialize the sensors.
     */
    private void initSensors() {
        sensorManager = (SensorManager) this
                .getSystemService(Context.SENSOR_SERVICE);

        fusedGyroscopeSensor = new FusedGyroscopeSensor();
    }


    /**
     * Restarts all of the sensor observers and resets the activity to the
     * initial state. This should only be called *after* a call to reset().
     */
    private void restart() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);

        // Do not register for gyroscope updates if we are going to use the
        // fused version of the sensor...
        if (!useFusedEstimation) {
            boolean enabled = sensorManager.registerListener(this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                    SensorManager.SENSOR_DELAY_FASTEST);
        }

        // If we want to use the fused version of the gyroscope sensor.
        if (useFusedEstimation) {
            boolean hasGravity = sensorManager.registerListener(
                    fusedGyroscopeSensor,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                    SensorManager.SENSOR_DELAY_FASTEST);

            // If for some reason the gravity sensor does not exist, fall back
            // onto the acceleration sensor.
            if (!hasGravity) {
                sensorManager.registerListener(fusedGyroscopeSensor,
                        sensorManager
                                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                        SensorManager.SENSOR_DELAY_FASTEST);
            }

            sensorManager.registerListener(fusedGyroscopeSensor,
                    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                    SensorManager.SENSOR_DELAY_FASTEST);

            boolean enabled = sensorManager.registerListener(
                    fusedGyroscopeSensor,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                    SensorManager.SENSOR_DELAY_FASTEST);

        }
    }

    /**
     * Removes all of the sensor observers and resets the activity to the
     * initial state.
     */
    private void reset() {
        sensorManager.unregisterListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));

        sensorManager.unregisterListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));

        if (!useFusedEstimation) {
            sensorManager.unregisterListener(this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
        }

        if (useFusedEstimation) {
            sensorManager.unregisterListener(fusedGyroscopeSensor,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY));

            sensorManager.unregisterListener(fusedGyroscopeSensor,
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));

            sensorManager.unregisterListener(fusedGyroscopeSensor,
                    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));

            sensorManager.unregisterListener(fusedGyroscopeSensor,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));

            fusedGyroscopeSensor.removeObserver(this);
        }

        initMaths();

        hasInitialOrientation = false;
        stateInitializedCalibrated = false;
        stateInitializedRaw = false;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub

    }

    private static final int SPEECH_REQUEST_CODE = 0;
    private static final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor();


    // Create an intent that can start the Speech Recognizer activity
    private void SpeechRecognizerInit() {
        (new displaySpeechRecognizer()).run();
    }

    public class displaySpeechRecognizer implements Runnable {
        @Override
        public void run() {
            final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
            worker.schedule(this, TIME_DELAY_SPEECH_RECOGNIZER, TimeUnit.SECONDS);
            Common.TIME_DELAY_SPEECH_RECOGNIZER = 8;
        }
    }

    // This callback is invoked when the Speech Recognizer returns.
    // This is where you process the intent and extract the speech text from the intent.
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            Toast.makeText(this, spokenText, Toast.LENGTH_SHORT).show();
            Commands.handleMsg(spokenText);
        }
        super.onActivityResult(requestCode, resultCode, data);

    }
}
