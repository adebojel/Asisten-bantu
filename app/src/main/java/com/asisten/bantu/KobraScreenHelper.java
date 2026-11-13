package com.asisten.bantu;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import java.util.Locale;

/**
 * Pengendali terpadu untuk seluruh layar Kobra OS:
 * 1) Navigasi tombol fisik eksternal / keyboard Bluetooth (DPAD_UP, DPAD_DOWN, ENTER, BACK)
 *    disertai pembacaan fokus lewat Speaker.
 * 2) Gestur layar:
 *    - Usap dua jari ke bawah: baca semua notifikasi terbaru.
 *    - Usap dua jari ke atas: hentikan suara & sirine seketika.
 *    - Ketuk dua kali di mana saja: mulai mendengarkan perintah suara.
 */
public final class KobraScreenHelper {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean twoFingerActive = false;
    private float startTwoFingerY = 0f;
    private boolean swipeTriggered = false;
    private long lastTapDownAt = 0L;
    private float lastTapX = 0f;
    private float lastTapY = 0f;
    private boolean singleFingerMoved = false;

    public boolean handleKeyEvent(Activity activity, KeyEvent event, Speaker speaker) {
        if (activity == null || event == null) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            HapticFeedback.vibrateAction(activity);
            mainHandler.postDelayed(() -> announceCurrentFocus(activity, speaker), 60L);
            return false; // Biarkan sistem memindahkan fokus, lalu baca elemen fokusnya
        }
        if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER) {
            HapticFeedback.vibrateAction(activity);
            return false;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            HapticFeedback.vibrateAction(activity);
            if (speaker != null) {
                speaker.stop();
            }
            if (code == KeyEvent.KEYCODE_ESCAPE) {
                if (activity instanceof androidx.activity.ComponentActivity) {
                    ((androidx.activity.ComponentActivity) activity).getOnBackPressedDispatcher().onBackPressed();
                } else {
                    activity.finish();
                }
                return true;
            }
            return false;
        }
        return false;
    }

    public boolean handleTouchEvent(Activity activity, MotionEvent ev, Speaker speaker) {
        if (activity == null || ev == null) return false;
        float density = activity.getResources().getDisplayMetrics().density;
        float swipeThreshold = 90f * density;
        float doubleTapSlop = 56f * density;

        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                singleFingerMoved = false;
                if (ev.getPointerCount() == 1) {
                    long now = System.currentTimeMillis();
                    float dx = ev.getX() - lastTapX;
                    float dy = ev.getY() - lastTapY;
                    if (now - lastTapDownAt <= 320L && (dx * dx + dy * dy) <= (doubleTapSlop * doubleTapSlop)) {
                        lastTapDownAt = 0L;
                        HapticFeedback.vibrateAction(activity);
                        AsistenAccessibilityService.requestListen();
                        return true;
                    }
                    lastTapDownAt = now;
                    lastTapX = ev.getX();
                    lastTapY = ev.getY();
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (ev.getPointerCount() == 2) {
                    twoFingerActive = true;
                    swipeTriggered = false;
                    startTwoFingerY = (ev.getY(0) + ev.getY(1)) / 2f;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (ev.getPointerCount() == 1) {
                    float dx = Math.abs(ev.getX() - lastTapX);
                    float dy = Math.abs(ev.getY() - lastTapY);
                    if (dx > 18f * density || dy > 18f * density) {
                        singleFingerMoved = true;
                    }
                } else if (twoFingerActive && !swipeTriggered && ev.getPointerCount() >= 2) {
                    float curY = (ev.getY(0) + ev.getY(1)) / 2f;
                    float deltaY = curY - startTwoFingerY;
                    if (deltaY > swipeThreshold) {
                        swipeTriggered = true;
                        HapticFeedback.vibrateSuccess(activity);
                        String notif = KobraNotificationService.summary(12);
                        if (speaker != null) speaker.speak(notif);
                        return true;
                    } else if (deltaY < -swipeThreshold) {
                        swipeTriggered = true;
                        HapticFeedback.vibrateAction(activity);
                        if (speaker != null) speaker.stop();
                        EmergencyBeacon.stopAll(activity, null);
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (singleFingerMoved) {
                    lastTapDownAt = 0L;
                }
                if (ev.getPointerCount() <= 1 || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    boolean consumed = swipeTriggered;
                    twoFingerActive = false;
                    swipeTriggered = false;
                    if (consumed) return true;
                }
                break;
        }
        return false;
    }

    private void announceCurrentFocus(Activity activity, Speaker speaker) {
        if (speaker == null || activity.isFinishing() || activity.isDestroyed()) return;
        View v = activity.getCurrentFocus();
        if (v == null) return;
        StringBuilder sb = new StringBuilder();
        CharSequence desc = v.getContentDescription();
        if (desc != null && desc.toString().trim().length() > 0) {
            sb.append(desc.toString().trim());
        } else if (v instanceof TextView) {
            CharSequence txt = ((TextView) v).getText();
            if (txt != null) sb.append(txt.toString().trim());
        }
        if (v instanceof CompoundButton) {
            sb.append(((CompoundButton) v).isChecked() ? ", aktif" : ", mati");
        }
        String label = sb.toString().replaceAll("\\s+", " ").trim();
        if (!label.isEmpty()) {
            speaker.speak(label.toLowerCase(Locale.ROOT).length() > 140 ? label.substring(0, 140) : label);
        }
    }
}
