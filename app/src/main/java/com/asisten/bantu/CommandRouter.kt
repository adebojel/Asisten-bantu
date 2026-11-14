package com.asisten.bantu

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import com.asisten.bantu.voiceaccess.VoiceAccessEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * CommandRouter: Pengarah Perintah Hibrida 4-Tingkat untuk Kobra OS.
 *
 * Prioritas 1 — VoiceAccessEngine + Kontrol Guardian / Memori / Briefing Offline:
 *   Nomor layar, Grid 3x3 presisi, edit teks, baca layar, navigasi, media, serta
 *   perintah "saya aman", "ingat bahwa...", dan "ringkasan harian".
 *
 * Prioritas 2 — Local Macro Cache (< 100ms):
 *   Mencocokkan perintah dengan urutan aksi UI yang tersimpan di SharedPreferences dan langsung
 *   mengeksekusinya via UnifiedAccessibilityEngine + RecoveryEngine tanpa memanggil LLM.
 *
 * Prioritas 3 — Offline Gemma 3 1B + Memori Lokal + Persona:
 *   Memproses tanya-jawab offline memakai LocalLlmEngine dengan injeksi LocalMemoryStore & KobraPersona.
 *
 * Prioritas 4 — Online Gemini Vision/Text API (atau OpenRouter fallback):
 *   Mengambil tangkapan layar Android 11+ (Eyes) dan pohon UI AccessibilityNodeInfo, lalu
 *   menjalankan analisis visual atau loop agen multi-langkah dengan Anti-Stuck & Pop-up Recovery.
 */
object CommandRouter {

    private const val TAG = "KobraCommandRouter"
    private const val PREF_GEMINI_KEY = "gemini_api_key"
    private const val GEMINI_MODEL = "gemini-2.5-flash"
    private const val MAX_AGENT_STEPS = 8

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @JvmStatic
    fun setUserGeminiApiKey(context: Context, key: String?) {
        val prefs = context.applicationContext.getSharedPreferences(
            KobraConfig.PREFS_CONFIG,
            Context.MODE_PRIVATE
        )
        prefs.edit().putString(PREF_GEMINI_KEY, key?.trim().orEmpty()).apply()
    }

    @JvmStatic
    fun resolveGeminiApiKey(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(
            KobraConfig.PREFS_CONFIG,
            Context.MODE_PRIVATE
        )
        val userSaved = prefs.getString(PREF_GEMINI_KEY, "")?.trim().orEmpty()
        if (userSaved.isNotEmpty()) return userSaved

        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotEmpty()) return buildKey

