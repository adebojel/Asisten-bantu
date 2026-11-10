package com.asisten.bantu;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

/**
 * Teman ngobrol Kobra. Suara ditangkap layanan utama (tidak ada mikrofon ganda).
 * Urutan jawaban: otak offline (kalkulator dan pengetahuan dasar), lalu AI online jika ada internet dan API key.
 */
public class AiChatActivity extends AppCompatActivity {

    static volatile AiChatActivity current;

    private TextView tvHistory;
    private ScrollView scroll;
    private Speaker speaker;
    private KobraConfig config;
    private AiClient ai;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder transcript = new StringBuilder();
    private final ArrayList<String> recent = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);
        tvHistory = findViewById(R.id.tvChatHistory);
        scroll = findViewById(R.id.scrollChat);
        Button btnListen = findViewById(R.id.btnListenChat);
        Button btnClose = findViewById(R.id.btnCloseChat);
        speaker = Speaker.get(this);
        config = new KobraConfig(this);
        ai = new AiClient(config);

        btnListen.setOnClickListener(v -> AsistenAccessibilityService.requestListen());
        btnClose.setOnClickListener(v -> finish());

        String greeting = config.hasAi()
                ? "Halo. Saya Kobra, teman ngobrol Anda. Silakan bicara."
                : "Halo. Saya Kobra, teman ngobrol Anda. Tanpa API key AI, saya hanya bisa menjawab hal dasar. Isi API key di Pengaturan AI untuk obrolan yang lebih pintar.";
        append("Kobra", greeting);
        speaker.speak(greeting);
    }

    @Override protected void onResume() {
        super.onResume();
        current = this;
    }

    @Override protected void onPause() {
        super.onPause();
        if (current == this) current = null;
    }

    /** Dipanggil layanan utama ketika suara diterima saat halaman ini terbuka. */
    void onVoice(String text) {
        main.post(() -> {
            append("Anda", text);
            remember("Pengguna: " + text);

            String local = OfflineAi.get(this).answer(text);
            if (local != null) {
                respond(local);
                return;
            }
            if (!config.hasAi()) {
                respond("Saya belum bisa menjawab itu tanpa AI. Isi API key AI di Pengaturan untuk obrolan yang lebih pintar.");
                return;
            }
            if (!NetworkState.isOnline(this)) {
                respond("Internet sedang terputus, jadi saya hanya bisa menjawab hal dasar. Coba lagi saat tersambung.");
                return;
            }
            respond("Sebentar, saya pikirkan dulu.");
            StringBuilder context = new StringBuilder();
            for (String line : recent) context.append(line).append("\n");
            ai.chat(AiClient.SYSTEM_TUNANETRA, context.toString(), new AiClient.Callback() {
                @Override public void onResult(String answer) { respond(answer); }
                @Override public void onError(String message) { respond(message); }
            });
        });
    }

    private void respond(String reply) {
        append("Kobra", reply);
        remember("Kobra: " + reply);
        speaker.speak(reply);
    }

    private void remember(String line) {
        recent.add(line);
        if (recent.size() > 8) recent.remove(0);
    }

    private void append(String who, String text) {
        transcript.append(who).append(": ").append(text).append("\n\n");
        tvHistory.setText(transcript.toString());
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
