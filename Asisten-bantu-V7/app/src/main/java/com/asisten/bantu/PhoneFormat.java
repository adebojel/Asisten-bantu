package com.asisten.bantu;

/** Mengubah nomor telepon ke format internasional tanpa tanda plus (untuk tautan wa.me dan Telegram). */
public final class PhoneFormat {
    private PhoneFormat() {}

    /** Nomor tanpa kode negara dianggap nomor Indonesia. Hasil kosong jika tidak ada angka. */
    public static String toInternational(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        boolean plus = trimmed.startsWith("+");
        String d = trimmed.replaceAll("\\D", "");
        if (d.isEmpty()) return "";
        if (plus) return d;
        if (d.startsWith("00")) return d.substring(2);
        if (d.startsWith("0")) return "62" + d.substring(1);
        if (d.startsWith("62")) return d;
        if (d.startsWith("8")) return "62" + d;
        return d;
    }
}
