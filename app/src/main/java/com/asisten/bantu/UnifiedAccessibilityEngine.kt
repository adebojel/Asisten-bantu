package com.asisten.bantu

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Mesin terpadu Satu Pintu untuk AsistenAccessibilityService.
 * Menggabungkan:
 * 1) DeVA Eyes (ekstraksi pohon elemen UI AccessibilityNodeInfo + takeScreenshot Android 11+).
 * 2) DeVA Finger & Jarvis Agent (klik label/koordinat, swipe, scroll, ketik teks, tombol global).
 * 3) Fitur eks-KobraAccessibilityService (YouTube Auto-Play #67, pembaca layar global opsional
 *    anti-bentrok TTS internal, serta pengenalan gestur 2 jari pada Android 11+).
 */
object UnifiedAccessibilityEngine {

    private const val TAG = "KobraUnifiedAcc"
    private const val PREF_GLOBAL_READER = "global_screen_reader_enabled"

    private val ytLock = Any()
    @Volatile
    private var pendingYoutubeUntilMs: Long = 0L
    @Volatile
    private var pendingYoutubeQuery: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var serviceScope: CoroutineScope? = null

    private var externalTts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false
    private var internalSpeaker: Speaker? = null
    private var appContext: Context? = null
    private var lastSpokenExternal = ""
    private var lastSpokenExternalAt = 0L

    private val internalSpeechListener = object : Speaker.Listener {
        override fun onSpeechStart() {
            stopExternalTts()
        }

        override fun onSpeechEnd() {}
    }

    data class ScreenElement(
        val index: Int,
        val text: String,
        val contentDescription: String,
        val viewId: String,
        val className: String,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val checkable: Boolean,
        val enabled: Boolean,
        val bounds: Rect,
        val centerX: Int,
        val centerY: Int
    )

    data class ScreenSnapshot(
        val packageName: String,
        val elements: List<ScreenElement>,
        val formattedTree: String,
        val fingerprint: String
    )

    @JvmStatic
    fun getScope(): CoroutineScope {
        val current = serviceScope
        if (current != null) return current
        val created = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        serviceScope = created
        return created
    }

