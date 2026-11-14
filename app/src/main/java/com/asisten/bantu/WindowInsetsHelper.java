package com.asisten.bantu;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Helper terpadu untuk menangani WindowInsets (status bar, notch/cutout, navigation bar, dan IME)
 * pada Android 15/16 (targetSdk 36) agar konten atas dan tombol bawah tidak terpotong.
 */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {}

    public static void applySystemBarInsets(Activity activity, boolean applyBottom) {
        if (activity == null) return;
        try {
            WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        } catch (Throwable ignored) {}
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0) {
            applySystemBarInsets(content.getChildAt(0), applyBottom);
        }
        ThemeManager.applyTheme(activity);
    }

    public static void applySystemBarInsets(View rootView, boolean applyBottom) {
        if (rootView == null) return;
        final int baseLeft = rootView.getPaddingLeft();
        final int baseTop = rootView.getPaddingTop();
        final int baseRight = rootView.getPaddingRight();
        final int baseBottom = rootView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets sysBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime()
            );
            int bottomPadding = applyBottom ? baseBottom + sysBars.bottom : baseBottom;
            v.setPadding(
                    baseLeft + sysBars.left,
                    baseTop + sysBars.top,
                    baseRight + sysBars.right,
                    bottomPadding
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }
}
