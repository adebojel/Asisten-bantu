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
                "diam dulu", "diam", "jangan bicara", "berhenti bicara",
                "mode diam", "nonaktifkan asisten", "matikan asisten",
                "istirahat dulu", "jangan merespons");
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
                .replaceAll("[^\\\\p{L}\\\\p{N}]+", " ")
                .trim()
                .replaceAll("\\\\s+", " ");
    }
}
