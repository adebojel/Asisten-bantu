package com.asisten.bantu;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Small local-only action history for the deterministic screen agent.
 * It stores action names and bounded target labels only; no screenshots,
 * screen dumps, message bodies, passwords, or notification content are recorded.
 */
public final class LocalAgentActionLog {
    private static final String PREFS = "kobra_local_agent_log";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 30;
    private static final int MAX_TARGET_LENGTH = 72;

    private LocalAgentActionLog() {}

    public static synchronized void record(Context context, String action, String target, String result) {
        if (context == null || action == null || action.trim().isEmpty()) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray entries = new JSONArray(prefs.getString(KEY_ENTRIES, "[]"));
            JSONObject item = new JSONObject();
            item.put("at", System.currentTimeMillis());
            item.put("action", action.trim());
            item.put("target", sanitize(target));
            item.put("result", sanitize(result));
            entries.put(item);
            JSONArray bounded = new JSONArray();
            int start = Math.max(0, entries.length() - MAX_ENTRIES);
            for (int i = start; i < entries.length(); i++) bounded.put(entries.getJSONObject(i));
            prefs.edit().putString(KEY_ENTRIES, bounded.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static synchronized String describe(Context context) {
        if (context == null) return "Riwayat agen layar tidak tersedia.";
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray entries = new JSONArray(prefs.getString(KEY_ENTRIES, "[]"));
            if (entries.length() == 0) return "Belum ada riwayat tindakan agen layar.";
            StringBuilder out = new StringBuilder("Ada ")
                    .append(entries.length()).append(" catatan tindakan agen layar. ");
            int start = Math.max(0, entries.length() - 5);
            for (int i = start; i < entries.length(); i++) {
                JSONObject item = entries.getJSONObject(i);
                out.append(item.optString("action", "aksi"));
                String target = item.optString("target", "");
                if (!target.isEmpty()) out.append(" ").append(target);
                out.append(": ").append(item.optString("result", "tidak diketahui")).append(". ");
            }
            return out.toString().trim();
        } catch (Exception ignored) {
            return "Riwayat agen layar tidak dapat dibaca.";
        }
    }

    public static synchronized void clear(Context context) {
        if (context == null) return;
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_ENTRIES).apply();
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        return clean.length() > MAX_TARGET_LENGTH ? clean.substring(0, MAX_TARGET_LENGTH) : clean;
    }
}
