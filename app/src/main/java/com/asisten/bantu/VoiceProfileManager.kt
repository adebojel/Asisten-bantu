package com.asisten.bantu

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Pengelola 7 Preset Nama Nusantara & Karakter Suara Ala Gemini untuk Kobra OS.
 * Mengatur Wake Word (Nama Panggilan Aktif), Pitch, Kecepatan (Rate), dan pemilihan
 * objek android.speech.tts.Voice Bahasa Indonesia (pria/wanita) terbaik pada perangkat.
 */
object VoiceProfileManager {

    enum class GenderHint {
        NEUTRAL,
        MALE,
        FEMALE
    }

    data class VoicePreset(
        val id: String,
        val displayName: String,
        val defaultWakeWord: String,
        val description: String,
        val pitch: Float,
        val rate: Float,
        val genderHint: GenderHint,
        val isCustom: Boolean = false
    )

    const val PRESET_KOBRA = "kobra"
    const val PRESET_ARKA = "arka"
    const val PRESET_BIMA = "bima"
    const val PRESET_NALA = "nala"
    const val PRESET_CAKRA = "cakra"
    const val PRESET_TARA = "tara"
    const val PRESET_KUSTOM = "kustom"

    /**
     * Nama suara pada Gemini Android picker. Gemini Live API memakai katalog HD
     * berbeda; apiVoiceName adalah padanan karakter, bukan salinan suara proprietary.
     */
    data class GeminiLiveVoiceProfile(
        val id: String,
        val displayName: String,
        val character: String,
        val apiVoiceName: String,
        val pitch: Float,
        val rate: Float
    )

    @JvmField
    val GEMINI_LIVE_VOICES: List<GeminiLiveVoiceProfile> = listOf(
        GeminiLiveVoiceProfile("ursa", "Ursa", "Engaged • Mid-range", "Gacrux", 0.96f, 0.98f),
        GeminiLiveVoiceProfile("nova", "Nova", "Calm • Mid-range", "Callirrhoe", 1.00f, 0.94f),
        GeminiLiveVoiceProfile("pegasus", "Pegasus", "Engaged • Deeper", "Charon", 0.82f, 0.96f),
        GeminiLiveVoiceProfile("vega", "Vega", "Bright • Higher", "Zephyr", 1.18f, 1.02f),
        GeminiLiveVoiceProfile("capella", "Capella", "British accent • Higher", "Aoede", 1.14f, 0.98f),
        GeminiLiveVoiceProfile("dipper", "Dipper", "Engaged • Deeper", "Alnilam", 0.84f, 0.94f),
        GeminiLiveVoiceProfile("orbit", "Orbit", "Energetic • Deeper", "Puck", 0.90f, 1.12f),
        GeminiLiveVoiceProfile("orion", "Orion", "Bright • Deeper", "Algieba", 0.88f, 1.04f),
        GeminiLiveVoiceProfile("lyra", "Lyra", "Bright • Higher", "Achernar", 1.16f, 1.02f),
        GeminiLiveVoiceProfile("eclipse", "Eclipse", "Energetic • Mid-range", "Laomedeia", 1.04f, 1.12f)
    )

    @JvmStatic
    fun getGeminiLiveVoice(id: String?): GeminiLiveVoiceProfile {
        val clean = id?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return GEMINI_LIVE_VOICES.firstOrNull { it.id == clean }
            ?: GEMINI_LIVE_VOICES.first { it.id == "lyra" }
    }

    @JvmField
    val PRESETS: List<VoicePreset> = listOf(
        VoicePreset(
            id = PRESET_KOBRA,
            displayName = "Kobra",
            defaultWakeWord = "Kobra",
            description = "Tegas & Netral (Default)",
            pitch = 1.00f,
            rate = 1.00f,
            genderHint = GenderHint.NEUTRAL
        ),
        VoicePreset(
            id = PRESET_ARKA,
            displayName = "Arka",
            defaultWakeWord = "Arka",
            description = "Bersahabat & Jelas",
            pitch = 1.08f,
            rate = 1.02f,
            genderHint = GenderHint.MALE
        ),
        VoicePreset(
            id = PRESET_BIMA,
            displayName = "Bima",
            defaultWakeWord = "Bima",
            description = "Pria Berat & Berwibawa",
            pitch = 0.78f,
            rate = 0.94f,
            genderHint = GenderHint.MALE
        ),
        VoicePreset(
            id = PRESET_NALA,
            displayName = "Nala",
            defaultWakeWord = "Nala",
            description = "Wanita Lembut & Tenang",
            pitch = 1.12f,
            rate = 0.92f,
            genderHint = GenderHint.FEMALE
        ),
        VoicePreset(
            id = PRESET_CAKRA,
            displayName = "Cakra",
            defaultWakeWord = "Cakra",
            description = "Cepat & Sigap",
            pitch = 0.95f,
            rate = 1.30f,
            genderHint = GenderHint.MALE
        ),
        VoicePreset(
            id = PRESET_TARA,
            displayName = "Tara",
            defaultWakeWord = "Tara",
            description = "Ceria & Energik",
            pitch = 1.18f,
            rate = 1.08f,
            genderHint = GenderHint.FEMALE
        ),
        VoicePreset(
            id = PRESET_KUSTOM,
            displayName = "Kustom Sendiri",
            defaultWakeWord = "Kobra",
            description = "Nama, Pitch & Kecepatan Bebas",
            pitch = 1.00f,
            rate = 1.00f,
            genderHint = GenderHint.NEUTRAL,
            isCustom = true
        )
    )

