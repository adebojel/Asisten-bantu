package com.asisten.bantu.voiceaccess

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
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
            collectInteractive(root, raw, maxNodes * 2)
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
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return null
        val normalized = nodes.map { it to it.label.lowercase(Locale.ROOT) }
        normalized.firstOrNull { it.second == q }?.let { return it.first }
        val partial = normalized.filter { it.second.contains(q) || q.contains(it.second) }
        // Jangan menebak target pertama jika beberapa label cocok.
        return partial.singleOrNull()?.first
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
