package com.asisten.bantu;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.speech.tts.TextToSpeech;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.os.Bundle;
import android.os.BatteryManager;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.provider.Settings;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.Button;
import android.graphics.PixelFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AsistenAccessibilityService extends AccessibilityService implements TextToSpeech.OnInitListener {
    private static final String TAG = "AsistenBantu";
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    
    // Ikon Melayang (Floating Window)
    private WindowManager windowManager;
    private Button floatingMicButton;

    // State Percakapan Universal Konfirmasi
    // 0: Normal, 1: Menunggu Konfirmasi Aksi Umum, 2: Tunggu Kontak Pesan, 3: Tunggu Isi Pesan, 4: Konfirmasi Akhir Pesan
    private int conversationState = 0; 
    
    // Variabel Pending untuk Eksekusi Setelah Dikonfirmasi
    private String pendingActionType = "";
    private String pendingActionValue = "";
    private String targetContact = "";
    private String messageBody = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Layanan Aksesibilitas Terhubung!");
        tts = new TextToSpeech(this, this);
        
        setupFloatingWindow();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new MyRecognitionListener());
        
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        
        startListening();
    }

    private void setupFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingMicButton = new Button(this);
        floatingMicButton.setText("🎤 Kobra");
        floatingMicButton.setBackgroundColor(0xFF00FF00); 
        floatingMicButton.setTextColor(0xFF000000);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 50;
        params.y = 200;

        try {
            windowManager.addView(floatingMicButton, params);
        } catch (Exception e) {
            Log.e(TAG, "Gagal membuat floating window: " + e.getMessage());
        }
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

                // Jika sedang dalam mode percakapan interaktif / konfirmasi
                if (conversationState > 0) {
                    handleConversationFlow(spokenText);
                } 
                else if (spokenText.contains("kobra")) {
                    int index = spokenText.indexOf("kobra");
                    String command = spokenText.substring(index + 5).trim();
                    
                    if (command.isEmpty()) {
                        if (tts != null) tts.speak("Sistem online, Tuan. Ada yang bisa saya bantu?", TextToSpeech.QUEUE_FLUSH, null, null);
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

    // Alur Konfirmasi Universal dan Pesan Bertahap
    private void handleConversationFlow(String spokenText) {
        boolean isConfirmed = spokenText.contains("iya") || spokenText.contains("ya") || spokenText.contains("lanjut") || spokenText.contains("jalankan");

        switch (conversationState) {
            case 1: // Konfirmasi Universal (Aplikasi, Wi-Fi, Senter, Bluetooth, dll)
                if (isConfirmed) {
                    executeConfirmedAction(pendingActionType, pendingActionValue);
                } else {
                    if (tts != null) tts.speak("Perintah dibatalkan.", TextToSpeech.QUEUE_FLUSH, null, null);
                }
                resetConversation();
                break;

            case 2: // Menunggu nama kontak untuk kirim pesan
                targetContact = spokenText;
                conversationState = 3;
                if (tts != null) tts.speak("Kepada " + targetContact + ". Silahkan sebutkan isi pesan yang ingin dikirim.", TextToSpeech.QUEUE_FLUSH, null, null);
                break;

            case 3: // Menunggu isi pesan
                messageBody = spokenText;
                conversationState = 4;
                if (tts != null) tts.speak("Pesan berbunyi: " + messageBody + ". Apakah pesan ini mau dikirim sekarang? Katakan iya untuk mengirim.", TextToSpeech.QUEUE_FLUSH, null, null);
                break;

            case 4: // Konfirmasi akhir kirim pesan
                if (isConfirmed) {
                    if (tts != null) tts.speak("Pesan dikirim sekarang.", TextToSpeech.QUEUE_FLUSH, null, null);
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(messageBody)));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } else {
                    if (tts != null) tts.speak("Pengiriman pesan dibatalkan.", TextToSpeech.QUEUE_FLUSH, null, null);
                }
                resetConversation();
                break;
        }
    }

    private void resetConversation() {
        conversationState = 0;
        pendingActionType = "";
        pendingActionValue = "";
    }

    // Deteksi Perintah Awal & Minta Konfirmasi Sebelum Eksekusi
    private void executeCommand(String spokenText) {
        // Baca Layar (Bisa langsung tanpa konfirmasi agar cepat)
        if (spokenText.contains("baca layar") || spokenText.contains("apa di layar") || spokenText.contains("isi layar")) {
            readCurrentScreenContent();
        }
        // Status Baterai
        else if (spokenText.contains("baterai") || spokenText.contains("status")) {
            checkBatteryStatus();
        }
        // Internet / Wi-Fi
        else if (spokenText.contains("wifi") || spokenText.contains("internet") || spokenText.contains("data seluler")) {
            requestConfirmation("WIFI_PANEL", "Pengaturan Internet", "Membuka pengaturan internet");
        }
        // Bluetooth
        else if (spokenText.contains("bluetooth")) {
            requestConfirmation("BLUETOOTH_PANEL", "Pengaturan Bluetooth", "Membuka pengaturan bluetooth");
        }
        // Senter (Panel Cepat)
        else if (spokenText.contains("senter") || spokenText.contains("lampu")) {
            requestConfirmation("FLASHLIGHT_PANEL", "Panel Cepat Senter", "Membuka panel cepat senter");
        }
        // Kirim Pesan (Masuk ke alur bertahap khusus pesan)
        else if (spokenText.contains("kirim pesan") || spokenText.contains("buka chat")) {
            conversationState = 2;
            if (tts != null) tts.speak("Baik, kepada siapa anda ingin mengirim pesan?", TextToSpeech.QUEUE_FLUSH, null, null);
        }
        // Putar YouTube / Media
        else if (spokenText.contains("youtube") || spokenText.contains("putar") || spokenText.contains("cari lagu") || spokenText.contains("cari video")) {
            String query = spokenText.replace("putar", "").replace("cari lagu", "").replace("cari video", "").replace("di youtube", "").replace("youtube", "").trim();
            requestConfirmation("YOUTUBE", query, "Mencari dan memutar " + query + " di YouTube");
        } 
        // Telepon
        else if (spokenText.startsWith("panggil ") || spokenText.startsWith("telepon ")) {
            String target = spokenText.replace("panggil", "").replace("telepon", "").trim();
            requestConfirmation("CALL", target, "Menghubungi " + target);
        } 
        // Navigasi Maps
        else if (spokenText.startsWith("arahin ke ") || spokenText.startsWith("navigasi ke ")) {
            String destination = spokenText.replace("arahin ke", "").replace("navigasi ke", "").trim();
            requestConfirmation("MAPS", destination, "Mengarahkan navigasi ke " + destination);
        } 
        // Kembali ke Beranda
        else if (spokenText.contains("kembali ke beranda") || spokenText.contains("home")) {
            requestConfirmation("HOME", "Beranda", "Kembali ke beranda perangkat");
        } 
        // Buka Aplikasi Apapun Secara Universal
        else if (spokenText.startsWith("buka ")) {
            String appName = spokenText.replace("buka ", "").trim();
            requestConfirmation("OPEN_APP", appName, "Membuka aplikasi " + appName);
        }
    }

    // Fungsi Memicu Permintaan Konfirmasi Universal
    private void requestConfirmation(String actionType, String actionValue, String description) {
        pendingActionType = actionType;
        pendingActionValue = actionValue;
        conversationState = 1; // Masuk mode tunggu konfirmasi universal
        if (tts != null) {
            tts.speak("Anda ingin " + description + ". Apakah ingin dijalankan sekarang? Katakan iya.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    // Eksekusi Nyata Setelah Pengguna Mengatakan "Iya"
    private void executeConfirmedAction(String actionType, String actionValue) {
        switch (actionType) {
            case "WIFI_PANEL":
                try {
                    Intent panelIntent = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
                    panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(panelIntent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                break;

            case "BLUETOOTH_PANEL":
                Intent btIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(btIntent);
                break;

            case "FLASHLIGHT_PANEL":
                try {
                    performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
                } catch (Exception e) {
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                }
                break;

            case "YOUTUBE":
                try {
                    Intent ytIntent = new Intent(Intent.ACTION_SEARCH);
                    ytIntent.setPackage("com.google.android.youtube");
                    ytIntent.putExtra("query", actionValue);
                    ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(ytIntent);
                } catch (Exception e) {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(actionValue)));
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(webIntent);
                }
                break;

            case "CALL":
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(Uri.parse("tel:" + actionValue));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(callIntent);
                break;

            case "MAPS":
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(actionValue));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(mapIntent);
                } catch (Exception e) {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(actionValue)));
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(webIntent);
                }
                break;

            case "HOME":
                performGlobalAction(GLOBAL_ACTION_HOME);
                break;

            case "OPEN_APP":
                openAppUniversal(actionValue);
                break;
        }
    }

    private void openAppUniversal(String appName) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(0);
        String targetPackage = null;
        String realAppName = appName;

        for (ApplicationInfo packageInfo : packages) {
            String label = pm.getApplicationLabel(packageInfo).toString().toLowerCase();
            if (label.contains(appName)) {
                targetPackage = packageInfo.packageName;
                realAppName = pm.getApplicationLabel(packageInfo).toString();
                break;
            }
        }

        if (targetPackage != null) {
            if (tts != null) tts.speak("Membuka " + realAppName, TextToSpeech.QUEUE_FLUSH, null, null);
            Intent intent = pm.getLaunchIntentForPackage(targetPackage);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } else {
            if (tts != null) tts.speak("Aplikasi " + appName + " tidak ditemukan di perangkat.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void readCurrentScreenContent() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            StringBuilder sb = new StringBuilder();
            readNodeRecursive(rootNode, sb);
            String resultText = sb.toString().trim();
            
            if (!resultText.isEmpty()) {
                if (tts != null) {
                    if (resultText.length() > 300) {
                        resultText = resultText.substring(0, 300) + "... dan masih banyak lagi.";
                    }
                    tts.speak("Isi layar: " + resultText, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            } else {
                if (tts != null) tts.speak("Tidak ada teks terdeteksi di layar.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
            rootNode.recycle();
        } else {
            if (tts != null) tts.speak("Gagal membaca layar.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void readNodeRecursive(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null && node.getText().length() > 0) {
            sb.append(node.getText().toString()).append(". ");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            readNodeRecursive(child, sb);
            if (child != null) child.recycle();
        }
    }

    private void checkBatteryStatus() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (tts != null) {
            tts.speak("Sisa baterai perangkat saat ini " + batteryLevel + " persen, Tuan.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (windowManager != null && floatingMicButton != null) {
            try {
                windowManager.removeView(floatingMicButton);
            } catch (Exception e) {}
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("id", "ID"));
            tts.speak("Sistem Kobra online. Siap melayani.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (windowManager != null && floatingMicButton != null) {
            try {
                windowManager.removeView(floatingMicButton);
            } catch (Exception e) {}
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
