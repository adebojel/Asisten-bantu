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
import android.os.Handler;
import android.os.Looper;
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
    private Handler mainHandler;
    
    // Ikon Melayang (Floating Window)
    private WindowManager windowManager;
    private Button floatingMicButton;

    // State Percakapan Universal Konfirmasi & AI Flow
    // 0: Standby / Menunggu Wake Word "Kobra"
    // 1: Menunggu Konfirmasi Universal Aksi Sistem
    // 2: Menunggu Nama Kontak Pesan
    // 3: Menunggu Isi Pesan
    // 4: Konfirmasi Akhir Kirim Pesan
    private int conversationState = 0; 
    
    private String pendingActionType = "";
    private String pendingActionValue = "";
    private String targetContact = "";
    private String messageBody = "";
    private boolean isSpeaking = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Layanan Aksesibilitas Terhubung!");
        mainHandler = new Handler(Looper.getMainLooper());
        tts = new TextToSpeech(this, this);
        
        setupFloatingWindow();

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                initSpeechRecognizer();
            }
        });
    }

    private void initSpeechRecognizer() {
        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new MyRecognitionListener());
            
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            
            startListening();
        } catch (Exception e) {
            Log.e(TAG, "Gagal inisialisasi SpeechRecognizer: " + e.getMessage());
        }
    }

    private void setupFloatingWindow() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            floatingMicButton = new Button(this);
            floatingMicButton.setText("🎤 Kobra AI");
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

            windowManager.addView(floatingMicButton, params);
        } catch (Exception e) {
            Log.e(TAG, "Gagal membuat floating window: " + e.getMessage());
        }
    }

    // Fungsi Standby Pendengaran Tanpa Henti (Always Standby)
    private void startListening() {
        if (speechRecognizer != null && !isSpeaking) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        speechRecognizer.stopListening();
                        speechRecognizer.startListening(recognizerIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Gagal mulai mendengar: " + e.getMessage());
                    }
                }
            });
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
            // Auto restart mikrofon agar selalu standby mendengarkan
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startListening();
                }
            }, 500);
        }
        
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String spokenText = matches.get(0).toLowerCase();
                Log.d(TAG, "Mendengar: " + spokenText);

                if (conversationState > 0) {
                    handleConversationFlow(spokenText);
                } 
                else if (spokenText.contains("kobra")) {
                    int index = spokenText.indexOf("kobra");
                    String command = spokenText.substring(index + 5).trim();
                    
                    if (command.isEmpty()) {
                        speakText("Sistem Kobra online. Siap mendengarkan perintah, Tuan.");
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

    // Sistem Bicara dengan Penundaan Mikrofon agar Tidak Merekam Suara Sendiri
    private void speakText(final String text) {
        isSpeaking = true;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception e) {}
        }

        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }

        // Beri jeda waktu agar TTS selesai bicara, lalu aktifkan kembali mic standby
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                isSpeaking = false;
                startListening();
            }
        }, 4000); // Jeda 4 detik untuk memastikan suara selesai dibacakan
    }

    // Alur Percakapan Interaktif Konfirmasi Bertahap
    private void handleConversationFlow(String spokenText) {
        boolean isConfirmed = spokenText.contains("iya") || spokenText.contains("ya") || spokenText.contains("lanjut") || spokenText.contains("jalankan");

        switch (conversationState) {
            case 1: // Konfirmasi Universal Sistem
                if (isConfirmed) {
                    executeConfirmedAction(pendingActionType, pendingActionValue);
                } else {
                    speakText("Perintah dibatalkan.");
                }
                resetConversation();
                break;

            case 2: // Menunggu kontak pesan
                targetContact = spokenText;
                conversationState = 3;
                speakText("Kepada " + targetContact + ". Silahkan sebutkan isi pesan yang ingin dikirim.");
                break;

            case 3: // Menunggu isi pesan
                messageBody = spokenText;
                conversationState = 4;
                speakText("Pesan berbunyi: " + messageBody + ". Apakah pesan mau dikirim sekarang? Katakan iya untuk mengirim.");
                break;

            case 4: // Konfirmasi akhir kirim pesan
                if (isConfirmed) {
                    speakText("Pesan dikirim sekarang.");
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(messageBody)));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } else {
                    speakText("Pengiriman pesan dibatalkan.");
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

    private void executeCommand(String spokenText) {
        if (spokenText.contains("baca layar") || spokenText.contains("apa di layar") || spokenText.contains("isi layar")) {
            readCurrentScreenContent();
        }
        else if (spokenText.contains("baterai") || spokenText.contains("status")) {
            checkBatteryStatus();
        }
        else if (spokenText.contains("wifi") || spokenText.contains("internet") || spokenText.contains("data seluler")) {
            requestConfirmation("WIFI_PANEL", "Pengaturan Internet", "membuka pengaturan internet");
        }
        else if (spokenText.contains("bluetooth")) {
            requestConfirmation("BLUETOOTH_PANEL", "Pengaturan Bluetooth", "membuka pengaturan bluetooth");
        }
        else if (spokenText.contains("senter") || spokenText.contains("lampu")) {
            requestConfirmation("FLASHLIGHT_PANEL", "Panel Cepat Senter", "membuka panel cepat senter");
        }
        else if (spokenText.contains("kirim pesan") || spokenText.contains("buka chat")) {
            conversationState = 2;
            speakText("Baik, kepada siapa anda ingin mengirim pesan?");
        }
        else if (spokenText.contains("youtube") || spokenText.contains("putar") || spokenText.contains("cari lagu") || spokenText.contains("cari video")) {
            String query = spokenText.replace("putar", "").replace("cari lagu", "").replace("cari video", "").replace("di youtube", "").replace("youtube", "").trim();
            requestConfirmation("YOUTUBE", query, "mencari dan memutar " + query + " di YouTube");
        } 
        else if (spokenText.startsWith("panggil ") || spokenText.startsWith("telepon ")) {
            String target = spokenText.replace("panggil", "").replace("telepon", "").trim();
            requestConfirmation("CALL", target, "menghubungi " + target);
        } 
        else if (spokenText.startsWith("arahin ke ") || spokenText.startsWith("navigasi ke ")) {
            String destination = spokenText.replace("arahin ke", "").replace("navigasi ke", "").trim();
            requestConfirmation("MAPS", destination, "mengarahkan navigasi ke " + destination);
        } 
        else if (spokenText.contains("kembali ke beranda") || spokenText.contains("home")) {
            requestConfirmation("HOME", "Beranda", "kembali ke beranda perangkat");
        } 
        else if (spokenText.startsWith("buka ")) {
            String appName = spokenText.replace("buka ", "").trim();
            requestConfirmation("OPEN_APP", appName, "membuka aplikasi " + appName);
        }
    }

    private void requestConfirmation(String actionType, String actionValue, String description) {
        pendingActionType = actionType;
        pendingActionValue = actionValue;
        conversationState = 1; 
        speakText("Anda ingin " + description + ". Apakah ingin dijalankan sekarang? Katakan iya.");
    }

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
            speakText("Membuka " + realAppName);
            Intent intent = pm.getLaunchIntentForPackage(targetPackage);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } else {
            speakText("Aplikasi " + appName + " tidak ditemukan.");
        }
    }

    private void readCurrentScreenContent() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            StringBuilder sb = new StringBuilder();
            readNodeRecursive(rootNode, sb);
            String resultText = sb.toString().trim();
            
            if (!resultText.isEmpty()) {
                if (resultText.length() > 300) {
                    resultText = resultText.substring(0, 300) + "... dan masih banyak lagi.";
                }
                speakText("Isi layar: " + resultText);
            } else {
                speakText("Tidak ada teks terdeteksi di layar.");
            }
            rootNode.recycle();
        } else {
            speakText("Gagal membaca layar.");
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
        speakText("Sisa baterai perangkat saat ini " + batteryLevel + " persen, Tuan.");
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
            speakText("Sistem Kobra AI online. Mikrofon aktif standby.");
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
