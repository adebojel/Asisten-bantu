package com.asisten.bantu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Pengelola Memori AI Lokal (Persistent Memory Manager) tanpa server eksternal.
 * Menyimpan preferensi, fakta pribadi, kebiasaan, dan catatan penting pengguna agar
 * seluruh model AI (Gemma 3 1B, Gemini, maupun OpenRouter) selalu relevan.
 */
object LocalMemoryStore {

    private const val PREFS_NAME = KobraConfig.PREFS_CONFIG
    private const val KEY_MEMORY_JSON = "local_ai_memory_v1"
    private const val MAX_MEMORIES = 60

    data class MemoryItem(
        val id: String,
        val text: String,
        val category: String,
        val createdAt: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("text", text)
            put("category", category)
            put("created_at", createdAt)
        }

        companion object {
            fun fromJson(obj: JSONObject): MemoryItem? {
                val txt = obj.optString("text", "").trim()
                if (txt.isEmpty()) return null
                return MemoryItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    text = txt,
                    category = obj.optString("category", "Umum"),
                    createdAt = obj.optLong("created_at", System.currentTimeMillis())
                )
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun listMemories(context: Context): List<MemoryItem> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MEMORY_JSON, "[]") ?: "[]"
        val out = ArrayList<MemoryItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                MemoryItem.fromJson(obj)?.let { out.add(it) }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    @JvmStatic
    @Synchronized
    fun addMemory(context: Context, text: String, category: String = "Preferensi"): MemoryItem? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val list = listMemories(context).toMutableList()
        val lower = clean.lowercase(Locale.ROOT)
        list.removeAll { it.text.lowercase(Locale.ROOT) == lower }
        val item = MemoryItem(
            id = "mem_${System.currentTimeMillis()}_${(100..999).random()}",
            text = clean,
            category = category.ifBlank { "Preferensi" },
            createdAt = System.currentTimeMillis()
        )
        list.add(0, item)
        while (list.size > MAX_MEMORIES) {
            list.removeAt(list.lastIndex)
        }
        persist(context, list)
        return item
    }

    @JvmStatic
    @Synchronized
    fun updateMemory(context: Context, id: String, newText: String, category: String = "Preferensi"): Boolean {
        val clean = newText.trim()
        if (clean.isEmpty()) return false
        val list = listMemories(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return false
        list[idx] = list[idx].copy(text = clean, category = category.ifBlank { list[idx].category })
        persist(context, list)
        return true
    }

    @JvmStatic
    @Synchronized
    fun deleteMemory(context: Context, id: String): Boolean {
        val list = listMemories(context).toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) persist(context, list)
        return removed
    }

    @JvmStatic
    @Synchronized
    fun clearAll(context: Context) {
        persist(context, emptyList())
    }

    @JvmStatic
    fun buildPromptContext(context: Context, maxItems: Int = 8): String {
        val items = listMemories(context)
        if (items.isEmpty()) return ""
        val sb = StringBuilder("Memori pengguna yang harus diingat:\n")
        for (item in items.take(maxItems)) {
            sb.append("- [").append(item.category).append("] ").append(item.text).append('\n')
        }
        return sb.toString().trim()
    }

    @JvmStatic
    fun formatSpeechSummary(context: Context): String {
        val items = listMemories(context)
        if (items.isEmpty()) {
            return "Memori lokal AI masih kosong. Katakan ingat bahwa, diikuti informasi yang ingin disimpan."
        }
        val sb = StringBuilder("Ada ${items.size} memori tersimpan. ")
        for ((idx, item) in items.take(5).withIndex()) {
            sb.append("Memori ${idx + 1}: ${item.text}. ")
        }
        return sb.toString().trim()
    }

    @JvmStatic
    fun formatDate(ts: Long): String {
        return try {
            SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID")).format(Date(ts))
        } catch (_: Throwable) {
            ""
        }
    }

    @Synchronized
    private fun persist(context: Context, list: List<MemoryItem>) {
        val arr = JSONArray()
        for (m in list) arr.put(m.toJson())
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MEMORY_JSON, arr.toString()).apply()
    }
}
