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
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.provider.Settings;
import android.content.Context;
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
            startListening();
        }
        
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String spokenText = matches.get(0).toLowerCase();
                Log.d(TAG, "Mendengar: " + spokenText);
                
                if (spokenText.contains("kobra")) {
                    int index = spokenText.indexOf("kobra");
                    String command = spokenText.substring(index + 5).trim();
                    
                    if (command.isEmpty()) {
                        if (tts != null) tts.speak("Ada yang bisa saya bantu?", TextToSpeech.QUEUE_FLUSH, null, null);
                    } else {
                        executeCommand(command);
                    }
                }
            }
            startListening();
        }
        
        @Override
        public void onPartialResults(Bundle partialResults) {}
        @Override
        public void onEvent(int eventType, Bundle params) {}
    }

    private void executeCommand(String spokenText) {
        // 1. Kontrol Koneksi & Internet
        if (spokenText.contains("wifi") || spokenText.contains("internet") || spokenText.contains("data seluler")) {
            openInternetPanel();
        }
        // 2. Kontrol Bluetooth
        else if (spokenText.contains("bluetooth")) {
            openBluetoothPanel();
        }
        // 3. Kontrol Senter (Membuka Panel Quick Settings)
        else if (spokenText.contains("senter") || spokenText.contains("lampu")) {
            openFlashlightPanel();
        }
        // 4. Buka WhatsApp
        else if (spokenText.contains("whatsapp") || spokenText.contains("watsap") || spokenText.contains("wa")) {
            openSpecificApp("com.whatsapp", "WhatsApp");
        }
        // 5. Buka Instagram
        else if (spokenText.contains("instagram") || spokenText.contains("ig")) {
            openSpecificApp("com.instagram.android", "Instagram");
        }
        // 6. Buka TikTok
        else if (spokenText.contains("tiktok")) {
            openSpecificApp("com.zhiliaoapp.musically", "TikTok");
        }
        // 7. Putar Musik / Video di YouTube
        else if (spokenText.contains("youtube") || spokenText.contains("putar") || spokenText.contains("cari lagu") || spokenText.contains("putar video")) {
            String query = spokenText.replace("putar", "").replace("cari lagu", "").replace("cari video", "").replace("di youtube", "").replace("youtube", "").trim();
            playYouTube(query);
        } 
        // 8. Menelepon
        else if (spokenText.startsWith("panggil ") || spokenText.startsWith("telepon ")) {
            String target = spokenText.replace("panggil", "").replace("telepon", "").trim();
            makePhoneCall(target);
        } 
        // 9. Navigasi Maps
        else if (spokenText.startsWith("arahin ke ") || spokenText.startsWith("navigasi ke ") || spokenText.startsWith("jalan ke ")) {
            String destination = spokenText.replace("arahin ke", "").replace("navigasi ke", "").replace("jalan ke", "").trim();
            openMaps(destination);
        } 
        // 10. Kembali ke Beranda
        else if (spokenText.contains("kembali ke beranda") || spokenText.contains("home")) {
            if (tts != null) tts.speak("Kembali ke beranda", TextToSpeech.QUEUE_FLUSH, null, null);
            performGlobalAction(GLOBAL_ACTION_HOME);
        } 
        // 11. Aplikasi Umum Lainnya (Pencarian Dinamis)
        else if (spokenText.startsWith("buka ")) {
            String appName = spokenText.replace("buka ", "").trim();
            openAppByName(appName);
        }
    }

    private void openSpecificApp(String packageName, String appName) {
        if (tts != null) tts.speak("Membuka " + appName, TextToSpeech.QUEUE_FLUSH, null, null);
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            if (tts != null) tts.speak("Aplikasi " + appName + " tidak terpasang", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void openInternetPanel() {
        if (tts != null) tts.speak("Membuka pengaturan internet", TextToSpeech.QUEUE_FLUSH, null, null);
        try {
            Intent panelIntent = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(panelIntent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private void openBluetoothPanel() {
        if (tts != null) tts.speak("Membuka pengaturan bluetooth", TextToSpeech.QUEUE_FLUSH, null, null);
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // Membuka Panel Quick Settings untuk akses Senter instan
    private void openFlashlightPanel() {
        if (tts != null) tts.speak("Membuka panel cepat", TextToSpeech.QUEUE_FLUSH, null, null);
        try {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        }
    }

    private void openAppByName(String appName) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(0);
        String targetPackage = null;

        for (ApplicationInfo packageInfo : packages) {
            String label = pm.getApplicationLabel(packageInfo).toString().toLowerCase();
            if (label.contains(appName)) {
                targetPackage = packageInfo.packageName;
                break;
            }
        }

        if (targetPackage != null) {
            if (tts != null) tts.speak("Membuka " + appName, TextToSpeech.QUEUE_FLUSH, null, null);
            Intent intent = pm.getLaunchIntentForPackage(targetPackage);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } else {
            if (tts != null) tts.speak("Aplikasi " + appName + " tidak ditemukan", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void playYouTube(String query) {
        if (tts != null) tts.speak("Mencari " + query, TextToSpeech.QUEUE_FLUSH, null, null);
        try {
            Intent intent = new Intent(Intent.ACTION_SEARCH);
            intent.setPackage("com.google.android.youtube");
            intent.putExtra("query", query);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(webIntent);
        }
    }

    private void makePhoneCall(String target) {
        if (tts != null) tts.speak("Menghubungi " + target, TextToSpeech.QUEUE_FLUSH, null, null);
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + target));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openMaps(String destination) {
        if (tts != null) tts.speak("Mengarahkan ke " + destination, TextToSpeech.QUEUE_FLUSH, null, null);
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(mapIntent);
        } catch (Exception e) {
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(destination)));
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(webIntent);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

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
            tts.speak("Kobra aktif.", TextToSpeech.QUEUE_FLUSH, null, null);
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
