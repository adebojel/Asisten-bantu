package com.asisten.bantu;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import java.util.Locale;

/**
 * Pembaca Layar Global Opsional untuk Kobra OS.
 * Membacakan tombol, menu, atau judul jendela di aplikasi pihak ketiga (di luar Kobra OS)
 * dengan TTS terpisah yang otomatis diam saat TTS internal Kobra (Speaker) sedang berbicara,
 * serta mendukung gestur dua jari pada Android 11+ (API 30+).
 */
public class KobraAccessibilityService extends AccessibilityService {

    private static volatile KobraAccessibilityService instance;

    private TextToSpeech externalTts;
    private boolean ttsReady = false;
    private Speaker internalSpeaker;
    private KobraConfig config;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String lastSpokenText = "";
    private long lastSpokenAt = 0L;

    public static KobraAccessibilityService getInstance() {
        return instance;
    }

    private final Speaker.Listener internalSpeechListener = new Speaker.Listener() {
        @Override public void onSpeechStart() {
            stopExternalTts();
        }
        @Override public void onSpeechEnd() {}
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        config = new KobraConfig(this);
        internalSpeaker = Speaker.get(this);
        internalSpeaker.addListener(internalSpeechListener);

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                AccessibilityServiceInfo info = getServiceInfo();
                if (info != null) {
                    info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES;
                    setServiceInfo(info);
                }
            } catch (Exception ignored) {}
        }

        externalTts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || externalTts == null) return;
            ttsReady = true;
            int r = externalTts.setLanguage(new Locale("id", "ID"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                externalTts.setLanguage(Locale.getDefault());
            }
            applyTtsSettings();
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkgCs = event.getPackageName();
        String pkg = pkgCs != null ? pkgCs.toString() : "";

        // Jangan bacakan UI internal Kobra OS atau saat TTS internal sedang berbicara
        if (pkg.isEmpty() || getPackageName().equals(pkg)) return;
        if (internalSpeaker != null && internalSpeaker.isSpeaking()) return;
        if (isAnotherSpokenServiceActive()) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_SELECTED
                && type != AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        String phrase = extractSpokenPhrase(event, pkg, type);
        if (phrase.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (phrase.equals(lastSpokenText) && now - lastSpokenAt < 900L) return;
        lastSpokenText = phrase;
        lastSpokenAt = now;

        speakExternal(phrase);
    }

    @Override
    public boolean onGesture(AccessibilityGestureEvent gestureEvent) {
        if (Build.VERSION.SDK_INT >= 30 && gestureEvent != null) {
            int id = gestureEvent.getGestureId();
            if (id == GESTURE_2_FINGER_SWIPE_DOWN) {
                stopExternalTts();
                HapticFeedback.vibrateAction(this);
                String msg = KobraNotificationService.summary(12);
                if (internalSpeaker != null) internalSpeaker.speak(msg);
                return true;
            }
            if (id == GESTURE_2_FINGER_SWIPE_UP) {
                stopExternalTts();
                if (internalSpeaker != null) internalSpeaker.stop();
                EmergencyBeacon.stopAll(this, null);
                HapticFeedback.vibrateAction(this);
                return true;
            }
            if (id == GESTURE_DOUBLE_TAP) {
                stopExternalTts();
                HapticFeedback.vibrateAction(this);
                AsistenAccessibilityService.requestListen();
                return true;
            }
        }
        return super.onGesture(gestureEvent);
    }

    @Override
    public void onInterrupt() {
        stopExternalTts();
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (internalSpeaker != null) {
            internalSpeaker.removeListener(internalSpeechListener);
        }
        if (externalTts != null) {
            try {
                externalTts.stop();
                externalTts.shutdown();
            } catch (Exception ignored) {}
            externalTts = null;
            ttsReady = false;
        }
        super.onDestroy();
    }

    private void applyTtsSettings() {
        if (externalTts == null || !ttsReady || config == null) return;
        try {
            externalTts.setSpeechRate(config.getSpeechRate());
            externalTts.setPitch(config.getSpeechPitch());
        } catch (Exception ignored) {}
    }

    private void speakExternal(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (internalSpeaker != null && internalSpeaker.isSpeaking()) return;
        main.post(() -> {
            if (!ttsReady || externalTts == null) return;
            if (internalSpeaker != null && internalSpeaker.isSpeaking()) return;
            applyTtsSettings();
            try {
                externalTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kobra_ext_" + System.currentTimeMillis());
            } catch (Exception ignored) {}
        });
    }

    private void stopExternalTts() {
        if (externalTts != null) {
            try {
                externalTts.stop();
            } catch (Exception ignored) {}
        }
    }

    private boolean isAnotherSpokenServiceActive() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            if (am == null) return false;
            List<AccessibilityServiceInfo> list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN);
            if (list == null) return false;
            for (AccessibilityServiceInfo info : list) {
                String id = info.getId();
                if (id != null && !id.startsWith(getPackageName() + "/")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String extractSpokenPhrase(AccessibilityEvent event, String pkg, int eventType) {
        StringBuilder sb = new StringBuilder();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String appName = resolveAppLabel(pkg);
            String title = joinCharSequences(event.getText());
            if (!title.isEmpty()) {
                return appName.isEmpty() || title.toLowerCase(Locale.ROOT).contains(appName.toLowerCase(Locale.ROOT))
                        ? title : appName + ", " + title;
            }
            return appName.isEmpty() ? "" : "Membuka " + appName;
        }

        AccessibilityNodeInfo src = event.getSource();
        if (src != null) {
            try {
                CharSequence desc = src.getContentDescription();
                CharSequence txt = src.getText();
                if (desc != null && desc.toString().trim().length() > 0) {
                    sb.append(desc.toString().trim());
                } else if (txt != null && txt.toString().trim().length() > 0) {
                    sb.append(txt.toString().trim());
                }
                if (sb.length() > 0) {
                    CharSequence cls = src.getClassName();
                    if (cls != null) {
                        String c = cls.toString();
                        if (c.contains("Button") && !c.contains("RadioButton")) {
                            sb.append(", tombol");
                        } else if (c.contains("CheckBox") || c.contains("Switch")) {
                            sb.append(src.isChecked() ? ", aktif" : ", mati");
                        } else if (c.contains("EditText")) {
                            sb.append(", kolom teks");
                        }
                    }
                }
            } finally {
                try { src.recycle(); } catch (Exception ignored) {}
            }
        }
        if (sb.length() == 0 && event.getContentDescription() != null) {
            sb.append(event.getContentDescription().toString().trim());
        }
        if (sb.length() == 0) {
            sb.append(joinCharSequences(event.getText()));
        }
        String out = sb.toString().replaceAll("\\s+", " ").trim();
        if (out.length() > 180) out = out.substring(0, 180);
        return out;
    }

    private static String joinCharSequences(List<CharSequence> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (CharSequence cs : list) {
            if (cs != null) {
                String t = cs.toString().trim();
                if (!t.isEmpty()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(t);
                }
            }
        }
        return sb.toString();
    }

    private String resolveAppLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return "";
        }
    }
}
