package com.asisten.bantu;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Penyaring keamanan tautan phishing/penipuan pada notifikasi & SMS,
 * serta verifikasi PIN suara/ketukan untuk mengunci menu sensitif.
 */
public final class SecurityAndPinHelper {

    public interface PinCallback {
        void onUnlocked();
        void onCancelled();
    }

    private static final Pattern SUSPICIOUS_URL = Pattern.compile(
            "(https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
            + "|bit\\.ly/|tinyurl\\.com/|s\\.id/|t\\.co/|rb\\.gy/|shorturl\\.at/"
            + "|\\.(apk|xyz|top|click|buzz|gq|tk|ml|cf|work|loan)(/|\\b))",
            Pattern.CASE_INSENSITIVE);

    private static final String[] PHISHING_PHRASES = {
            "klik link", "klik tautan", "undian", "menang hadiah", "rekening diblokir",
            "blokir rekening", "verifikasi akun", "kode otp", "berikan otp",
            "surat tilang", "paket tertahan", "pinjaman langsung cair", "file apk",
            "instal aplikasi", "update tarif", "konfirmasi saldo"
    };

    private SecurityAndPinHelper() {}

    /**
     * Memeriksa teks notifikasi atau SMS terhadap tautan mencurigakan / indikasi phishing.
     * Mengembalikan teks peringatan jika terindikasi berbahaya, atau string kosong jika aman.
     */
    public static String inspectForPhishing(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        boolean hasSuspiciousUrl = SUSPICIOUS_URL.matcher(lower).find();
        boolean hasPhishingPhrase = false;
        for (String phrase : PHISHING_PHRASES) {
            if (lower.contains(phrase)) {
                hasPhishingPhrase = true;
                break;
            }
        }
        boolean hasAnyLink = lower.contains("http://") || lower.contains("https://") || lower.contains("www.");
        if (hasSuspiciousUrl || (hasAnyLink && hasPhishingPhrase)) {
            return "PERINGATAN KEAMANAN! Pesan ini mengandung tautan atau pola penipuan mencurigakan. Jangan buka tautan dan jangan berikan kode rahasia atau OTP.";
        }
        if (hasPhishingPhrase && (lower.contains("transfer") || lower.contains("otp") || lower.contains("apk"))) {
            return "Waspada, pesan ini memuat pola penipuan sensitif. Periksa kebenarannya terlebih dahulu.";
        }
        return "";
    }

    /** Mengekstrak deretan digit PIN dari teks angka atau ucapan kata bilangan Indonesia. */
    public static String extractPinDigits(String spokenOrTyped) {
        if (spokenOrTyped == null) return "";
        String trimmed = spokenOrTyped.trim();
        String onlyDigits = trimmed.replaceAll("\\D", "");
        if (onlyDigits.length() >= 4) {
            return onlyDigits;
        }
        List<Integer> nums = OfflineBrain.numbers(trimmed);
        StringBuilder sb = new StringBuilder();
        for (Integer n : nums) {
            sb.append(n);
        }
        return sb.toString();
    }

    /**
     * Menampilkan dialog PIN kontras tinggi untuk membuka menu sensitif jika PIN telah diatur.
     */
    public static void requirePinIfSet(Context context, KobraConfig config, Speaker speaker, String menuName, PinCallback callback) {
        if (!config.hasSecurityPin()) {
            callback.onUnlocked();
            return;
        }
        speaker.speak("Menu " + menuName + " dikunci PIN keamanan. Masukkan PIN Anda.");
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Masukkan 4-6 digit PIN");
        input.setContentDescription("Kolom PIN keamanan untuk membuka " + menuName);

        new AlertDialog.Builder(context)
                .setTitle("🔒 Kunci PIN: " + menuName)
                .setMessage("Masukkan PIN keamanan (4-6 digit) untuk melanjutkan.")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("BUKA", (dialog, which) -> {
                    String entered = input.getText().toString().trim();
                    if (config.verifySecurityPin(entered)) {
                        HapticFeedback.vibrateSuccess(context);
                        speaker.speak("PIN benar. Menu dibuka.");
                        callback.onUnlocked();
                    } else {
                        HapticFeedback.vibrateWarning(context);
                        speaker.speak("PIN salah. Akses ditolak.");
                        callback.onCancelled();
                    }
                })
                .setNegativeButton("BATAL", (dialog, which) -> {
                    HapticFeedback.vibrateAction(context);
                    speaker.speak("Dibatalkan.");
                    callback.onCancelled();
                })
                .show();
    }
}
