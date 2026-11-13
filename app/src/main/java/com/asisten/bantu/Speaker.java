package com.asisten.bantu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Speaker implements TextToSpeech.OnInitListener {

    public interface Listener {
        void onSpeechStart();
        void onSpeechEnd();
    }

    private static volatile Speaker sInstance;

    private final Context context;
    private final Handler mainHandler;
    private TextToSpeech tts;
    private boolean ready = false;
    private volatile boolean sedangBicara = false;
    private Runnable onEndCallback = null;
    private final List<Listener> listeners = new ArrayList<>();

    public static synchronized Speaker get(Context ctx) {
        if (sInstance == null) {
            sInstance = new Speaker(ctx.getApplicationContext());
        }
        return sInstance;
    }

    public Speaker(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.tts = new TextToSpeech(context, this);
    }

    public synchronized void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public synchronized void removeListener(Listener l) {
        if (l != null) {
            listeners.remove(l);
        }
    }

    private synchronized void notifySpeechStart() {
        sedangBicara = true;
        for (Listener l : new ArrayList<>(listeners)) {
            try {
                l.onSpeechStart();
            } catch (Exception ignored) {}
        }
    }

    private synchronized void notifySpeechEnd() {
        sedangBicara = false;
        if (onEndCallback != null) {
            Runnable cb = onEndCallback;
            onEndCallback = null;
            cb.run();
        }
        for (Listener l : new ArrayList<>(listeners)) {
            try {
                l.onSpeechEnd();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        if (tts == null) return;
        ready = true;
        int r = tts.setLanguage(new Locale("id", "ID"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault());
        }
        applyVoiceSettings();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                mainHandler.post(() -> notifySpeechStart());
            }

            @Override
            public void onDone(String utteranceId) {
                mainHandler.post(() -> notifySpeechEnd());
            }

            @Override
            public void onError(String utteranceId) {
                mainHandler.post(() -> notifySpeechEnd());
            }
        });
    }

    public void applyVoiceSettings() {
        KobraConfig cfg = new KobraConfig(context);
        updateVoiceSettings(cfg.getSpeechRate(), cfg.getSpeechPitch());
    }

    public void updateVoiceSettings(float speechRate, float pitch) {
        if (tts != null && ready) {
            try {
                tts.setSpeechRate(Math.max(0.5f, Math.min(2.0f, speechRate)));
                tts.setPitch(Math.max(0.5f, Math.min(2.0f, pitch)));
            } catch (Exception ignored) {}
        }
    }

    public void speak(String teks) {
        if (teks == null || teks.trim().isEmpty()) return;
        // Tandai sedang bicara sejak pemanggilan untuk mencegah loop mikrofon
        notifySpeechStart();
        if (tts != null) {
            try {
                tts.stop();
                tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, "kobra_" + System.currentTimeMillis());
            } catch (Exception e) {
                notifySpeechEnd();
            }
        }
    }

    public void speakQueued(String teks) {
        if (teks == null || teks.trim().isEmpty()) return;
        if (tts != null) {
            try {
                tts.speak(teks, TextToSpeech.QUEUE_ADD, null, "kobra_q_" + System.currentTimeMillis());
            } catch (Exception ignored) {}
        }
    }

    public void stop() {
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception ignored) {}
        }
        notifySpeechEnd();
    }

    public boolean isSpeaking() {
        return sedangBicara || (tts != null && tts.isSpeaking());
    }

    public void shutdown() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception ignored) {}
        tts = null;
        ready = false;
        sedangBicara = false;
    }

    public void setOnEnd(Runnable r) {
        this.onEndCallback = r;
    }
}
