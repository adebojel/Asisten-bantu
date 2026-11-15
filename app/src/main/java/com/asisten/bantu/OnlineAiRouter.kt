package com.asisten.bantu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Router AI Online 3-Lapis untuk Kobra OS (Zero Hardcoded API Keys):
 * 1) OpenRouter API: hanya model yang harga input dan output-nya diverifikasi 0.0
 * 2) Jika tidak tersedia/gagal: caller kembali ke Qwen3 lokal atau basis pengetahuan offline
 *
 * Tidak ada endpoint yang memerlukan saldo/kredit atau jalur API berbayar sebagai fallback.
 */
object OnlineAiRouter {

    private const val TAG = "KobraOnlineAiRouter"
    private const val GEMINI_PRIMARY_MODEL = "gemini-2.5-flash"
    private const val GEMINI_FALLBACK_MODEL = "gemini-2.0-flash"
    private const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    data class AiResult(
        val text: String,
        val providerLabel: String
    )

    interface Callback {
        fun onResult(text: String, providerLabel: String)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pool: ExecutorService = Executors.newCachedThreadPool()

    // Katalog model gratis OpenRouter berubah-ubah. Cache hasil discovery agar router
    // dapat memilih model gratis yang masih aktif tanpa bergantung pada satu ID model.
    @Volatile private var freeModelCache: List<String> = emptyList()
    @Volatile private var freeModelCacheAtMs: Long = 0L
    private val freeModelCacheLock = Any()
    private val freeModelCacheTtlMs = TimeUnit.HOURS.toMillis(6)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @JvmStatic
    fun getActiveProviderSummary(context: Context): String {
        val cfg = KobraConfig(context.applicationContext)
        if (cfg.apiKey.isNotEmpty()) return "OpenRouter • model harga nol terverifikasi"
        return "AI lokal Qwen3 • online gratis belum dikonfigurasi"
    }

    @JvmStatic
    fun askAsync(context: Context, userPrompt: String, callback: Callback) {
        askAsync(context, userPrompt, null, callback)
    }

    @JvmStatic
    fun askAsync(context: Context, userPrompt: String, systemPrompt: String?, callback: Callback) {
        val appCtx = context.applicationContext
        if (!NetworkState.isOnline(appCtx)) {
            mainHandler.post {
                callback.onError("Koneksi internet terputus. Periksa Wi-Fi atau Data Seluler.")
            }
            return
        }
        pool.execute {
            val result = askBlocking(appCtx, userPrompt, systemPrompt, null)
            mainHandler.post {
                if (result != null && result.text.isNotBlank()) {
                    callback.onResult(result.text, result.providerLabel)
                } else {
                    callback.onError("Server AI online sedang sibuk atau koneksi tidak stabil.")
                }
            }
        }
    }

