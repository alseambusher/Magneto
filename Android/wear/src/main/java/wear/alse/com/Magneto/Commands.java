package wear.alse.com.Magneto;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by salse on 2/13/15.
 */
public class Commands {

    public static String DICTATE="dictate";
    public static Boolean isDictation = false;
    static final Map<String, String> commandMap = new HashMap<String, String>() {{
        put("close window","1");
        put("minimise","2");
        put("minimize","2");
        put("maximize","3");
        put("maximise","3");
        put("next","4");
        //put("fill","5");
        put("back","6");
        put("select all","7");
        put("undo","8");
        put("copy","9");
        put("cut","10");
        put("right click","11");
        put("click","12");
        put("open quickbooks","13");
        put("paste","14");
        put("zoom in","15");
        put("zoomin","15");
        put("zoom out","16");
        put("take photo", "17");
        put("take video", "18");
        put("pause video", "19");
        put("unpause video", "20");
        put("help me", "21");
        put("lights on", "22");
        put("lights off", "23");
    }};

    public static void handleMsg(String msg){

        Object value = commandMap.get(msg);
        Log.d("alse",String.valueOf(value));
        Log.d("alse",msg);
        if(isDictation){
            Common.TIME_DELAY_SPEECH_RECOGNIZER = 8;
            write_new_dictation(msg);
            isDictation = false;
            return;
        }
        if(value!=null){
            write_new_voice(String.valueOf(value));
        }
        try {
           String  command = msg.split(" ")[0];
            if(command.toLowerCase().equals(DICTATE)){
                Common.TIME_DELAY_SPEECH_RECOGNIZER = 20;
                isDictation = true;
            }
        } catch (Exception e){

        }
    }

    public static void write_new_gesture(int x, int y, int z){
        String msg = ""+x+" "+y+" "+z+"\n";
        writeLog(msg, Common.MOUSE_LOGFILE);
    }

    public static void write_new_voice(String msg){
        writeLog(msg + "\n", Common.VOICE_LOGFILE);
    }

    public static void write_new_navigation(String nav){
        writeLog(nav + "\n", Common.NAVIGATION_LOGFILE);
    }

    public static void write_new_dictation(String msg){
        writeLog(msg, Common.DICTATE_LOGFILE);
    }

    public static void writeLog(String msg, String _file){
        File logFile= new File(_file);
        try {
            if(!logFile.exists())
                logFile.createNewFile();
            FileOutputStream fOut = new FileOutputStream(logFile, true);
            OutputStreamWriter myOutWriter =
                    new OutputStreamWriter(fOut);
            myOutWriter.append(msg);
            myOutWriter.close();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteLog(String _file){
        File logFile = new File(_file);
        if(logFile.exists()){
            try {
                logFile.delete();
            } catch (Exception e){
               e.printStackTrace();
            }
        }
    }
}
