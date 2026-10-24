package com.asisten.bantu;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.speech.tts.TextToSpeech;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.os.Bundle;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AsistenAccessibilityService extends AccessibilityService implements TextToSpeech.OnInitListener {
    private static final String TAG = "AsistenBantu";
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Layanan Aksesibilitas Terhubung!");
        tts = new TextToSpeech(this, this);
        
        // Inisialisasi SpeechRecognizer sistem dengan preferensi offline
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new MyRecognitionListener());
        
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        
        startListening();
    }

    private void startListening() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.startListening(recognizerIntent);
            } catch (Exception e) {
                Log.e(TAG, "Gagal mulai mendengar: " + e.getMessage());
            }
        }
    }

    private class MyRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {}
        @Override
        public void onBeginningOfSpeech() {}
        @Override
        public void onRmsChanged(float rmsdB) {}
        @Override
        public void onBufferReceived(byte[] buffer) {}
        @Override
        public void onEndOfSpeech() {}
        
        @Override
        public void onError(int error) {
            // Otomatis mendengarkan ulang saat jeda atau selesai
            startListening();
        }
        
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String spokenText = matches.get(0);
                Log.d(TAG, "Perintah Suara: " + spokenText);
                if (tts != null) {
                    tts.speak("Mendengar: " + spokenText, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
            startListening();
        }
        
        @Override
        public void onPartialResults(Bundle partialResults) {}
        @Override
        public void onEvent(int eventType, Bundle params) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            
            List<CharSequence> textList = event.getText();
            if (textList != null && !textList.isEmpty()) {
                CharSequence sourceText = textList.get(0);
                if (sourceText != null && sourceText.length() > 0) {
                    String spokenText = sourceText.toString();
                    if (tts != null) {
                        tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("id", "ID"));
            tts.speak("Asisten suara siap mendengarkan.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