    @JvmStatic
    fun testConnection(context: Context, callback: Callback) {
        val activeName = VoiceProfileManager.getActiveAssistantName(context)
        val testPrompt = "Perkenalkan dirimu sebagai $activeName dari Kobra OS dalam satu kalimat Bahasa Indonesia yang ramah dan singkat."
        val sysPrompt = KobraPersona.buildFullSystemPrompt(context)
        askAsync(context, testPrompt, sysPrompt, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun askBlocking(
        context: Context,
        userPrompt: String,
        systemPrompt: String? = null,
        imageBase64: String? = null
    ): AiResult? {
        val appCtx = context.applicationContext
        if (!NetworkState.isOnline(appCtx)) return null

        val cfg = KobraConfig(appCtx)
        val effectiveSystem = if (!systemPrompt.isNullOrBlank()) {
            systemPrompt.trim()
        } else {
            KobraPersona.buildFullSystemPrompt(appCtx)
        }

        // Kebijakan Kobra: Gemini API key dinonaktifkan dari failover otomatis
        // karena akun dengan billing aktif dapat dikenai biaya. Jalur online otomatis
        // hanya memakai model OpenRouter yang harga input/output-nya diverifikasi nol,
        // lalu endpoint tanpa API key sebagai fallback.
        // Lapis 2: OpenRouter API Key manual
        val openRouterKey = cfg.apiKey
        if (openRouterKey.isNotEmpty()) {
            val orReply = callOpenRouterWithFallback(openRouterKey, effectiveSystem, userPrompt, imageBase64)
            if (!orReply.isNullOrBlank()) {
                return AiResult(cleanForSpeech(orReply), "OpenRouter AI")
            }
        }

        return null
    }

    @JvmStatic
    fun callGeminiWithFallback(apiKey: String, fullPrompt: String, imageBase64: String?): String? {
        val primary = callGeminiSingleModel(apiKey, GEMINI_PRIMARY_MODEL, fullPrompt, imageBase64)
        if (!primary.isNullOrBlank()) return primary
        return callGeminiSingleModel(apiKey, GEMINI_FALLBACK_MODEL, fullPrompt, imageBase64)
    }

    private fun callGeminiSingleModel(
        apiKey: String,
        model: String,
        prompt: String,
        screenshotBase64: String?
    ): String? {
        return try {
            val parts = JSONArray()
            parts.put(JSONObject().put("text", prompt))
            if (!screenshotBase64.isNullOrEmpty()) {
                val inlineData = JSONObject()
                    .put("mimeType", "image/jpeg")
                    .put("data", screenshotBase64)
                parts.put(JSONObject().put("inlineData", inlineData))
            }

            val content = JSONObject().put("parts", parts)
            val payload = JSONObject()
                .put("contents", JSONArray().put(content))
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.3)
                        .put("maxOutputTokens", 500)
                )

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Gemini ($model) HTTP ${resp.code}; lanjut fallback gratis: $bodyStr")
                    return null
                }
                val root = JSONObject(bodyStr)
                val candidates = root.optJSONArray("candidates") ?: return null
                if (candidates.length() == 0) return null
                val respParts = candidates.getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts") ?: return null
                val sb = StringBuilder()
                for (i in 0 until respParts.length()) {
                    val txt = respParts.getJSONObject(i).optString("text", "")
                    if (txt.isNotEmpty()) sb.append(txt).append(' ')
                }
                sb.toString().trim().ifEmpty { null }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menghubungi Gemini ($model): ${t.message}")
            null
        }
    }

    private fun callOpenRouterWithFallback(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String?
    ): String? {
        val candidates = ArrayList<String>()
        if (imageBase64.isNullOrEmpty()) {
            // Hanya gunakan ID yang ditemukan pada katalog harga live dan memiliki
            // tarif prompt serta completion tepat 0.0. Akhiran ":free" saja tidak
            // cukup sebagai bukti harga; bila katalog gagal dibaca, jangan kirim request.
            val discoveredFree = discoverFreeTextModels(apiKey)
            for (m in discoveredFree) {
                if (!candidates.contains(m)) candidates.add(m)
            }
        } else {
            // Untuk gambar, jangan memakai daftar statis yang belum diverifikasi harganya.
            // Model vision hanya dipakai jika katalog provider menyatakan kedua tarif nol.
            for (m in discoverFreeVisionModels(apiKey)) {
                if (!candidates.contains(m)) candidates.add(m)
            }
        }

        // Jika satu model limit/429/5xx, lanjut ke model gratis berikutnya.
        for (model in candidates.distinct()) {
            val reply = callOpenRouterSingle(apiKey, model, systemPrompt, userPrompt, imageBase64)
            if (!reply.isNullOrBlank()) return reply
        }
        return null
    }

