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
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Router AI Online 3-Lapis untuk Kobra OS (Zero Hardcoded API Keys):
 * 1) Prioritas 1 (Opsional Manual): Google Gemini API (gemini-2.5-flash -> fallback gemini-2.0-flash)
 * 2) Prioritas 2 (Opsional Manual): OpenRouter API (model pilihan -> free fallback list)
 * 3) Prioritas 3 (Default Gratis Tanpa API Key): Pollinations Text AI (POST /openai -> fallback GET)
 */
object OnlineAiRouter {

    private const val TAG = "KobraOnlineAiRouter"
    private const val GEMINI_PRIMARY_MODEL = "gemini-2.5-flash"
    private const val GEMINI_FALLBACK_MODEL = "gemini-2.0-flash"
    private const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    private const val POLLINATIONS_OPENAI_ENDPOINT = "https://text.pollinations.ai/openai"
    private const val POLLINATIONS_GET_BASE = "https://text.pollinations.ai/"

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
        val cfg = KobraConfig(context.applicationContext)
        if (cfg.getOpenAiApiKey().isNotEmpty()) return "OpenAI GPT"
        val geminiKey = CommandRouter.resolveGeminiApiKey(context)
        if (geminiKey.isNotEmpty()) return "Gemini AI Studio"
        if (cfg.hasAi()) return "OpenRouter AI"
        return "AI Online Gratis (Tanpa Key)"
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

        // Lapis 1: OpenAI Responses API (GPT online).
        val openAiKey = cfg.getOpenAiApiKey()
        if (openAiKey.isNotEmpty()) {
            val openAiReply = callOpenAiResponses(openAiKey, effectiveSystem, userPrompt)
            if (!openAiReply.isNullOrBlank()) return AiResult(cleanForSpeech(openAiReply), "OpenAI GPT")
        }

        // Lapis 2: Gemini API Key manual
        val geminiKey = CommandRouter.resolveGeminiApiKey(appCtx)
        if (geminiKey.isNotEmpty()) {
            val combinedPrompt = "$effectiveSystem\n\n$userPrompt"
            val geminiReply = callGeminiWithFallback(geminiKey, combinedPrompt, imageBase64)
            if (!geminiReply.isNullOrBlank()) {
                return AiResult(cleanForSpeech(geminiReply), "Gemini AI")
            }
        }

        // Lapis 2: OpenRouter API Key manual
        val openRouterKey = cfg.apiKey
        if (openRouterKey.isNotEmpty()) {
            val orReply = callOpenRouterWithFallback(openRouterKey, cfg, effectiveSystem, userPrompt, imageBase64)
            if (!orReply.isNullOrBlank()) {
                return AiResult(cleanForSpeech(orReply), "OpenRouter AI")
            }
        }

        // Lapis 3: AI Online Gratis Tanpa API Key (Pollinations OpenAI -> GET fallback)
        if (imageBase64.isNullOrEmpty()) {
            val freeReply = callPollinationsFree(effectiveSystem, userPrompt)
            if (!freeReply.isNullOrBlank()) {
                return AiResult(cleanForSpeech(freeReply), "AI Online Gratis")
            }
        }

        return null
    }

    /** OpenAI Responses API text route; this is online, not an embedded model. */
    private fun callOpenAiResponses(apiKey: String, systemPrompt: String, userPrompt: String): String? {
        return try {
            val payload = JSONObject().put("model", "gpt-4.1-mini")
                .put("instructions", systemPrompt).put("input", userPrompt).put("max_output_tokens", 600)
            val request = Request.Builder().url("https://api.openai.com/v1/responses")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) { Log.w(TAG, "OpenAI Responses HTTP ${response.code}: $body"); return null }
                val output = JSONObject(body).optJSONArray("output") ?: return null
                val result = StringBuilder()
                for (i in 0 until output.length()) {
                    val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val part = content.optJSONObject(j) ?: continue
                        if (part.optString("type") == "output_text") result.append(part.optString("text")).append(' ')
                    }
                }
                result.toString().trim().ifEmpty { null }
            }
        } catch (t: Throwable) { Log.w(TAG, "OpenAI Responses gagal: ${t.message}"); null }
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
                    Log.w(TAG, "Gemini ($model) HTTP ${resp.code}: $bodyStr")
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
        cfg: KobraConfig,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String?
    ): String? {
        val candidates = ArrayList<String>()
        if (imageBase64.isNullOrEmpty()) {
            val preferred = cfg.model
            if (preferred.isNotEmpty()) candidates.add(preferred)
            // Gunakan katalog live terlebih dahulu; daftar bawaan tetap menjadi
            // fallback jika endpoint katalog sedang tidak tersedia.
            for (m in discoverFreeTextModels(apiKey)) {
                if (!candidates.contains(m)) candidates.add(m)
            }
            for (m in KobraConfig.TEXT_MODELS) {
                if (!candidates.contains(m)) candidates.add(m)
            }
        } else {
            for (m in KobraConfig.VISION_MODELS) {
                if (!candidates.contains(m)) candidates.add(m)
            }
        }

        // Avoid serially waiting on a long tail of unavailable free models.
        // A short, bounded provider chain is more usable for a voice assistant.
        for (model in candidates.take(3)) {
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
                    Log.w(TAG, "OpenRouter ($model) HTTP ${resp.code}")
                    return null
                }
                parseOpenAiStyleContent(body)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menghubungi OpenRouter ($model): ${t.message}")
            null
        }
    }

    private fun callPollinationsFree(systemPrompt: String, userPrompt: String): String? {
        // 1) HTTP POST OpenAI Chat Completions ke Pollinations
        val postReply = callPollinationsPost(systemPrompt, userPrompt)
        if (!postReply.isNullOrBlank()) return postReply

        // 2) Fallback HTTP GET Pollinations bila POST terhambat
        return callPollinationsGet(systemPrompt, userPrompt)
    }

    private fun callPollinationsPost(systemPrompt: String, userPrompt: String): String? {
        return try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            messages.put(JSONObject().put("role", "user").put("content", userPrompt))

            val payload = JSONObject()
                .put("model", "openai")
                .put("messages", messages)
                .put("private", true)

            val req = Request.Builder()
                .url(POLLINATIONS_OPENAI_ENDPOINT)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "KobraOS/7.2")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty().trim()
                if (!resp.isSuccessful || body.isEmpty()) {
                    Log.w(TAG, "Pollinations POST HTTP ${resp.code}")
                    return null
                }
                if (body.startsWith("{")) {
                    parseOpenAiStyleContent(body)
                } else if (!looksLikeHtmlError(body)) {
                    body
                } else {
                    null
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Pollinations POST gagal: ${t.message}")
            null
        }
    }

    private fun callPollinationsGet(systemPrompt: String, userPrompt: String): String? {
        return try {
            val clippedPrompt = userPrompt.trim().take(900)
            val clippedSystem = systemPrompt.trim().take(500)
            val encodedPrompt = URLEncoder.encode(clippedPrompt, "UTF-8").replace("+", "%20")
            val encodedSystem = URLEncoder.encode(clippedSystem, "UTF-8")
            val url = "${POLLINATIONS_GET_BASE}${encodedPrompt}?model=openai&private=true&system=${encodedSystem}"

            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "KobraOS/7.2")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty().trim()
                if (!resp.isSuccessful || body.isEmpty() || looksLikeHtmlError(body)) {
                    Log.w(TAG, "Pollinations GET HTTP ${resp.code}")
                    return null
                }
                if (body.startsWith("{")) {
                    parseOpenAiStyleContent(body) ?: body
                } else {
                    body
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Pollinations GET gagal: ${t.message}")
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
