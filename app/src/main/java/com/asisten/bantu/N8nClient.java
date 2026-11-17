package com.asisten.bantu;

import android.os.Handler;
import android.os.Looper;

/**
 * Compatibility facade for retired n8n/webhook integration.
 * Local routines are implemented inside KOBRA; this class never performs network requests.
 */
@Deprecated
public final class N8nClient {
    public interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private N8nClient() {}

    public static void send(String webhookUrl, String command, Callback cb) {
        if (cb != null) {
            MAIN.post(() -> cb.onError("Webhook eksternal dinonaktifkan. Gunakan otomatisasi lokal KOBRA."));
        }
    }
}
