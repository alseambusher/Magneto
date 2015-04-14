package wear.alse.com.Magneto;

/**
 * Created by salse on 2/13/15.
 */
public class Common {
    public static String MOUSE_LOGFILE = "/sdcard/motion.log";
    public static String VOICE_LOGFILE = "/sdcard/voice.log";
    public static String DICTATE_LOGFILE = "/sdcard/dictate.log";
    public static String NAVIGATION_LOGFILE = "/sdcard/nav.log";
    public static int THRESHOLD_ANGLE = 2;
    // speech recognizer keeps spawning every 5 seconds
    public static int TIME_DELAY_SPEECH_RECOGNIZER = 8;

    public static String MOTION_LEFT = "l";
    public static String MOTION_RIGHT = "r";
    public static String MOTION_TOP = "t";
    public static String MOTION_BOTTOM = "b";
    public static String MOTION_CENTER = "C";

    public static float[] matrixMultiplication(float[] A, float[] B)
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
}
