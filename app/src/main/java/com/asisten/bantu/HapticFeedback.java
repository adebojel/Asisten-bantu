package com.asisten.bantu;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Pengelola getaran taktil berpola (aksi, sukses, peringatan, dan Morse SOS).
 */
public final class HapticFeedback {

    private HapticFeedback() {}

    @SuppressWarnings("deprecation")
    private static Vibrator getVibrator(Context context) {
        Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) return vm.getDefaultVibrator();
        }
        return (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** Getaran singkat untuk setiap aksi suara atau ketukan tombol. */
    public static void vibrateAction(Context context) {
        vibrateOneShot(context, 45);
    }

    /** Getaran ganda untuk konfirmasi berhasil. */
    public static void vibrateSuccess(Context context) {
        vibratePattern(context, new long[]{0, 55, 70, 55}, -1);
    }

    /** Getaran tiga kali untuk peringatan sistem/sensor. */
    public static void vibrateWarning(Context context) {
        vibratePattern(context, new long[]{0, 140, 90, 140, 90, 140}, -1);
    }

    /** Getaran pola Morse SOS (... --- ...) untuk keadaan darurat atau tempat bising. */
    public static void vibrateSosMorse(Context context, boolean repeat) {
        long[] sosTimings = new long[]{
                0,
                150, 110, 150, 110, 150, // S (...)
                250,
                420, 120, 420, 120, 420, // O (---)
                250,
                150, 110, 150, 110, 150, // S (...)
                900
        };
        vibratePattern(context, sosTimings, repeat ? 0 : -1);
    }

    public static void stop(Context context) {
        try {
            Vibrator v = getVibrator(context);
            if (v != null) v.cancel();
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private static void vibrateOneShot(Context context, long ms) {
        try {
            Vibrator v = getVibrator(context);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private static void vibratePattern(Context context, long[] timings, int repeatIndex) {
        try {
            Vibrator v = getVibrator(context);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(timings, repeatIndex));
            } else {
                v.vibrate(timings, repeatIndex);
            }
        } catch (Exception ignored) {}
    }
}
