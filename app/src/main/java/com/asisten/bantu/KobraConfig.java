package com.asisten.bantu;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Semua pengaturan yang tersimpan: API key AI, model, suara TTS, kontras, catatan, dan kontak SOS. */
public final class KobraConfig {

    public static final String PREFS_CONFIG = "kobra_config";
    public static final String PREFS_SOS = "kobra_sos";

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

    private final Context appContext;
    private final SharedPreferences prefs;
    private final SharedPreferences sos;

    public KobraConfig(Context c) {
        this.appContext = c.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_CONFIG, Context.MODE_PRIVATE);
        this.sos = appContext.getSharedPreferences(PREFS_SOS, Context.MODE_PRIVATE);
    }

    public String getApiKey() { return prefs.getString("api_key", "").trim(); }
    public void setApiKey(String v) { prefs.edit().putString("api_key", v == null ? "" : v.trim()).apply(); }
    public boolean hasAi() { return !getApiKey().isEmpty(); }

    public String getModel() { return prefs.getString("model", "").trim(); }
    public void setModel(String v) { prefs.edit().putString("model", v == null ? "" : v.trim()).apply(); }

    public String getN8nUrl() { return prefs.getString("n8n_url", "").trim(); }
    public void setN8nUrl(String v) { prefs.edit().putString("n8n_url", v == null ? "" : v.trim()).apply(); }

    public boolean isReadNotifications() { return prefs.getBoolean("read_notifications", true); }
    public void setReadNotifications(boolean v) { prefs.edit().putBoolean("read_notifications", v).apply(); }

    public float getSpeechRate() { return clampTts(prefs.getFloat("speech_rate", 0.9f)); }
    public void setSpeechRate(float rate) { prefs.edit().putFloat("speech_rate", clampTts(rate)).apply(); }

    public float getSpeechPitch() { return clampTts(prefs.getFloat("speech_pitch", 1.0f)); }
    public void setSpeechPitch(float pitch) { prefs.edit().putFloat("speech_pitch", clampTts(pitch)).apply(); }

    public boolean isHighContrast() { return prefs.getBoolean("high_contrast", false); }
    public void setHighContrast(boolean enabled) { prefs.edit().putBoolean("high_contrast", enabled).apply(); }

    public String getNotes() { return prefs.getString("notes", ""); }
    public void setNotes(String notes) { prefs.edit().putString("notes", notes == null ? "" : notes).apply(); }

    public boolean isFallDetectionEnabled() { return prefs.getBoolean("fall_detection", true); }
    public void setFallDetectionEnabled(boolean enabled) { prefs.edit().putBoolean("fall_detection", enabled).apply(); }

    public boolean isPocketLockEnabled() { return prefs.getBoolean("pocket_lock", true); }
    public void setPocketLockEnabled(boolean enabled) { prefs.edit().putBoolean("pocket_lock", enabled).apply(); }

    public boolean isPrivacyMode() { return prefs.getBoolean("privacy_mode", false); }
    public void setPrivacyMode(boolean enabled) { prefs.edit().putBoolean("privacy_mode", enabled).apply(); }

    public String getSecurityPin() { return prefs.getString("security_pin", "").trim(); }
    public void setSecurityPin(String pin) {
        String clean = pin == null ? "" : pin.replaceAll("\\D", "");
        prefs.edit().putString("security_pin", clean).apply();
    }
    public boolean hasSecurityPin() { return getSecurityPin().length() >= 4; }
    public boolean verifySecurityPin(String candidate) {
        if (!hasSecurityPin()) return true;
        if (candidate == null) return false;
        return getSecurityPin().equals(candidate.replaceAll("\\D", ""));
    }

    public boolean isAutoPrivateOnBt() { return prefs.getBoolean("auto_private_bt", true); }
    public void setAutoPrivateOnBt(boolean enabled) { prefs.edit().putBoolean("auto_private_bt", enabled).apply(); }

    public boolean isLayeredConfirmEnabled() { return prefs.getBoolean("layered_confirm", true); }
    public void setLayeredConfirmEnabled(boolean enabled) { prefs.edit().putBoolean("layered_confirm", enabled).apply(); }

    public void setSavedLocation(double lat, double lon) {
        prefs.edit()
                .putString("saved_lat", String.valueOf(lat))
                .putString("saved_lon", String.valueOf(lon))
                .apply();
    }
    public double getSavedLat() {
        try {
            String s = prefs.getString("saved_lat", "");
            return s.isEmpty() ? Double.NaN : Double.parseDouble(s);
        } catch (Exception e) { return Double.NaN; }
    }
    public double getSavedLon() {
        try {
            String s = prefs.getString("saved_lon", "");
            return s.isEmpty() ? Double.NaN : Double.parseDouble(s);
        } catch (Exception e) { return Double.NaN; }
    }

    public String getMedName() { return prefs.getString("med_name", "").trim(); }
    public void setMedName(String v) { prefs.edit().putString("med_name", v == null ? "" : v.trim()).apply(); }

    public String getMedBlood() { return prefs.getString("med_blood", "").trim(); }
    public void setMedBlood(String v) { prefs.edit().putString("med_blood", v == null ? "" : v.trim()).apply(); }

    public String getMedAllergy() { return prefs.getString("med_allergy", "").trim(); }
    public void setMedAllergy(String v) { prefs.edit().putString("med_allergy", v == null ? "" : v.trim()).apply(); }

    public String getMedCondition() { return prefs.getString("med_condition", "").trim(); }
    public void setMedCondition(String v) { prefs.edit().putString("med_condition", v == null ? "" : v.trim()).apply(); }

    public String getMedFamily() { return prefs.getString("med_family", "").trim(); }
    public void setMedFamily(String v) { prefs.edit().putString("med_family", v == null ? "" : v.trim()).apply(); }

    public boolean hasMedicalCard() {
        return !getMedName().isEmpty() || !getMedBlood().isEmpty() || !getMedAllergy().isEmpty()
                || !getMedCondition().isEmpty() || !getMedFamily().isEmpty();
    }

    public String getMedicalCardSpeech() {
        if (!hasMedicalCard()) {
            return "Kartu identitas medis darurat belum diisi. Silakan isi nama, golongan darah, alergi, dan kontak keluarga di menu Kartu Medis.";
        }
        StringBuilder sb = new StringBuilder("Kartu identitas medis darurat. ");
        if (!getMedName().isEmpty()) sb.append("Nama: ").append(getMedName()).append(". ");
        if (!getMedBlood().isEmpty()) sb.append("Golongan darah: ").append(getMedBlood()).append(". ");
        if (!getMedAllergy().isEmpty()) sb.append("Alergi: ").append(getMedAllergy()).append(". ");
        if (!getMedCondition().isEmpty()) sb.append("Kondisi medis atau obat: ").append(getMedCondition()).append(". ");
        if (!getMedFamily().isEmpty()) sb.append("Kontak keluarga darurat: ").append(getMedFamily()).append(".");
        return sb.toString().trim();
    }

    public void saveFavoritePlace(String label, double lat, double lon) {
        if (label == null || label.trim().isEmpty()) return;
        String cleanLabel = label.trim();
        String key = cleanLabel.toLowerCase(Locale.ROOT);
        try {
            JSONObject map = new JSONObject(prefs.getString("favorite_places", "{}"));
            JSONObject item = new JSONObject();
            item.put("label", cleanLabel);
            item.put("lat", lat);
            item.put("lon", lon);
            map.put(key, item);
            prefs.edit().putString("favorite_places", map.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Mengembalikan {lat, lon} untuk label tempat favorit, atau null jika belum ada. */
    public double[] getFavoritePlace(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String q = query.trim().toLowerCase(Locale.ROOT);
        try {
            JSONObject map = new JSONObject(prefs.getString("favorite_places", "{}"));
            if (map.has(q)) {
                JSONObject item = map.getJSONObject(q);
                return new double[]{item.getDouble("lat"), item.getDouble("lon")};
            }
            Iterator<String> it = map.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (q.contains(k) || k.contains(q)) {
                    JSONObject item = map.getJSONObject(k);
                    return new double[]{item.getDouble("lat"), item.getDouble("lon")};
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String getFavoritePlaceLabel(String query) {
        if (query == null) return "";
        String q = query.trim().toLowerCase(Locale.ROOT);
        try {
            JSONObject map = new JSONObject(prefs.getString("favorite_places", "{}"));
            if (map.has(q)) return map.getJSONObject(q).optString("label", query.trim());
            Iterator<String> it = map.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (q.contains(k) || k.contains(q)) {
                    return map.getJSONObject(k).optString("label", k);
                }
            }
        } catch (Exception ignored) {}
        return query.trim();
    }

    public java.util.List<String> listFavoritePlaceLabels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            JSONObject map = new JSONObject(prefs.getString("favorite_places", "{}"));
            Iterator<String> it = map.keys();
            while (it.hasNext()) {
                String k = it.next();
                out.add(map.getJSONObject(k).optString("label", k));
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Kunci: name1, phone1, name2, phone2 (sama dengan versi sebelumnya, data lama tetap terbaca). */
    public String getSos(String key) { return sos.getString(key, "").trim(); }
    public void setSos(String key, String value) { sos.edit().putString(key, value == null ? "" : value.trim()).apply(); }

    public boolean hasSosContact() {
        return !getSos("phone1").isEmpty() || !getSos("phone2").isEmpty();
    }

    private static float clampTts(float v) {
        return Math.max(0.5f, Math.min(2.0f, v));
    }

    /**
     * Mengekspor seluruh data SharedPreferences (kobra_config & kobra_sos) beserta tipe datanya ke JSON.
     * Tidak menyertakan berkas model .task.
     */
    public String exportBackupJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "KobraOS");
        root.put("backup_version", 1);
        root.put("exported_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));

        JSONObject stores = new JSONObject();
        stores.put(PREFS_CONFIG, serializePrefs(prefs));
        stores.put(PREFS_SOS, serializePrefs(sos));
        root.put("prefs", stores);
        return root.toString(2);
    }

    /**
     * Memulihkan seluruh data SharedPreferences dari JSON hasil cadangan dengan menjaga tipe data asli.
     */
    public int importBackupJson(String jsonText) throws Exception {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            throw new IllegalArgumentException("Berkas cadangan kosong.");
        }
        JSONObject root = new JSONObject(jsonText);
        if (!"KobraOS".equals(root.optString("app")) || !root.has("prefs")) {
            throw new IllegalArgumentException("Format berkas bukan cadangan resmi Kobra OS.");
        }
        JSONObject stores = root.getJSONObject("prefs");
        int restoredCount = 0;
        if (stores.has(PREFS_CONFIG)) {
            restoredCount += restorePrefs(prefs, stores.getJSONObject(PREFS_CONFIG));
        }
        if (stores.has(PREFS_SOS)) {
            restoredCount += restorePrefs(sos, stores.getJSONObject(PREFS_SOS));
        }
        return restoredCount;
    }

    private static JSONObject serializePrefs(SharedPreferences sp) throws Exception {
        JSONObject out = new JSONObject();
        Map<String, ?> all = sp.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            Object val = entry.getValue();
            if (val == null) continue;
            JSONObject item = new JSONObject();
            if (val instanceof String) {
                item.put("type", "string");
                item.put("value", val);
            } else if (val instanceof Boolean) {
                item.put("type", "boolean");
                item.put("value", val);
            } else if (val instanceof Integer) {
                item.put("type", "int");
                item.put("value", val);
            } else if (val instanceof Long) {
                item.put("type", "long");
                item.put("value", val);
            } else if (val instanceof Float) {
                item.put("type", "float");
                item.put("value", ((Float) val).doubleValue());
            } else if (val instanceof Set<?>) {
                JSONArray arr = new JSONArray();
                for (Object s : (Set<?>) val) {
                    if (s != null) arr.put(s.toString());
                }
                item.put("type", "stringset");
                item.put("value", arr);
            } else {
                continue;
            }
            out.put(entry.getKey(), item);
        }
        return out;
    }

    private static int restorePrefs(SharedPreferences sp, JSONObject data) throws Exception {
        SharedPreferences.Editor editor = sp.edit();
        editor.clear();
        int count = 0;
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = data.getJSONObject(key);
            String type = item.optString("type", "string");
            switch (type) {
                case "string":
                    editor.putString(key, item.optString("value", ""));
                    count++;
                    break;
                case "boolean":
                    editor.putBoolean(key, item.optBoolean("value", false));
                    count++;
                    break;
                case "int":
                    editor.putInt(key, item.optInt("value", 0));
                    count++;
                    break;
                case "long":
                    editor.putLong(key, item.optLong("value", 0L));
                    count++;
                    break;
                case "float":
                    editor.putFloat(key, (float) item.optDouble("value", 0.0));
                    count++;
                    break;
                case "stringset":
                    JSONArray arr = item.optJSONArray("value");
                    Set<String> set = new HashSet<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            set.add(arr.optString(i, ""));
                        }
                    }
                    editor.putStringSet(key, set);
                    count++;
                    break;
                default:
                    break;
            }
        }
        editor.apply();
        return count;
    }
}
