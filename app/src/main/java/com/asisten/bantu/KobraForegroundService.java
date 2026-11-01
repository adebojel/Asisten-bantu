
package com.asisten.bantu;
import android.app.*; import android.content.Intent; import android.os.Build; import android.os.IBinder; import android.speech.tts.TextToSpeech; import androidx.core.app.NotificationCompat; import java.util.Locale;
public class KobraForegroundService extends Service implements TextToSpeech.OnInitListener {
    private static final String CHANNEL_ID = "KobraOSV6"; private TextToSpeech tts; private boolean fromBoot = false;
    @Override public void onCreate() { super.onCreate(); createChannel(); tts = new TextToSpeech(this, this); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) fromBoot = intent.getBooleanExtra("from_boot", false);
        Intent ni = new Intent(this, KobraLauncherActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, ni, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("🐍 KOBRA OS V6 - Otak Super + Tongkat + AI").setContentText("Kobra tolong SOS. Kobra tongkat. Kobra teman ngobrol.").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(pi).setOngoing(true).build();
        startForeground(1001, n); return START_STICKY;
    }
    @Override public void onInit(int status) { if (status == TextToSpeech.SUCCESS) { tts.setLanguage(new Locale("id","ID")); tts.setSpeechRate(0.9f); if (fromBoot) tts.speak("Halo. Kobra OS V6 Otak Super telah aktif. Fitur baru: baca KTP KK, mode tongkat pintar deteksi halangan dengan getar, dan teman ngobrol AI offline. Ucapkan Kobra baca KTP, Kobra mode tongkat, atau Kobra teman ngobrol.", TextToSpeech.QUEUE_FLUSH, null, null); } }
    private void createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Kobra OS V6", NotificationManager.IMPORTANCE_LOW); c.setDescription("Kobra V6 tetap hidup"); getSystemService(NotificationManager.class).createNotificationChannel(c); } }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() { Intent r = new Intent(this, KobraForegroundService.class); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(r); else startService(r); super.onDestroy(); }
}

