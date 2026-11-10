package com.asisten.bantu;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

public class Speaker implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private Context context;
    private boolean sedangBicara = false;
    private Runnable onEndCallback = null;

    public Speaker(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        if (tts == null) return;
        int r = tts.setLanguage(new Locale("id", "ID"));
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault());
        }
        tts.setSpeechRate(1.0f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                sedangBicara = true;
                try {
                    if (AsistenAccessibilityService.instance != null) {
                        AsistenAccessibilityService.instance.onTtsStart();
                    }
                } catch (Exception ignored) {}
            }
            @Override
            public void onDone(String utteranceId) {
                sedangBicara = false;
                if (onEndCallback != null) {
                    Runnable cb = onEndCallback;
                    onEndCallback = null;
                    cb.run();
                }
                try {
                    if (AsistenAccessibilityService.instance != null) {
                        AsistenAccessibilityService.instance.onTtsEnd();
                    }
                } catch (Exception ignored) {}
            }
            @Override
            public void onError(String utteranceId) {
                sedangBicara = false;
                try {
                    if (AsistenAccessibilityService.instance != null) {
                        AsistenAccessibilityService.instance.onTtsEnd();
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    public void speak(String teks) {
        if (teks == null || teks.isEmpty()) return;
        if (tts == null) return;
        sedangBicara = true;
        try {
            if (AsistenAccessibilityService.instance != null) {
                AsistenAccessibilityService.instance.stopMic();
            }
        } catch (Exception ignored) {}
        tts.stop();
        tts.speak(teks, TextToSpeech.QUEUE_FLUSH, null, "kobra_" + System.currentTimeMillis());
    }

    public void speakQueued(String teks) {
        if (teks == null || teks.isEmpty()) return;
        if (tts == null) return;
        tts.speak(teks, TextToSpeech.QUEUE_ADD, null, "kobra_q_" + System.currentTimeMillis());
    }

    public boolean isSpeaking() {
        return sedangBicara;
    }

    public void shutdown() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception ignored) {}
        tts = null;
    }

    public void setOnEnd(Runnable r) {
        this.onEndCallback = r;
    }
}