        val legacyKey = prefs.getString("api_key", "")?.trim().orEmpty()
        if (legacyKey.startsWith("AIza")) return legacyKey
        return ""
    }

    /**
     * Dipanggil di awal rute suara:
     * 1) VoiceAccessEngine, Guardian, Daily Briefing, dan Memori Lokal
     * 2) Local Macro Cache (< 100ms)
     * 3) Analisis visual layar atau instruksi multi-langkah
     */
    @JvmStatic
    fun tryRouteFastOrVisual(
        service: AsistenAccessibilityService,
        rawCommand: String,
        voiceAccessEngine: VoiceAccessEngine?,
        sayCallback: Consumer<String>
    ): Boolean {
        val raw = rawCommand.trim()
        val c = raw.lowercase(Locale.ROOT)
        if (c.isEmpty()) return false

        // Prioritas 1A: VoiceAccessEngine (jika tidur, hanya terima bangun)
        if (voiceAccessEngine != null && voiceAccessEngine.handleCommand(raw, sayCallback)) {
            return true
        }

        // Konfirmasi Guardian Check-In
        if (c == "saya aman" || c == "aman" || c == "kondisi aman" || c == "konfirmasi aman") {
            GuardianManager.acknowledgeSafe(service, true)
            return true
        }

        // Daily Morning Briefing suara
        if (c.contains("ringkasan harian") || c.contains("briefing pagi") || c.contains("laporan pagi") || c.contains("kabar hari ini")) {
            sayCallback.accept(GuardianManager.buildDailyBriefingSpeech(service))
            return true
        }

        // Manajemen Memori AI Lokal lewat suara
        if (c.startsWith("ingat bahwa ") || c.startsWith("ingat ini ") || c.startsWith("simpan memori ")) {
            val fact = raw.replaceFirst(Regex("^(ingat\\s+bahwa|ingat\\s+ini|simpan\\s+memori)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (fact.isNotEmpty()) {
                LocalMemoryStore.addMemory(service, fact, "Suara")
                sayCallback.accept("Siap, saya ingat: $fact.")
                return true
            }
        }
        if (c.contains("apa yang kamu ingat") || c.contains("daftar memori") || c.contains("cek memori")) {
            sayCallback.accept(LocalMemoryStore.formatSpeechSummary(service))
            return true
        }

        // Kontrol pembaca layar global terpadu
        if (c.contains("nyalakan pembaca layar global") || c.contains("aktifkan pembaca global")) {
            UnifiedAccessibilityEngine.setGlobalReaderEnabled(service, true)
            sayCallback.accept("Pembaca layar global diaktifkan. Aplikasi di luar Kobra OS kini dibacakan saat disentuh.")
            return true
        }
        if (c.contains("matikan pembaca layar global") || c.contains("nonaktifkan pembaca global")) {
            UnifiedAccessibilityEngine.setGlobalReaderEnabled(service, false)
            sayCallback.accept("Pembaca layar global dimatikan.")
            return true
        }

        // Manajemen makro lokal lewat suara
        val macroCache = LocalMacroCache(service)
        if (c.contains("daftar makro") || c.contains("cek makro") || c.contains("makro apa saja")) {
            sayCallback.accept(macroCache.listMacrosSpeech())
            return true
        }
        if (c.contains("hapus semua makro") || c.contains("hapus makro") || c.contains("bersihkan makro")) {
            macroCache.clearAll()
            sayCallback.accept("Semua makro cepat lokal telah dibersihkan.")
            return true
        }

        // Prioritas 2: Local Macro Cache (< 100ms, tanpa memanggil LLM)
        val cachedMacro = macroCache.findMatchingMacro(c)
        if (cachedMacro != null) {
            executeMacroSequenceAsync(service, cachedMacro, macroCache, sayCallback)
            return true
        }

        // Permintaan analisis visual layar (Screenshot + UI Tree -> Gemini Vision)
        if (isVisualScreenQuery(c)) {
            if (!NetworkState.isOnline(service)) {
                sayCallback.accept("Internet terputus. Membaca teks layar secara offline: " + service.readScreenText())
                return true
            }
            val apiKey = resolveGeminiApiKey(service)
            if (apiKey.isEmpty()) {
                sayCallback.accept("Kunci Gemini API belum diatur. Membacakan teks layar lokal: " + service.readScreenText())
                return true
            }
            analyzeScreenVisuallyAsync(service, c, apiKey, sayCallback)
            return true
        }

        // Instruksi multi-langkah lintas layar
        if (isComplexMultiStepCommand(c) && NetworkState.isOnline(service)) {
            val apiKey = resolveGeminiApiKey(service)
            if (apiKey.isNotEmpty()) {
                runMultiStepAgentAsync(service, rawCommand, apiKey, macroCache, sayCallback)
                return true
            }
        }

        return false
    }

    /**
     * Backward-compatible overload jika dipanggil tanpa VoiceAccessEngine.
     */
    @JvmStatic
    fun tryRouteFastOrVisual(
        service: AsistenAccessibilityService,
        rawCommand: String,
        sayCallback: Consumer<String>
    ): Boolean {
        return tryRouteFastOrVisual(service, rawCommand, null, sayCallback)
    }

    /**
     * Prioritas 3 (Gemma 3 1B Offline + Memori Lokal + Persona) -> Prioritas 4 (Gemini / OpenRouter).
     */
    @JvmStatic
    fun routeComplexFallback(
        service: AsistenAccessibilityService,
        command: String,
        localLlmEngine: LocalLlmEngine?,
        config: KobraConfig,
        speaker: Speaker
    ) {
        val online = NetworkState.isOnline(service)
        val macroCache = LocalMacroCache(service)
        val fullInstruction = KobraPersona.buildFullSystemPrompt(service)

        // Prioritas 3: Mode Offline (Gemma 3 1B bila siap & tidak Safe Mode)
        if (!online) {
            if (localLlmEngine != null && localLlmEngine.isModelPresent) {
                if (localLlmEngine.isSafeMode && localLlmEngine.lastError.isNotEmpty()) {
                    speaker.speak(
                        "Mode Aman aktif karena ${localLlmEngine.lastError}. " +
                            "Anda tetap bisa memakai Voice Access nomor layar dan perintah sistem lokal."
                    )
                } else if (localLlmEngine.isInitialized) {
                    speaker.speak("Memproses dengan otak offline...")
                    localLlmEngine.summarizeTextAsync(fullInstruction, "Suara", command, speaker, null)
                } else {
                    speaker.speak("Menyiapkan otak offline, harap tunggu...")
                    UnifiedAccessibilityEngine.getScope().launch(Dispatchers.IO) {
                        val ready = localLlmEngine.initEngine()
                        withContext(Dispatchers.Main) {
                            if (ready) {
                                localLlmEngine.summarizeTextAsync(fullInstruction, "Suara", command, speaker, null)
                            } else {
                                speaker.speak("Gagal memuat model offline. Beralih ke Mode Aman perintah sistem lokal.")
                            }
                        }
                    }
                }
            } else {
                speaker.speak("Internet terputus dan model offline titik task belum terpasang.")
            }
            return
        }

        // Opsional webhook n8n bila dikonfigurasi
        if (config.n8nUrl.isNotEmpty()) {
            speaker.speak("Sebentar, saya teruskan ke otomatisasi.")
            N8nClient.send(config.n8nUrl, command, object : N8nClient.Callback {
                override fun onResult(text: String) {
                    speaker.speak(text)
                }

                override fun onError(message: String) {
                    dispatchOnlineTier4(service, command, macroCache, config, speaker)
                }
            })
            return
        }

        dispatchOnlineTier4(service, command, macroCache, config, speaker)
    }

    private fun dispatchOnlineTier4(
        service: AsistenAccessibilityService,
        command: String,
        macroCache: LocalMacroCache,
        config: KobraConfig,
        speaker: Speaker
    ) {
        val geminiKey = resolveGeminiApiKey(service)
        if (geminiKey.isNotEmpty() && shouldUseScreenAgent(command)) {
            runMultiStepAgentAsync(service, command, geminiKey, macroCache) { msg: String ->
                speaker.speak(msg)
            }
            return
        }

        speaker.speak("Sebentar.")
        val sysPrompt = KobraPersona.buildFullSystemPrompt(service)
        OnlineAiRouter.askAsync(service, command, sysPrompt, object : OnlineAiRouter.Callback {
            override fun onResult(text: String, providerLabel: String) {
                speaker.speak(text)
            }

            override fun onError(message: String) {
                val engine = service.localLlmEngine
                if (engine != null && engine.isModelPresent && !engine.isSafeMode) {
                    if (engine.isInitialized) {
                        engine.summarizeTextAsync(sysPrompt, "Suara", command, speaker, null)
                    } else {
                        engine.initEngineAsync(false) { ok, _ ->
                            if (ok) engine.summarizeTextAsync(sysPrompt, "Suara", command, speaker, null)
                            else speaker.speak(message)
                        }
                    }
                } else {
                    speaker.speak(message)
                }
            }
        })
    }

    private fun executeMacroSequenceAsync(
        service: AccessibilityService,
        macro: MacroSequence,
        cache: LocalMacroCache,
        sayCallback: Consumer<String>
    ) {
        UnifiedAccessibilityEngine.getScope().launch {
            val recovery = RecoveryEngine()
            sayCallback.accept("Menjalankan makro cepat.")
            var allOk = true

            for ((idx, step) in macro.steps.withIndex()) {
                val ok = executeSingleStep(service, step)
                if (!ok) {
                    val dismissed = RecoveryEngine.tryDismissBlockingPopup(service)
                    if (dismissed != null) {
                        delay(450L)
                        if (!executeSingleStep(service, step)) {
                            allOk = false
                            break
                        }
                    } else if (recovery.shouldRecover) {
                        allOk = false
                        break
                    } else {
                        delay(350L)
                        if (!executeSingleStep(service, step)) {
                            allOk = false
                            break
                        }
                    }
                }
                recovery.recordAction("${step.action}:${step.target}")
                if (idx < macro.steps.lastIndex) {
                    delay(if (step.action == "open_app") 900L else 320L)
                }
            }

            if (allOk) {
                cache.recordUsage(macro.id)
            } else {
                sayCallback.accept("Sebagian langkah makro gagal karena tampilan layar berubah.")
            }
        }
    }

    private fun analyzeScreenVisuallyAsync(
        service: AccessibilityService,
        userQuestion: String,
        apiKey: String,
        sayCallback: Consumer<String>
    ) {
        sayCallback.accept("Menganalisis tampilan layar dengan Gemini.")
        UnifiedAccessibilityEngine.getScope().launch {
            val snapshot = withContext(Dispatchers.Default) {
                UnifiedAccessibilityEngine.captureScreenSnapshot(service, preferForeignWindow = false)
            }
            val screenshotB64 = UnifiedAccessibilityEngine.captureScreenshotBase64(service)
            val personaPrompt = KobraPersona.buildFullSystemPrompt(service)

            val prompt = buildString {
                append(personaPrompt).append("\n\n")
                append("Pengguna bertanya tentang layar HP saat ini: \"$userQuestion\"\n")
                append("Ringkasan elemen UI:\n").append(snapshot.formattedTree.take(2200)).append("\n\n")
                append("Jelaskan isi layar secara akurat, singkat, jelas tanpa format markdown atau simbol.")
            }

            val reply = withContext(Dispatchers.IO) {
                callGeminiRest(apiKey, prompt, screenshotB64)
            }
            sayCallback.accept(
                cleanForSpeech(reply ?: "Maaf, analisis visual layar gagal. Teks layar lokal: ${snapshot.formattedTree.take(300)}")
            )
        }
    }

    private fun runMultiStepAgentAsync(
        service: AccessibilityService,
        goal: String,
        apiKey: String,
        macroCache: LocalMacroCache,
        sayCallback: Consumer<String>
    ) {
        sayCallback.accept("Memproses perintah bertahap.")
        UnifiedAccessibilityEngine.getScope().launch {
            val recovery = RecoveryEngine()
            val executedSteps = ArrayList<MacroStep>()
            var lastPkg = ""
            var finalMessage = ""

            for (stepIdx in 1..MAX_AGENT_STEPS) {
                val autoPopup = RecoveryEngine.tryDismissBlockingPopup(service)
                if (autoPopup != null) {
                    delay(450L)
                }

                val snapshot = withContext(Dispatchers.Default) {
                    UnifiedAccessibilityEngine.captureScreenSnapshot(service, preferForeignWindow = true)
                }
                if (snapshot.packageName.isNotEmpty()) {
                    lastPkg = snapshot.packageName
                }

                recovery.recordScreen(snapshot.fingerprint)
                if (recovery.shouldRecover) {
                    val suggestion = recovery.suggestRecovery()
                    when (suggestion.strategy) {
                        RecoveryEngine.Strategy.DISMISS_POPUP -> {
                            RecoveryEngine.tryDismissBlockingPopup(service)
                        }
                        RecoveryEngine.Strategy.SCROLL_DOWN -> {
                            UnifiedAccessibilityEngine.scrollGlobal(service, forward = true)
                        }
                        RecoveryEngine.Strategy.WAIT_SETTLE -> {
                            delay(suggestion.waitMs)
                        }
                        RecoveryEngine.Strategy.PRESS_BACK -> {
                            UnifiedAccessibilityEngine.performGlobalNavAction(
                                service,
                                AccessibilityService.GLOBAL_ACTION_BACK
                            )
                        }
                    }
                    delay(500L)
                    continue
                }

                val screenshotB64 = UnifiedAccessibilityEngine.captureScreenshotBase64(service)
                val agentPrompt = buildAgentStepPrompt(goal, stepIdx, snapshot, executedSteps)
                val rawJson = withContext(Dispatchers.IO) {
                    callGeminiRest(apiKey, agentPrompt, screenshotB64)
                }

                val parsed = parseAgentDecision(rawJson)
                if (parsed == null) {
                    finalMessage = "Tidak dapat memahami langkah berikutnya dari layar saat ini."
                    break
                }

                if (parsed.action == "done") {
                    finalMessage = parsed.speech.ifEmpty { "Tugas selesai dijalankan." }
                    break
                }

                recovery.recordAction("${parsed.action}:${parsed.target}:${parsed.x},${parsed.y}")
                val step = MacroStep(parsed.action, parsed.target, parsed.x, parsed.y)
                val ok = executeSingleStep(service, step)
                if (ok) {
                    executedSteps.add(step)
                }
                delay(if (parsed.action == "open_app") 1100L else 650L)
            }

            if (executedSteps.isNotEmpty() && finalMessage.isNotEmpty()) {
                macroCache.saveMacro(goal, lastPkg, executedSteps)
            }
            if (finalMessage.isEmpty()) {
                finalMessage = if (executedSteps.isNotEmpty()) {
                    "Langkah yang diminta telah dijalankan."
                } else {
                    "Tugas belum bisa diselesaikan di layar ini."
                }
            }
            sayCallback.accept(cleanForSpeech(finalMessage))
        }
    }

    @JvmStatic
    fun callGeminiBlocking(context: Context, prompt: String, imageBase64: String? = null): String? {
        val key = resolveGeminiApiKey(context)
        if (key.isEmpty()) return null
        val fullPrompt = KobraPersona.buildFullSystemPrompt(context) + "\n\n" + prompt
        return OnlineAiRouter.callGeminiWithFallback(key, fullPrompt, imageBase64)?.let { cleanForSpeech(it) }
    }

    private suspend fun executeSingleStep(service: AccessibilityService, step: MacroStep): Boolean {
        return when (step.action) {
            "open_app" -> UnifiedAccessibilityEngine.openAppSmart(service, step.target)
            "click_label" -> {
                var ok = UnifiedAccessibilityEngine.clickByNameGlobal(service, step.target)
                if (!ok && step.x >= 0 && step.y >= 0) {
                    ok = UnifiedAccessibilityEngine.clickAt(service, step.x.toFloat(), step.y.toFloat())
                }
                ok
            }
            "click_xy" -> UnifiedAccessibilityEngine.clickAt(service, step.x.toFloat(), step.y.toFloat())
            "type_text" -> {
                val typed = UnifiedAccessibilityEngine.typeGlobalText(service, step.target)
                if (typed) {
                    delay(180L)
                    UnifiedAccessibilityEngine.performImeEnter(service)
                }
                typed
            }
            "scroll_down" -> UnifiedAccessibilityEngine.scrollGlobal(service, forward = true)
            "scroll_up" -> UnifiedAccessibilityEngine.scrollGlobal(service, forward = false)
            "back" -> UnifiedAccessibilityEngine.performGlobalNavAction(
                service,
                AccessibilityService.GLOBAL_ACTION_BACK
            )
            "home" -> UnifiedAccessibilityEngine.performGlobalNavAction(
                service,
                AccessibilityService.GLOBAL_ACTION_HOME
            )
            else -> false
        }
    }

    private data class AgentDecision(
        val action: String,
        val target: String,
        val x: Int,
        val y: Int,
        val speech: String
    )

    private fun buildAgentStepPrompt(
        goal: String,
        stepIndex: Int,
        snapshot: UnifiedAccessibilityEngine.ScreenSnapshot,
        history: List<MacroStep>
    ): String {
        val historyText = if (history.isEmpty()) "Belum ada"
        else history.joinToString(" -> ") { "${it.action}(${it.target.ifEmpty { "${it.x},${it.y}" }})" }

        return """
Kamu adalah agen kendali antarmuka Android Kobra OS.
Tujuan pengguna: "$goal"
Langkah ke: $stepIndex dari $MAX_AGENT_STEPS
Riwayat langkah: $historyText
Elemen layar aktif:
${snapshot.formattedTree.take(2600)}

Balas HANYA 1 objek JSON valid tanpa markdown dengan salah satu action berikut:
- {"action":"open_app","target":"nama atau package app","speech":""}
- {"action":"click_label","target":"teks tombol persis","x":0,"y":0,"speech":""}
- {"action":"click_xy","target":"","x":500,"y":800,"speech":""}
- {"action":"type_text","target":"teks yang diketik","speech":""}
- {"action":"scroll_down","target":"","speech":""}
- {"action":"scroll_up","target":"","speech":""}
- {"action":"back","target":"","speech":""}
- {"action":"home","target":"","speech":""}
- {"action":"done","target":"","speech":"Ringkasan singkat Bahasa Indonesia untuk dibacakan TTS"}
        """.trimIndent()
    }

    private fun parseAgentDecision(rawText: String?): AgentDecision? {
        if (rawText.isNullOrBlank()) return null
        return try {
            val cleaned = rawText
                .replace("```json", "", ignoreCase = true)
                .replace("```", "")
                .trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val obj = JSONObject(cleaned.substring(start, end + 1))
            val action = obj.optString("action", "").trim().lowercase(Locale.ROOT)
            if (action.isEmpty()) return null
            AgentDecision(
                action = action,
                target = obj.optString("target", "").trim(),
                x = obj.optInt("x", -1),
                y = obj.optInt("y", -1),
                speech = obj.optString("speech", "").trim()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal parsing keputusan JSON agen", t)
            null
        }
    }

    private fun callGeminiRest(apiKey: String, prompt: String, screenshotBase64: String?): String? {
        return OnlineAiRouter.callGeminiWithFallback(apiKey, prompt, screenshotBase64)
    }

    private fun isVisualScreenQuery(c: String): Boolean {
        return c.contains("analisa layar") ||
            c.contains("analisis layar") ||
            c.contains("lihat layar") ||
            c.contains("jelaskan layar") ||
            c.contains("jelaskan tampilan") ||
            c.contains("deskripsi layar") ||
            c.contains("cek layar visual") ||
            c.contains("layar ini apa")
    }

    private fun isComplexMultiStepCommand(c: String): Boolean {
        return (c.contains(" lalu ") || c.contains(" kemudian ")) &&
            (c.startsWith("buka ") || c.contains("klik ") || c.contains("tekan ") || c.contains("cari "))
    }

    private fun shouldUseScreenAgent(command: String): Boolean {
        val c = command.lowercase(Locale.ROOT)
        return isComplexMultiStepCommand(c) ||
            c.contains("pesan ") ||
            c.contains("centang ") ||
            c.contains("pilih menu ") ||
            c.contains("tutup iklan") ||
            c.contains("geser ke ")
    }

    private fun cleanForSpeech(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
