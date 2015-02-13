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

    static final Map<String, String> commandMap = new HashMap<String, String>() {{
        put("close window","1");
        put("minimise","2");
        put("minimize","2");
        put("maximize","3");
        put("minimise","3");
        put("next","4");
        put("fill","5");
        put("back","6");
        put("select all","7");
        put("minimize","8");
    }};

    public static void handleMsg(String msg){
        Object value = commandMap.get(msg);
        Log.d("alse",String.valueOf(value));
        Log.d("alse",msg);
        if(value!=null){
            write_new_voice(String.valueOf(value));
        }
    }

    public static void write_new_gesture(int x, int y, int z){
        String msg = ""+x+" "+y+" "+z+"\n";
        writeLog(msg, Common.MOTION_LOGFILE);
    }


    public static void write_new_voice(String msg){
        writeLog(msg+"\n", Common.VOICE_LOGFILE);
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
}
