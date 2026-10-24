package com.asisten.bantu;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.List;
import java.util.Locale;

public class AsistenAccessibilityService extends AccessibilityService implements TextToSpeech.OnInitListener {
    private static final String TAG = "AsistenBantu";
    private TextToSpeech tts;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Layanan Aksesibilitas Asisten Bantu Berhasil Terhubung!");
        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            CharSequence packageName = event.getPackageName();
            List<CharSequence> textList = event.getText();
            CharSequence sourceText = (textList != null && !textList.isEmpty()) ? textList.get(0) : "";
            
            // Di sinilah nanti AI membaca teks atau elemen di layar HP
            Log.d(TAG, "Aplikasi aktif: " + packageName + " | Teks: " + sourceText);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Layanan Aksesibilitas Terhenti");
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("id", "ID"));
            tts.speak("Asisten Bantu siap mendampingi.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
