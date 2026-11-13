package com.asisten.bantu;

import android.content.Context;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.util.Locale;

/**
 * Penerjemah offline ML Kit Translation untuk Bahasa Indonesia <-> Inggris / Arab.
 * Kamus diunduh sekali saat pertama digunakan lalu berjalan 100% offline.
 */
public final class OfflineTranslatorHelper {

    public interface Callback {
        void onTranslated(String translatedText, String targetLangLabel);
        void onStatus(String statusMessage);
        void onError(String message);
    }

    public static final class ParsedRequest {
        public final String sourceLang;
        public final String targetLang;
        public final String text;

        ParsedRequest(String sourceLang, String targetLang, String text) {
            this.sourceLang = sourceLang;
            this.targetLang = targetLang;
            this.text = text;
        }
    }

    private OfflineTranslatorHelper() {}

    public static String languageLabel(String mlkitLang) {
        if (TranslateLanguage.ENGLISH.equals(mlkitLang)) return "Inggris";
        if (TranslateLanguage.ARABIC.equals(mlkitLang)) return "Arab";
        return "Indonesia";
    }

    public static void translate(Context context, String text, String sourceLang, String targetLang, Callback cb) {
        if (text == null || text.trim().isEmpty()) {
            cb.onError("Teks yang akan diterjemahkan masih kosong.");
            return;
        }
        String src = sourceLang == null ? TranslateLanguage.INDONESIAN : sourceLang;
        String dst = targetLang == null ? TranslateLanguage.ENGLISH : targetLang;
        if (src.equals(dst)) {
            cb.onTranslated(text.trim(), languageLabel(dst));
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(dst)
                .build();
        Translator translator = Translation.getClient(options);

        cb.onStatus("Memeriksa kamus offline " + languageLabel(src) + " ke " + languageLabel(dst) + "...");
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    translator.translate(text.trim())
                            .addOnSuccessListener(result -> {
                                try { translator.close(); } catch (Exception ignored) {}
                                cb.onTranslated(result, languageLabel(dst));
                            })
                            .addOnFailureListener(e -> {
                                try { translator.close(); } catch (Exception ignored) {}
                                cb.onError("Gagal menerjemahkan teks: " + (e.getMessage() != null ? e.getMessage() : "kesalahan sistem"));
                            });
                })
                .addOnFailureListener(e -> {
                    try { translator.close(); } catch (Exception ignored) {}
                    cb.onError("Kamus offline belum tersedia. Sambungkan internet sekali untuk mengunduh kamus "
                            + languageLabel(src) + " ke " + languageLabel(dst) + ".");
                });
    }

    /** Mengurai perintah suara seperti "terjemahkan ke inggris selamat pagi" atau "terjemahkan dari arab ...". */
    public static ParsedRequest parseVoiceRequest(String cmd) {
        if (cmd == null) return null;
        String s = cmd.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        String src = TranslateLanguage.INDONESIAN;
        String dst = TranslateLanguage.ENGLISH;

        if (lower.contains("dari inggris ke indonesia") || lower.contains("dari bahasa inggris ke indonesia")) {
            src = TranslateLanguage.ENGLISH;
            dst = TranslateLanguage.INDONESIAN;
        } else if (lower.contains("dari arab ke indonesia") || lower.contains("dari bahasa arab ke indonesia")) {
            src = TranslateLanguage.ARABIC;
            dst = TranslateLanguage.INDONESIAN;
        } else if (lower.contains("ke arab") || lower.contains("ke bahasa arab") || lower.contains("bahasa arabnya")) {
            src = TranslateLanguage.INDONESIAN;
            dst = TranslateLanguage.ARABIC;
        } else if (lower.contains("ke inggris") || lower.contains("ke bahasa inggris") || lower.contains("bahasa inggrisnya")) {
            src = TranslateLanguage.INDONESIAN;
            dst = TranslateLanguage.ENGLISH;
        } else if (lower.contains("dari inggris") || lower.contains("dari bahasa inggris")) {
            src = TranslateLanguage.ENGLISH;
            dst = TranslateLanguage.INDONESIAN;
        } else if (lower.contains("dari arab") || lower.contains("dari bahasa arab")) {
            src = TranslateLanguage.ARABIC;
            dst = TranslateLanguage.INDONESIAN;
        } else if (lower.contains("ke indonesia") || lower.contains("ke bahasa indonesia")) {
            src = TranslateLanguage.ENGLISH;
            dst = TranslateLanguage.INDONESIAN;
        }

        String text = lower
                .replaceFirst("^(terjemahkan|arti|artikan|translate)\\s*", "")
                .replaceAll("\\b(dari|ke)\\s+(bahasa\\s+)?(indonesia|inggris|arab)\\b", " ")
                .replaceAll("\\bbahasa\\s+(inggrisnya|arabnya|indonesianya)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return new ParsedRequest(src, dst, text);
    }
}
