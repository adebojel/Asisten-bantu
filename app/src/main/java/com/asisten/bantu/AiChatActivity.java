package com.asisten.bantu;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

/**
 * Teman ngobrol Kobra. Suara ditangkap layanan utama (tidak ada mikrofon ganda).
 * Urutan jawaban: otak offline (kalkulator dan pengetahuan dasar), model LLM offline Gemma 3 on-device,
 * lalu AI online jika ada internet dan API key.
 */
public class AiChatActivity extends AppCompatActivity {

    static volatile AiChatActivity current;

    private TextView tvHistory, tvModelStatusChat;
    private ScrollView scroll;
    private Button btnImportModelChat;
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
        tvModelStatusChat = findViewById(R.id.tvModelStatusChat);
        scroll = findViewById(R.id.scrollChat);
        btnImportModelChat = findViewById(R.id.btnImportModelChat);
        Button btnListen = findViewById(R.id.btnListenChat);
        Button btnResetSession = findViewById(R.id.btnResetChatSession);
        Button btnClose = findViewById(R.id.btnCloseChat);
        speaker = Speaker.get(this);
        config = new KobraConfig(this);
        ai = new AiClient(config);

        btnListen.setOnClickListener(v -> AsistenAccessibilityService.requestListen());
        btnResetSession.setOnClickListener(v -> resetChatSession());
        btnClose.setOnClickListener(v -> finish());
        btnImportModelChat.setOnClickListener(v -> {
            speaker.speak("Membuka pengaturan AI untuk mengimpor model offline.");
            startActivity(new Intent(this, SettingsActivity.class));
        });

        checkModelStatus();

        LocalLlmEngine engine = LocalLlmEngine.getInstance(this);
        boolean hasLocal = engine.isModelPresent();
        String greeting;
        if (config.hasAi()) {
            greeting = "Halo. Saya Kobra, teman ngobrol Anda. Silakan bicara.";
        } else if (hasLocal && !engine.isSafeMode()) {
            greeting = "Halo. Saya Kobra. Mesin AI offline Gemma 3 1B aktif dan siap digunakan. Silakan bicara.";
        } else if (hasLocal) {
            greeting = "Halo. Saya Kobra dalam Mode Aman perintah lokal karena mesin AI offline belum dapat dimuat ke memori. Silakan bicara.";
        } else {
            greeting = "Halo. Saya Kobra dalam Mode Aman. Belum ada API key AI online atau berkas model offline. Anda dapat mengimpor model offline .task atau mengisi API key di Pengaturan AI.";
        }
        append("Kobra", greeting);
        speaker.speak(greeting);
    }

    @Override protected void onResume() {
        super.onResume();
        current = this;
        checkModelStatus();
    }

    private void checkModelStatus() {
        LocalLlmEngine engine = LocalLlmEngine.getInstance(this);
        boolean hasModel = engine.isModelPresent();
        String styleInfo = "Gaya: " + config.getAssistantStyleLabel()
                + " | Memori: " + engine.getSessionTurnCount() + " giliran";
        if (hasModel) {
            long mb = engine.getModelFile().length() / (1024 * 1024);
            if (engine.isSafeMode() && !engine.getLastError().isEmpty()) {
                tvModelStatusChat.setText("⚠️ Mode Aman: " + engine.getLastError() + " (" + mb + " MB) • " + styleInfo);
                tvModelStatusChat.setTextColor(0xFFFFAB40);
                btnImportModelChat.setVisibility(View.VISIBLE);
            } else {
                tvModelStatusChat.setText("🧠 Model offline: Gemma 3 1B siap (" + mb + " MB) • " + styleInfo);
                tvModelStatusChat.setTextColor(0xFF00E676);
                btnImportModelChat.setVisibility(View.GONE);
            }
        } else {
            tvModelStatusChat.setText("⚠️ Mode Aman: Model offline (.task) belum terpasang • Gaya: " + config.getAssistantStyleLabel());
            tvModelStatusChat.setTextColor(0xFFFFEB3B);
            btnImportModelChat.setVisibility(View.VISIBLE);
        }
    }

    private void resetChatSession() {
        LocalLlmEngine.getInstance(this).clearSessionHistory();
        recent.clear();
        transcript.setLength(0);
        String msg = "Sesi percakapan baru dimulai. Konteks sebelumnya dibersihkan.";
        append("Kobra", msg);
        checkModelStatus();
        speaker.speak(msg);
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
            LocalLlmEngine localLlm = LocalLlmEngine.getInstance(this);
            boolean hasLocalModel = localLlm.isModelPresent();

            if (!NetworkState.isOnline(this)) {
                if (hasLocalModel) {
                    askOfflineLlm(text, localLlm);
                    return;
                }
                respond("Mode Aman aktif karena internet terputus dan model offline belum dipasang. Anda tetap bisa memakai kalkulator suara dan perintah sistem.");
                return;
            }
            if (!config.hasAi()) {
                if (hasLocalModel) {
                    askOfflineLlm(text, localLlm);
                    return;
                }
                respond("Mode Aman aktif. Saya belum bisa menjawab pertanyaan bebas tanpa AI. Impor model offline .task atau isi API key di Pengaturan AI.");
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

    private void askOfflineLlm(String text, LocalLlmEngine localLlm) {
        if (localLlm.isSafeMode() && !localLlm.getLastError().isEmpty()) {
            fallbackSafeMode(localLlm.getLastError());
            return;
        }
        append("Kobra", "Sedang berpikir offline (Gemma 3)...");
        LocalLlmEngine.StreamingCallback cb = new LocalLlmEngine.StreamingCallback() {
            private final StringBuilder buffer = new StringBuilder();
            @Override public void onPartialResult(String partialText, boolean isComplete) {
                if (partialText != null) buffer.append(partialText);
                if (isComplete) {
                    String reply = buffer.toString().trim();
                    if (!reply.isEmpty()) {
                        remember("Kobra: " + reply);
                        append("Kobra", reply);
                        checkModelStatus();
                    }
                }
            }
            @Override public void onError(String message) {
                checkModelStatus();
                respond("Beralih ke Mode Aman perintah lokal: " + message);
            }
        };

        if (localLlm.isInitialized()) {
            localLlm.generateResponseAsync(text, speaker, cb);
        } else {
            new Thread(() -> {
                if (localLlm.initEngine()) {
                    localLlm.generateResponseAsync(text, speaker, cb);
                } else {
                    main.post(() -> {
                        checkModelStatus();
                        fallbackSafeMode(localLlm.getLastError());
                    });
                }
            }).start();
        }
    }

    private void fallbackSafeMode(String reason) {
        String detail = (reason == null || reason.isEmpty()) ? "Model tidak dapat dimuat" : reason;
        respond("Model offline tidak dapat dimuat (" + detail + "). Kobra beralih ke Mode Aman untuk perintah sistem dan kalkulator lokal.");
    }

    private void respond(String reply) {
        append("Kobra", reply);
        remember("Kobra: " + reply);
        speaker.speak(reply);
    }

    private void remember(String line) {
        recent.add(line);
        if (recent.size() > 16) recent.remove(0);
    }

    private void append(String who, String text) {
        transcript.append(who).append(": ").append(text).append("\n\n");
        tvHistory.setText(transcript.toString());
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
