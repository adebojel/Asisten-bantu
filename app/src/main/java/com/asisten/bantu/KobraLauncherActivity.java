package com.asisten.bantu;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Layar utama Kobra: tombol besar dan daftar aplikasi, semuanya bisa dijalankan lewat suara. */
public class KobraLauncherActivity extends AppCompatActivity {

    private TextView tvClock, tvListen, tvScreenReader;
    private final KobraScreenHelper screenHelper = new KobraScreenHelper();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            tvClock.setText(new SimpleDateFormat("HH:mm - EEEE, dd MMMM", new Locale("id", "ID")).format(new Date()));
            handler.postDelayed(this, 30000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        tvClock = findViewById(R.id.tvClock);
        tvListen = findViewById(R.id.tvListen);
        tvScreenReader = findViewById(R.id.tvScreenReader);

        RecyclerView rv = findViewById(R.id.rvApps);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new AppAdapter(loadApps()));

        findViewById(R.id.btnReadScreen).setOnClickListener(v -> readScreen());
        findViewById(R.id.btnMic).setOnClickListener(v -> {
            if (AsistenAccessibilityService.getInstance() == null) speak("Aktifkan aksesibilitas Kobra dulu di menu Setup.");
            else AsistenAccessibilityService.requestListen();
        });
        findViewById(R.id.btnVisionMoney).setOnClickListener(v -> openVision("money"));
        findViewById(R.id.btnVisionColor).setOnClickListener(v -> openVision("color"));
        findViewById(R.id.btnVisionFace).setOnClickListener(v -> openVision("face"));
        findViewById(R.id.btnVisionKtp).setOnClickListener(v -> openVision("ktp"));
        findViewById(R.id.btnNavStick).setOnClickListener(v ->
                KobraScreenHelper.safeStartActivity(this, new Intent(this, NavigationActivity.class), Speaker.get(this), "Gagal membuka Mode Tongkat."));
        findViewById(R.id.btnAiChat).setOnClickListener(v ->
                KobraScreenHelper.safeStartActivity(this, new Intent(this, AiChatActivity.class), Speaker.get(this), "Gagal membuka Teman Ngobrol AI."));
        findViewById(R.id.btnSosEmergency).setOnClickListener(v -> {
            AsistenAccessibilityService s = AsistenAccessibilityService.getInstance();
            if (s == null) speak("Aktifkan aksesibilitas Kobra dulu di menu Setup.");
            else s.triggerSos();
        });

        if (!Perms.hasAll(this, Perms.required())) ActivityCompat.requestPermissions(this, Perms.required(), 100);
        KobraForegroundService.start(this);
        handler.post(clockTick);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (screenHelper.handleKeyEvent(this, event, Speaker.get(this))) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (screenHelper.handleTouchEvent(this, ev, Speaker.get(this))) return true;
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        super.onDestroy();
    }

    private void readScreen() {
        AsistenAccessibilityService s = AsistenAccessibilityService.getInstance();
        if (s == null) { speak("Aktifkan aksesibilitas Kobra dulu di menu Setup."); return; }
        String text = s.readScreenText();
        tvScreenReader.setText(text);
        speak(text);
    }

    private void openVision(String mode) {
        Intent i = new Intent(this, VisionActivity.class);
        i.putExtra("mode", mode);
        KobraScreenHelper.safeStartActivity(this, i, Speaker.get(this), "Gagal membuka kamera Mata Kedua.");
    }

    private void speak(String text) {
        tvListen.setText("🗣️ " + text);
        Speaker.get(this).speak(text);
    }

    private List<ApplicationInfo> loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> out = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (pm.getLaunchIntentForPackage(ai.packageName) != null) out.add(ai);
        }
        Collections.sort(out, (a, b) -> pm.getApplicationLabel(a).toString()
                .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        return out;
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {
        private final List<ApplicationInfo> items;
        private final PackageManager pm = getPackageManager();

        AppAdapter(List<ApplicationInfo> items) { this.items = items; }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_app, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH holder, int position) {
            ApplicationInfo app = items.get(position);
            String label = pm.getApplicationLabel(app).toString();
            holder.tv.setText(label);
            holder.itemView.setOnClickListener(v -> {
                Intent i = pm.getLaunchIntentForPackage(app.packageName);
                if (i != null && KobraScreenHelper.safeStartActivity(KobraLauncherActivity.this, i,
                        Speaker.get(KobraLauncherActivity.this), "Tidak dapat membuka " + label + ".")) {
                    speak("Membuka " + label);
                }
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(View v) {
                super(v);
                tv = v.findViewById(R.id.tvAppName);
            }
        }
    }
}