    private fun discoverFreeTextModels(apiKey: String): List<String> {
        val now = System.currentTimeMillis()
        val cached = freeModelCache
        if (cached.isNotEmpty() && now - freeModelCacheAtMs < freeModelCacheTtlMs) return cached

        return synchronized(freeModelCacheLock) {
            val secondCheck = freeModelCache
            if (secondCheck.isNotEmpty() && now - freeModelCacheAtMs < freeModelCacheTtlMs) {
                secondCheck
            } else {
                try {
                    val request = Request.Builder()
                        .url("https://openrouter.ai/api/v1/models")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("HTTP-Referer", "https://github.com/adebojel/Asisten-bantu")
                        .addHeader("X-Title", "Kobra OS")
                        .get()
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            secondCheck
                        } else {
                            val data = JSONObject(response.body?.string().orEmpty()).optJSONArray("data")
                            val discovered = ArrayList<String>()
                            if (data != null) {
                                for (i in 0 until data.length()) {
                                    val model = data.optJSONObject(i) ?: continue
                                    val id = model.optString("id", "").trim()
                                    val pricing = model.optJSONObject("pricing") ?: continue
                                    val promptPrice = pricing.optString("prompt", "")
                                    val completionPrice = pricing.optString("completion", "")
                                    val isFree = promptPrice.toDoubleOrNull() == 0.0 &&
                                            completionPrice.toDoubleOrNull() == 0.0
                                    val modality = model.optJSONObject("architecture")
                                        ?.optString("modality", "").orEmpty()
                                    val isTextCapable = modality.isEmpty() || modality.contains("text")
                                    if (id.isNotEmpty() && isFree && isTextCapable &&
                                            !id.contains("embedding", ignoreCase = true) &&
                                            !id.contains("audio", ignoreCase = true)) {
                                        discovered.add(id)
                                    }
                                }
                            }
                            val result = discovered.distinct().take(3)
                            if (result.isNotEmpty()) {
                                freeModelCache = result
                                freeModelCacheAtMs = now
                            }
                            result
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Discovery model gratis OpenRouter gagal: ${t.message}")
                    secondCheck
                }
            }
        }
    }

    private fun discoverFreeVisionModels(apiKey: String): List<String> {
        return try {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/adebojel/Asisten-bantu")
                .addHeader("X-Title", "Kobra OS")
                .get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val data = JSONObject(response.body?.string().orEmpty()).optJSONArray("data")
                    ?: return emptyList()
                val result = ArrayList<String>()
                for (i in 0 until data.length()) {
                    val model = data.optJSONObject(i) ?: continue
                    val id = model.optString("id", "").trim()
                    val pricing = model.optJSONObject("pricing") ?: continue
                    val zeroPrice = pricing.optString("prompt", "").toDoubleOrNull() == 0.0 &&
                        pricing.optString("completion", "").toDoubleOrNull() == 0.0
                    val modality = model.optJSONObject("architecture")
                        ?.optString("modality", "").orEmpty()
                    val vision = modality.contains("image", ignoreCase = true)
                    if (id.isNotEmpty() && zeroPrice && vision) result.add(id)
                }
                result.distinct().take(3)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Discovery model vision gratis gagal: ${t.message}")
            emptyList()
        }
    }

    private fun callOpenRouterSingle(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String?
    ): String? {
        return try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))

            val userMsg = JSONObject().put("role", "user")
            if (imageBase64.isNullOrEmpty()) {
                userMsg.put("content", userPrompt)
            } else {
                val parts = JSONArray()
                parts.put(JSONObject().put("type", "text").put("text", userPrompt))
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64"))
                )
                userMsg.put("content", parts)
            }
            messages.put(userMsg)

            val payload = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("max_tokens", 450)

            val req = Request.Builder()
                .url(OPENROUTER_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/adebojel/Asisten-bantu")
                .addHeader("X-Title", "Kobra OS")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "OpenRouter ($model) HTTP ${resp.code}; lanjut fallback gratis")
                    return null
                }
                val parsed = parseOpenAiStyleContent(body)
                if (parsed.isNullOrBlank()) {
                    Log.w(TAG, "OpenRouter ($model) tidak mengembalikan teks; lanjut fallback gratis")
                }
                parsed
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menghubungi OpenRouter ($model): ${t.message}")
            null
        }
    }

    private fun parseOpenAiStyleContent(rawJson: String): String? {
        return try {
            val json = JSONObject(rawJson)
            val choices = json.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val first = choices.getJSONObject(0)
            val msg = first.optJSONObject("message")
            val content = msg?.optString("content", "")?.trim()
                ?: first.optString("text", "").trim()
            content.ifEmpty { null }
        } catch (_: Throwable) {
            null
        }
    }

    private fun looksLikeHtmlError(text: String): Boolean {
        val low = text.lowercase()
        return low.startsWith("<!doctype") || low.startsWith("<html") || low.contains("cloudflare") && low.contains("<body")
    }

    @JvmStatic
    fun cleanForSpeech(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
