package com.asisten.bantu.voiceaccess

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Mengindeks kontrol interaktif DAN teks/label layar yang terlihat menjadi target suara.
 * Target teks tetap dapat diketuk lewat koordinat jika aplikasi tidak mengekspos tombol semantik.
 */
object NodeIndexer {

    data class IndexedNode(
        val number: Int,
        val label: String,
        val centerX: Int,
        val centerY: Int,
        val bounds: Rect,
        val isClickable: Boolean,
        val isEditable: Boolean,
        val isScrollable: Boolean,
        val isCheckable: Boolean,
        val viewId: String,
        val className: String
    )

    @JvmStatic
    fun isWhatsAppPackage(packageName: String?): Boolean {
        return packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b"
    }

    @JvmStatic
    fun indexScreen(service: AccessibilityService?, maxNodes: Int = 60): List<IndexedNode> {
        if (service == null) return emptyList()
        val root = selectActiveRoot(service) ?: return emptyList()
        val raw = ArrayList<IndexedNode>()
        try {
            collectInteractive(root, raw, maxNodes * 4)
        } finally {
            safeRecycle(root)
        }

        // Urutkan dari atas ke bawah (band 28px), lalu kiri ke kanan
        raw.sortWith { a, b ->
            val bandA = a.bounds.top / 28
            val bandB = b.bounds.top / 28
            if (bandA != bandB) bandA.compareTo(bandB)
            else a.bounds.left.compareTo(b.bounds.left)
        }

        val deduped = ArrayList<IndexedNode>()
        for (item in raw) {
            val dup = deduped.any { existing ->
                kotlin.math.abs(existing.centerX - item.centerX) < 18 &&
                    kotlin.math.abs(existing.centerY - item.centerY) < 18
            }
            if (!dup) {
                deduped.add(item.copy(number = deduped.size + 1))
                if (deduped.size >= maxNodes) break
            }
        }
        return deduped
    }

    @JvmStatic
    fun buildAudioReadout(nodes: List<IndexedNode>, limit: Int = 18): String {
        if (nodes.isEmpty()) {
            return "Tidak ada elemen tombol atau teks yang terindeks pada layar saat ini."
        }
        val sb = StringBuilder("Ditemukan ${nodes.size} elemen layar. ")
        val maxShow = minOf(limit, nodes.size)
        for (i in 0 until maxShow) {
            val n = nodes[i]
            val role = when {
                n.isEditable -> "kolom ketik"
                n.isCheckable -> "sakelar"
                n.isScrollable && !n.isClickable -> "daftar gulir"
                n.isClickable -> "kontrol interaktif"
                else -> "teks/area layar, bisa dicoba ketuk"
            }
            sb.append("Nomor ").append(n.number).append(", ").append(n.label).append(" (").append(role).append("). ")
        }
        if (nodes.size > maxShow) {
            sb.append("Serta ").append(nodes.size - maxShow).append(" elemen lainnya. Sebutkan klik nomor untuk memilih.")
        } else {
            sb.append("Sebutkan klik nomor untuk memilih.")
        }
        return sb.toString().trim()
    }

    @JvmStatic
    fun findByNumber(nodes: List<IndexedNode>, number: Int): IndexedNode? {
        return nodes.firstOrNull { it.number == number }
    }

    @JvmStatic
    fun findByLabel(nodes: List<IndexedNode>, query: String): IndexedNode? {
        val candidates = nodes.map { node ->
            TargetLabelMatcher.Candidate(
                value = node,
                label = node.label,
                resourceId = node.viewId,
                left = node.bounds.left,
                top = node.bounds.top,
                right = node.bounds.right,
                bottom = node.bounds.bottom,
                actionable = node.isClickable || node.isEditable || node.isCheckable
            )
        }
        return when (val match = TargetLabelMatcher.resolve(query, candidates)) {
            is TargetLabelMatcher.Result.Found -> match.candidate.value
            is TargetLabelMatcher.Result.Ambiguous,
            TargetLabelMatcher.Result.NotFound -> null
        }
    }

