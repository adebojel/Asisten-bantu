package com.asisten.bantu

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Mesin pemulihan anti-macet (diadaptasi dari RecoveryEngine milik Jarvis ke Kotlin murni),
 * penutup pop-up penghalang otomatis, serta Local Macro Cache berbasis SharedPreferences
 * agar perintah berulang dapat dieksekusi di bawah 100ms tanpa memanggil LLM.
 */
class RecoveryEngine {

    enum class Strategy {
        DISMISS_POPUP,
        SCROLL_DOWN,
        WAIT_SETTLE,
        PRESS_BACK
    }

    data class RecoverySuggestion(
        val strategy: Strategy,
        val waitMs: Long = 0L,
        val reason: String
    )

    companion object {
        private const val TAG = "KobraRecoveryEngine"
        private const val SIGNATURE_WINDOW = 3
        private const val UNCHANGED_THRESHOLD = 3

        // Only explicit dismissal/refusal labels are eligible. Never auto-grant
        // runtime permissions or accept a confirmation dialog during recovery.
        private val SAFE_POPUP_LABELS = setOf(
            "lewati",
            "skip",
            "tutup",
            "close",
            "nanti saja",
            "nanti",
            "later",
            "not now",
            "tidak sekarang",
            "no thanks",
            "tidak, terima kasih"
        )

        @JvmStatic
        internal fun isSafeDismissPopupLabel(rawLabel: String): Boolean {
            return rawLabel.trim().lowercase(Locale.ROOT) in SAFE_POPUP_LABELS
        }

        private val BLOCKED_DESTRUCTIVE_KEYWORDS = listOf(
            "bayar", "pay", "transfer", "kirim uang", "beli", "checkout",
            "hapus", "delete", "remove", "uninstal", "copot", "konfirmasi pin",
            "reset", "keluar akun", "logout"
        )

        /**
         * Memindai node layar aktif untuk mencari tombol pop-up / izin / iklan penghalang yang aman ditutup.
         */
        @JvmStatic
        fun tryDismissBlockingPopup(service: AccessibilityService): String? {
            val root = try {
                service.rootInActiveWindow
            } catch (_: Throwable) {
                null
            } ?: return null

            val candidates = ArrayList<AccessibilityNodeInfo>()
            collectClickableNodes(root, candidates)
            var matchedLabel: String? = null

            try {
                for (node in candidates) {
                    val label = extractNodeText(node).lowercase(Locale.ROOT)
                    if (label.isEmpty() || label.length > 42) continue
                    if (BLOCKED_DESTRUCTIVE_KEYWORDS.any { label.contains(it) }) continue

                    val isPopupButton = isSafeDismissPopupLabel(label)
                    if (isPopupButton && node.isEnabled) {
                        var clickable: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
                        try {
                            while (clickable != null && !clickable.isClickable) {
                                val parent = clickable.parent
                                safeRecycle(clickable)
                                clickable = parent
                            }
                            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                matchedLabel = extractNodeText(node)
                                break
                            }
                        } finally {
                            safeRecycle(clickable)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Gagal memeriksa pop-up penghalang", t)
            } finally {
                for (n in candidates) {
                    safeRecycle(n)
                }
                safeRecycle(root)
            }
            return matchedLabel
        }

        private fun collectClickableNodes(
            node: AccessibilityNodeInfo?,
            out: MutableList<AccessibilityNodeInfo>
        ) {
            if (node == null || out.size >= 80) return
            try {
                if (node.isVisibleToUser && node.isEnabled) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (!rect.isEmpty) {
                        val txt = extractNodeText(node)
                        if (txt.isNotEmpty() && (node.isClickable || node.className?.contains("Button") == true)) {
                            out.add(AccessibilityNodeInfo.obtain(node))
                        }
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    try {
                        collectClickableNodes(child, out)
                    } finally {
                        safeRecycle(child)
                    }
                }
            } catch (_: Throwable) {
            }
        }

        private fun extractNodeText(node: AccessibilityNodeInfo): String {
            val t = node.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) return t
            return node.contentDescription?.toString()?.trim().orEmpty()
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

    private val recentSignatures = ArrayDeque<String>()
    private var lastScreenFingerprint: String? = null
    private var unchangedScreens: Int = 0
    private var recoveryCursor: Int = 0

    fun reset() {
        recentSignatures.clear()
        lastScreenFingerprint = null
        unchangedScreens = 0
        recoveryCursor = 0
    }

    fun recordAction(actionSignature: String) {
        if (actionSignature.isBlank()) return
        recentSignatures.addLast(actionSignature.trim().lowercase(Locale.ROOT))
        while (recentSignatures.size > 10) {
            recentSignatures.removeFirst()
        }
    }

    fun recordScreen(fingerprint: String) {
        if (fingerprint.isNotEmpty() && fingerprint == lastScreenFingerprint) {
            unchangedScreens++
        } else {
            unchangedScreens = 0
            lastScreenFingerprint = fingerprint
        }
    }

    val isRepeatingActions: Boolean
        get() {
            if (recentSignatures.size < SIGNATURE_WINDOW) return false
            val tail = recentSignatures.toList().takeLast(SIGNATURE_WINDOW)
            return tail.toSet().size == 1
        }

    val isScreenStuck: Boolean
        get() = unchangedScreens >= UNCHANGED_THRESHOLD

    val shouldRecover: Boolean
        get() = isRepeatingActions || isScreenStuck

    fun suggestRecovery(): RecoverySuggestion {
        val strategies = listOf(
            RecoverySuggestion(
                Strategy.DISMISS_POPUP,
                reason = "Pemulihan otomatis: menutup dialog atau pop-up penghalang."
            ),
            RecoverySuggestion(
                Strategy.SCROLL_DOWN,
                reason = "Pemulihan otomatis: layar tampak diam, menggulir ke bawah."
            ),
            RecoverySuggestion(
                Strategy.WAIT_SETTLE,
                waitMs = 1500L,
                reason = "Pemulihan otomatis: menunggu tampilan selesai dimuat."
            ),
            RecoverySuggestion(
                Strategy.PRESS_BACK,
                reason = "Pemulihan otomatis: kembali dari halaman buntu."
            )
        )
        val picked = strategies[recoveryCursor % strategies.size]
        recoveryCursor++
        return picked
    }
}

data class MacroStep(
    val action: String,
    val target: String = "",
    val x: Int = -1,
    val y: Int = -1
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("action", action)
        put("target", target)
        put("x", x)
        put("y", y)
    }

    companion object {
        fun fromJson(obj: JSONObject): MacroStep = MacroStep(
            action = obj.optString("action", ""),
            target = obj.optString("target", ""),
            x = obj.optInt("x", -1),
            y = obj.optInt("y", -1)
        )
    }
}

data class MacroSequence(
    val id: String,
    val triggerNormalized: String,
    val targetPackage: String,
    val steps: List<MacroStep>,
    val successCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("trigger", triggerNormalized)
        put("pkg", targetPackage)
        put("success_count", successCount)
        put("last_used", lastUsedAt)
        val arr = JSONArray()
        for (s in steps) arr.put(s.toJson())
        put("steps", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): MacroSequence? {
            val trigger = obj.optString("trigger", "").trim()
            val arr = obj.optJSONArray("steps") ?: return null
            if (trigger.isEmpty() || arr.length() == 0) return null
            val list = ArrayList<MacroStep>(arr.length())
            for (i in 0 until arr.length()) {
                val stepObj = arr.optJSONObject(i) ?: continue
                val step = MacroStep.fromJson(stepObj)
                if (step.action.isNotEmpty()) list.add(step)
            }
            if (list.isEmpty()) return null
            return MacroSequence(
                id = obj.optString("id", trigger),
                triggerNormalized = trigger,
                targetPackage = obj.optString("pkg", ""),
                steps = list,
                successCount = obj.optInt("success_count", 1),
                lastUsedAt = obj.optLong("last_used", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Penyimpanan urutan makro lokal di SharedPreferences agar perintah UI yang sudah pernah
 * berhasil dijalankan bisa langsung diputar ulang < 100ms tanpa memanggil LLM.
 */
class LocalMacroCache(context: Context) {

    companion object {
        private const val PREFS_NAME = "kobra_macro_cache"
        private const val KEY_MACROS_JSON = "macros_v1"
        private const val MAX_MACROS = 32

        @JvmStatic
        fun normalizeCommand(raw: String): String {
            return raw.lowercase(Locale.ROOT)
                .replace(Regex("\\b(kobra|tolong|coba|silakan|dong|ya)\\b"), " ")
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun findMatchingMacro(spokenCommand: String): MacroSequence? {
        val norm = normalizeCommand(spokenCommand)
        if (norm.isEmpty()) return null
        val all = loadAllInternal()
        var best: MacroSequence? = null
        var bestScore = 0.0

        val normWords = norm.split(" ").filter { it.length >= 2 }.toSet()
        for (macro in all) {
            if (macro.triggerNormalized == norm) {
                return macro
            }
            val triggerWords = macro.triggerNormalized.split(" ").filter { it.length >= 2 }.toSet()
            if (triggerWords.isEmpty() || normWords.isEmpty()) continue
            val intersect = normWords.intersect(triggerWords).size.toDouble()
            val union = normWords.union(triggerWords).size.toDouble()
            val jaccard = if (union > 0.0) intersect / union else 0.0
            if (jaccard >= 0.80 && jaccard > bestScore) {
                bestScore = jaccard
                best = macro
            }
        }
        return best
    }

    @Synchronized
    fun saveMacro(rawCommand: String, targetPackage: String, steps: List<MacroStep>) {
        val norm = normalizeCommand(rawCommand)
        if (norm.length < 4 || steps.isEmpty()) return

        val filteredSteps = steps.filter {
            it.action.isNotEmpty() && it.action != "wait" && it.action != "done"
        }
        if (filteredSteps.isEmpty()) return

        val current = loadAllInternal().toMutableList()
        val existingIdx = current.indexOfFirst { it.triggerNormalized == norm }
        val updated = if (existingIdx >= 0) {
            val prev = current[existingIdx]
            current.removeAt(existingIdx)
            MacroSequence(
                id = prev.id,
                triggerNormalized = norm,
                targetPackage = targetPackage.ifEmpty { prev.targetPackage },
                steps = filteredSteps,
                successCount = prev.successCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        } else {
            MacroSequence(
                id = "macro_${System.currentTimeMillis()}",
                triggerNormalized = norm,
                targetPackage = targetPackage,
                steps = filteredSteps,
                successCount = 1,
                lastUsedAt = System.currentTimeMillis()
            )
        }
        current.add(0, updated)
        while (current.size > MAX_MACROS) {
            current.removeAt(current.lastIndex)
        }
        persistAll(current)
    }

    @Synchronized
    fun recordUsage(macroId: String) {
        val list = loadAllInternal().toMutableList()
        val idx = list.indexOfFirst { it.id == macroId }
        if (idx >= 0) {
            val old = list[idx]
            list[idx] = old.copy(
                successCount = old.successCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
            persistAll(list)
        }
    }

    @Synchronized
    fun listMacrosSpeech(): String {
        val all = loadAllInternal()
        if (all.isEmpty()) {
            return "Belum ada makro cepat yang tersimpan."
        }
        val sb = StringBuilder("Ada ${all.size} makro tersimpan: ")
        val limit = minOf(6, all.size)
        for (i in 0 until limit) {
            if (i > 0) sb.append(", ")
            sb.append(all[i].triggerNormalized)
        }
        sb.append(".")
        return sb.toString()
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().remove(KEY_MACROS_JSON).apply()
    }

    private fun loadAllInternal(): List<MacroSequence> {
        val raw = prefs.getString(KEY_MACROS_JSON, "[]") ?: "[]"
        val out = ArrayList<MacroSequence>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val m = MacroSequence.fromJson(obj)
                if (m != null) out.add(m)
            }
        } catch (_: Throwable) {
        }
        return out
    }

    private fun persistAll(list: List<MacroSequence>) {
        val arr = JSONArray()
        for (m in list) arr.put(m.toJson())
        prefs.edit().putString(KEY_MACROS_JSON, arr.toString()).apply()
    }
}
