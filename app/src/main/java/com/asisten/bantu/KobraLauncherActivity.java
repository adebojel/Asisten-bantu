
package com.asisten.bantu;

import android.content.Intent; import android.content.pm.ApplicationInfo; import android.content.pm.PackageManager; import android.os.Bundle; import android.os.Handler; import android.os.Looper; import android.speech.tts.TextToSpeech; import android.widget.Button; import android.widget.TextView; import androidx.appcompat.app.AppCompatActivity; import androidx.recyclerview.widget.LinearLayoutManager; import androidx.recyclerview.widget.RecyclerView; import java.text.SimpleDateFormat; import java.util.*;

public class KobraLauncherActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts; private TextView tvClock, tvListen, tvScreenReader; private Handler handler = new Handler(Looper.getMainLooper()); private Runnable clockRunnable; private List<ApplicationInfo> appList = new ArrayList<>();
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_launcher);
        tvClock = findViewById(R.id.tvClock); tvListen = findViewById(R.id.tvListen); tvScreenReader = findViewById(R.id.tvScreenReader); RecyclerView rv = findViewById(R.id.rvApps); rv.setLayoutManager(new LinearLayoutManager(this));
        tts = new TextToSpeech(this, this); loadApps(rv);
        clockRunnable = new Runnable() { @Override public void run() { SimpleDateFormat sdf = new SimpleDateFormat("HH:mm - EEEE, dd MMMM", new Locale("id","ID")); tvClock.setText(sdf.format(new Date())); handler.postDelayed(this, 60000); } }; handler.post(clockRunnable);
        findViewById(R.id.btnReadScreen).setOnClickListener(v -> { if (AsistenAccessibilityService.getInstance() != null) { String text = AsistenAccessibilityService.getInstance().readCurrentScreenContentForLauncher(); tvScreenReader.setText(text); speak(text); } else speak("Aktifkan aksesibilitas dulu"); });
        findViewById(R.id.btnMic).setOnClickListener(v -> speak("Ucapkan Kobra. Contoh: baca KTP, mode tongkat, teman ngobrol, baca uang, tolong"));
        findViewById(R.id.btnVisionMoney).setOnClickListener(v -> openVision("money")); findViewById(R.id.btnVisionColor).setOnClickListener(v -> openVision("color")); findViewById(R.id.btnVisionFace).setOnClickListener(v -> openVision("face")); findViewById(R.id.btnVisionKtp).setOnClickListener(v -> openVision("ktp"));
        findViewById(R.id.btnNavStick).setOnClickListener(v -> { Intent i = new Intent(this, NavigationActivity.class); startActivity(i); speak("Membuka mode tongkat pintar. Arahkan kamera ke depan."); });
        findViewById(R.id.btnAiChat).setOnClickListener(v -> { Intent i = new Intent(this, AiChatActivity.class); startActivity(i); speak("Membuka teman ngobrol AI offline."); });
        findViewById(R.id.btnSosEmergency).setOnClickListener(v -> { if (AsistenAccessibilityService.getInstance() != null) AsistenAccessibilityService.getInstance().triggerSosFromLauncher(); else speak("SOS butuh aksesibilitas aktif"); });
        Intent fg = new Intent(this, KobraForegroundService.class); if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(fg); else startService(fg);
    }
    private void openVision(String mode) { Intent intent = new Intent(this, VisionActivity.class); intent.putExtra("mode", mode); startActivity(intent); }
    private void loadApps(RecyclerView rv) { PackageManager pm = getPackageManager(); List<ApplicationInfo> all = pm.getInstalledApplications(PackageManager.GET_META_DATA); for (ApplicationInfo ai : all) if (pm.getLaunchIntentForPackage(ai.packageName) != null) appList.add(ai); appList.sort((a,b) -> pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString())); rv.setAdapter(new AppAdapter()); }
    class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> { @Override public VH onCreateViewHolder(android.view.ViewGroup p, int t) { android.view.View v = getLayoutInflater().inflate(R.layout.item_app, p, false); return new VH(v); } @Override public void onBindViewHolder(VH h, int pos) { ApplicationInfo ai = appList.get(pos); String name = getPackageManager().getApplicationLabel(ai).toString(); h.tv.setText(name); h.itemView.setOnClickListener(v -> { Intent intent = getPackageManager().getLaunchIntentForPackage(ai.packageName); if (intent != null) { startActivity(intent); speak("Membuka " + name); } }); } @Override public int getItemCount() { return appList.size(); } class VH extends RecyclerView.ViewHolder { TextView tv; VH(android.view.View v) { super(v); tv = v.findViewById(R.id.tvAppName); } } }
    @Override public void onInit(int status) { if (status == TextToSpeech.SUCCESS) { tts.setLanguage(new Locale("id","ID")); tts.setSpeechRate(0.9f); speak("Halo. Kobra OS V6 Otak Super aktif. Baru: baca KTP KK dokumen, mode tongkat pintar deteksi halangan getar, dan teman ngobrol AI offline. Ucapkan Kobra baca KTP, Kobra mode tongkat, atau Kobra teman ngobrol."); } }
    private void speak(String s){ if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, null); tvListen.setText("🗣️ " + s); }
    @Override protected void onDestroy(){ if (handler != null && clockRunnable != null) handler.removeCallbacks(clockRunnable); if (tts != null){ tts.stop(); tts.shutdown(); } super.onDestroy(); }
}

