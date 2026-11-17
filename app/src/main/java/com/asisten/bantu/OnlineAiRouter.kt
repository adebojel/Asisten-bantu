package com.asisten.bantu

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Compatibility facade for retired cloud AI routing.
 * KOBRA is offline-only: this class deliberately performs no network requests.
 */
object OnlineAiRouter {
    data class AiResult(val text: String, val providerLabel: String)

    interface Callback {
        fun onResult(text: String, providerLabel: String)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun getActiveProviderSummary(context: Context): String = "AI lokal • Qwen3 / MediaPipe"

    @JvmStatic
    fun askAsync(context: Context, userPrompt: String, callback: Callback) {
        askAsync(context, userPrompt, null, callback)
    }

    @JvmStatic
    fun askAsync(context: Context, userPrompt: String, systemPrompt: String?, callback: Callback) {
        mainHandler.post {
            callback.onError("AI online telah dinonaktifkan. KOBRA hanya menggunakan model dan pengetahuan lokal.")
        }
    }

    @JvmStatic
    fun testConnection(context: Context, callback: Callback) {
        mainHandler.post {
            callback.onError("Koneksi AI server tidak digunakan. Uji model melalui Pengelola Model Lokal.")
        }
    }

    @JvmStatic
    @JvmOverloads
    fun askBlocking(
        context: Context,
        userPrompt: String,
        systemPrompt: String? = null,
        imageBase64: String? = null
    ): AiResult? = null

    @JvmStatic
    fun callGeminiWithFallback(apiKey: String, fullPrompt: String, imageBase64: String?): String? = null
}
