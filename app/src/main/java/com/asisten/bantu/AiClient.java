package com.asisten.bantu;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

/**
 * Compatibility facade for the retired OpenRouter client.
 * This class intentionally contains no HTTP client, endpoint, credential use, or network access.
 */
@Deprecated
public class AiClient {
    public interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    public static final String SYSTEM_TUNANETRA =
            "Kamu adalah Kobra, asisten aksesibilitas lokal untuk pengguna tunanetra. Jawab dalam Bahasa Indonesia, jelas, singkat, dan jujur.";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public AiClient(KobraConfig config) {}

    public void chat(String system, String userText, Callback cb) {
        reportDisabled(cb);
    }

    public void describeImage(String prompt, Bitmap image, Callback cb) {
        reportDisabled(cb);
    }

    private static void reportDisabled(Callback cb) {
        if (cb == null) return;
        MAIN.post(() -> cb.onError("AI server dinonaktifkan. Gunakan kemampuan AI lokal KOBRA."));
    }
}