    @JvmStatic
    fun isGlobalReaderEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(
            KobraConfig.PREFS_CONFIG,
            Context.MODE_PRIVATE
        )
        return prefs.getBoolean(PREF_GLOBAL_READER, false)
    }

    @JvmStatic
    fun setGlobalReaderEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(
            KobraConfig.PREFS_CONFIG,
            Context.MODE_PRIVATE
        )
        prefs.edit().putBoolean(PREF_GLOBAL_READER, enabled).apply()
        if (!enabled) {
            stopExternalTts()
        }
    }

    @JvmStatic
    fun onServiceConnected(service: AccessibilityService) {
        appContext = service.applicationContext
        serviceScope?.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        internalSpeaker = Speaker.get(service.applicationContext).also { spk ->
            spk.removeListener(internalSpeechListener)
            spk.addListener(internalSpeechListener)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val info = service.serviceInfo
                if (info != null) {
                    info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
                    service.serviceInfo = info
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Gagal mendaftarkan flag multi-finger gesture", t)
            }
        }

        initExternalTtsIfNeeded(service.applicationContext)
    }

    @JvmStatic
    fun onServiceDestroyed() {
        try {
            serviceScope?.cancel()
        } catch (_: Throwable) {
        }
        serviceScope = null

        try {
            internalSpeaker?.removeListener(internalSpeechListener)
        } catch (_: Throwable) {
        }
        internalSpeaker = null

        val tts = externalTts
        externalTts = null
        ttsReady = false
        if (tts != null) {
            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Throwable) {
            }
        }
    }

    private fun initExternalTtsIfNeeded(ctx: Context) {
        if (externalTts != null) return
        try {
            externalTts = TextToSpeech(ctx) { status ->
                val tts = externalTts ?: return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) return@TextToSpeech
                ttsReady = true
                val res = tts.setLanguage(Locale("id", "ID"))
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault())
                }
                applyExternalTtsSettings(ctx)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "External TTS gagal diinisialisasi", t)
        }
    }

    private fun applyExternalTtsSettings(ctx: Context) {
        val tts = externalTts ?: return
        if (!ttsReady) return
        try {
            val cfg = KobraConfig(ctx)
            tts.setSpeechRate(cfg.speechRate)
            tts.setPitch(cfg.speechPitch)
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun stopExternalTts() {
        val tts = externalTts ?: return
        try {
            tts.stop()
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun onAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()

        if ("com.google.android.youtube" == pkg && System.currentTimeMillis() <= pendingYoutubeUntilMs) {
            mainHandler.postDelayed({
                val ytRoot = try {
                    service.rootInActiveWindow
                } catch (_: Throwable) {
                    null
                }
                try {
                    tryConsumeYoutubeAutoPlay(ytRoot, "com.google.android.youtube")
                } catch (_: Throwable) {
                } finally {
                    safeRecycle(ytRoot)
                }
            }, 900L)
        }

        // 2) Pembaca Layar Global Opsional untuk aplikasi pihak ketiga (anti-bentrok TTS internal)
        if (!isGlobalReaderEnabled(service)) return
        if (pkg.isEmpty() || service.packageName == pkg) return
        if (internalSpeaker?.isSpeaking == true) return
        if (isAnotherSpokenServiceActive(service)) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_VIEW_FOCUSED
            && type != AccessibilityEvent.TYPE_VIEW_CLICKED
            && type != AccessibilityEvent.TYPE_VIEW_SELECTED
            && type != AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
            && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val phrase = extractSpokenPhrase(service, event, pkg, type)
        if (phrase.isEmpty()) return

        val now = System.currentTimeMillis()
        if (phrase == lastSpokenExternal && now - lastSpokenExternalAt < 900L) return
        lastSpokenExternal = phrase
        lastSpokenExternalAt = now

        speakExternal(service, phrase)
    }

    @JvmStatic
    fun onGesture(service: AccessibilityService, gestureEvent: AccessibilityGestureEvent?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && gestureEvent != null) {
            when (gestureEvent.gestureId) {
                AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN -> {
                    stopExternalTts()
                    HapticFeedback.vibrateAction(service)
                    val msg = KobraNotificationService.summary(12)
                    Speaker.get(service).speak(msg)
                    return true
                }
                AccessibilityService.GESTURE_2_FINGER_SWIPE_UP -> {
                    stopExternalTts()
                    Speaker.get(service).stop()
                    EmergencyBeacon.stopAll(service, null)
                    HapticFeedback.vibrateAction(service)
                    return true
                }
                AccessibilityService.GESTURE_DOUBLE_TAP -> {
                    stopExternalTts()
                    HapticFeedback.vibrateAction(service)
                    AsistenAccessibilityService.requestListen()
                    return true
                }
            }
        }
        return false
    }

    private fun speakExternal(ctx: Context, text: String) {
        if (text.trim().isEmpty()) return
        if (internalSpeaker?.isSpeaking == true) return
        mainHandler.post {
            val tts = externalTts ?: return@post
            if (!ttsReady) return@post
            if (internalSpeaker?.isSpeaking == true) return@post
            applyExternalTtsSettings(ctx)
            try {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kobra_ext_${System.currentTimeMillis()}")
            } catch (_: Throwable) {
            }
        }
    }

    private fun isAnotherSpokenServiceActive(ctx: Context): Boolean {
        return try {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN) ?: return false
            list.any { info ->
                val id = info.id
                id != null && !id.startsWith(ctx.packageName + "/")
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun extractSpokenPhrase(
        ctx: Context,
        event: AccessibilityEvent,
        pkg: String,
        eventType: Int
    ): String {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val appName = resolveAppLabel(ctx, pkg)
            val title = joinCharSequences(event.text)
            if (title.isNotEmpty()) {
                return if (appName.isEmpty() || title.lowercase(Locale.ROOT).contains(appName.lowercase(Locale.ROOT))) {
                    title
                } else {
                    "$appName, $title"
                }
            }
            return if (appName.isEmpty()) "" else "Membuka $appName"
        }

        val sb = StringBuilder()
        val src = event.source
        if (src != null) {
            try {
                val desc = src.contentDescription?.toString()?.trim().orEmpty()
                val txt = src.text?.toString()?.trim().orEmpty()
                if (desc.isNotEmpty()) sb.append(desc)
                else if (txt.isNotEmpty()) sb.append(txt)

                if (sb.isNotEmpty()) {
                    val cls = src.className?.toString().orEmpty()
                    when {
                        cls.contains("Button") && !cls.contains("RadioButton") -> sb.append(", tombol")
                        cls.contains("CheckBox") || cls.contains("Switch") ->
                            sb.append(if (src.isChecked) ", aktif" else ", mati")
                        cls.contains("EditText") -> sb.append(", kolom teks")
                    }
                }
            } finally {
                safeRecycle(src)
            }
        }
        if (sb.isEmpty() && event.contentDescription != null) {
            sb.append(event.contentDescription.toString().trim())
        }
        if (sb.isEmpty()) {
            sb.append(joinCharSequences(event.text))
        }
        val out = sb.toString().replace(Regex("\\s+"), " ").trim()
        return if (out.length > 180) out.substring(0, 180) else out
    }

    private fun joinCharSequences(list: List<CharSequence>?): String {
        if (list.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        for (cs in list) {
            val t = cs?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append(", ")
                sb.append(t)
            }
        }
        return sb.toString()
    }

    private fun resolveAppLabel(ctx: Context, pkg: String): String {
        return try {
            val pm: PackageManager = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Throwable) {
            ""
        }
    }

    // =========================================================================
    // DeVA "Eyes": Pembaca Pohon Elemen UI & Screenshot Android 11+ (API 30+)
    // =========================================================================

    fun captureScreenSnapshot(service: AccessibilityService, preferForeignWindow: Boolean = true): ScreenSnapshot {
        val selfPkg = service.packageName
        val root = selectBestWindowRoot(service, selfPkg, preferForeignWindow)
            ?: return ScreenSnapshot("", emptyList(), "Layar tidak tersedia.", "empty")

        val elements = ArrayList<ScreenElement>()
        val pkgName = root.packageName?.toString().orEmpty()
        try {
            traverseNodes(root, elements, 90)
        } finally {
            safeRecycle(root)
        }

        val formatted = if (elements.isEmpty()) {
            "Tidak ada elemen interaktif atau teks pada layar."
        } else {
            buildString {
                append("Aplikasi aktif: ").append(pkgName.ifEmpty { "unknown" }).append('\n')
                for (el in elements) {
                    val typeName = el.className.substringAfterLast('.').ifEmpty { "View" }
                    val label = el.text.ifEmpty { el.contentDescription }.ifEmpty {
                        el.viewId.substringAfterLast('/')
                    }
                    val flags = buildList {
                        if (el.clickable) add("clickable")
                        if (el.editable) add("editable")
                        if (el.scrollable) add("scrollable")
                    }.joinToString(",")
                    append("#${el.index} [$typeName] \"$label\"")
                    if (flags.isNotEmpty()) append(" ($flags)")
                    append(" @(${el.centerX},${el.centerY})\n")
                }
            }.trim()
        }

        val fingerprint = "$pkgName|${elements.size}|" +
            elements.take(18).joinToString("|") { "${it.text}:${it.contentDescription}:${it.centerX},${it.centerY}" }

        return ScreenSnapshot(pkgName, elements, formatted, fingerprint)
    }

    private fun selectBestWindowRoot(
        service: AccessibilityService,
        selfPkg: String,
        preferForeignWindow: Boolean
    ): AccessibilityNodeInfo? {
        val active = try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        }
        if (!preferForeignWindow) return active
        if (active != null && active.packageName?.toString() != selfPkg) {
            return active
        }

        try {
            val wins = service.windows
            if (wins != null) {
                var bestForeign: AccessibilityNodeInfo? = null
                for (w in wins) {
                    if (w.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
                    val r = w.root ?: continue
                    val pkg = r.packageName?.toString().orEmpty()
                    if (pkg.isNotEmpty() && pkg != selfPkg) {
                        if (w.isActive || w.isFocused) {
                            safeRecycle(bestForeign)
                            safeRecycle(active)
                            return r
                        }
                        if (bestForeign == null) {
                            bestForeign = r
                        } else {
                            safeRecycle(r)
                        }
                    } else {
                        safeRecycle(r)
                    }
                }
                if (bestForeign != null) {
                    safeRecycle(active)
                    return bestForeign
                }
            }
        } catch (_: Throwable) {
        }
        return active
    }

    private fun traverseNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<ScreenElement>,
        maxElements: Int
    ) {
        if (out.size >= maxElements) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val className = node.className?.toString().orEmpty()

        val interactive = node.isClickable || node.isEditable || node.isScrollable ||
            node.isCheckable || node.isLongClickable
        val hasLabel = text.isNotEmpty() || desc.isNotEmpty()
        val onScreen = rect.width() > 0 && rect.height() > 0

        if (node.isVisibleToUser && onScreen && (interactive || hasLabel)) {
            out.add(
                ScreenElement(
                    index = out.size,
                    text = text,
                    contentDescription = desc,
                    viewId = viewId,
                    className = className,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    checkable = node.isCheckable,
                    enabled = node.isEnabled,
                    bounds = rect,
                    centerX = rect.centerX(),
                    centerY = rect.centerY()
                )
            )
        }

        for (i in 0 until node.childCount) {
            if (out.size >= maxElements) break
            val child = node.getChild(i) ?: continue
            try {
                traverseNodes(child, out, maxElements)
            } finally {
                safeRecycle(child)
            }
        }
    }

    /**
     * Mengambil tangkapan layar pada Android 11+ (API 30+) menjadi Base64 JPEG terkompresi.
     * HardwareBuffer dan Bitmap selalu ditutup/didaur ulang segera agar tidak bocor memori.
     */
    suspend fun captureScreenshotBase64(service: AccessibilityService): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        return withTimeoutOrNull(4500L) {
            captureScreenshotInternal(service)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreenshotInternal(service: AccessibilityService): String? =
        suspendCancellableCoroutine { continuation ->
            try {
                val executor = ContextCompat.getMainExecutor(service)
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                            var hwBitmap: Bitmap? = null
                            var softBitmap: Bitmap? = null
                            var scaledBitmap: Bitmap? = null
                            val hwBuffer = screenshotResult.hardwareBuffer
                            try {
                                hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, screenshotResult.colorSpace)
                                hwBuffer.close()
                                if (hwBitmap == null) {
                                    if (continuation.isActive) continuation.resume(null)
                                    return
                                }
                                softBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                hwBitmap.recycle()
                                hwBitmap = null

                                if (softBitmap == null) {
                                    if (continuation.isActive) continuation.resume(null)
                                    return
                                }

                                val maxSide = maxOf(softBitmap.width, softBitmap.height)
                                val uploadBitmap = if (maxSide > 1080) {
                                    val ratio = 1080f / maxSide
                                    scaledBitmap = Bitmap.createScaledBitmap(
                                        softBitmap,
                                        (softBitmap.width * ratio).toInt().coerceAtLeast(1),
                                        (softBitmap.height * ratio).toInt().coerceAtLeast(1),
                                        true
                                    )
                                    scaledBitmap
                                } else {
                                    softBitmap
                                }

                                val baos = ByteArrayOutputStream()
                                uploadBitmap.compress(Bitmap.CompressFormat.JPEG, 72, baos)
                                val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                                if (continuation.isActive) continuation.resume(encoded)
                            } catch (t: Throwable) {
                                Log.e(TAG, "Gagal memproses hasil screenshot", t)
                                try {
                                    hwBuffer.close()
                                } catch (_: Throwable) {
                                }
                                if (continuation.isActive) continuation.resume(null)
                            } finally {
                                try {
                                    if (scaledBitmap != null && scaledBitmap !== softBitmap) {
                                        scaledBitmap.recycle()
                                    }
                                    softBitmap?.recycle()
                                    hwBitmap?.recycle()
                                } catch (_: Throwable) {
                                }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot gagal dengan kode: $errorCode")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Exception saat memanggil takeScreenshot", t)
                if (continuation.isActive) continuation.resume(null)
            }
        }

    // =========================================================================
    // DeVA "Finger" & Eksekutor Aksi Global Satu Pintu
    // =========================================================================

    @JvmStatic
    fun performGlobalNavAction(service: AccessibilityService?, globalAction: Int): Boolean {
        if (service == null) return false
        return try {
            service.performGlobalAction(globalAction)
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun clickAt(service: AccessibilityService?, x: Float, y: Float): Boolean {
        if (service == null || x < 0f || y < 0f) return false
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun longClickAt(service: AccessibilityService?, x: Float, y: Float): Boolean {
        if (service == null || x < 0f || y < 0f) return false
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 900L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun swipe(
        service: AccessibilityService?,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long = 360L
    ): Boolean {
        if (service == null) return false
        return try {
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(50L, 2500L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun scrollGlobal(service: AccessibilityService?, forward: Boolean): Boolean {
        if (service == null) return false
        val root = try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        }
        if (root != null) {
            try {
                val scrollable = findScrollableNode(root, true) ?: findScrollableNode(root, false)
                if (scrollable != null) {
                    val ok = scrollable.performAction(
                        if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    )
                    if (ok) return true
                }
            } finally {
                safeRecycle(root)
            }
        }
        // Fallback gestur swipe
        val dm = service.resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val yStart = if (forward) dm.heightPixels * 0.72f else dm.heightPixels * 0.28f
        val yEnd = if (forward) dm.heightPixels * 0.28f else dm.heightPixels * 0.72f
        return swipe(service, cx, yStart, cx, yEnd, 360L)
    }

    @JvmStatic
    fun clickByNameGlobal(service: AccessibilityService?, targetName: String?): Boolean {
        if (service == null || targetName.isNullOrBlank()) return false
        val root = try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return false
        return try {
            clickNodeByName(root, targetName)
        } finally {
            safeRecycle(root)
        }
    }

    @JvmStatic
    fun clickNodeByName(root: AccessibilityNodeInfo?, targetName: String?): Boolean {
        if (root == null || targetName.isNullOrBlank()) return false
        val q = targetName.trim().lowercase(Locale.ROOT)
        val node = findNodeByLabel(root, q, exact = true) ?: findNodeByLabel(root, q, exact = false) ?: return false
        var cur: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        return try {
            while (cur != null && !cur.isClickable) {
                val parent = cur.parent
                safeRecycle(cur)
                cur = parent
            }
            cur != null && cur.isEnabled && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            safeRecycle(cur)
        }
    }

    @JvmStatic
    fun typeGlobalText(service: AccessibilityService?, text: String?): Boolean {
        if (service == null) return false
        val root = try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        }
        return try {
            typeTextInRoot(service, root, text)
        } finally {
            safeRecycle(root)
        }
    }

    @JvmStatic
    fun typeTextInRoot(context: Context?, root: AccessibilityNodeInfo?, text: String?): Boolean {
        if (text == null) return false
        if (context != null) {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Kobra OS", text))
            } catch (_: Throwable) {
            }
        }
        if (root == null) return false
        var field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (field == null || !field.isEditable) {
            field = findEditableNode(root)
        }
        if (field == null) return false

        try {
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        } catch (_: Throwable) {
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return true
        }
        return field.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    @JvmStatic
    fun performImeEnter(service: AccessibilityService?): Boolean {
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val root = try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return false
        return try {
            val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } finally {
            safeRecycle(root)
        }
    }

    @JvmStatic
    fun openAppSmart(context: Context, queryOrPkg: String): Boolean {
        val clean = queryOrPkg.trim()
        if (clean.isEmpty()) return false
        val pm = context.packageManager

        // Coba sebagai package name langsung
        if (clean.contains(".")) {
            val direct = pm.getLaunchIntentForPackage(clean)
            if (direct != null) {
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(direct)
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }

        // Cocokkan via AppLauncher / AppMatcher bawaan Asisten-bantu
        return try {
            val apps = AppLauncher.installed(context)
            val matches = AppMatcher.find(apps, clean, 1)
            if (matches.isNotEmpty()) {
                val intent = pm.getLaunchIntentForPackage(matches[0].app.pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    // =========================================================================
    // #67 YouTube Voice Search & Auto-Play (Disatukan dari KobraAccessibilityService)
    // =========================================================================

    @JvmStatic
    fun markPendingYoutubeAutoPlay(query: String?) {
        synchronized(ytLock) {
            pendingYoutubeQuery = query?.trim()?.lowercase(Locale.ROOT).orEmpty()
            pendingYoutubeUntilMs = System.currentTimeMillis() + 12_000L
        }
    }

    @JvmStatic
    fun tryConsumeYoutubeAutoPlay(root: AccessibilityNodeInfo?, pkg: String?): Boolean {
        if (root == null) return false
        val query: String
        synchronized(ytLock) {
            if (System.currentTimeMillis() > pendingYoutubeUntilMs) return false
            val activePkg = root.packageName?.toString() ?: (pkg ?: "")
            if (!activePkg.contains("youtube")) return false
            query = pendingYoutubeQuery
        }

        var videoNode = findFirstYoutubeVideoNode(root, query, strict = true)
        if (videoNode == null) {
            videoNode = findFirstYoutubeVideoNode(root, query, strict = false)
        }
        if (videoNode == null) return false

        synchronized(ytLock) {
            if (System.currentTimeMillis() > pendingYoutubeUntilMs) return false
            pendingYoutubeUntilMs = 0L
        }
        return videoNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findFirstYoutubeVideoNode(
        node: AccessibilityNodeInfo?,
        query: String,
        strict: Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isClickable && node.isVisibleToUser && node.isEnabled) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.top >= 180 && bounds.width() >= 260 && bounds.height() >= 140) {
                val label = collectNodeSummary(node).lowercase(Locale.ROOT)
                if (label.isNotEmpty() && !isYoutubeChromeLabel(label)) {
                    if (!strict) {
                        if (label.length >= 12) return node
                    } else {
                        val looksLikeVideo = label.length >= 20 ||
                            label.contains("menit") || label.contains("detik") || label.contains("jam") ||
                            label.contains("minute") || label.contains("second") ||
                            label.contains("ditonton") || label.contains("views") ||
                            label.contains("buka channel") || label.contains("go to channel") ||
                            (query.isNotEmpty() && containsAnyWord(label, query))
                        if (looksLikeVideo) return node
                    }
                }
            }
        }
        for (i in 0 until node.childCount) {
            val r = findFirstYoutubeVideoNode(node.getChild(i), query, strict)
            if (r != null) return r
        }
        return null
    }

    private fun isYoutubeChromeLabel(lower: String): Boolean {
        val chrome = arrayOf(
            "telusuri", "search", "penelusuran suara", "voice search",
            "kembali", "navigate up", "back", "filter", "beranda", "home",
            "shorts", "subscription", "anda", "you", "koleksi", "library",
            "transmisikan", "cast", "notifikasi", "notifications", "akun", "account",
            "opsi lainnya", "more options", "menu tindakan", "buat", "create",
            "semua", "all", "video", "unwatched", "belum ditonton", "terbaru", "live"
        )
        for (c in chrome) {
            if (lower == c) return true
        }
        return lower.startsWith("filter ") || lower.startsWith("telusuri ")
    }

    private fun containsAnyWord(text: String, query: String): Boolean {
        for (w in query.split(Regex("\\s+"))) {
            if (w.length >= 3 && text.contains(w)) return true
        }
        return false
    }

    private fun collectNodeSummary(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        if (!node.contentDescription.isNullOrEmpty()) {
            sb.append(node.contentDescription).append(' ')
        }
        if (!node.text.isNullOrEmpty()) {
            sb.append(node.text).append(' ')
        }
        var i = 0
        while (i < node.childCount && sb.length < 120) {
            val ch = node.getChild(i)
            if (ch != null) {
                if (!ch.text.isNullOrEmpty()) sb.append(ch.text).append(' ')
                else if (!ch.contentDescription.isNullOrEmpty()) sb.append(ch.contentDescription).append(' ')
            }
            i++
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun findNodeByLabel(
        node: AccessibilityNodeInfo?,
        q: String,
        exact: Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser) {
            if (matchesLabel(node.text, q, exact) || matchesLabel(node.contentDescription, q, exact)) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val found = findNodeByLabel(node.getChild(i), q, exact)
            if (found != null) return found
        }
        return null
    }

    private fun matchesLabel(cs: CharSequence?, q: String, exact: Boolean): Boolean {
        if (cs == null) return false
        val s = cs.toString().trim().lowercase(Locale.ROOT)
        if (s.isEmpty()) return false
        return if (exact) s == q else s.contains(q)
    }

    private fun findScrollableNode(
        node: AccessibilityNodeInfo?,
        visibleOnly: Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable && (!visibleOnly || node.isVisibleToUser)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val r = findScrollableNode(node.getChild(i), visibleOnly)
            if (r != null) return r
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val r = findEditableNode(node.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun safeRecycle(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                @Suppress("DEPRECATION")
                node.recycle()
            } catch (_: Throwable) {
            }
        }
    }
}
