package com.asisten.bantu;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mengirim perintah yang tidak dikenali ke webhook n8n milik pengguna (opsional).
 * n8n bisa meneruskannya ke AI, Google Sheet, atau otomatisasi lain, lalu mengirim balasan teks.
 */
public final class N8nClient {

    public interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private N8nClient() {}

    public static void send(String webhookUrl, String command, Callback cb) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            post(cb, null, "Webhook n8n belum diatur.");
            return;
        }
        POOL.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("perintah", command).put("sumber", "kobra-os");
                HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl.trim()).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }
                int code = conn.getResponseCode();
                if (code / 100 != 2) {
                    post(cb, null, "Webhook n8n menjawab kode " + code + ".");
                    return;
                }
                String raw = readAll(conn.getInputStream()).trim();
                String reply = raw;
                try {
                    JSONObject j = new JSONObject(raw);
                    reply = j.optString("balasan", raw);
                } catch (Exception ignored) { }
                post(cb, reply.isEmpty() ? "Perintah diterima." : reply, null);
            } catch (Exception e) {
                post(cb, null, "Tidak bisa terhubung ke n8n. Periksa internet dan alamat webhook.");
            }
        });
    }

    private static String readAll(InputStream is) throws Exception {
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
}
