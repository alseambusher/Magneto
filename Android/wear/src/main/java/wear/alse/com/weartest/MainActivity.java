package wear.alse.com.weartest;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener,
        FusedGyroscopeSensorListener
{

    public static final float EPSILON = 0.000000001f;

    private static final String tag = MainActivity.class.getSimpleName();
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final int MEAN_FILTER_WINDOW = 10;
    private static final int MIN_SAMPLE_COUNT = 30;

    private boolean hasInitialOrientation = false;
    private boolean stateInitializedCalibrated = false;
    private boolean stateInitializedRaw = false;

    private boolean useFusedEstimation = false;
    private boolean useRadianUnits = false;
    private String LOGFILE = "/sdcard/motion.log";

    private DecimalFormat df;

    // Calibrated maths.
    private float[] currentRotationMatrixCalibrated;
    private float[] deltaRotationMatrixCalibrated;
    private float[] deltaRotationVectorCalibrated;
    private float[] gyroscopeOrientationCalibrated;

    // Uncalibrated maths
    private float[] currentRotationMatrixRaw;
    private float[] deltaRotationMatrixRaw;
    private float[] deltaRotationVectorRaw;
    private float[] gyroscopeOrientationRaw;

    // accelerometer and magnetometer based rotation matrix
    private float[] initialRotationMatrix;

    private FusedGyroscopeSensor fusedGyroscopeSensor;


    private long timestampOldCalibrated = 0;
    private long timestampOldRaw = 0;

    private MeanFilter accelerationFilter;
    private MeanFilter magneticFilter;

    // We need the SensorManager to register for Sensor Events.
    private SensorManager sensorManager;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
            }
        });
        initMaths();
        initSensors();
        initFilters();
        displaySpeechRecognizer();

    };


    public void onResume()
    {
        super.onResume();
        restart();
    }

    public void onPause()
    {
        super.onPause();

        reset();
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
        {
            onGyroscopeSensorChanged(event.values, event.timestamp);
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        {
            onGyroscopeSensorUncalibratedChanged(event.values, event.timestamp);
        }
    }

    @Override
    public void onAngularVelocitySensorChanged(float[] angularVelocity,
                                               long timeStamp)
    {
        //gaugeBearingCalibrated.updateBearing(angularVelocity[0]);
        //gaugeTiltCalibrated.updateRotation(angularVelocity);

        if (useRadianUnits)
        {

            //xAxisCalibrated.setText(df.format(angularVelocity[0]));
            //yAxisCalibrated.setText(df.format(angularVelocity[1]));
            //zAxisCalibrated.setText(df.format(angularVelocity[2]));
        }
        else
        {
            //xAxisCalibrated.setText(df.format(Math
                    //.toDegrees(angularVelocity[0])));
            //yAxisCalibrated.setText(df.format(Math
                    //.toDegrees(angularVelocity[1])));
            //zAxisCalibrated.setText(df.format(Math
                    //.toDegrees(angularVelocity[2])));
        }
    }

    public void onGyroscopeSensorChanged(float[] gyroscope, long timestamp)
    {
        // don't start until first accelerometer/magnetometer orientation has
        // been acquired
        if (!hasInitialOrientation)
        {
            return;
        }

        // Initialization of the gyroscope based rotation matrix
        if (!stateInitializedCalibrated)
        {
            currentRotationMatrixCalibrated = matrixMultiplication(
                    currentRotationMatrixCalibrated, initialRotationMatrix);

            stateInitializedCalibrated = true;
        }

        // This timestep's delta rotation to be multiplied by the current
        // rotation after computing it from the gyro sample data.
        if (timestampOldCalibrated != 0 && stateInitializedCalibrated)
        {
            final float dT = (timestamp - timestampOldCalibrated) * NS2S;

            // Axis of the rotation sample, not normalized yet.
            float axisX = gyroscope[0];
            float axisY = gyroscope[1];
            float axisZ = gyroscope[2];

            // Calculate the angular speed of the sample
            float omegaMagnitude = (float) Math.sqrt(axisX * axisX + axisY
                    * axisY + axisZ * axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            if (omegaMagnitude > EPSILON)
            {
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

        //gaugeBearingCalibrated.updateBearing(gyroscopeOrientationCalibrated[0]);
        //gaugeTiltCalibrated.updateRotation(gyroscopeOrientationCalibrated);

        if (useRadianUnits)
        {
            //Log.d("aaaaaaaaa",
                    //df.format(gyroscopeOrientationCalibrated[0]));
            //xAxisCalibrated.setText(df
                    //.format(gyroscopeOrientationCalibrated[0]));
            //yAxisCalibrated.setText(df
                    //.format(gyroscopeOrientationCalibrated[1]));
            //zAxisCalibrated.setText(df
                    //.format(gyroscopeOrientationCalibrated[2]));
        }
        else
        {
            Log.d("aaaaaaaaab",
                    String.valueOf(Math
                            .toDegrees(gyroscopeOrientationCalibrated[0])));
            //xAxisCalibrated.setText(df.format(Math
                    //.toDegrees(gyroscopeOrientationCalibrated[0])));
            //yAxisCalibrated.setText(df.format(Math
                    //.toDegrees(gyroscopeOrientationCalibrated[1])));
            //zAxisCalibrated.setText(df.format(Math
                    //.toDegrees(gyroscopeOrientationCalibrated[2])));
        }
    }

    public void onGyroscopeSensorUncalibratedChanged(float[] gyroscope,
                                                     long timestamp)
    {
        // don't start until first accelerometer/magnetometer orientation has
        // been acquired
        if (!hasInitialOrientation)
        {
            return;
        }

        // Initialization of the gyroscope based rotation matrix
        if (!stateInitializedRaw)
        {
            currentRotationMatrixRaw = matrixMultiplication(
                    currentRotationMatrixRaw, initialRotationMatrix);

            stateInitializedRaw = true;

        }

        // This timestep's delta rotation to be multiplied by the current
        // rotation after computing it from the gyro sample data.
        if (timestampOldRaw != 0 && stateInitializedRaw)
        {
            final float dT = (timestamp - timestampOldRaw) * NS2S;

            // Axis of the rotation sample, not normalized yet.
            float axisX = gyroscope[0];
            float axisY = gyroscope[1];
            float axisZ = gyroscope[2];

            // Calculate the angular speed of the sample
            float omegaMagnitude = (float) Math.sqrt(axisX * axisX + axisY
                    * axisY + axisZ * axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            if (omegaMagnitude > EPSILON)
            {
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
    private void initFilters()
    {
        accelerationFilter = new MeanFilter();
        accelerationFilter.setWindowSize(MEAN_FILTER_WINDOW);

        magneticFilter = new MeanFilter();
        magneticFilter.setWindowSize(MEAN_FILTER_WINDOW);
    }

    /**
     * Initialize the data structures required for the maths.
     */
    private void initMaths()
    {
        initialRotationMatrix = new float[9];

        deltaRotationVectorCalibrated = new float[4];
        deltaRotationMatrixCalibrated = new float[9];
        currentRotationMatrixCalibrated = new float[9];
        gyroscopeOrientationCalibrated = new float[3];

        // Initialize the current rotation matrix as an identity matrix...
        currentRotationMatrixCalibrated[0] = 1.0f;
        currentRotationMatrixCalibrated[4] = 1.0f;
        currentRotationMatrixCalibrated[8] = 1.0f;

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
    private void initSensors()
    {
        sensorManager = (SensorManager) this
                .getSystemService(Context.SENSOR_SERVICE);

        fusedGyroscopeSensor = new FusedGyroscopeSensor();
    }

    private float[] matrixMultiplication(float[] a, float[] b)
    {
        float[] result = new float[9];

        result[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
        result[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
        result[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];

        result[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
        result[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
        result[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];

        result[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
        result[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
        result[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];

        return result;
    }

    /**
     * Restarts all of the sensor observers and resets the activity to the
     * initial state. This should only be called *after* a call to reset().
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void restart()
    {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);

        // Do not register for gyroscope updates if we are going to use the
        // fused version of the sensor...
        if (!useFusedEstimation)
        {
            boolean enabled = sensorManager.registerListener(this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                    SensorManager.SENSOR_DELAY_FASTEST);

            if (!enabled)
            {
                showGyroscopeNotAvailableAlert();
            }
        }

        // If we want to use the fused version of the gyroscope sensor.
        if (useFusedEstimation)
        {
            boolean hasGravity = sensorManager.registerListener(
                    fusedGyroscopeSensor,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                    SensorManager.SENSOR_DELAY_FASTEST);

            // If for some reason the gravity sensor does not exist, fall back
            // onto the acceleration sensor.
            if (!hasGravity)
            {
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
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void reset()
    {
        sensorManager.unregisterListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));

        sensorManager.unregisterListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));

        if (!useFusedEstimation)
        {
            sensorManager.unregisterListener(this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
        }

        if (useFusedEstimation)
        {
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

    private void showGyroscopeNotAvailableAlert()
    {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        // set title
        alertDialogBuilder.setTitle("Gyroscope Not Available");

        // set dialog message
        alertDialogBuilder
                .setMessage(
                        "Your device is not equipped with a gyroscope or it is not responding...")
                .setCancelable(false)
                .setNegativeButton("I'll look around...",
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                // if this button is clicked, just close
                                // the dialog box and do nothing
                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    private void writeLog(String msg){
        File logFile= new File(LOGFILE);
        try {
            if(!logFile.exists())
                logFile.createNewFile();
            FileOutputStream fOut = new FileOutputStream(logFile, true);
            OutputStreamWriter myOutWriter =
                    new OutputStreamWriter(fOut);
            myOutWriter.append(msg);
            myOutWriter.close();
            fOut.close();
            Log.d("aaaaaaaaaaaaa", "aaaaaaaaaaaa");
        } catch (IOException e) {
            Log.d("aaaaaaaaaaaaa","aaaaaaaaaaaaaaaaaaaaaaaaaaa");
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        // TODO Auto-generated method stub

    }

    private static final int SPEECH_REQUEST_CODE = 0;

    // Create an intent that can start the Speech Recognizer activity
    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    // Start the activity, the intent will be populated with the speech text
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
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
            SpeechSynthesis.handleMsg(spokenText);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}

