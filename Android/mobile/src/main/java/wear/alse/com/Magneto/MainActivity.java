package wear.alse.com.Magneto;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;


public class MainActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
            int notificationId = 001;

            NotificationCompat.Builder notificationBuilder =
                    new NotificationCompat.Builder(MainActivity.this)
                            .setSmallIcon(R.drawable.logo)
                            .setContentTitle("Magneto")
                            .setOngoing(true)
                            .setContentText("Running");

            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(MainActivity.this);

            notificationManager.notify(notificationId, notificationBuilder.build());
    }

}
