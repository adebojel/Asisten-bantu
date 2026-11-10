package com.asisten.bantu;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Memuat basis pengetahuan offline dari assets/kobra_kb.txt dan menyediakan satu OfflineBrain bersama. */
public final class OfflineAi {
    private static OfflineBrain brain;

    private OfflineAi() {}

    public static synchronized OfflineBrain get(Context c) {
        if (brain == null) {
            String text = "";
            try (InputStream is = c.getApplicationContext().getAssets().open("kobra_kb.txt")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
                text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception ignored) { }
            brain = OfflineBrain.fromText(text);
        }
        return brain;
    }
}