    private fun collectInteractive(
        node: AccessibilityNodeInfo?,
        out: MutableList<IndexedNode>,
        limit: Int
    ) {
        if (node == null || out.size >= limit) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val onScreen = rect.width() > 6 && rect.height() > 6 && rect.right > 0 && rect.bottom > 0

        if (node.isVisibleToUser && node.isEnabled && onScreen) {
            val isActionable = node.isClickable || node.isLongClickable || node.isEditable || node.isCheckable
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString()?.trim().orEmpty()
            } else {
                ""
            }
            val viewId = node.viewIdResourceName.orEmpty().substringAfterLast('/')
            val cls = node.className?.toString().orEmpty().substringAfterLast('.')

            val resolvedLabel = when {
                text.isNotEmpty() -> text
                desc.isNotEmpty() -> desc
                hint.isNotEmpty() -> hint
                isActionable -> collectChildText(node).ifEmpty {
                    if (viewId.isNotEmpty()) viewId.replace('_', ' ') else cls.ifEmpty { "Elemen" }
                }
                else -> ""
            }

            // Jangan batasi target pada tombol. Label, teks, item daftar, judul video,
            // dan elemen UI non-clickable juga menjadi target bernomor. Ketukan pada
            // pusat teks akan naik ke parent clickable atau memakai gesture koordinat.
            val isTextTarget = resolvedLabel.isNotEmpty() && !node.isScrollable
            if ((isActionable || isTextTarget) && resolvedLabel.isNotEmpty()) {
                out.add(
                    IndexedNode(
                        number = 0,
                        label = resolvedLabel.take(64),
                        centerX = rect.centerX(),
                        centerY = rect.centerY(),
                        bounds = Rect(rect),
                        isClickable = node.isClickable || node.isLongClickable,
                        isEditable = node.isEditable,
                        isScrollable = node.isScrollable,
                        isCheckable = node.isCheckable,
                        viewId = viewId,
                        className = cls
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            if (out.size >= limit) break
            val child = node.getChild(i) ?: continue
            try {
                collectInteractive(child, out, limit)
            } finally {
                safeRecycle(child)
            }
        }
    }

    private fun collectChildText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        for (i in 0 until node.childCount) {
            if (sb.length >= 60) break
            val ch = node.getChild(i) ?: continue
            try {
                val t = ch.text?.toString()?.trim().orEmpty()
                val d = ch.contentDescription?.toString()?.trim().orEmpty()
                val piece = t.ifEmpty { d }
                if (piece.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(piece)
                }
            } finally {
                safeRecycle(ch)
            }
        }
        return sb.toString().trim()
    }

    /**
     * Re-resolves the current accessibility tree and performs ACTION_CLICK on the
     * smallest actionable node containing the indexed screen point. This avoids
     * acting on stale AccessibilityNodeInfo instances after a screen transition.
     */
    @JvmStatic
    fun performClickAt(service: AccessibilityService?, x: Int, y: Int): Boolean {
        if (service == null || x < 0 || y < 0) return false
        val root = selectActiveRoot(service) ?: return false
        return try {
            performClickAtRecursive(root, x, y)
        } finally {
            safeRecycle(root)
        }
    }

    private fun performClickAtRecursive(node: AccessibilityNodeInfo?, x: Int, y: Int): Boolean {
        if (node == null || !node.isVisibleToUser || !node.isEnabled) return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) return false

        // Prefer the deepest clickable child so nested rows do not swallow a
        // command intended for a smaller button inside the row.
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (performClickAtRecursive(child, x, y)) return true
            } finally {
                safeRecycle(child)
            }
        }
        return node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Move accessibility focus to the deepest readable node at a screen point. */
    @JvmStatic
    fun focusAt(service: AccessibilityService?, x: Int, y: Int): Boolean {
        if (service == null || x < 0 || y < 0) return false
        val root = selectActiveRoot(service) ?: return false
        return try {
            focusAtRecursive(root, x, y)
        } finally {
            safeRecycle(root)
        }
    }

    private fun focusAtRecursive(node: AccessibilityNodeInfo?, x: Int, y: Int): Boolean {
        if (node == null || !node.isVisibleToUser || !node.isEnabled) return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) return false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (focusAtRecursive(child, x, y)) return true
            } finally {
                safeRecycle(child)
            }
        }
        val hasLabel = !node.text.isNullOrBlank() ||
            !node.contentDescription.isNullOrBlank() ||
            node.isClickable || node.isEditable || node.isCheckable
        return hasLabel && node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    }

    /**
     * Advances the focused text node by one word using Android's native text movement granularity.
     * Returns the word selected by the app's accessibility provider, or null if unsupported.
     */
    @JvmStatic
    fun moveFocusedTextByWord(service: AccessibilityService?, forward: Boolean): String? {
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val root = selectActiveRoot(service) ?: return null
        var focused: AccessibilityNodeInfo? = null
        return try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return null
            val text = focused?.text?.toString().orEmpty()
            if (text.isEmpty()) return null
            val args = Bundle().apply {
                putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                )
                putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false)
            }
            val action = if (forward) AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
                else AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
            if (focused?.performAction(action, args) != true) return null
            focused?.refresh()
            val start = focused?.textSelectionStart ?: -1
            val end = focused?.textSelectionEnd ?: -1
            val range = resolveWordRange(text, start, end) ?: return null
            text.substring(range.first, range.last + 1)
        } catch (_: Throwable) {
            null
        } finally {
            safeRecycle(focused)
            safeRecycle(root)
        }
    }

    @JvmStatic
    fun focusedWordText(service: AccessibilityService?): String? {
        if (service == null) return null
        val root = selectActiveRoot(service) ?: return null
        var focused: AccessibilityNodeInfo? = null
        return try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return null
            val text = focused?.text?.toString().orEmpty()
            val start = focused?.textSelectionStart ?: -1
            val end = focused?.textSelectionEnd ?: -1
            // A selected/focused word must be explicitly exposed by the provider.
            if (start < 0 || end <= start) return null
            val range = resolveWordRange(text, start, end) ?: return null
            text.substring(range.first, range.last + 1)
        } finally {
            safeRecycle(focused)
            safeRecycle(root)
        }
    }

    /**
     * Returns a tight screen rectangle for the current word using Android's per-character
     * text geometry. No full-line or full-TextView rectangle is drawn as a word substitute.
     */
    @JvmStatic
    fun focusedWordBounds(service: AccessibilityService?): Rect? {
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val root = selectActiveRoot(service) ?: return null
        var focused: AccessibilityNodeInfo? = null
        return try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return null
            val text = focused?.text?.toString().orEmpty()
            if (text.isEmpty()) return null
            val start = focused?.textSelectionStart ?: -1
            val end = focused?.textSelectionEnd ?: -1
            // Do not assume cursor zero or draw a fake first-word rectangle.
            if (start < 0 || end <= start) return null
            val range = resolveWordRange(text, start, end) ?: return null
            val args = Bundle().apply {
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, range.first)
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, range.last - range.first + 1)
            }
            val node = focused ?: return null
            // Request geometry only from providers that explicitly advertise support.
            // This avoids treating unsupported/empty extras as a usable word location.
            if (!node.availableExtraData.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)) return null
            if (!node.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args)) return null
            val rects = node.extras.getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
                ?: return null
            val union = RectF()
            var hasRect = false
            for (item in rects) {
                val charRect = when (item) {
                    is RectF -> item
                    is Rect -> RectF(item)
                    else -> continue
                }
                // Ignore missing, off-screen, and zero-area character boxes. The requested
                // range is already a single word, so the union remains word-sized.
                if (charRect.isEmpty || !charRect.left.isFinite() || !charRect.top.isFinite() ||
                    !charRect.right.isFinite() || !charRect.bottom.isFinite()) continue
                if (!hasRect) {
                    union.set(charRect)
                    hasRect = true
                } else {
                    union.union(charRect)
                }
            }
            if (!hasRect || union.isEmpty) return null
            Rect(
                kotlin.math.floor(union.left).toInt(),
                kotlin.math.floor(union.top).toInt(),
                kotlin.math.ceil(union.right).toInt(),
                kotlin.math.ceil(union.bottom).toInt()
            ).takeIf { !it.isEmpty }
        } catch (_: Throwable) {
            null
        } finally {
            safeRecycle(focused)
            safeRecycle(root)
        }
    }

    private fun resolveWordRange(text: String, selectionStart: Int, selectionEnd: Int): IntRange? {
        if (text.isEmpty()) return null
        if (selectionStart >= 0 && selectionEnd > selectionStart) {
            val start = selectionStart.coerceIn(0, text.length)
            val end = selectionEnd.coerceIn(start, text.length)
            if (end > start && text.substring(start, end).any { it.isLetterOrDigit() }) return start until end
        }
        val cursor = (if (selectionEnd >= 0) selectionEnd else 0).coerceIn(0, text.length)
        var index = if (cursor == text.length) cursor - 1 else cursor
        if (index < 0) return null
        if (!text[index].isLetterOrDigit()) {
            while (index < text.length && !text[index].isLetterOrDigit()) index++
            if (index >= text.length) return null
        }
        var start = index
        var end = index + 1
        while (start > 0 && text[start - 1].isLetterOrDigit()) start--
        while (end < text.length && text[end].isLetterOrDigit()) end++
        return if (end > start) start until end else null
    }

    /** Current focused node label, without retaining AccessibilityNodeInfo references. */
    @JvmStatic
    fun focusedLabel(service: AccessibilityService?): String {
        if (service == null) return ""
        val root = selectActiveRoot(service) ?: return ""
        var focused: AccessibilityNodeInfo? = null
        return try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            val node = focused ?: return ""
            node.text?.toString()?.trim().orEmpty()
                .ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
                .ifEmpty { node.className?.toString()?.substringAfterLast('.') ?: "" }
        } finally {
            safeRecycle(focused)
            safeRecycle(root)
        }
    }

    /** Current accessibility-focused bounds, if the active app exposes a focused node. */
    @JvmStatic
    fun focusedBounds(service: AccessibilityService?): Rect? {
        if (service == null) return null
        val root = selectActiveRoot(service) ?: return null
        var focused: AccessibilityNodeInfo? = null
        return try {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            focused?.let {
                val bounds = Rect()
                it.getBoundsInScreen(bounds)
                if (bounds.isEmpty) null else bounds
            }
        } finally {
            safeRecycle(focused)
            safeRecycle(root)
        }
    }

    @JvmStatic
    fun activePackageName(service: AccessibilityService?): String? {
        if (service == null) return null
        val root = selectActiveRoot(service) ?: return null
        return try {
            root.packageName?.toString()
        } finally {
            safeRecycle(root)
        }
    }

    private fun selectActiveRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        return try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        }
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
