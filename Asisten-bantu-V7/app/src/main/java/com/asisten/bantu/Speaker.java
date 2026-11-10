package com.asisten.bantu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Satu-satunya sumber suara untuk seluruh aplikasi. Mengirim sinyal mulai dan selesai bicara
 * supaya mikrofon berhenti saat Kobra bicara dan mulai lagi setelahnya. Ucapan dianggap
 * "sedang berlangsung" sejak diminta, bukan sejak TTS mulai, agar mikrofon tidak sempat menyala di sela.
 */
public final class Speaker {

    public interface Listener {
        void onSpeechStart();
        void onSpeechEnd();
    }

    private static final int MAX_CHARS = 3500;
    private static Speaker instance;

    private final KobraConfig config;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> active = new HashSet<>();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final List<String> waiting = new ArrayList<>();
    private final TextToSpeech tts;
    private boolean ready = false;
    private boolean announced = false;

    private Speaker(Context app) {
        config = new KobraConfig(app);
        tts = new TextToSpeech(app, status -> main.post(() -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(new Locale("id", "ID"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
            ready = true;
            List<String> pending = new ArrayList<>(waiting);
            waiting.clear();
            for (String w : pending) doSpeak(w, TextToSpeech.QUEUE_ADD);
        }));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                main.post(() -> {
                    if (!announced && active.contains(id)) {
                        announced = true;
                        for (Listener l : listeners) l.onSpeechStart();
                    }
                });
            }
            @Override public void onDone(String id) { finish(id); }
            @Override public void onError(String id) { finish(id); }
        });
    }

    private void finish(String id) {
        main.post(() -> {
            active.remove(id);
            if (active.isEmpty()) {
                announced = false;
                for (Listener l : listeners) l.onSpeechEnd();
            }
        });
    }

    public static synchronized Speaker get(Context c) {
        if (instance == null) instance = new Speaker(c.getApplicationContext());
        return instance;
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    /** Dari thread mana pun. Ucapan baru memotong ucapan yang sedang berjalan. */
    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        main.post(() -> {
            if (ready) {
                doSpeak(text, TextToSpeech.QUEUE_FLUSH);
            } else {
                waiting.clear();
                waiting.add(text);
            }
        });
    }

    /** Antre di belakang ucapan yang sedang berjalan (untuk notifikasi masuk). */
    public void speakQueued(String text) {
        if (text == null || text.trim().isEmpty()) return;
        main.post(() -> {
            if (ready) doSpeak(text, TextToSpeech.QUEUE_ADD);
            else waiting.add(text);
        });
    }

    private void doSpeak(String text, int mode) {
        String t = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        tts.setSpeechRate(config.getSpeechRate());
        String id = "kobra-" + System.nanoTime();
        if (mode == TextToSpeech.QUEUE_FLUSH) active.clear();
        active.add(id);
        int r = tts.speak(t, mode, null, id);
        if (r != TextToSpeech.SUCCESS) finish(id);
    }

    public void stop() {
        main.post(() -> {
            tts.stop();
            active.clear();
            waiting.clear();
            announced = false;
            for (Listener l : listeners) l.onSpeechEnd();
        });
    }

    /** Hanya dipanggil dari thread utama. */
    public boolean isSpeaking() { return !active.isEmpty(); }
}
