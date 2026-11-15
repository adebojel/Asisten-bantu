package com.asisten.bantu;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded local transcript shared by ChatActivity and the global voice service. */
public final class UnifiedConversationHistory {
    private static final String PREFS = "kobra_chat_history_v1";
    private static final String KEY = "messages";
    private static final int MAX_MESSAGES = 120;

    private UnifiedConversationHistory() {}

    public static synchronized void append(Context context, String text, boolean fromUser, String meta) {
        if (context == null || text == null || text.trim().isEmpty()) return;
        Context app = context.getApplicationContext();
        JSONArray messages;
        try {
            String raw = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            messages = new JSONArray(raw);
        } catch (Exception ignored) {
            messages = new JSONArray();
        }
        JSONObject item = new JSONObject();
        try {
            item.put("text", text.trim());
            item.put("user", fromUser);
            item.put("meta", meta == null ? "Kobra OS" : meta);
            messages.put(item);
        } catch (Exception ignored) {
            return;
        }
        JSONArray bounded = new JSONArray();
        int start = Math.max(0, messages.length() - MAX_MESSAGES);
        for (int i = start; i < messages.length(); i++) {
            JSONObject value = messages.optJSONObject(i);
            if (value != null) bounded.put(value);
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, bounded.toString()).apply();
    }

    public static String read(Context context) {
        if (context == null) return "[]";
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]");
    }
}
