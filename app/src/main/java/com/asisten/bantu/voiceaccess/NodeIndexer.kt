package com.asisten.bantu.voiceaccess

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Mengindeks seluruh elemen UI interaktif pada layar aktif menjadi daftar bernomor 1..N
 * secara spasial (atas ke bawah, kiri ke kanan) untuk fitur Google Voice Access offline.
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
                else -> "tombol"
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
        return nodes.firstOrNull { it.label.lowercase(Locale.ROOT) == q }
            ?: nodes.firstOrNull { it.label.lowercase(Locale.ROOT).contains(q) }
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

            if (isActionable && resolvedLabel.isNotEmpty()) {
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
