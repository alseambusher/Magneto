package wear.alse.com.Magneto;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;

public class FusedGyroscopeSensor implements SensorEventListener
{
    private static final String tag = FusedGyroscopeSensor.class
            .getSimpleName();

    public static final float FILTER_COEFFICIENT = 0.5f;

    public static final float EPSILON = 0.000000001f;

    // private static final float NS2S = 1.0f / 10000.0f;
    // Nano-second to second conversion
    private static final float NS2S = 1.0f / 1000000000.0f;

    // list to keep track of the observers
    private ArrayList<AngularVelocityListener> observersAngularVelocity;

    private boolean hasOrientation = false;

    private float dT = 0;

    private float omegaMagnitude = 0;

    private float thetaOverTwo = 0;
    private float sinThetaOverTwo = 0;
    private float cosThetaOverTwo = 0;

    private float[] gravity = new float[]
            { 0, 0, 0 };

    // angular speeds from gyro
    private float[] gyroscope = new float[3];

    // rotation matrix from gyro data
    private float[] gyroMatrix = new float[9];

    // orientation angles from gyro matrix
    private float[] gyroOrientation = new float[3];

    // magnetic field vector
    private float[] magnetic = new float[3];

    // orientation angles from accel and magnet
    private float[] orientation = new float[3];

    // final orientation angles from sensor fusion
    private float[] fusedOrientation = new float[3];

    // accelerometer and magnetometer based rotation matrix
    private float[] rotationMatrix = new float[9];

    private float[] absoluteFrameOrientation = new float[3];

    // copy the new gyro values into the gyro array
    // convert the raw gyro data into a rotation vector
    private float[] deltaVector = new float[4];

    // convert rotation vector into rotation matrix
    private float[] deltaMatrix = new float[9];

    private long timeStamp;

    private boolean initState = false;

    private MeanFilter meanFilterAcceleration;
    private MeanFilter meanFilterMagnetic;


    public FusedGyroscopeSensor()
    {
        super();

        observersAngularVelocity = new ArrayList<AngularVelocityListener>();

        meanFilterAcceleration = new MeanFilter();
        meanFilterAcceleration.setWindowSize(10);

        meanFilterMagnetic = new MeanFilter();
        meanFilterMagnetic.setWindowSize(10);

        gyroOrientation[0] = 0.0f;
        gyroOrientation[1] = 0.0f;
        gyroOrientation[2] = 0.0f;

        // Initialize gyroMatrix with identity matrix
        gyroMatrix[0] = 1.0f;
        gyroMatrix[1] = 0.0f;
        gyroMatrix[2] = 0.0f;
        gyroMatrix[3] = 0.0f;
        gyroMatrix[4] = 1.0f;
        gyroMatrix[5] = 0.0f;
        gyroMatrix[6] = 0.0f;
        gyroMatrix[7] = 0.0f;
        gyroMatrix[8] = 1.0f;

    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            onGravitySensorChanged(event.values, event.timestamp);
        }

