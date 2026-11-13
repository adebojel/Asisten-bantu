package com.asisten.bantu;

import android.graphics.Bitmap;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pengurai cerdas offline untuk Mata Kedua:
 * - Nominal lembaran uang Rupiah (untuk penghitung lembar uang tunai berurutan)
 * - Pembaca angka/teks layar perangkat digital (ATM, timbangan, tensimeter, meteran)
 * - Pemindai struk & tangkapan layar transfer bank / dompet digital Indonesia
 */
public final class MoneyAndReceiptParser {

    public static final class BillResult {
        public final long amount;
        public final boolean confidentOcr;
        public final String explanation;

        BillResult(long amount, boolean confidentOcr, String explanation) {
            this.amount = amount;
            this.confidentOcr = confidentOcr;
            this.explanation = explanation;
        }
    }

    private static final long[] VALID_RUPIAH = {
            100_000L, 50_000L, 20_000L, 10_000L, 5_000L, 2_000L, 1_000L, 500L, 200L, 100L
    };

    private static final String[] BANK_PROVIDERS = {
            "BCA", "BRI", "BNI", "MANDIRI", "BSI", "CIMB", "PERMATA", "DANAMON",
            "SEABANK", "JAGO", "BTN", "MEGA", "OCBC", "PANIN",
            "DANA", "OVO", "GOPAY", "SHOPEEPAY", "LINKAJA", "QRIS", "BI-FAST"
    };

    private static final Pattern RP_AMOUNT = Pattern.compile(
            "(?:RP\\.?|IDR)\\s*([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{4,9})(?:[.,]00)?",
            Pattern.CASE_INSENSITIVE);

    private MoneyAndReceiptParser() {}

