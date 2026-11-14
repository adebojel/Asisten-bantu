package com.asisten.bantu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

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
    private final AtomicLong utteranceSequence = new AtomicLong();
    private volatile String activeUtteranceId = null;
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
                mainHandler.post(() -> {
                    activeUtteranceId = utteranceId;
                    notifySpeechStart();
                });
            }

            @Override
            public void onDone(String utteranceId) {
                mainHandler.post(() -> finishUtterance(utteranceId));
            }

            @Override
            public void onError(String utteranceId) {
                mainHandler.post(() -> finishUtterance(utteranceId));
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                mainHandler.post(() -> finishUtterance(utteranceId));
            }
        });
    }

    private void finishUtterance(String utteranceId) {
        // Callback TTS lama (misalnya setelah QUEUE_FLUSH) tidak boleh
        // mematikan status bicara untuk ucapan yang lebih baru.
        if (utteranceId == null || !utteranceId.equals(activeUtteranceId)) return;
        activeUtteranceId = null;
        notifySpeechEnd();
    }

    public void applyVoiceSettings() {
        if (tts != null && ready) {
            VoiceProfileManager.applyToTextToSpeech(context, tts);
        }
    }

    public void updateVoiceSettings(float speechRate, float pitch) {
        if (tts != null && ready) {
            try {
                tts.setSpeechRate(Math.max(0.5f, Math.min(2.0f, speechRate)));
                tts.setPitch(Math.max(0.5f, Math.min(1.8f, pitch)));
            } catch (Exception ignored) {}
        }
    }

    public void speak(String teks) {
        if (teks == null || teks.trim().isEmpty()) return;
        // Tandai sedang bicara sejak pemanggilan untuk mencegah loop mikrofon
        if (tts == null) return;
        String utteranceId = "kobra_" + utteranceSequence.incrementAndGet();
        activeUtteranceId = utteranceId;
        notifySpeechStart();
        try {
            tts.stop();
            int result = tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            if (result == TextToSpeech.ERROR) finishUtterance(utteranceId);
        } catch (Exception e) {
            finishUtterance(utteranceId);
        }
    }

    public void speakQueued(String teks) {
        if (teks == null || teks.trim().isEmpty()) return;
        if (tts != null) {
            try {
                String utteranceId = "kobra_q_" + utteranceSequence.incrementAndGet();
                tts.speak(teks, TextToSpeech.QUEUE_ADD, null, utteranceId);
            } catch (Exception ignored) {}
        }
    }

    public void stop() {
        activeUtteranceId = null;
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
        activeUtteranceId = null;
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception ignored) {}
        tts = null;
        ready = false;
        sedangBicara = false;
        synchronized (Speaker.class) {
            if (sInstance == this) sInstance = null;
        }
    }

    public void setOnEnd(Runnable r) {
        this.onEndCallback = r;
    }
}
