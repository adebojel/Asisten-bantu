package com.asisten.bantu

import android.content.Context
import android.util.Log

/**
 * Small deterministic offline knowledge base shipped with the app.
 * Exact keyword matches are answered before an LLM so known safety guidance
 * remains available without network access or model initialization.
 */
object OfflineKnowledgeStore {
    private const val TAG = "KobraOfflineKnowledge"
    @Volatile private var cachedBrain: OfflineBrain? = null

    @JvmStatic
    fun answer(context: Context, query: String): String? {
        if (query.isBlank()) return null
        return try {
            val brain = cachedBrain ?: synchronized(this) {
                cachedBrain ?: context.applicationContext.assets.open("kobra_kb.txt")
                    .bufferedReader(Charsets.UTF_8).use { reader ->
                        OfflineBrain.fromText(reader.readText()).also { cachedBrain = it }
                    }
            }
            brain.answer(query)
        } catch (error: Exception) {
            Log.e(TAG, "Could not load offline knowledge asset", error)
            null
        }
    }
}
