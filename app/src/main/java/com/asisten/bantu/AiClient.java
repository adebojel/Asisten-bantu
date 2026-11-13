package com.asisten.bantu;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Klien OpenRouter (gratis). Mencoba beberapa model berurutan jika model sedang sibuk. */
public class AiClient {

    public interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    public static final String SYSTEM_TUNANETRA =
            "Kamu adalah Kobra, asisten untuk tunanetra. Jawab dalam Bahasa Indonesia, singkat, jelas, "
            + "dan hangat. Jangan pakai format markdown, simbol, atau daftar bernomor karena jawabanmu "
            + "akan dibacakan lewat suara. Jika menyangkut keselamatan, sebutkan risikonya lebih dulu.";

    private static final String ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final KobraConfig config;

    public AiClient(KobraConfig config) { this.config = config; }

    public void chat(String system, String userText, Callback cb) {
        List<String> models = new ArrayList<>();
        String pref = config.getModel();
        if (!pref.isEmpty()) models.add(pref);
        for (String m : KobraConfig.TEXT_MODELS) if (!models.contains(m)) models.add(m);
        String styledSystem = system + " " + config.getAssistantStyleSystemPrompt();
        run(models, styledSystem, userText, null, cb);
    }

    public void describeImage(String prompt, Bitmap image, Callback cb) {
        List<String> models = new ArrayList<>();
        for (String m : KobraConfig.VISION_MODELS) models.add(m);
        String styledSystem = SYSTEM_TUNANETRA + " " + config.getAssistantStyleSystemPrompt();
        run(models, styledSystem, prompt, image, cb);
    }

    private void run(List<String> models, String system, String user, Bitmap image, Callback cb) {
        final String key = config.getApiKey();
        if (key.isEmpty()) {
            post(cb, null, "API key AI belum diisi. Buka Kobra, lalu Pengaturan AI.");
            return;
        }
        POOL.execute(() -> {
            String error = "Koneksi AI gagal. Periksa koneksi internet.";
            for (String model : models) {
                try {
                    post(cb, request(key, model, system, user, image), null);
                    return;
                } catch (ApiException e) {
                    error = e.getMessage();
                    if (!e.retry) break;
                } catch (Exception e) {
                    error = "Koneksi AI gagal. Periksa koneksi internet.";
                }
            }
            post(cb, null, error);
        });
    }

    private static String request(String key, String model, String system, String user, Bitmap image)
            throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));

        JSONObject userMsg = new JSONObject().put("role", "user");
        if (image == null) {
            userMsg.put("content", user);
        } else {
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("type", "text").put("text", user));
            JSONObject img = new JSONObject().put("url", "data:image/jpeg;base64," + toBase64(image));
            parts.put(new JSONObject().put("type", "image_url").put("image_url", img));
            userMsg.put("content", parts);
        }
        messages.put(userMsg);

        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("max_tokens", 400);

        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setRequestProperty("HTTP-Referer", "https://github.com/adebojel/Asisten-bantu");
        conn.setRequestProperty("X-Title", "Kobra OS");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        int code = conn.getResponseCode();
        String raw = readAll(code / 100 == 2 ? conn.getInputStream() : conn.getErrorStream());
        if (code / 100 != 2) {
            if (code == 401) throw new ApiException("API key tidak valid. Periksa di Pengaturan AI.", false);
            boolean retry = code == 429 || code == 404 || code >= 500;
            throw new ApiException(retry ? "Model gratis sedang sibuk, saya coba model lain."
                    : "AI menolak permintaan, kode " + code, retry);
        }
        JSONObject json = new JSONObject(raw);
        return json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim();
    }

    private static String toBase64(Bitmap src) {
        Bitmap bmp = src;
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max > 1024) {
            float s = 1024f / max;
            bmp = Bitmap.createScaledBitmap(src, Math.round(src.getWidth() * s), Math.round(src.getHeight() * s), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static void post(Callback cb, String text, String error) {
        MAIN.post(() -> {
            if (text != null) cb.onResult(text);
            else cb.onError(error);
        });
    }

    private static class ApiException extends Exception {
        final boolean retry;
        ApiException(String message, boolean retry) {
            super(message);
            this.retry = retry;
        }
    }
}
