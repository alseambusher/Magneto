package wear.alse.com.Magneto;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * Created by salse on 2/13/15.
 */
public class Commands {

    public static void handleMsg(String msg){
        //TODO
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