    /** Memformat angka rupiah agar mudah dibaca layar dan TTS. */
    public static String formatRupiah(long amount) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("id", "ID"));
        return nf.format(amount) + " rupiah";
    }

    /** Mengenali nominal lembaran uang Rupiah dari teks OCR kamera dan warna dominan bila perlu. */
    public static BillResult parseRupiahBill(String ocrText, Bitmap bmp) {
        String text = ocrText == null ? "" : ocrText;
        String upper = text.toUpperCase(Locale.ROOT);
        String digitsOnly = upper.replace(".", "").replace(",", "").replace("O", "0");

        if (upper.contains("SERATUS RIBU") || digitsOnly.contains("100000")) {
            return new BillResult(100_000L, true, "Seratus ribu rupiah");
        }
        if (upper.contains("LIMA PULUH RIBU") || digitsOnly.contains("50000")) {
            return new BillResult(50_000L, true, "Lima puluh ribu rupiah");
        }
        if (upper.contains("DUA PULUH RIBU") || digitsOnly.contains("20000")) {
            return new BillResult(20_000L, true, "Dua puluh ribu rupiah");
        }
        if (upper.contains("SEPULUH RIBU") || digitsOnly.contains("10000")) {
            return new BillResult(10_000L, true, "Sepuluh ribu rupiah");
        }
        if (upper.contains("LIMA RIBU") || digitsOnly.contains("5000")) {
            return new BillResult(5_000L, true, "Lima ribu rupiah");
        }
        if (upper.contains("DUA RIBU") || digitsOnly.contains("2000")) {
            return new BillResult(2_000L, true, "Dua ribu rupiah");
        }
        if (upper.contains("SERIBU") || digitsOnly.contains("1000")) {
            return new BillResult(1_000L, true, "Seribu rupiah");
        }

        // Jika teks uang terdeteksi sebagian tetapi angkanya buram, gunakan ciri warna dominan Rupiah
        if (bmp != null && (upper.contains("BANK INDONESIA") || upper.contains("RUPIAH") || upper.contains("NKRI"))) {
            String color = OfflineVision.colorName(bmp);
            switch (color) {
                case "merah":
                case "merah muda":
                    return new BillResult(100_000L, false, "Kemungkinan seratus ribu rupiah dari warna merah");
                case "biru":
                    return new BillResult(50_000L, false, "Kemungkinan lima puluh ribu rupiah dari warna biru");
                case "hijau":
                    return new BillResult(20_000L, false, "Kemungkinan dua puluh ribu rupiah dari warna hijau");
                case "ungu":
                    return new BillResult(10_000L, false, "Kemungkinan sepuluh ribu rupiah dari warna ungu");
                case "cokelat":
                case "oranye":
                case "kuning":
                    return new BillResult(5_000L, false, "Kemungkinan lima ribu rupiah dari warna kuning kecokelatan");
                case "abu-abu":
                    return new BillResult(2_000L, false, "Kemungkinan dua ribu rupiah dari warna abu-abu");
                default:
                    break;
            }
        }
        return new BillResult(0L, false, "");
    }

    /** Mengekstrak nominal rupiah dari jawaban teks AI online (misal "Uang kertas Rp 50.000"). */
    public static long parseAiNominal(String aiText) {
        if (aiText == null || aiText.trim().isEmpty()) return 0L;
        String lower = aiText.toLowerCase(Locale.ROOT);
        String compact = lower.replace(".", "").replace(",", "");
        for (long val : VALID_RUPIAH) {
            if (compact.contains(String.valueOf(val))) {
                return val;
            }
        }
        if (lower.contains("seratus ribu")) return 100_000L;
        if (lower.contains("lima puluh ribu")) return 50_000L;
        if (lower.contains("dua puluh ribu")) return 20_000L;
        if (lower.contains("sepuluh ribu")) return 10_000L;
        if (lower.contains("lima ribu")) return 5_000L;
        if (lower.contains("dua ribu")) return 2_000L;
        if (lower.contains("seribu")) return 1_000L;
        if (lower.contains("lima ratus")) return 500L;
        if (lower.contains("dua ratus")) return 200L;
        if (lower.contains("seratus")) return 100L;
        return 0L;
    }

    /** Mengurai pembacaan layar perangkat digital (ATM, timbangan, termometer, meteran, tensimeter). */
    public static String parseDigitalScreen(String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            return "Layar digital belum terbaca. Dekatkan kamera dan hindari pantulan cahaya.";
        }
        String cleaned = ocrText.replaceAll("[|]+", " ").replaceAll("\\s+", " ").trim();
        // Perbaiki huruf O di antara angka agar terbaca sebagai 0
        cleaned = cleaned.replaceAll("(?<=\\d)[Oo](?=\\d)", "0")
                         .replaceAll("(?<=\\d)[Oo]\\b", "0");

        StringBuilder units = new StringBuilder();
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.contains("kg")) units.append("Satuan kilogram terdeteksi. ");
        else if (lower.contains("mmhg")) units.append("Pembacaan tekanan darah terdeteksi. ");
        else if (lower.contains("kwh")) units.append("Pembacaan meteran listrik terdeteksi. ");
        else if (lower.contains("rp") || lower.contains("saldo") || lower.contains("pin")) {
            units.append("Pembacaan layar transaksi terdeteksi. ");
        }

        if (cleaned.length() > 320) {
            cleaned = cleaned.substring(0, 320) + ". Masih ada teks lainnya.";
        }
        return units + "Terbaca di layar: " + cleaned;
    }

    /** Memindai struk kertas EDC/ATM atau tangkapan layar transfer bank & dompet digital. */
    public static String parseBankTransferReceipt(String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            return "Struk atau tangkapan layar transfer tidak terbaca. Pastikan tulisan jelas dan tidak terpotong.";
        }

        String[] rawLines = ocrText.split("\\r?\\n");
        String upperAll = ocrText.toUpperCase(Locale.ROOT);

        String bank = "";
        for (String b : BANK_PROVIDERS) {
            if (upperAll.contains(b)) {
                bank = b;
                break;
            }
        }

        String status = "";
        if (upperAll.contains("BERHASIL") || upperAll.contains("SUKSES") || upperAll.contains("SUCCESS")) {
            status = "Transfer berhasil";
        } else if (upperAll.contains("GAGAL") || upperAll.contains("FAILED")) {
            status = "Transaksi gagal";
        } else if (upperAll.contains("PENDING") || upperAll.contains("DIPROSES")) {
            status = "Transaksi masih diproses";
        }

        String nominal = extractTransferAmount(ocrText, rawLines);
        String sender = extractParty(rawLines, "PENGIRIM", "DARI", "SUMBER DANA", "REKENING ASAL", "PEMILIK");
        String recipient = extractParty(rawLines, "PENERIMA", "KEPADA", "TUJUAN", "NAMA PENERIMA", "REKENING TUJUAN", "KE ");
        String dateLine = extractDateLine(rawLines);

        if (nominal.isEmpty() && sender.isEmpty() && recipient.isEmpty() && bank.isEmpty()) {
            String snippet = ocrText.replaceAll("\\s+", " ").trim();
            if (snippet.length() > 240) snippet = snippet.substring(0, 240);
            return "Bukti transfer belum dapat dikenali formatnya. Teks yang terbaca: " + snippet;
        }

        StringBuilder sb = new StringBuilder("Hasil pindai bukti transfer");
        if (!bank.isEmpty()) sb.append(" ").append(bank);
        sb.append(". ");
        if (!status.isEmpty()) sb.append(status).append(". ");
        if (!nominal.isEmpty()) sb.append("Nominal ").append(nominal).append(". ");
        else sb.append("Nominal belum terbaca jelas. ");
        if (!sender.isEmpty()) sb.append("Pengirim: ").append(sender).append(". ");
        if (!recipient.isEmpty()) sb.append("Penerima: ").append(recipient).append(". ");
        if (!dateLine.isEmpty()) sb.append("Waktu: ").append(dateLine).append(".");
        return sb.toString().trim();
    }

    private static String extractTransferAmount(String fullText, String[] lines) {
        // Prioritaskan baris dengan kata kunci nominal / total / jumlah
        for (int i = 0; i < lines.length; i++) {
            String u = lines[i].toUpperCase(Locale.ROOT);
            if (u.contains("NOMINAL") || u.contains("JUMLAH") || u.contains("TOTAL") || u.contains("TRANSFER")) {
                String candidate = matchRpValue(lines[i]);
                if (!candidate.isEmpty()) return candidate;
                if (i + 1 < lines.length) {
                    candidate = matchRpValue(lines[i + 1]);
                    if (!candidate.isEmpty()) return candidate;
                }
            }
        }
        // Fallback: ambil nilai Rp terbesar yang masuk akal (>= 1000)
        Matcher m = RP_AMOUNT.matcher(fullText);
        long best = 0L;
        while (m.find()) {
            long val = parseDigitsSafe(m.group(1));
            if (val >= 1000L && val > best) {
                best = val;
            }
        }
        return best > 0L ? formatRupiah(best) : "";
    }

    private static String matchRpValue(String line) {
        Matcher m = RP_AMOUNT.matcher(line);
        if (m.find()) {
            long v = parseDigitsSafe(m.group(1));
            if (v > 0) return formatRupiah(v);
        }
        return "";
    }

    private static long parseDigitsSafe(String raw) {
        if (raw == null) return 0L;
        String d = raw.replace(".", "").replace(",", "").trim();
        try {
            return Long.parseLong(d);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String extractParty(String[] lines, String... keys) {
        List<String> normalized = new ArrayList<>();
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty()) normalized.add(t);
        }
        for (int i = 0; i < normalized.size(); i++) {
            String line = normalized.get(i);
            String u = line.toUpperCase(Locale.ROOT);
            for (String k : keys) {
                if (u.contains(k)) {
                    int colon = line.indexOf(':');
                    if (colon >= 0 && colon + 1 < line.length()) {
                        String after = line.substring(colon + 1).trim();
                        if (!after.isEmpty() && !after.matches("[0-9*xX\\- ]{4,}")) {
                            return after;
                        }
                    }
                    String stripped = u.replace(k.trim(), "").replace(":", "").trim();
                    if (stripped.length() >= 3 && !stripped.matches("[0-9*xX\\- ]+")) {
                        return line.replaceFirst("(?i)^.*?" + Pattern.quote(k.trim()) + "[:\\s]*", "").trim();
                    }
                    if (i + 1 < normalized.size()) {
                        String next = normalized.get(i + 1);
                        if (!next.toUpperCase(Locale.ROOT).startsWith("RP") && !next.matches("[0-9*xX\\- ]{4,}")) {
                            return next;
                        }
                    }
                }
            }
        }
        return "";
    }

    private static String extractDateLine(String[] lines) {
        for (String l : lines) {
            String t = l.trim();
            if (t.matches(".*\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b.*")
                    || t.matches(".*\\b\\d{1,2}\\s+(Jan|Feb|Mar|Apr|Mei|May|Jun|Jul|Agu|Aug|Sep|Okt|Oct|Nov|Des|Dec)[a-z]*\\s+\\d{2,4}.*")) {
                return t;
            }
        }
        return "";
    }
}
