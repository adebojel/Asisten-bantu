package com.asisten.bantu;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Verifies immutable model files before a local inference runtime can open them. */
public final class ModelFileIntegrity {
    private ModelFileIntegrity() {}

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    public static boolean matches(File file, long expectedBytes, String expectedSha256) {
        if (file == null || expectedSha256 == null || !file.isFile()
                || expectedBytes <= 0 || file.length() != expectedBytes
                || !expectedSha256.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }
        try {
            return expectedSha256.equalsIgnoreCase(sha256(file));
        } catch (Exception ignored) {
            return false;
        }
    }
}