        if (event.sensor.getType() == Sensor.TYPE_GRAVITY)
        {
            onGravitySensorChanged(event.values, event.timestamp);
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
        {
            onMagneticSensorChanged(event.values, event.timestamp);
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
        {
            onGyroscopeSensorChanged(event.values, event.timestamp);
        }

    }

    public void notifyObservers()
    {
        System.arraycopy(gyroOrientation, 0, absoluteFrameOrientation, 0, 3);

        for (AngularVelocityListener g : observersAngularVelocity)
        {
            g.onAngularVelocitySensorChanged(absoluteFrameOrientation,
                    timeStamp);
        }
    }

    public void registerObserver(AngularVelocityListener g)
    {
        observersAngularVelocity.add(g);
    }

    public void removeObserver(AngularVelocityListener g)
    {
        int i = observersAngularVelocity.indexOf(g);
        if (i >= 0)
        {
            observersAngularVelocity.remove(i);
        }
    }

    /**
     * Calculates orientation angles from accelerometer and magnetometer output.
     */
    private void calculateOrientation()
    {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity,
                magnetic))
        {
            SensorManager.getOrientation(rotationMatrix, orientation);

            hasOrientation = true;
        }
    }

    private float[] getRotationMatrixFromOrientation(float[] orientation)
    {
        float[] xM = new float[9];
        float[] yM = new float[9];
        float[] zM = new float[9];

        float sinX = (float) Math.sin(orientation[1]);
        float cosX = (float) Math.cos(orientation[1]);
        float sinY = (float) Math.sin(orientation[2]);
        float cosY = (float) Math.cos(orientation[2]);
        float sinZ = (float) Math.sin(orientation[0]);
        float cosZ = (float) Math.cos(orientation[0]);

        // rotation about x-axis (pitch)
        xM[0] = 1.0f;
        xM[1] = 0.0f;
        xM[2] = 0.0f;
        xM[3] = 0.0f;
        xM[4] = cosX;
        xM[5] = sinX;
        xM[6] = 0.0f;
        xM[7] = -sinX;
        xM[8] = cosX;

        // rotation about y-axis (roll)
        yM[0] = cosY;
        yM[1] = 0.0f;
        yM[2] = sinY;
        yM[3] = 0.0f;
        yM[4] = 1.0f;
        yM[5] = 0.0f;
        yM[6] = -sinY;
        yM[7] = 0.0f;
        yM[8] = cosY;

        // rotation about z-axis (azimuth)
        zM[0] = cosZ;
        zM[1] = sinZ;
        zM[2] = 0.0f;
        zM[3] = -sinZ;
        zM[4] = cosZ;
        zM[5] = 0.0f;
        zM[6] = 0.0f;
        zM[7] = 0.0f;
        zM[8] = 1.0f;

        // Build the composite rotation... rotation order is y, x, z (roll,
        // pitch, azimuth)
        float[] resultMatrix = matrixMultiplication(xM, yM);
        resultMatrix = matrixMultiplication(zM, resultMatrix);
        return resultMatrix;
    }
    private void getRotationVectorFromGyro(float timeFactor)
    {

        // Calculate the angular speed of the sample
        omegaMagnitude = (float) Math.sqrt(Math.pow(gyroscope[0], 2)
                + Math.pow(gyroscope[1], 2) + Math.pow(gyroscope[2], 2));

        // Normalize the rotation vector if it's big enough to get the axis
        if (omegaMagnitude > EPSILON)
        {
            gyroscope[0] /= omegaMagnitude;
            gyroscope[1] /= omegaMagnitude;
            gyroscope[2] /= omegaMagnitude;
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        thetaOverTwo = omegaMagnitude * timeFactor;
        sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
        cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

        deltaVector[0] = sinThetaOverTwo * gyroscope[0];
        deltaVector[1] = sinThetaOverTwo * gyroscope[1];
        deltaVector[2] = sinThetaOverTwo * gyroscope[2];
        deltaVector[3] = cosThetaOverTwo;
    }

    /**
     * Multiply A by B.
     *
     * @param A
     * @param B
     * @return A*B
     */
    private float[] matrixMultiplication(float[] A, float[] B)
    {
        float[] result = new float[9];

        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

        return result;
    }

    /**
     * Calculate the fused orientation.
     */
    private void calculateFusedOrientation()
    {
        float oneMinusCoeff = (1.0f - FILTER_COEFFICIENT);

		/*
		 * Fix for 179° <--> -179° transition problem: Check whether one of the
		 * two orientation angles (gyro or accMag) is negative while the other
		 * one is positive. If so, add 360° (2 * math.PI) to the negative value,
		 * perform the sensor fusion, and remove the 360° from the result if it
		 * is greater than 180°. This stabilizes the output in
		 * positive-to-negative-transition cases.
		 */

        // azimuth
        if (gyroOrientation[0] < -0.5 * Math.PI && orientation[0] > 0.0)
        {
            fusedOrientation[0] = (float) (FILTER_COEFFICIENT
                    * (gyroOrientation[0] + 2.0 * Math.PI) + oneMinusCoeff
                    * orientation[0]);
            fusedOrientation[0] -= (fusedOrientation[0] > Math.PI) ? 2.0 * Math.PI
                    : 0;
        }
        else if (orientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0)
        {
            fusedOrientation[0] = (float) (FILTER_COEFFICIENT
                    * gyroOrientation[0] + oneMinusCoeff
                    * (orientation[0] + 2.0 * Math.PI));
            fusedOrientation[0] -= (fusedOrientation[0] > Math.PI) ? 2.0 * Math.PI
                    : 0;
        }
        else
        {
            fusedOrientation[0] = FILTER_COEFFICIENT * gyroOrientation[0]
                    + oneMinusCoeff * orientation[0];
        }

        // pitch
        if (gyroOrientation[1] < -0.5 * Math.PI && orientation[1] > 0.0)
        {
            fusedOrientation[1] = (float) (FILTER_COEFFICIENT
                    * (gyroOrientation[1] + 2.0 * Math.PI) + oneMinusCoeff
                    * orientation[1]);
            fusedOrientation[1] -= (fusedOrientation[1] > Math.PI) ? 2.0 * Math.PI
                    : 0;
        }
        else if (orientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0)
        {
            fusedOrientation[1] = (float) (FILTER_COEFFICIENT
                    * gyroOrientation[1] + oneMinusCoeff
                    * (orientation[1] + 2.0 * Math.PI));
            fusedOrientation[1] -= (fusedOrientation[1] > Math.PI) ? 2.0 * Math.PI
                    : 0;
        }
        else
        {
            fusedOrientation[1] = FILTER_COEFFICIENT * gyroOrientation[1]
                    + oneMinusCoeff * orientation[1];
        }

        // roll
        if (gyroOrientation[2] < -0.5 * Math.PI && orientation[2] > 0.0)
        {
            fusedOrientation[2] = (float) (FILTER_COEFFICIENT
                    * (gyroOrientation[2] + 2.0 * Math.PI) + oneMinusCoeff
                    * orientation[2]);
            fusedOrientation[2] -= (fusedOrientation[2] > Math.PI) ? 2.0 * Math.PI
                    : 0;
        }
        else if (orientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0)
        {
            fusedOrientation[2] = (float) (FILTER_COEFFICIENT
                    * gyroOrientation[2] + oneMinusCoeff
                    * (orientation[2] + 2.0 * Math.PI));
            fusedOrientation[2] -= (fusedOrientation[2] > Math.PI) ? 2.0 * Math.PI
                    : 0;
        }
        else
        {
            fusedOrientation[2] = FILTER_COEFFICIENT * gyroOrientation[2]
                    + oneMinusCoeff * orientation[2];
        }

        // overwrite gyro matrix and orientation with fused orientation
        // to comensate gyro drift
        gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);

        System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);

        notifyObservers();
    }

    public void onAccelerationSensorChanged(float[] gravity, long timeStamp)
    {
        // Get a local copy of the raw magnetic values from the device sensor.
        System.arraycopy(gravity, 0, this.gravity, 0, gravity.length);

        this.gravity = meanFilterAcceleration.filterFloat(this.gravity);

        calculateOrientation();
    }


    public void onMagneticSensorChanged(float[] magnetic, long timeStamp)
    {
        // Get a local copy of the raw magnetic values from the device sensor.
        System.arraycopy(magnetic, 0, this.magnetic, 0, magnetic.length);

        this.magnetic = meanFilterMagnetic.filterFloat(this.magnetic);
    }

    public void onGravitySensorChanged(float[] gravity, long timeStamp)
    {
        // Get a local copy of the raw magnetic values from the device sensor.
        System.arraycopy(gravity, 0, this.gravity, 0, gravity.length);

        this.gravity = meanFilterAcceleration.filterFloat(this.gravity);

        calculateOrientation();
    }

    public void onGyroscopeSensorChanged(float[] gyroscope, long timeStamp)
    {
        // don't start until first accelerometer/magnetometer orientation has
        // been acquired
        if (!hasOrientation)
        {
            return;
        }

        // Initialization of the gyroscope based rotation matrix
        if (!initState)
        {
            gyroMatrix = matrixMultiplication(gyroMatrix, rotationMatrix);
            initState = true;
        }

        if (this.timeStamp != 0)
        {
            dT = (timeStamp - this.timeStamp) * NS2S;

            System.arraycopy(gyroscope, 0, this.gyroscope, 0, 3);
            getRotationVectorFromGyro(dT / 2.0f);
        }

        // measurement done, save current time for next interval
        this.timeStamp = timeStamp;

        // Get the rotation matrix from the gyroscope
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

        // Apply the new rotation interval on the gyroscope based rotation
        // matrix to form a composite rotation matrix. The product of two
        // rotation matricies is a rotation matrix...
        // Multiplication of rotation matrices corresponds to composition of
        // rotations... Which in this case are the rotation matrix from the
        // fused orientation and the rotation matrix from the current gyroscope
        // outputs.
        gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

        // Get the gyroscope based orientation from the composite rotation
        // matrix. This orientation will be fused via complementary filter with
        // the orientation from the acceleration sensor and magnetic sensor.
        SensorManager.getOrientation(gyroMatrix, gyroOrientation);

        calculateFusedOrientation();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        // TODO Auto-generated method stub

    }
}