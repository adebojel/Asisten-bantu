package com.asisten.bantu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Layar Chat AI Generasi Baru Kobra OS:
 * - Barge-In / Interruptible Voice via VoiceOrbView & tombol Mic 3D
 * - 1-Tap Persona Switcher Bar (Kobra Utama, Pembaca & Ringkas, Tutor Bahasa, Asisten Kerja)
 * - Glassmorphism Chat Bubbles (RecyclerView) dengan avatar 3D & tombol putar ulang TTS
 * - Lampiran Gambar (Vision / OCR) & injeksi LocalMemoryStore + Safe Mode Reset
 */
open class ChatActivity : AppCompatActivity() {

    companion object {
        @JvmField
        var current: ChatActivity? = null
    }

    private lateinit var rvMessages: RecyclerView
    private lateinit var adapter: ChatBubbleAdapter
    private lateinit var etInput: EditText
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvModelInfo: TextView
    private lateinit var tvAttachment: TextView
    private lateinit var voiceOrb: VoiceOrbView
    private lateinit var btnResetSafeMode: Button

    private lateinit var btnPersonaUtama: Button
    private lateinit var btnPersonaPembaca: Button
    private lateinit var btnPersonaTutor: Button
    private lateinit var btnPersonaKerja: Button

    private lateinit var speaker: Speaker
    private lateinit var config: KobraConfig
    private lateinit var aiClient: AiClient
    private val screenHelper = KobraScreenHelper()
    private val ioExec = Executors.newSingleThreadExecutor()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var handsFreeConversation = false
    private var suppressHandsFreeRearm = false
    private var recognitionGeneration = 0L
    private var attachedImageB64: String? = null
    private var attachedBitmap: Bitmap? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) loadAttachmentFromUri(uri)
    }

    private val speechListener = object : Speaker.Listener {
        override fun onSpeechStart() {
            runOnUiThread {
                if (!isListening) voiceOrb.setOrbState(VoiceOrbView.OrbState.SPEAKING)
            }
        }
        override fun onSpeechEnd() {
            runOnUiThread {
                if (!isListening) voiceOrb.setOrbState(VoiceOrbView.OrbState.IDLE)
                // Turn-taking hands-free mode: listen again after the assistant finishes speaking.
                // This is not full-duplex streaming; it uses Android SpeechRecognizer per turn.
                if (handsFreeConversation && !suppressHandsFreeRearm && !isFinishing && current === this@ChatActivity) {
                    voiceOrb.postDelayed({
                        if (handsFreeConversation && !isFinishing && !isListening && current === this@ChatActivity) {
                            startSpeechRecognition()
                        }
                    }, 450L)
                }
            }
        }
    }

    data class ChatMessage(
        val text: String,
        val fromUser: Boolean,
        val meta: String = "Kobra OS"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        WindowInsetsHelper.applySystemBarInsets(this, true)
        ThemeManager.applyTheme(this)

        speaker = Speaker.get(this)
        config = KobraConfig(this)
        aiClient = AiClient(config)

        rvMessages = findViewById(R.id.rvChatMessages)
        etInput = findViewById(R.id.etChatInput)
        tvHeaderTitle = findViewById(R.id.tvChatHeaderTitle)
        tvModelInfo = findViewById(R.id.tvChatModelInfo)
        tvAttachment = findViewById(R.id.tvChatAttachment)
        voiceOrb = findViewById(R.id.voiceOrbChat)
        btnResetSafeMode = findViewById(R.id.btnResetSafeModeChat)

        btnPersonaUtama = findViewById(R.id.btnPersonaUtama)
        btnPersonaPembaca = findViewById(R.id.btnPersonaPembaca)
        btnPersonaTutor = findViewById(R.id.btnPersonaTutor)
        btnPersonaKerja = findViewById(R.id.btnPersonaKerja)

        adapter = ChatBubbleAdapter { msgText: String ->
            HapticFeedback.vibrateAction(this)
            speaker.speak(msgText)
        }
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = adapter

        findViewById<ImageButton>(R.id.btnBackChat).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnChatSend).setOnClickListener { sendCurrentInput() }
        findViewById<ImageButton>(R.id.btnChatAttachImage).setOnClickListener {
            HapticFeedback.vibrateAction(this)
            try {
                imagePicker.launch(arrayOf("image/*"))
            } catch (_: Throwable) {
                speaker.speak("Pemilih gambar tidak tersedia.")
            }
        }

        val bargeInListener = View.OnClickListener {
            HapticFeedback.vibrateAction(this)
            triggerVoiceBargeIn()
        }
        findViewById<ImageButton>(R.id.btnChatMic).setOnClickListener(bargeInListener)
        voiceOrb.setOnClickListener(bargeInListener)
        val btnHandsFree = findViewById<Button>(R.id.btnHandsFreeConversation)
        btnHandsFree.setOnClickListener {
            val enable = !handsFreeConversation
            if (enable) {
                // Suppress the TTS completion callback so it cannot start a second recognizer.
                suppressHandsFreeRearm = true
                speaker.stop()
                suppressHandsFreeRearm = false
                handsFreeConversation = true
                btnHandsFree.text = "Percakapan hands-free: AKTIF"
                tvModelInfo.text = "Mode percakapan aktif • bicara bergantian"
                startSpeechRecognition()
            } else {
                handsFreeConversation = false
                btnHandsFree.text = "Percakapan hands-free"
                stopSpeechRecognition()
                refreshModeBanner()
            }
        }

        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput()
                true
            } else false
        }

        btnPersonaUtama.setOnClickListener { switchPersona(KobraPersona.KOBRA_UTAMA) }
        btnPersonaPembaca.setOnClickListener { switchPersona(KobraPersona.PEMBACA_RINGKAS) }
        btnPersonaTutor.setOnClickListener { switchPersona(KobraPersona.TUTOR_BAHASA) }
        btnPersonaKerja.setOnClickListener { switchPersona(KobraPersona.ASISTEN_KERJA) }

        btnResetSafeMode.setOnClickListener {
            HapticFeedback.vibrateAction(this)
            config.clearLlmSafeMode()
            refreshModeBanner()
            val msg = "Mode Aman model lokal berhasil direset."
            addAiMessage(msg, "Sistem")
            speaker.speak(msg)
        }

        tvAttachment.setOnClickListener {
            attachedImageB64 = null
            attachedBitmap?.recycle()
            attachedBitmap = null
            tvAttachment.visibility = View.GONE
        }

        syncAssistantIdentity()
        refreshPersonaChips()
        refreshModeBanner()
        val activeName = VoiceProfileManager.getActiveAssistantName(this)
        val activePersona = KobraPersona.fromId(config.activePersonaId)
        val greeting = "Halo, saya $activeName (${activePersona.title}). Silakan ketik, lampirkan gambar, atau ketuk tombol mikrofon kapan saja untuk memotong suara saya."
        addAiMessage(greeting, "$activeName • ${activePersona.title}")
        speaker.speak(greeting)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screenHelper.handleKeyEvent(this, event, speaker)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (screenHelper.handleTouchEvent(this, ev, speaker)) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        current = this
        ThemeManager.applyTheme(this)
        refreshPersonaChips()
        adapter.notifyDataSetChanged()
        speaker.addListener(speechListener)
        syncAssistantIdentity()
        refreshModeBanner()
    }

    private fun syncAssistantIdentity() {
        val activeName = VoiceProfileManager.getActiveAssistantName(this)
        tvHeaderTitle.text = "ASISTEN AI ${activeName.uppercase(Locale.ROOT)}"
        btnPersonaUtama.text = "$activeName Utama"
    }

    override fun onPause() {
        if (current === this) current = null
        handsFreeConversation = false
        speaker.removeListener(speechListener)
        stopSpeechRecognition()
        super.onPause()
    }

    @Deprecated("Deprecated in Android API 23; retained for compatibility with this Activity permission flow")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 301) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                handsFreeConversation = false
                findViewById<Button>(R.id.btnHandsFreeConversation).text = "Percakapan hands-free"
                speaker.speak("Izin mikrofon belum diberikan. Aktifkan izin mikrofon untuk memakai percakapan suara.")
            }
        }
    }

    fun onVoice(text: String) {
        runOnUiThread {
            val clean = text.trim()
            if (clean.isNotEmpty()) {
                etInput.setText(clean)
                sendCurrentInput()
            }
        }
    }

    override fun onDestroy() {
        stopSpeechRecognition()
        attachedBitmap?.recycle()
        attachedBitmap = null
        ioExec.shutdown()
        super.onDestroy()
    }

    private fun switchPersona(persona: KobraPersona) {
        HapticFeedback.vibrateAction(this)
        config.setActivePersonaId(persona.id)
        refreshPersonaChips()
        val info = "Persona diganti ke ${persona.title}. ${persona.subtitle}."
        addAiMessage(info, persona.title)
        speaker.speak("Mode ${persona.title} aktif.")
    }

    private fun refreshPersonaChips() {
        val current = KobraPersona.fromId(config.activePersonaId)
        styleChip(btnPersonaUtama, current == KobraPersona.KOBRA_UTAMA)
        styleChip(btnPersonaPembaca, current == KobraPersona.PEMBACA_RINGKAS)
        styleChip(btnPersonaTutor, current == KobraPersona.TUTOR_BAHASA)
        styleChip(btnPersonaKerja, current == KobraPersona.ASISTEN_KERJA)
    }

    private fun styleChip(btn: Button, selected: Boolean) {
        ThemeManager.styleChip(btn, selected, false)
    }

    private fun refreshModeBanner() {
        val llm = LocalLlmEngine.getInstance(this)
        val isSafe = config.isLlmSafeMode
        btnResetSafeMode.visibility = if (isSafe) View.VISIBLE else View.GONE

        val online = NetworkState.isOnline(this)
        val onlineLabel = OnlineAiRouter.getActiveProviderSummary(this)
        val label = when {
            isSafe && online -> "Mode Aman Lokal • $onlineLabel Siap"
            isSafe -> "Mode Aman Lokal • Fallback Sistem Offline"
            llm.isInitialized -> "Gemma 3 1B Offline Aktif • Memori ${LocalMemoryStore.listMemories(this).size}"
            llm.isModelPresent && online -> "Model Lokal Siap • $onlineLabel"
            llm.isModelPresent -> "Model Lokal Siap (Offline)"
            online -> "$onlineLabel Siap (${NetworkState.connectionType(this)})"
            else -> "Mode Utilitas Lokal & Voice Access (Offline)"
        }
        tvModelInfo.text = label
    }

    private fun triggerVoiceBargeIn() {
        // Barge-In: hentikan TTS seketika agar pengguna langsung bicara
        if (speaker.isSpeaking) {
            suppressHandsFreeRearm = true
            speaker.stop()
            suppressHandsFreeRearm = false
        }
        if (isListening) {
            stopSpeechRecognition()
            return
        }
        if (!Perms.has(this, Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 301)
            return
        }
        startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speaker.speak("Pengenalan suara tidak tersedia di perangkat ini.")
            return
        }
        try {
            stopSpeechRecognition()
            val generation = ++recognitionGeneration
            val sr = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = sr
            isListening = true
            voiceOrb.setOrbState(VoiceOrbView.OrbState.LISTENING)
            HapticFeedback.vibrateAction(this)

            sr.setRecognitionListener(object : RecognitionListener {
                private fun isCurrentSession(): Boolean =
                    speechRecognizer === sr && recognitionGeneration == generation && !isFinishing

                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrentSession()) return
                    tvModelInfo.text = "Mendengarkan suara Anda..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    if (isCurrentSession()) voiceOrb.updateRmsDb(rmsdB)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (!isCurrentSession()) return
                    // Keep the session marked active until final results or an error arrive.
                    voiceOrb.setOrbState(VoiceOrbView.OrbState.LISTENING)
                }
                override fun onError(error: Int) {
                    if (!isCurrentSession()) return
                    isListening = false
                    speechRecognizer = null
                    try { sr.destroy() } catch (_: Throwable) {}
                    voiceOrb.setOrbState(VoiceOrbView.OrbState.IDLE)
                    refreshModeBanner()
                    if (handsFreeConversation && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        voiceOrb.postDelayed({
                            if (handsFreeConversation && !isListening && current === this@ChatActivity && !isFinishing) {
                                startSpeechRecognition()
                            }
                        }, 900L)
                    }
                }
                override fun onResults(results: Bundle?) {
                    if (!isCurrentSession()) return
                    isListening = false
                    speechRecognizer = null
                    try { sr.destroy() } catch (_: Throwable) {}
                    voiceOrb.setOrbState(VoiceOrbView.OrbState.IDLE)
                    refreshModeBanner()
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = list?.firstOrNull()?.trim().orEmpty()
                    if (spoken.isNotEmpty()) {
                        etInput.setText(spoken)
                        sendCurrentInput()
                    } else if (handsFreeConversation) {
                        voiceOrb.postDelayed({
                            if (handsFreeConversation && !isListening && current === this@ChatActivity && !isFinishing) {
                                startSpeechRecognition()
                            }
                        }, 500L)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            sr.startListening(intent)
        } catch (_: Throwable) {
            isListening = false
            voiceOrb.setOrbState(VoiceOrbView.OrbState.IDLE)
        }
    }

    private fun stopSpeechRecognition() {
        isListening = false
        recognitionGeneration++
        val oldRecognizer = speechRecognizer
        speechRecognizer = null
        try {
            oldRecognizer?.cancel()
            oldRecognizer?.destroy()
        } catch (_: Throwable) {
        }
        voiceOrb.setOrbState(VoiceOrbView.OrbState.IDLE)
    }

    private fun loadAttachmentFromUri(uri: Uri) {
        ioExec.execute {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val raw = BitmapFactory.decodeStream(stream)
                    if (raw != null) {
                        val maxSide = maxOf(raw.width, raw.height)
                        val scaled = if (maxSide > 1080) {
                            val ratio = 1080f / maxSide
                            Bitmap.createScaledBitmap(
                                raw,
                                (raw.width * ratio).toInt().coerceAtLeast(1),
                                (raw.height * ratio).toInt().coerceAtLeast(1),
                                true
                            )
                        } else raw
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        runOnUiThread {
                            attachedBitmap?.recycle()
                            attachedBitmap = scaled
                            if (scaled !== raw) raw.recycle()
                            attachedImageB64 = b64
                            tvAttachment.visibility = View.VISIBLE
                            tvAttachment.text = "Gambar terlampir siap dianalisis (ketuk untuk batal)."
                            speaker.speak("Gambar dilampirkan. Silakan tulis atau ucapkan pertanyaan Anda.")
                        }
                    }
                }
            } catch (_: Throwable) {
                runOnUiThread { speaker.speak("Gagal memuat gambar.") }
            }
        }
    }

    private fun sendCurrentInput() {
        val question = etInput.text?.toString()?.trim().orEmpty()
        val imgB64 = attachedImageB64
        val imgBmp = attachedBitmap
        if (question.isEmpty() && imgB64 == null) return

        val displayPrompt = if (question.isEmpty()) "Tolong jelaskan isi gambar ini secara jelas." else question
        etInput.setText("")
        addUserMessage(displayPrompt)

        attachedImageB64 = null
        tvAttachment.visibility = View.GONE

        // 1) Jika ada gambar terlampir: prioritaskan Gemini Vision / OpenRouter Vision / ML Kit OCR offline
        if (imgB64 != null) {
            handleImageQuestion(displayPrompt, imgB64, imgBmp)
            return
        }

        // 2) Perintah Memori / Guardian / Daily Briefing / Sistem Utilitas Lokal Offline
        val lower = displayPrompt.lowercase(Locale.ROOT)
        if (lower.startsWith("ingat bahwa ") || lower.startsWith("ingat ini ") || lower.startsWith("simpan memori ")) {
            val fact = displayPrompt.replaceFirst(Regex("^(ingat\\s+bahwa|ingat\\s+ini|simpan\\s+memori)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (fact.isNotEmpty()) {
                LocalMemoryStore.addMemory(this, fact, "Chat")
                replyAndSpeak("Baik, sudah saya simpan ke memori lokal: $fact.", "Memori Lokal")
                return
            }
        }
        if (lower == "saya aman" || lower == "konfirmasi aman") {
            GuardianManager.acknowledgeSafe(this, false)
            replyAndSpeak("Status aman Anda tercatat oleh Guardian Kobra OS.", "Guardian")
            return
        }
        if (lower.contains("ringkasan harian") || lower.contains("briefing pagi")) {
            replyAndSpeak(GuardianManager.buildDailyBriefingSpeech(this), "Ringkasan Harian")
            return
        }

        val localUtil = tryLocalUtilityCommand(displayPrompt)
        if (localUtil != null) {
            replyAndSpeak(localUtil, "Sistem Offline")
            return
        }

        // 3) Model Lokal Gemma 3 1B bila terpasang & tidak dalam Safe Mode
        val llm = LocalLlmEngine.getInstance(this)
        val systemPrompt = KobraPersona.buildFullSystemPrompt(this)
        if (llm.isModelPresent && !config.isLlmSafeMode && !llm.isSafeMode) {
            tvModelInfo.text = "Memproses dengan Gemma 3 1B Offline..."
            llm.summarizeTextAsync(systemPrompt, "Chat", displayPrompt, speaker) { reply: String? ->
                if (isFinishing || isDestroyed) return@summarizeTextAsync
                val cleanReply = reply?.trim().orEmpty()
                if (cleanReply.isNotEmpty() && !cleanReply.contains("Gagal memproses") && !cleanReply.contains("Mode Aman")) {
                    addAiMessage(cleanReply, "Gemma 3 1B Offline")
                    speaker.speak(cleanReply)
                    refreshModeBanner()
                } else {
                    routeToOnlineOrFallback(displayPrompt, systemPrompt)
                }
            }
            return
        }

        // 4) Online Gemini / OpenRouter / Offline Fallback (tidak berhenti di pesan error Mode Aman)
        routeToOnlineOrFallback(displayPrompt, systemPrompt)
    }

    private fun handleImageQuestion(prompt: String, imgB64: String, bmp: Bitmap?) {
        tvModelInfo.text = "Menganalisis gambar..."
        if (NetworkState.isOnline(this)) {
            ioExec.execute {
                val geminiReply = CommandRouter.callGeminiBlocking(this, prompt, imgB64)
                if (!geminiReply.isNullOrBlank()) {
                    runOnUiThread {
                        bmp?.recycle()
                        attachedBitmap = null
                        replyAndSpeak(geminiReply, "Gemini Vision")
                        refreshModeBanner()
                    }
                    return@execute
                }
                runOnUiThread {
                    if (config.hasAi() && bmp != null) {
                        aiClient.describeImage(prompt, bmp, object : AiClient.Callback {
                            override fun onResult(text: String) {
                                bmp.recycle()
                                attachedBitmap = null
                                replyAndSpeak(text, "OpenRouter Vision")
                                refreshModeBanner()
                            }
                            override fun onError(message: String) {
                                fallbackOfflineImageOcr(bmp)
                            }
                        })
                    } else {
                        fallbackOfflineImageOcr(bmp)
                    }
                }
            }
        } else {
            fallbackOfflineImageOcr(bmp)
        }
    }

    private fun fallbackOfflineImageOcr(bmp: Bitmap?) {
        if (bmp == null) {
            replyAndSpeak("Gambar tidak dapat diproses secara offline.", "Mata Lokal")
            return
        }
        val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        ocr.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { res ->
                val text = res.text?.trim().orEmpty()
                val msg = if (text.isNotEmpty()) {
                    "Teks terbaca secara offline dari gambar: $text"
                } else {
                    "Mode offline aktif. Warna dominan gambar terlihat ${OfflineVision.detailedColorName(bmp)}."
                }
                bmp.recycle()
                attachedBitmap = null
                replyAndSpeak(msg, "OCR Offline")
                refreshModeBanner()
            }
            .addOnFailureListener {
                bmp.recycle()
                attachedBitmap = null
                replyAndSpeak("Gagal membaca gambar secara offline.", "OCR Offline")
                refreshModeBanner()
            }
    }

    private fun routeToOnlineOrFallback(question: String, systemPrompt: String) {
        if (NetworkState.isOnline(this)) {
            tvModelInfo.text = "Menghubungi ${OnlineAiRouter.getActiveProviderSummary(this)}..."
            OnlineAiRouter.askAsync(this, question, systemPrompt, object : OnlineAiRouter.Callback {
                override fun onResult(text: String, providerLabel: String) {
                    if (isFinishing || isDestroyed) return
                    replyAndSpeak(text, providerLabel)
                    refreshModeBanner()
                }

                override fun onError(message: String) {
                    if (isFinishing || isDestroyed) return
                    replyAndSpeak(buildOfflineFallback(question), "Mode Lokal")
                    refreshModeBanner()
                }
            })
            return
        }

        replyAndSpeak(buildOfflineFallback(question), "Mode Lokal")
        refreshModeBanner()
    }

    private fun tryLocalUtilityCommand(raw: String): String? {
        val c = raw.lowercase(Locale.ROOT).trim()
        if (c == "bantuan" || c == "daftar perintah" || c == "perintah") {
            return "Perintah offline yang siap dipakai: jam berapa, cek sinyal, cek langkah, agenda, ringkasan harian, apa yang kamu ingat, atau perhitungan matematika."
        }
        if (c.contains("cek sinyal") || c.contains("status sinyal")) {
            return SystemUtilityHelper.buildSignalAndCarrierReport(this)
        }
        if (c.contains("langkah") || c.contains("pedometer")) {
            return "Hari ini Anda telah berjalan ${config.todaySteps} langkah, sekitar ${config.todayDistanceMeters} meter."
        }
        if (c.contains("agenda") || c.contains("jadwal")) {
            val s = config.listPlannerSpeech()
            return if (s.isEmpty()) "Belum ada jadwal agenda tersimpan." else s
        }
        if (c.contains("apa yang kamu ingat") || c.contains("daftar memori")) {
            return LocalMemoryStore.formatSpeechSummary(this)
        }
        return OfflineAi.get(this).answer(c)
    }

    private fun buildOfflineFallback(question: String): String {
        val offlineAns = OfflineAi.get(this).answer(question)
        if (!offlineAns.isNullOrBlank()) return offlineAns
        return "Saat ini koneksi internet sedang terputus atau server sedang sibuk, dan model lokal belum dimuat. " +
            "Anda tetap dapat menanyakan jam, sinyal, langkah, agenda, kalkulator, atau menyambungkan internet untuk AI Online gratis tanpa API key."
    }

    private fun addUserMessage(text: String) {
        adapter.addMessage(ChatMessage(text, true))
        rvMessages.scrollToPosition(adapter.itemCount - 1)
    }

    private fun addAiMessage(text: String, meta: String) {
        adapter.addMessage(ChatMessage(text, false, meta))
        rvMessages.scrollToPosition(adapter.itemCount - 1)
    }

    private fun replyAndSpeak(text: String, meta: String) {
        addAiMessage(text, meta)
        speaker.speak(text)
    }

    private class ChatBubbleAdapter(
        private val onReplayClick: (String) -> Unit
    ) : RecyclerView.Adapter<ChatBubbleAdapter.VH>() {

        private val items = ArrayList<ChatMessage>()

        fun addMessage(msg: ChatMessage) {
            items.add(msg)
            notifyItemInserted(items.size - 1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val ctx = holder.itemView.context
            if (item.fromUser) {
                holder.layoutAi.visibility = View.GONE
                holder.layoutUser.visibility = View.VISIBLE
                holder.tvUserText.text = item.text
                ThemeManager.styleChatBubble(ctx, holder.cardUser, null, holder.tvUserText, null, true)
            } else {
                holder.layoutUser.visibility = View.GONE
                holder.layoutAi.visibility = View.VISIBLE
                holder.tvAiMeta.text = item.meta
                holder.tvAiText.text = item.text
                ThemeManager.styleChatBubble(ctx, holder.cardAi, holder.tvAiMeta, holder.tvAiText, holder.tvReplayLabel, false)
                ThemeManager.applyToDynamicView(ctx, holder.btnReplay)
                holder.btnReplay.setOnClickListener { onReplayClick(item.text) }
                holder.tvReplayLabel.setOnClickListener { onReplayClick(item.text) }
            }
        }

        override fun getItemCount(): Int = items.size

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val layoutAi: LinearLayout = v.findViewById(R.id.layoutBubbleAi)
            val layoutUser: LinearLayout = v.findViewById(R.id.layoutBubbleUser)
            val cardAi: LinearLayout = v.findViewById(R.id.cardBubbleAi)
            val cardUser: LinearLayout = v.findViewById(R.id.cardBubbleUser)
            val tvAiMeta: TextView = v.findViewById(R.id.tvBubbleAiMeta)
            val tvAiText: TextView = v.findViewById(R.id.tvBubbleAiText)
            val btnReplay: ImageButton = v.findViewById(R.id.btnReplayTts)
            val tvReplayLabel: TextView = v.findViewById(R.id.tvReplayLabel)
            val tvUserText: TextView = v.findViewById(R.id.tvBubbleUserText)
        }
    }
}
