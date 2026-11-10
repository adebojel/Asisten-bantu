package com.asisten.bantu;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;

/** Gestur layar lewat layanan aksesibilitas: geser dan ketuk di posisi relatif layar. */
public final class DeviceControl {
    private DeviceControl() {}

    /** Arah adalah arah gerak jari: kiri, kanan, atas, atau bawah. */
    public static boolean swipe(AccessibilityService s, String direction) {
        DisplayMetrics dm = s.getResources().getDisplayMetrics();
        float w = dm.widthPixels, h = dm.heightPixels;
        float x1 = w / 2f, y1 = h / 2f, x2 = x1, y2 = y1;
        switch (direction) {
            case "kiri": x1 = w * 0.8f; x2 = w * 0.2f; break;
            case "kanan": x1 = w * 0.2f; x2 = w * 0.8f; break;
            case "atas": y1 = h * 0.7f; y2 = h * 0.3f; break;
            case "bawah": y1 = h * 0.3f; y2 = h * 0.7f; break;
            default: return false;
        }
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        return dispatch(s, p, 320);
    }

    /** fx dan fy antara 0 dan 1 (posisi relatif layar). */
    public static boolean tap(AccessibilityService s, float fx, float fy, long durationMs) {
        DisplayMetrics dm = s.getResources().getDisplayMetrics();
        Path p = new Path();
        p.moveTo(dm.widthPixels * fx, dm.heightPixels * fy);
        return dispatch(s, p, durationMs);
    }

    private static boolean dispatch(AccessibilityService s, Path path, long duration) {
        try {
            GestureDescription g = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                    .build();
            return s.dispatchGesture(g, null, null);
        } catch (Exception e) {
            return false;
        }
    }
}
