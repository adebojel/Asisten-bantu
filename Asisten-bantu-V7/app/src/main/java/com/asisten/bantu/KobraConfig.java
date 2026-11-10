package com.asisten.bantu;

import android.content.Context;
import android.content.SharedPreferences;

/** Semua pengaturan yang tersimpan: API key AI, model, kecepatan suara, dan kontak SOS. */
public final class KobraConfig {

    /** Model gratis OpenRouter untuk percakapan teks, dicoba berurutan jika ada yang sibuk. */
    public static final String[] TEXT_MODELS = {
            "meta-llama/llama-3.3-70b-instruct:free",
            "deepseek/deepseek-chat-v3-0324:free",
            "google/gemma-3-27b-it:free"
    };

    /** Model gratis yang bisa membaca gambar. */
    public static final String[] VISION_MODELS = {
            "google/gemma-3-27b-it:free",
            "mistralai/mistral-small-3.1-24b-instruct:free"
    };

    private final SharedPreferences prefs;
    private final SharedPreferences sos;

    public KobraConfig(Context c) {
        Context app = c.getApplicationContext();
        prefs = app.getSharedPreferences("kobra_config", Context.MODE_PRIVATE);
        sos = app.getSharedPreferences("kobra_sos", Context.MODE_PRIVATE);
    }

    public String getApiKey() { return prefs.getString("api_key", "").trim(); }
    public void setApiKey(String v) { prefs.edit().putString("api_key", v.trim()).apply(); }
    public boolean hasAi() { return !getApiKey().isEmpty(); }

    public String getModel() { return prefs.getString("model", "").trim(); }
    public void setModel(String v) { prefs.edit().putString("model", v.trim()).apply(); }

    public String getN8nUrl() { return prefs.getString("n8n_url", "").trim(); }
    public void setN8nUrl(String v) { prefs.edit().putString("n8n_url", v.trim()).apply(); }

    public boolean isReadNotifications() { return prefs.getBoolean("read_notifications", true); }
    public void setReadNotifications(boolean v) { prefs.edit().putBoolean("read_notifications", v).apply(); }

    public float getSpeechRate() { return prefs.getFloat("speech_rate", 0.9f); }

    /** Kunci: name1, phone1, name2, phone2 (sama dengan versi sebelumnya, data lama tetap terbaca). */
    public String getSos(String key) { return sos.getString(key, "").trim(); }
    public void setSos(String key, String value) { sos.edit().putString(key, value.trim()).apply(); }
}
