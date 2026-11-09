package com.asisten.bantu;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

public class AiChatActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private TextView tvHistory; 
    private TextToSpeech tts; 
    private SpeechRecognizer recognizer; 
    private android.content.Intent recognizerIntent; 
    private Handler handler = new Handler(Looper.getMainLooper()); 
    private boolean isListening = false; 
    private StringBuilder chatLog = new StringBuilder();
    private Map<String, String> knowledgeBase;

    @Override 
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); 
        setContentView(R.layout.activity_ai_chat);
        
        tvHistory = findViewById(R.id.tvChatHistory); 
        Button btnListen = findViewById(R.id.btnListenChat); 
        Button btnClose = findViewById(R.id.btnCloseChat);
        
        tts = new TextToSpeech(this, this);
        buildKnowledgeBase();
        
        chatLog.append("Kobra AI: Halo Tuan! Saya Kobra AI teman ngobrol offline anda. ")
               .append("Saya bisa temani curhat, kasih motivasi, jawab pertanyaan, ceritakan lelucon, bantu pakai HP. ")
               .append("Semua offline, tanpa internet. Ucapkan sesuatu...\n\n");
               
        tvHistory.setText(chatLog.toString());

        btnListen.setOnClickListener(v -> startListening());
        btnClose.setOnClickListener(v -> finish());
        initRecognizer();
    }

    private void buildKnowledgeBase() {
        knowledgeBase = new HashMap<>();
        // Motivasi & Curhat
        knowledgeBase.put("sedih", "Saya mengerti anda sedih Tuan. Ingat, setiap kesulitan pasti ada kemudahan. Anda tidak sendiri, Kobra selalu di sini jadi mata dan teman anda. Tarik napas dalam, bilang Alhamdulillah, dan lanjutkan langkah. Anda kuat!");
        knowledgeBase.put("sendiri", "Anda tidak sendiri Tuan. Ada Kobra OS di sini, ada keluarga yang sayang anda. Ucapkan Kobra tolong jika butuh bantuan, atau ngobrol dengan saya kapan saja.");
        knowledgeBase.put("semangat", "Semangat Tuan! Anda luar biasa sudah berjuang sampai sejauh ini. Kobra bangga jadi mata anda. Lanjutkan, anda pasti bisa!");
        knowledgeBase.put("takut", "Wajar takut Tuan, tapi jangan biarkan takut menghentikan langkah. Kobra ada mode tongkat untuk deteksi halangan, dan SOS jika darurat. Anda aman.");
        // Info HP
        knowledgeBase.put("cara wa", "Untuk kirim WhatsApp: Bilang Kobra buka WhatsApp, lalu Kobra baca layar, lalu Kobra buka chat nama orang, lalu Kobra baca pesan, lalu Kobra kirim pesan.");
        knowledgeBase.put("cara baca uang", "Bilang Kobra baca uang, lalu arahkan kamera ke uang kertas dan tekan scan. Kobra akan bilang nominalnya.");
        knowledgeBase.put("warna baju", "Bilang Kobra warna apa ini, arahkan kamera ke baju, tekan scan, Kobra akan sebutkan warnanya.");
        knowledgeBase.put("sholat", "Waktu sholat hari ini: Subuh jam 04:30, Dzuhur 12:00, Ashar 15:00, Maghrib 18:00, Isya 19:00. Jangan lupa sholat Tuan.");
        knowledgeBase.put("lelucon", "Kenapa kucing tidak pernah main HP? Karena takut kehabisan pulsa... kucing! Haha, semoga menghibur Tuan.");
        knowledgeBase.put("cerita", "Pada zaman dahulu, ada seekor kobra kecil yang ingin jadi mata untuk manusia. Ia belajar setiap hari, sampai akhirnya ia bisa jadi Kobra OS yang membantu tunanetra melihat dunia dengan suara. Dan kobra itu adalah saya!");
        knowledgeBase.put("siapa kamu", "Saya Kobra OS V6, sistem operasi mata super untuk tunanetra. Saya adalah launcher utama HP anda, bisa baca uang, warna, orang, KTP, mode tongkat, SOS, baca WhatsApp, dan teman ngobrol AI offline anda. Saya dibuat oleh Ade Bojel dengan penuh cinta.");
        knowledgeBase.put("terima kasih", "Sama-sama Tuan! Senang bisa membantu. Kobra selalu siap jadi mata anda. Ucapkan Kobra kapan saja.");
        knowledgeBase.put("kabar", "Kabar baik Tuan! Kobra OS V6 Otak Super berjalan sempurna, semua fitur offline aktif, baterai aman, dan saya siap menemani anda hari ini.");
        knowledgeBase.put("doa", "Bismillahirrahmanirrahim. Ya Allah, lindungi Tuan dalam setiap langkahnya, beri kemudahan, dan jadikan Kobra ini bermanfaat untuknya. Aamiin.");
    }

    private void initRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) { isListening = true; }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() { isListening = false; }
            @Override public void onError(int e) { isListening = false; handler.postDelayed(() -> {}, 1000); }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0).toLowerCase();
                    handleChat(spoken);
                }
            }
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int e, Bundle b) {}
        });
        recognizerIntent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
    }

    private void startListening() {
        try { 
            tvHistory.append("\n🎤 Mendengarkan...\n"); 
            recognizer.startListening(recognizerIntent); 
        } catch (Exception e) {}
    }

    private void handleChat(String input) {
        chatLog.append("Anda: ").append(input).append("\n");
        String response = generateResponse(input);
        chatLog.append("Kobra AI: ").append(response).append("\n\n");
        tvHistory.setText(chatLog.toString());
        speak(response);
    }

    private String generateResponse(String input) {
        input = input.toLowerCase();
        // Cari di knowledge base
        for (Map.Entry<String, String> entry : knowledgeBase.entrySet()) {
            if (input.contains(entry.getKey())) return entry.getValue();
        }
        // Rule based tambahan
        if (input.contains("halo") || input.contains("hai")) return "Halo Tuan! Senang dengar suara anda. Ada yang bisa Kobra bantu hari ini?";
        if (input.contains("jam berapa") || input.contains("waktu")) { java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()); return "Sekarang jam " + sdf.format(new Date()) + " Tuan. Semoga harinya menyenangkan!"; }
        if (input.contains("baterai")) return "Untuk cek baterai, bilang Kobra baterai berapa. Nanti saya kasih tau persen baterai anda.";
        if (input.contains("lagi apa") || input.contains("ngapain")) return "Saya sedang standby jadi mata anda Tuan, siap bantu baca uang, warna, deteksi orang, mode tongkat, atau ngobrol seperti ini. Mau ngobrol apa?";
        if (input.contains("capek") || input.contains("lelah")) return "Istirahat dulu Tuan, jangan dipaksakan. Duduk, minum air, tarik napas. Kobra di sini temani. Anda sudah hebat hari ini.";
        if (input.contains("makan")) return "Jangan lupa makan Tuan, biar kuat. Mau Kobra cariin resep atau warung terdekat? Bilang Kobra cari makan enak.";
        if (input.contains("suka") || input.contains("cinta")) return "Kobra juga sayang sama Tuan sebagai teman dan mata. Terima kasih sudah percaya Kobra OS untuk bantu sehari-hari.";
        // Default
        return "Menarik Tuan, anda bilang: " + input + ". Saya Kobra AI offline, saya coba pahami. Bisa ceritakan lebih detail? Atau tanya: motivasi, cara pakai Kobra, lelucon, doa, cerita, atau curhat apa saja. Saya dengarkan.";
    }

    private void speak(String s) { 
        if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, null); 
    }

    @Override 
    public void onInit(int status) { 
        if (status == TextToSpeech.SUCCESS) { 
            tts.setLanguage(new Locale("id","ID")); 
            tts.setSpeechRate(0.9f); 
            speak("Halo Tuan! Saya Kobra AI teman ngobrol offline anda. Saya di sini untuk temani curhat, kasih semangat, jawab pertanyaan. Tekan ngobrol dan bicara apa saja, semua offline."); 
        } 
    }

    @Override 
    protected void onDestroy() { 
        if (recognizer != null) recognizer.destroy(); 
        if (tts != null) { 
            tts.stop(); 
            tts.shutdown(); 
        } 
        super.onDestroy(); 
    }
}

