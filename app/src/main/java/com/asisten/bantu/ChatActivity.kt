package com.asisten.bantu

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Build
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
import org.json.JSONArray
import org.json.JSONObject
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
        private const val CHAT_HISTORY_PREFS = "kobra_chat_history_v1"
        private const val CHAT_HISTORY_KEY = "messages"
        private const val MAX_PERSISTED_CHAT_MESSAGES = 120

        @JvmField
        var current: ChatActivity? = null

        /** Voice bridge: global AccessibilityService and the visible chat share one conversation. */
        @JvmStatic
        fun dispatchGlobalVoice(text: String): Boolean {
            val activity = current ?: return false
            if (!activity.handsFreeConversation || activity.isFinishing || activity.isDestroyed) return false
            activity.onVoice(text)
            return true
        }

        @JvmStatic
        fun isUnifiedConversationActive(): Boolean {
            val activity = current
            return activity != null && activity.handsFreeConversation &&
                !activity.isFinishing && !activity.isDestroyed
        }

        @JvmStatic
        fun appendGlobalUser(text: String) {
            val activity = current ?: return
            if (activity.isFinishing || activity.isDestroyed) return
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) activity.addUserMessage(text)
            }
        }

        @JvmStatic
        fun appendGlobalUser(context: Context, text: String) {
            val activity = current
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) activity.addUserMessage(text)
                }
            } else {
                UnifiedConversationHistory.append(context, text, true, "Mikrofon Mengambang")
            }
        }

        @JvmStatic
        fun appendGlobalAssistant(text: String, source: String = "Kontrol Kobra") {
            val activity = current ?: return
            if (activity.isFinishing || activity.isDestroyed) return
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) activity.addAiMessage(text, source)
            }
        }

        @JvmStatic
        fun appendGlobalAssistant(context: Context, text: String, source: String) {
            val activity = current
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) activity.addAiMessage(text, source)
                }
            } else {
                UnifiedConversationHistory.append(context, text, false, source)
            }
        }
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
    private var recognitionRetryCount = 0
    private var lastRecognizerWasOnDevice = false
    private lateinit var btnHandsFreeConversation: Button

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
                if (handsFreeConversation && !AsistenAccessibilityService.isRunning()
                    && !suppressHandsFreeRearm && !isFinishing && current === this@ChatActivity) {
                    voiceOrb.postDelayed({
                        if (handsFreeConversation && !AsistenAccessibilityService.isRunning()
                    && !isFinishing && !isListening && current === this@ChatActivity) {
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
        restoreChatHistory()
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
        btnHandsFreeConversation = findViewById(R.id.btnHandsFreeConversation)
        btnHandsFreeConversation.setOnClickListener {
            val enable = !handsFreeConversation
            if (enable) {
                // Suppress the TTS completion callback so it cannot start a second recognizer.
                suppressHandsFreeRearm = true
                speaker.stop()
                suppressHandsFreeRearm = false
                handsFreeConversation = true
                recognitionRetryCount = 0
                btnHandsFreeConversation.text = "Percakapan terpadu: AKTIF"
                tvModelInfo.text = "Percakapan Kobra terpadu • Chat + kontrol HP + suara"
                if (AsistenAccessibilityService.isRunning()) {
                    AsistenAccessibilityService.setUnifiedChatMode(true)
                } else {
                    startSpeechRecognition()
                }
            } else {
                handsFreeConversation = false
                btnHandsFreeConversation.text = "Percakapan terpadu"
                AsistenAccessibilityService.setUnifiedChatMode(false)
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
        if (adapter.itemCount == 0) {
            val greeting = "Halo, saya $activeName (${activePersona.title}). Percakapan suara dan chat Kobra terhubung dalam satu riwayat."
            addAiMessage(greeting, "$activeName • ${activePersona.title}")
            speaker.speak(greeting)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Tombol headset kabel/Bluetooth: satu tekan untuk mulai/berhenti bicara.
        // Hanya tangani saat layar Chat Kobra sedang aktif agar tidak mengambil alih pemutar lain.
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
            if (event.repeatCount == 0 && !event.isCanceled) triggerVoiceBargeIn()
            return true
        }
        return super.onKeyDown(keyCode, event)
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
        // The floating microphone can append turns while ChatActivity is paused.
        // Reload the shared transcript when the screen returns to the foreground.
        restoreChatHistory()
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
        AsistenAccessibilityService.setUnifiedChatMode(false)
        if (::btnHandsFreeConversation.isInitialized) btnHandsFreeConversation.text = "Percakapan terpadu"
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
                if (!isFinishing && !isDestroyed) startSpeechRecognition()
            } else {
                handsFreeConversation = false
                btnHandsFreeConversation.text = "Percakapan hands-free"
                speaker.speak("Izin mikrofon belum diberikan. Aktifkan izin mikrofon untuk memakai percakapan suara.")
            }
        }
    }

    fun onVoice(text: String) {
        runOnUiThread {
            val clean = text.trim()
            if (clean.isNotEmpty()) {
                // Device actions use the local command router; normal conversation
                // continues through the same Chat/AI history and voice response path.
                if (AsistenAccessibilityService.dispatchUnifiedInput(clean)) {
                    // Tampilkan juga perintah perangkat di riwayat percakapan terpadu.
                    addUserMessage(clean)
                    return@runOnUiThread
                }
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
        val safe = config.isLlmSafeMode
        btnResetSafeMode.visibility = if (safe) View.VISIBLE else View.GONE
        tvModelInfo.text = when {
            safe -> "Mode Aman Lokal • Perintah sistem offline tersedia"
            llm.isInitialized -> "Gemma lokal aktif • Memori ${LocalMemoryStore.listMemories(this).size}"
            llm.isModelPresent -> "Model Gemma lokal siap"
            LocalModelRuntimeManager.isModelAvailable(this) -> "Qwen3 lokal tersedia"
            else -> "Mode Utilitas Lokal & Voice Access (Offline)"
        }
    }

    private fun triggerVoiceBargeIn() {
        // Barge-In: hentikan TTS seketika agar pengguna langsung bicara
        if (speaker.isSpeaking) {
            suppressHandsFreeRearm = true
            speaker.stop()
            suppressHandsFreeRearm = false
        }
        if (handsFreeConversation && AsistenAccessibilityService.isRunning()) {
            AsistenAccessibilityService.setUnifiedChatMode(true)
            return
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

    /**
     * Offline-only speech capture. Never instantiate the generic system recognizer:
     * on devices without Android's on-device recognizer, fail closed and explain
     * that an offline Indonesian ASR pack is required.
     */
    private fun startSpeechRecognition() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            isListening = false
            speechRecognizer = null
            voiceOrb.setOrbState(VoiceOrbView.OrbState.IDLE)
            tvModelInfo.text = "Mesin pengenal suara offline belum tersedia."
            speaker.speak("Pengenalan suara offline belum tersedia. Pasang mesin pengenal suara offline Bahasa Indonesia untuk menggunakan fitur suara. Kobra tidak mengirim suara ke server.")
            if (handsFreeConversation) {
                handsFreeConversation = false
                btnHandsFreeConversation.text = "Percakapan hands-free"
            }
            return
        }
        try {
            stopSpeechRecognition()
            val generation = ++recognitionGeneration
            val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            lastRecognizerWasOnDevice = true
            speechRecognizer = sr
            isListening = true
            voiceOrb.setOrbState(VoiceOrbView.OrbState.LISTENING)
            HapticFeedback.vibrateAction(this)

            sr.setRecognitionListener(object : RecognitionListener {
                private fun isCurrentSession(): Boolean =
                    speechRecognizer === sr && recognitionGeneration == generation &&
                        !isFinishing && !isDestroyed

                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isCurrentSession()) return
                    tvModelInfo.text = "Mendengarkan secara offline..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    if (isCurrentSession()) voiceOrb.updateRmsDb(rmsdB)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (!isCurrentSession()) return
                    voiceOrb.setOrbState(VoiceOrbView.OrbState.LISTENING)
                }
                override fun onError(error: Int) {
                    if (!isCurrentSession()) return
                    isListening = false
                    speechRecognizer = null
                    try { sr.destroy() } catch (_: Throwable) {}
                    voiceOrb.setOrbState(VoiceOrbView.OrbState.IDLE)
                    refreshModeBanner()
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        handsFreeConversation = false
                        btnHandsFreeConversation.text = "Percakapan hands-free"
                        speaker.speak("Izin mikrofon diperlukan untuk melanjutkan percakapan.")
                    } else if (handsFreeConversation) {
                        scheduleHandsFreeRetry("pengenalan suara offline")
                    } else {
                        speaker.speak("Pengenalan suara offline gagal. Periksa paket bahasa Indonesia offline dan izin mikrofon.")
                    }
                }
                override fun onResults(results: Bundle?) {
                    if (!isCurrentSession()) return
                    isListening = false
                    speechRecognizer = null
                    try { sr.destroy() } catch (_: Throwable) {}
                    voiceOrb.setOrbState(VoiceOrbView.OrbState.IDLE)
                    refreshModeBanner()
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (spoken.isNotEmpty()) {
                        recognitionRetryCount = 0
                        etInput.setText(spoken)
                        sendCurrentInput()
                    } else if (handsFreeConversation) {
                        scheduleHandsFreeRetry("hasil suara offline kosong")
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            sr.startListening(intent)
        } catch (t: Throwable) {
            isListening = false
            speechRecognizer = null
            voiceOrb.setOrbState(VoiceOrbView.OrbState.IDLE)
            refreshModeBanner()
            if (handsFreeConversation) {
                scheduleHandsFreeRetry("inisialisasi pengenalan suara offline")
            } else {
                speaker.speak("Mesin pengenal suara offline gagal dimulai. Periksa izin mikrofon dan paket bahasa offline.")
            }
        }
    }

    private fun scheduleHandsFreeRetry(reason: String) {
        if (!handsFreeConversation || isFinishing || isDestroyed) return
        recognitionRetryCount++
        if (recognitionRetryCount > 3) {
            handsFreeConversation = false
            btnHandsFreeConversation.text = "Percakapan hands-free"
            refreshModeBanner()
            speaker.speak("Pengenalan suara gagal beberapa kali. Silakan tekan tombol mikrofon untuk mencoba lagi.")
            return
        }
        val delayMs = 700L * recognitionRetryCount
        voiceOrb.postDelayed({
            if (handsFreeConversation && !isListening && current === this@ChatActivity && !isFinishing && !isDestroyed) {
                startSpeechRecognition()
            }
        }, delayMs)
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

        // 3) Model lokal Gemma/Qwen. No cloud fallback is permitted.
        val llm = LocalLlmEngine.getInstance(this)
        val systemPrompt = KobraPersona.buildFullSystemPrompt(this)
        if (llm.isModelPresent && !config.isLlmSafeMode && !llm.isSafeMode) {
            tvModelInfo.text = "Memproses dengan model Gemma lokal..."
            llm.summarizeTextAsync(systemPrompt, "Chat", displayPrompt, speaker) { reply: String? ->
                if (isFinishing || isDestroyed) return@summarizeTextAsync
                val clean = reply?.trim().orEmpty()
                if (clean.isNotEmpty() && !clean.contains("Gagal memproses") && !clean.contains("Mode Aman")) {
                    replyAndSpeak(clean, "Gemma Lokal", displayPrompt)
                } else if (LocalModelRuntimeManager.isModelAvailable(this) &&
                    LocalModelRegistry.canAttemptLiteRtModel(this)) {
                    routeToOfflineQwen(displayPrompt, systemPrompt)
                } else {
                    routeToOnlineOrFallback(displayPrompt, systemPrompt)
                }
                refreshModeBanner()
            }
            return
        }

        if (LocalModelRuntimeManager.isModelAvailable(this) &&
            LocalModelRegistry.canAttemptLiteRtModel(this)) {
            routeToOfflineQwen(displayPrompt, systemPrompt)
            return
        }

        routeToOnlineOrFallback(displayPrompt, systemPrompt)
    }

    private fun handleImageQuestion(prompt: String, imgB64: String, bmp: Bitmap?) {
        tvModelInfo.text = "Membaca gambar secara lokal..."
        fallbackOfflineImageOcr(bmp, prompt)
    }

    private fun fallbackOfflineImageOcr(bmp: Bitmap?, prompt: String) {
        if (bmp == null) {
            replyAndSpeak("Gambar tidak dapat diproses secara offline.", "Mata Lokal", prompt)
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
                replyAndSpeak(msg, "OCR Offline", prompt)
                refreshModeBanner()
            }
            .addOnFailureListener {
                bmp.recycle()
                attachedBitmap = null
                replyAndSpeak("Gagal membaca gambar secara offline.", "OCR Offline", prompt)
                refreshModeBanner()
            }
    }

    private fun routeToOfflineQwen(question: String, systemPrompt: String) {
        tvModelInfo.text = "Memproses dengan Qwen3 0.6B Offline..."
        val localPrompt = "$systemPrompt\n\nPesan terbaru pengguna: $question"
        LocalModelRuntimeManager.answerAsync(this, localPrompt) { answer, error ->
            if (isFinishing || isDestroyed) return@answerAsync
            val clean = answer?.trim().orEmpty()
            if (clean.isNotEmpty()) {
                replyAndSpeak(clean, "Qwen3 0.6B Offline", question)
            } else {
                replyAndSpeak(buildOfflineFallback(question), "Mode Lokal (fallback)", question)
            }
            refreshModeBanner()
        }
    }

    private fun routeToOnlineOrFallback(question: String, systemPrompt: String) {
        // Kept as a source-compatible method name; it never opens a network connection.
        replyAndSpeak(buildOfflineFallback(question), "Mode Lokal", question)
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
        return "Model percakapan lokal belum tersedia atau belum berhasil dimuat. " +
            "AI Kobra tidak menghubungi server. Perintah jam, sinyal, langkah, agenda, kalkulator, memori, dan kontrol dasar tetap tersedia offline."
    }

    private fun addUserMessage(text: String) {
        adapter.addMessage(ChatMessage(text, true))
        persistChatHistory()
        rvMessages.scrollToPosition(adapter.itemCount - 1)
    }

    private fun addAiMessage(text: String, meta: String) {
        adapter.addMessage(ChatMessage(text, false, meta))
        persistChatHistory()
        rvMessages.scrollToPosition(adapter.itemCount - 1)
    }

    private fun persistChatHistory() {
        if (!::adapter.isInitialized) return
        val json = JSONArray()
        adapter.snapshot().forEach { message ->
            json.put(JSONObject().apply {
                put("text", message.text)
                put("user", message.fromUser)
                put("meta", message.meta)
            })
        }
        getSharedPreferences(CHAT_HISTORY_PREFS, MODE_PRIVATE).edit()
            .putString(CHAT_HISTORY_KEY, json.toString()).apply()
    }

    private fun restoreChatHistory() {
        val raw = UnifiedConversationHistory.read(this)
        if (raw == "[]") return
        try {
            val json = JSONArray(raw)
            val restored = ArrayList<ChatMessage>()
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                val message = item.optString("text").trim()
                if (message.isNotEmpty()) restored.add(
                    ChatMessage(message, item.optBoolean("user"), item.optString("meta", "Kobra OS"))
                )
            }
            adapter.restore(restored.takeLast(MAX_PERSISTED_CHAT_MESSAGES))
        } catch (_: Exception) {
            getSharedPreferences(CHAT_HISTORY_PREFS, MODE_PRIVATE).edit()
                .remove(CHAT_HISTORY_KEY).apply()
        }
    }

    private fun replyAndSpeak(text: String, meta: String, userTurn: String? = null) {
        addAiMessage(text, meta)
        // Keep one bounded local session history across online/offline provider switches.
        // Chat generation uses summarizeTextAsync(useHistory=false), so record each
        // completed Chat turn here exactly once.
        val sourceTurn = userTurn?.takeIf { it.isNotBlank() } ?: adapter.lastUserMessage()
        if (sourceTurn.isNotBlank() && text.isNotBlank()) {
            LocalLlmEngine.getInstance(this).addSessionTurn(sourceTurn, text)
        }
        speaker.speak(text)
    }

    private class ChatBubbleAdapter(
        private val onReplayClick: (String) -> Unit
    ) : RecyclerView.Adapter<ChatBubbleAdapter.VH>() {

        private val items = ArrayList<ChatMessage>()

        fun addMessage(msg: ChatMessage) {
            // Bound in-memory chat history so long voice sessions do not grow the adapter forever.
            if (items.size >= MAX_MESSAGES_IN_MEMORY) {
                items.removeAt(0)
                notifyItemRemoved(0)
            }
            items.add(msg)
            notifyItemInserted(items.size - 1)
        }

        fun lastUserMessage(): String =
            items.lastOrNull { it.fromUser }?.text.orEmpty()

        /** Last bounded turns for online AI continuity; omit the current user turn (sent separately). */
        fun recentConversationContext(): String {
            val endExclusive = if (items.lastOrNull()?.fromUser == true) items.size - 1 else items.size
            if (endExclusive <= 0) return ""
            return items.subList(0, endExclusive).takeLast(12)
                .joinToString("\n") { item ->
                    (if (item.fromUser) "Pengguna: " else "Kobra: ") + item.text
                }
                .takeLast(2600)
        }

        fun snapshot(): List<ChatMessage> = items.toList()

        fun restore(messages: List<ChatMessage>) {
            items.clear()
            items.addAll(messages.takeLast(MAX_MESSAGES_IN_MEMORY))
            notifyDataSetChanged()
        }

        companion object {
            private const val MAX_MESSAGES_IN_MEMORY = 120
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