    @JvmStatic
    fun getPresetById(id: String?): VoicePreset {
        val clean = id?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return PRESETS.firstOrNull { it.id == clean } ?: PRESETS[0]
    }

    @JvmStatic
    fun getActivePreset(context: Context): VoicePreset {
        val cfg = KobraConfig(context.applicationContext)
        return getPresetById(cfg.voiceProfileId)
    }

    @JvmStatic
    fun getActiveAssistantName(context: Context): String {
        val cfg = KobraConfig(context.applicationContext)
        val preset = getPresetById(cfg.voiceProfileId)
        if (preset.isCustom) {
            val custom = sanitizeWakeWord(cfg.customAssistantName)
            if (custom.isNotEmpty()) return custom
        }
        return preset.defaultWakeWord
    }

    @JvmStatic
    fun applyPreset(context: Context, presetId: String): VoicePreset {
        val ctx = context.applicationContext
        val cfg = KobraConfig(ctx)
        val preset = getPresetById(presetId)
        cfg.setVoiceProfileId(preset.id)
        if (!preset.isCustom) {
            cfg.setSpeechPitch(clampPitch(preset.pitch))
            cfg.setSpeechRate(clampRate(preset.rate))
        } else if (cfg.customAssistantName.isEmpty()) {
            cfg.setCustomAssistantName(preset.defaultWakeWord)
        }
        syncAllTts(ctx)
        return preset
    }

    @JvmStatic
    fun saveCustomProfile(context: Context, customName: String?, pitch: Float, rate: Float) {
        val ctx = context.applicationContext
        val cfg = KobraConfig(ctx)
        val cleanName = sanitizeWakeWord(customName).ifEmpty { "Kobra" }
        cfg.setVoiceProfileId(PRESET_KUSTOM)
        cfg.setCustomAssistantName(cleanName)
        cfg.setSpeechPitch(clampPitch(pitch))
        cfg.setSpeechRate(clampRate(rate))
        syncAllTts(ctx)
    }

    @JvmStatic
    fun adjustSpeechRate(context: Context, delta: Float): Float {
        val cfg = KobraConfig(context.applicationContext)
        val updated = clampRate(cfg.speechRate + delta)
        cfg.speechRate = updated
        syncAllTts(context.applicationContext)
        return updated
    }

    @JvmStatic
    fun adjustSpeechPitch(context: Context, delta: Float): Float {
        val cfg = KobraConfig(context.applicationContext)
        val updated = clampPitch(cfg.speechPitch + delta)
        cfg.speechPitch = updated
        syncAllTts(context.applicationContext)
        return updated
    }

    @JvmStatic
    fun resetSpeechRate(context: Context): Float {
        val cfg = KobraConfig(context.applicationContext)
        val preset = getPresetById(cfg.voiceProfileId)
        val updated = clampRate(if (preset.isCustom) 1.0f else preset.rate)
        cfg.speechRate = updated
        syncAllTts(context.applicationContext)
        return updated
    }

    @JvmStatic
    fun syncAllTts(context: Context) {
        val ctx = context.applicationContext
        Speaker.get(ctx).applyVoiceSettings()
        UnifiedAccessibilityEngine.refreshExternalTtsSettings(ctx)
    }

    @JvmStatic
    fun clampPitch(pitch: Float): Float {
        return pitch.coerceIn(0.5f, 1.8f)
    }

    @JvmStatic
    fun clampRate(rate: Float): Float {
        return rate.coerceIn(0.5f, 2.0f)
    }

