package com.asisten.bantu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Local AI memory; all live records are encrypted at rest using Android Keystore. */
object LocalMemoryStore {
    private const val MAX_MEMORIES = 60
    private const val MAX_TEXT_LENGTH = 1200
    private const val MAX_CATEGORY_LENGTH = 48

    data class MemoryItem(val id: String, val text: String, val category: String, val createdAt: Long) {
        fun toJson() = JSONObject().apply {
            put("id", id); put("text", text); put("category", category); put("created_at", createdAt)
        }
        companion object {
            fun fromJson(obj: JSONObject): MemoryItem? {
                val text = obj.optString("text", "").trim().take(MAX_TEXT_LENGTH)
                if (text.isEmpty()) return null
                return MemoryItem(
                    obj.optString("id", UUID.randomUUID().toString()).take(80),
                    text,
                    obj.optString("category", "Umum").trim().take(MAX_CATEGORY_LENGTH).ifBlank { "Umum" },
                    obj.optLong("created_at", System.currentTimeMillis()).coerceAtLeast(0L)
                )
            }
        }
    }

    @JvmStatic @Synchronized
    fun listMemories(context: Context): List<MemoryItem> =
        decode(EncryptedLocalMemoryStore.read(context) ?: return emptyList())

    @JvmStatic @Synchronized
    fun addMemory(context: Context, text: String, category: String = "Preferensi"): MemoryItem? {
        val raw = EncryptedLocalMemoryStore.read(context) ?: return null
        val clean = text.trim().take(MAX_TEXT_LENGTH)
        if (clean.isEmpty()) return null
        val list = decode(raw).toMutableList()
        list.removeAll { it.text.equals(clean, ignoreCase = true) }
        val item = MemoryItem(
            "mem_\${System.currentTimeMillis()}_\${UUID.randomUUID().toString().take(8)}",
            clean, category.trim().take(MAX_CATEGORY_LENGTH).ifBlank { "Preferensi" },
            System.currentTimeMillis()
        )
        list.add(0, item)
        while (list.size > MAX_MEMORIES) list.removeAt(list.lastIndex)
        return if (persist(context, list)) item else null
    }

    @JvmStatic @Synchronized
    fun updateMemory(context: Context, id: String, newText: String, category: String = "Preferensi"): Boolean {
        val raw = EncryptedLocalMemoryStore.read(context) ?: return false
        val clean = newText.trim().take(MAX_TEXT_LENGTH)
        if (clean.isEmpty()) return false
        val list = decode(raw).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return false
        list[idx] = list[idx].copy(
            text = clean,
            category = category.trim().take(MAX_CATEGORY_LENGTH).ifBlank { list[idx].category }
        )
        return persist(context, list)
    }

    @JvmStatic @Synchronized
    fun deleteMemory(context: Context, id: String): Boolean {
        val raw = EncryptedLocalMemoryStore.read(context) ?: return false
        val list = decode(raw).toMutableList()
        if (!list.removeAll { it.id == id }) return false
        return persist(context, list)
    }

    @JvmStatic @Synchronized
    fun clearAll(context: Context) { EncryptedLocalMemoryStore.clear(context) }

    /** Plain JSON is emitted only for an explicit, user-initiated backup export. */
    @JvmStatic @Synchronized
    fun exportBackupJson(context: Context): String {
        val arr = JSONArray()
        listMemories(context).forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    @JvmStatic @Synchronized
    fun importBackupJson(context: Context, json: String): Int {
        require(json.toByteArray(Charsets.UTF_8).size <= 64 * 1024) { "Data memori cadangan terlalu besar." }
        val input = JSONArray(json)
        val imported = ArrayList<MemoryItem>()
        for (i in 0 until minOf(input.length(), MAX_MEMORIES)) {
            input.optJSONObject(i)?.let { MemoryItem.fromJson(it)?.let(imported::add) }
        }
        if (!persist(context, imported)) throw IllegalStateException("Memori tidak dapat dienkripsi atau disimpan.")
        return imported.size
    }

    @JvmStatic fun buildPromptContext(context: Context, maxItems: Int = 8): String {
        val items = listMemories(context)
        if (items.isEmpty()) return ""
        return buildString {
            append("Memori pengguna yang harus diingat:\\n")
            items.take(maxItems.coerceIn(0, MAX_MEMORIES)).forEach {
                append("- [").append(it.category).append("] ").append(it.text).append('\\n')
            }
        }.trim()
    }

    @JvmStatic fun formatSpeechSummary(context: Context): String {
        val items = listMemories(context)
        if (items.isEmpty()) return "Memori lokal AI masih kosong. Katakan ingat bahwa, diikuti informasi yang ingin disimpan."
        return buildString {
            append("Ada ").append(items.size).append(" memori tersimpan. ")
            items.take(5).forEachIndexed { index, item ->
                append("Memori ").append(index + 1).append(": ").append(item.text).append(". ")
            }
        }.trim()
    }

    @JvmStatic fun formatDate(ts: Long): String = try {
        SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID")).format(Date(ts))
    } catch (_: Exception) { "" }

    private fun decode(raw: String): List<MemoryItem> = try {
        val arr = JSONArray(raw)
        val out = ArrayList<MemoryItem>()
        for (i in 0 until minOf(arr.length(), MAX_MEMORIES)) {
            arr.optJSONObject(i)?.let { MemoryItem.fromJson(it)?.let(out::add) }
        }
        out
    } catch (_: Exception) { emptyList() }

    private fun persist(context: Context, list: List<MemoryItem>): Boolean {
        val arr = JSONArray()
        list.take(MAX_MEMORIES).forEach { arr.put(it.toJson()) }
        return EncryptedLocalMemoryStore.write(context, arr.toString())
    }
}