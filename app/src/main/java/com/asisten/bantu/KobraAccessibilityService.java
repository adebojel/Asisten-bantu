package com.asisten.bantu;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
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
    private static final Object YT_LOCK = new Object();
    private static volatile long pendingYoutubeUntilMs = 0L;
    private static volatile String pendingYoutubeQuery = "";

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

    public static void markPendingYoutubeAutoPlay(String query) {
        synchronized (YT_LOCK) {
            pendingYoutubeQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            pendingYoutubeUntilMs = System.currentTimeMillis() + 12_000L;
        }
        KobraAccessibilityService svc = instance;
        if (svc != null) {
            svc.scheduleYoutubeAutoPlayCheck();
        }
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

        // Tangani pemutaran otomatis YouTube jika ada pencarian suara yang baru dibuka (#67)
        if ("com.google.android.youtube".equals(pkg) && System.currentTimeMillis() <= pendingYoutubeUntilMs) {
            main.postDelayed(() -> {
                try {
                    tryConsumeYoutubeAutoPlay(getRootInActiveWindow(), "com.google.android.youtube");
                } catch (Exception ignored) {}
            }, 900L);
        }

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

    // =========================================================================
    // #66 Universal Voice-Click & Scroll Global
    // =========================================================================

    public boolean clickByNameGlobal(String targetName) {
        return clickNodeByName(getRootInActiveWindow(), targetName);
    }

    public boolean scrollGlobal(boolean forward) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo scrollable = findScrollableNode(root, true);
        if (scrollable == null) scrollable = findScrollableNode(root, false);
        if (scrollable == null) return false;
        return scrollable.performAction(forward
                ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    public boolean performGlobalNavAction(int globalAction) {
        return performGlobalAction(globalAction);
    }

    public static boolean clickNodeByName(AccessibilityNodeInfo root, String targetName) {
        if (root == null || targetName == null || targetName.trim().isEmpty()) return false;
        String q = targetName.trim().toLowerCase(Locale.ROOT);
        AccessibilityNodeInfo node = findNodeByLabel(root, q, true);
        if (node == null) {
            node = findNodeByLabel(root, q, false);
        }
        AccessibilityNodeInfo cur = node;
        while (cur != null && !cur.isClickable()) {
            cur = cur.getParent();
        }
        return cur != null && cur.isEnabled() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private static AccessibilityNodeInfo findNodeByLabel(AccessibilityNodeInfo node, String q, boolean exact) {
        if (node == null) return null;
        if (node.isVisibleToUser()) {
            CharSequence t = node.getText();
            CharSequence d = node.getContentDescription();
            if (matchesLabel(t, q, exact) || matchesLabel(d, q, exact)) {
                return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNodeByLabel(node.getChild(i), q, exact);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean matchesLabel(CharSequence cs, String q, boolean exact) {
        if (cs == null) return false;
        String s = cs.toString().trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return false;
        return exact ? s.equals(q) : s.contains(q);
    }

    private static AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node, boolean visibleOnly) {
        if (node == null) return null;
        if (node.isScrollable() && (!visibleOnly || node.isVisibleToUser())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findScrollableNode(node.getChild(i), visibleOnly);
            if (r != null) return r;
        }
        return null;
    }

    // =========================================================================
    // #67 YouTube Voice Search & Auto-Play
    // =========================================================================

    private void scheduleYoutubeAutoPlayCheck() {
        main.postDelayed(() -> tryConsumeYoutubeAutoPlay(getRootInActiveWindow(), "com.google.android.youtube"), 1600L);
        main.postDelayed(() -> tryConsumeYoutubeAutoPlay(getRootInActiveWindow(), "com.google.android.youtube"), 3000L);
    }

    public static boolean tryConsumeYoutubeAutoPlay(AccessibilityNodeInfo root, String pkg) {
        if (root == null) return false;
        String query;
        synchronized (YT_LOCK) {
            if (System.currentTimeMillis() > pendingYoutubeUntilMs) return false;
            CharSequence rootPkg = root.getPackageName();
            String activePkg = rootPkg != null ? rootPkg.toString() : (pkg == null ? "" : pkg);
            if (!activePkg.contains("youtube")) return false;
            query = pendingYoutubeQuery;
        }

        AccessibilityNodeInfo videoNode = findFirstYoutubeVideoNode(root, query, true);
        if (videoNode == null) {
            videoNode = findFirstYoutubeVideoNode(root, query, false);
        }
        if (videoNode == null) return false;

        synchronized (YT_LOCK) {
            if (System.currentTimeMillis() > pendingYoutubeUntilMs) return false;
            pendingYoutubeUntilMs = 0L;
        }
        return videoNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private static AccessibilityNodeInfo findFirstYoutubeVideoNode(AccessibilityNodeInfo node, String query, boolean strict) {
        if (node == null) return null;
        if (node.isClickable() && node.isVisibleToUser() && node.isEnabled()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.top >= 180 && bounds.width() >= 260 && bounds.height() >= 140) {
                String label = collectNodeSummary(node).toLowerCase(Locale.ROOT);
                if (!label.isEmpty() && !isYoutubeChromeLabel(label)) {
                    if (!strict) {
                        if (label.length() >= 12) return node;
                    } else {
                        boolean looksLikeVideo = label.length() >= 20
                                || label.contains("menit") || label.contains("detik") || label.contains("jam")
                                || label.contains("minute") || label.contains("second")
                                || label.contains("ditonton") || label.contains("views")
                                || label.contains("buka channel") || label.contains("go to channel")
                                || (!query.isEmpty() && containsAnyWord(label, query));
                        if (looksLikeVideo) return node;
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findFirstYoutubeVideoNode(node.getChild(i), query, strict);
            if (r != null) return r;
        }
        return null;
    }

    private static boolean isYoutubeChromeLabel(String lower) {
        String[] chrome = {
                "telusuri", "search", "penelusuran suara", "voice search",
                "kembali", "navigate up", "back", "filter", "beranda", "home",
                "shorts", "subscription", "anda", "you", "koleksi", "library",
                "transmisikan", "cast", "notifikasi", "notifications", "akun", "account",
                "opsi lainnya", "more options", "menu tindakan", "buat", "create",
                "semua", "all", "video", "unwatched", "belum ditonton", "terbaru", "live"
        };
        for (String c : chrome) {
            if (lower.equals(c)) return true;
        }
        return lower.startsWith("filter ") || lower.startsWith("telusuri ");
    }

    private static boolean containsAnyWord(String text, String query) {
        for (String w : query.split("\\s+")) {
            if (w.length() >= 3 && text.contains(w)) return true;
        }
        return false;
    }

    private static String collectNodeSummary(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        if (node.getContentDescription() != null && node.getContentDescription().length() > 0) {
            sb.append(node.getContentDescription()).append(" ");
        }
        if (node.getText() != null && node.getText().length() > 0) {
            sb.append(node.getText()).append(" ");
        }
        for (int i = 0; i < node.getChildCount() && sb.length() < 120; i++) {
            AccessibilityNodeInfo ch = node.getChild(i);
            if (ch != null) {
                if (ch.getText() != null && ch.getText().length() > 0) {
                    sb.append(ch.getText()).append(" ");
                } else if (ch.getContentDescription() != null && ch.getContentDescription().length() > 0) {
                    sb.append(ch.getContentDescription()).append(" ");
                }
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    // =========================================================================
    // #68 Global Voice Typing (ACTION_SET_TEXT + Clipboard Paste Fallback)
    // =========================================================================

    public boolean typeGlobalText(String text) {
        return typeTextInRoot(this, getRootInActiveWindow(), text);
    }

    public static boolean typeTextInRoot(Context context, AccessibilityNodeInfo root, String text) {
        if (text == null) return false;
        if (context != null) {
            try {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Kobra OS", text));
                }
            } catch (Exception ignored) {}
        }
        if (root == null) return false;
        AccessibilityNodeInfo field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (field == null || !field.isEditable()) {
            field = findEditableNode(root);
        }
        if (field == null) return false;

        try {
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        } catch (Exception ignored) {}

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return true;
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_PASTE);
    }

    private static AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isVisibleToUser()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findEditableNode(node.getChild(i));
            if (r != null) return r;
        }
        return null;
    }
}