    @JvmStatic
    fun sanitizeWakeWord(raw: String?): String {
        val cleaned = raw.orEmpty()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return ""
        return cleaned.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString(" ") { word ->
                word.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale("id", "ID")) else ch.toString()
                }
            }
    }

    @JvmStatic
    fun formatSampleGreeting(context: Context): String {
        val name = getActiveAssistantName(context)
        return "Halo, saya $name, asisten suara Anda siap membantu."
    }

    /**
     * Menerapkan pilihan Voice Bahasa Indonesia (pria/wanita sesuai preset), Speech Rate, dan Pitch
     * pada instance android.speech.tts.TextToSpeech apa pun.
     */
    @JvmStatic
    fun applyToTextToSpeech(context: Context, tts: TextToSpeech?) {
        if (tts == null) return
        val ctx = context.applicationContext
        val cfg = KobraConfig(ctx)
        val preset = getPresetById(cfg.voiceProfileId)

        // Preset selection seeds these values; voice commands may then adjust them.
        val rate = clampRate(cfg.speechRate)
        val pitch = clampPitch(cfg.speechPitch)

        try {
            val bestVoice = selectBestIndonesianVoice(tts, preset.genderHint)
            if (bestVoice != null) {
                tts.voice = bestVoice
            }
        } catch (_: Throwable) {
        }

        try {
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
        } catch (_: Throwable) {
        }
    }

    private fun selectBestIndonesianVoice(tts: TextToSpeech, genderHint: GenderHint): Voice? {
        val voices = try {
            tts.voices
        } catch (_: Throwable) {
            null
        } ?: return null

        val indonesianVoices = voices.filter { v ->
            val lang = v.locale?.language?.lowercase(Locale.ROOT).orEmpty()
            lang == "id" || lang == "in"
        }
        if (indonesianVoices.isEmpty()) return null

        var best: Voice? = null
        var bestScore = Int.MIN_VALUE

        for (v in indonesianVoices) {
            var score = 0
            val country = v.locale?.country?.uppercase(Locale.ROOT).orEmpty()
            val nameLower = v.name?.lowercase(Locale.ROOT).orEmpty()

            if (country == "ID") score += 40
            if (!v.isNetworkConnectionRequired) score += 25
            if (v.quality >= Voice.QUALITY_HIGH) score += 15
            else if (v.quality >= Voice.QUALITY_NORMAL) score += 8

            val looksMale = nameLower.contains("male") && !nameLower.contains("female") ||
                nameLower.contains("pria") ||
                nameLower.contains("-ide-") || nameLower.contains("_ide_") ||
                nameLower.contains("-idc-") || nameLower.contains("_idc_")
            val looksFemale = nameLower.contains("female") ||
                nameLower.contains("wanita") ||
                nameLower.contains("-idd-") || nameLower.contains("_idd_") ||
                nameLower.contains("-idf-") || nameLower.contains("_idf_") ||
                nameLower.contains("-dfz-") || nameLower.contains("_dfz_")

            when (genderHint) {
                GenderHint.MALE -> {
                    if (looksMale) score += 70
                    if (looksFemale) score -= 35
                }
                GenderHint.FEMALE -> {
                    if (looksFemale) score += 70
                    if (looksMale) score -= 35
                }
                GenderHint.NEUTRAL -> {
                    if (!v.isNetworkConnectionRequired) score += 10
                }
            }

            if (score > bestScore) {
                bestScore = score
                best = v
            }
        }
        return best
    }

    /**
     * Mendeteksi dan memangkas kata panggil aktif (mis. "Arka", "Bima", "Nala", "Cakra", "Tara",
     * nama kustom, maupun fallback "Kobra" / "Kobra OS") dari ucapan pengguna.
     * Mengembalikan sisa perintah apabila wake word cocok, atau null bila tidak ada.
     */
    @JvmStatic
    fun stripWakeWord(context: Context, spokenLower: String?): String? {
        if (spokenLower.isNullOrBlank()) return null
        val s = spokenLower.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        val active = getActiveAssistantName(context).lowercase(Locale.ROOT).trim()

        val candidates = ArrayList<String>()
        if (active.isNotEmpty() && active != "kobra") {
            candidates.add("hai $active")
            candidates.add("halo $active")
            candidates.add("hei $active")
            candidates.add("hey $active")
            candidates.add(active)
        }
        candidates.add("hai kobra os")
        candidates.add("halo kobra os")
        candidates.add("kobra os")
        candidates.add("hai kobra")
        candidates.add("halo kobra")
        candidates.add("kobra")
        candidates.add("cobra")
        candidates.add("kobro")
        candidates.add("kobr")

        for (w in candidates) {
            if (w.isEmpty()) continue
            val idx = findWholeWordIndex(s, w)
            if (idx >= 0) {
                return s.substring(idx + w.length).trim()
            }
        }
        return null
    }

    private fun findWholeWordIndex(text: String, phrase: String): Int {
        var fromIndex = 0
        while (true) {
            val idx = text.indexOf(phrase, fromIndex)
            if (idx < 0) return -1
            val beforeOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
            val afterPos = idx + phrase.length
            val afterOk = afterPos >= text.length || !text[afterPos].isLetterOrDigit()
            if (beforeOk && afterOk) return idx
            fromIndex = idx + 1
        }
    }
}
