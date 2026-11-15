package com.asisten.bantu;

import java.util.Locale;

/** Conservative parser for visible call-status labels; never infers ringback from call launch alone. */
public final class CallStatusParser {
    public enum Status { RINGING, CONNECTED, ENDED, UNKNOWN }

    private CallStatusParser() {}

    public static Status parse(String visibleText) {
        if (visibleText == null || visibleText.trim().isEmpty()) return Status.UNKNOWN;
        String text = visibleText.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ").trim();
        if (contains(text, "panggilan berakhir", "call ended", "panggilan gagal",
                "call failed", "tidak dijawab", "no answer", "sibuk", "busy", "call declined")) {
            return Status.ENDED;
        }
        if (contains(text, "terhubung", "connected", "in call", "panggilan aktif",
                "sedang dalam panggilan")) {
            return Status.CONNECTED;
        }
        if (contains(text, "berdering", "ringing", "memanggil", "calling", "menghubungkan", "connecting")) {
            return Status.RINGING;
        }
        return Status.UNKNOWN;
    }

    private static boolean contains(String text, String... labels) {
        for (String label : labels) {
            if (text.contains(label)) return true;
        }
        return false;
    }
}
