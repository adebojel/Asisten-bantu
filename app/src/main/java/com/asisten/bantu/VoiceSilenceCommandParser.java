package com.asisten.bantu;

import java.util.Locale;

/** Pure command matching for voice silence and wake-word reactivation. */
public final class VoiceSilenceCommandParser {
    private VoiceSilenceCommandParser() { }

    public static boolean isWakeActivationCommand(String wakeTail) {
        return containsAnyPhrase(wakeTail,
                "aktif", "aktifkan", "bangun", "kembali", "nyala", "dengarkan", "saya panggil");
    }

    public static boolean isSilenceCommand(String wakeTail) {
        return containsAnyPhrase(wakeTail,
                "diam dulu", "diam", "jangan bicara",
                "mode diam", "nonaktifkan asisten", "matikan asisten",
                "istirahat dulu", "jangan merespons");
    }

    /** Exact cancellation phrases for free-form message bodies; do not cancel because body contains "tidak". */
    public static boolean isExplicitCancelCommand(String input) {
        String normalized = normalize(input);
        return "tidak".equals(normalized) || "tidak jadi".equals(normalized)
                || "batal".equals(normalized) || "batalkan".equals(normalized)
                || "cancel".equals(normalized) || "jangan kirim".equals(normalized)
                || "gak jadi".equals(normalized) || "nggak jadi".equals(normalized)
                || "enggak jadi".equals(normalized) || "urungkan".equals(normalized);
    }

    /** Stop speech immediately, including while the assistant is reading a long screen aloud. */
    public static boolean isSpeechInterruptCommand(String input) {
        String normalized = normalize(input);
        // Media-stop commands must continue to the media router, not be consumed
        // as speech interruption merely because they contain the word "stop".
        if (normalized.startsWith("stop video") || normalized.startsWith("berhenti video")
                || normalized.startsWith("hentikan video") || normalized.startsWith("pause video")
                || normalized.startsWith("stop musik") || normalized.startsWith("berhenti musik")
                || normalized.startsWith("stop lagu") || normalized.startsWith("hentikan sirine")) {
            return false;
        }
        return containsAnyPhrase(normalized,
                "berhenti", "stop", "diam", "diam dulu", "cukup",
                "berhenti bicara", "hentikan bicara", "hentikan suara",
                "stop bicara", "stop baca", "berhenti membaca", "hentikan bacaan",
                "jeda bicara", "pause suara", "jeda suara", "cukup bicara");
    }

    private static boolean containsAnyPhrase(String input, String... phrases) {
        String text = normalize(input);
        if (text.isEmpty()) return false;
        for (String phrase : phrases) {
            String target = normalize(phrase);
            if (text.equals(target) || text.startsWith(target + " ")
                    || text.endsWith(" " + target) || text.contains(" " + target + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
