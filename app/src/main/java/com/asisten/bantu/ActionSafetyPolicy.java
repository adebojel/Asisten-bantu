package com.asisten.bantu;

import java.util.Locale;

public final class ActionSafetyPolicy {
    private ActionSafetyPolicy() {}

    public static boolean requiresConfirmation(String target) {
        String value = normalize(target);
        if (value.isEmpty()) return false;
        String[] tokens = {
                "hapus", "delete", "remove", "uninstall", "transfer", "bayar", "payment",
                "pembayaran", "pay", "purchase", "beli", "checkout", "kirim", "send",
                "submit", "confirm", "allow", "izinkan", "install", "subscribe",
                "berlangganan", "sign out", "logout", "keluar akun", "clear data",
                "hapus data", "wipe", "reset pabrik", "factory reset", "hapus akun",
                "delete account", "format"
        };
        for (String token : tokens) if (containsPhrase(value, token)) return true;
        return false;
    }

    public static boolean requiresDoubleConfirmation(String target) {
        String value = normalize(target);
        String[] tokens = {
                "transfer", "bayar", "payment", "pembayaran", "factory reset",
                "reset pabrik", "hapus akun", "delete account", "hapus data",
                "clear data", "wipe", "format"
        };
        for (String token : tokens) if (containsPhrase(value, token)) return true;
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static boolean containsPhrase(String value, String phrase) {
        return (" " + value + " ").contains(" " + phrase + " ");
    }
}
