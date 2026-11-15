package com.asisten.bantu.voiceaccess

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.asisten.bantu.KobraConfig
import com.asisten.bantu.UnifiedAccessibilityEngine
import com.asisten.bantu.VoiceProfileManager
import java.util.Locale
import java.util.function.Consumer

/**
 * Mesin Voice Access 100% Offline untuk Kobra OS.
 * Mendukung penomoran elemen layar visual & audio ("baca layar"), klik/tahan via nomor atau nama,
 * Grid 3x3 presisi dengan zoom bersarang, navigasi sistem, pengeditan teks lengkap,
 * kontrol volume/media/telepon, dan mode Tidur/Bangun.
 */
class VoiceAccessEngine(private val service: AccessibilityService) {

    private val numberOverlay = NumberBadgeOverlay(service)
    private val gridOverlay = ScreenGridOverlay(service)

    @Volatile
    private var lastIndexedNodes: List<NodeIndexer.IndexedNode> = emptyList()

    @Volatile
    var isSleepMode: Boolean = false
        private set

    fun onDestroy() {
        numberOverlay.hide()
        gridOverlay.hide()
    }

    fun isSleeping(): Boolean = isSleepMode

    fun hideAllOverlays() {
        numberOverlay.hide()
        gridOverlay.hide()
    }

    fun refreshOverlaysIfActive() {
        val cfg = KobraConfig(service)
        if (numberOverlay.isShowing() && cfg.isVisualNumbersEnabled) {
            val nodes = NodeIndexer.indexScreen(service)
            lastIndexedNodes = nodes
            numberOverlay.show(nodes)
        }
    }

    fun toggleNumberBadges(sayCallback: Consumer<String>) {
        if (numberOverlay.isShowing()) {
            numberOverlay.hide()
            sayCallback.accept("Nomor layar disembunyikan.")
            return
        }
        val nodes = NodeIndexer.indexScreen(service)
        lastIndexedNodes = nodes
        if (nodes.isEmpty()) {
            sayCallback.accept("Tidak ada elemen bernomor di layar ini.")
            return
        }
        val cfg = KobraConfig(service)
        if (!cfg.isVisualNumbersEnabled) {
            cfg.setVisualNumbersEnabled(true)
        }
        numberOverlay.show(nodes)
        sayCallback.accept("Menampilkan ${nodes.size} nomor elemen layar.")
    }

    /**
     * Memproses perintah suara dengan prioritas tertinggi (#1 CommandRouter).
     * Mengembalikan true jika perintah sudah ditangani penuh oleh VoiceAccessEngine.
     */
    fun handleCommand(rawCommand: String, sayCallback: Consumer<String>): Boolean {
        val raw = rawCommand.trim()
        val c = raw.lowercase(Locale.ROOT)
        if (c.isEmpty()) return false

        // 1) Manajemen Tidur / Bangun
        val activeName = VoiceProfileManager.getActiveAssistantName(service)
        val activeWake = activeName.lowercase(Locale.ROOT)
        if (isSleepMode) {
            if (c.contains("bangun") || c.contains("nyala") || c.contains("mulai dengar") || c.contains("kobra") || (activeWake.isNotEmpty() && c.contains(activeWake))) {
                isSleepMode = false
                sayCallback.accept("$activeName aktif kembali dan siap menerima perintah.")
            }
            return true
        }

        if (c == "tidur" || c == "mode tidur" || c == "mode diam" || c == "jangan dengar") {
            isSleepMode = true
            hideAllOverlays()
            sayCallback.accept("Mode tidur aktif. Sebutkan bangun $activeWake jika ingin menyalakan suara lagi.")
            return true
        }

        // 2) Tampilan Nomor Layar & Grid 3x3
        val cfg = KobraConfig(service)
        if (c.contains("tampilkan nomor") || c.contains("munculkan nomor") || c.contains("lihat nomor")) {
            val nodes = NodeIndexer.indexScreen(service)
            lastIndexedNodes = nodes
            if (nodes.isEmpty()) {
                sayCallback.accept("Tidak ditemukan tombol interaktif untuk dinomori pada layar ini.")
                return true
            }
            if (cfg.isVisualNumbersEnabled) {
                numberOverlay.show(nodes)
            }
            sayCallback.accept("Menampilkan ${nodes.size} nomor layar. Sebutkan klik nomor untuk memilih.")
            return true
        }

        if (c.contains("sembunyikan nomor") || c.contains("tutup nomor") || c.contains("matikan nomor")) {
            numberOverlay.hide()
            sayCallback.accept("Nomor layar disembunyikan.")
            return true
        }

        if (c == "baca layar" || c == "bacakan layar" || c == "baca elemen" || c == "ada tombol apa") {
            val nodes = NodeIndexer.indexScreen(service)
            lastIndexedNodes = nodes
            if (cfg.isVisualNumbersEnabled && numberOverlay.isShowing()) {
                numberOverlay.show(nodes)
            }
            sayCallback.accept(NodeIndexer.buildAudioReadout(nodes))
            return true
        }

        if (c.contains("tampilkan grid") || c.contains("buka grid") || c.contains("mode grid")) {
            if (!cfg.isGridOverlayEnabled) {
                sayCallback.accept("Overlay grid sedang dinonaktifkan di Pengaturan.")
                return true
            }
            gridOverlay.showFullGrid()
            sayCallback.accept("Grid tiga kali tiga ditampilkan. Sebutkan ketuk nomor satu sampai sembilan, atau zoom nomor.")
            return true
        }

        if (c.contains("tutup grid") || c.contains("sembunyikan grid") || c.contains("matikan grid")) {
            gridOverlay.hide()
            sayCallback.accept("Grid ditutup.")
            return true
        }

        if (gridOverlay.isShowing()) {
            val zoomNum = extractGridNumber(c, listOf("zoom ", "perbesar ", "dekati "))
            if (zoomNum != null) {
                gridOverlay.zoomIntoCell(zoomNum)
                sayCallback.accept("Memperbesar kotak $zoomNum. Sebutkan ketuk nomor satu sampai sembilan.")
                return true
            }
            val tapGridNum = extractGridNumber(c, listOf("ketuk ", "klik ", "tekan ", "pilih ", "grid "))
                ?: parseSingleDigit1To9(c)
            if (tapGridNum != null) {
                val center = gridOverlay.getCellCenter(tapGridNum)
                if (center != null) {
                    gridOverlay.hide()
                    UnifiedAccessibilityEngine.clickAt(service, center.first, center.second)
                    sayCallback.accept("Mengetuk kotak grid $tapGridNum.")
                    return true
                }
            }
        }

        // 2B) Ketukan berbasis posisi layar. Untuk titik kecil gunakan grid + zoom bertingkat.
        val positionCell = resolvePositionCell(c)
        if (positionCell != null) {
            val dm = service.resources.displayMetrics
            val col = (positionCell - 1) % 3
            val row = (positionCell - 1) / 3
            val x = dm.widthPixels * (col + 0.5f) / 3f
            val y = dm.heightPixels * (row + 0.5f) / 3f
            numberOverlay.hide()
            gridOverlay.hide()
            UnifiedAccessibilityEngine.clickAt(service, x, y)
            sayCallback.accept("Mengetuk area " + positionLabel(positionCell) + ". Jika belum tepat, katakan tampilkan grid lalu zoom nomor untuk memperkecil area.")
            return true
        }
        // 3) Klik / Tekan Lama berdasarkan Nomor Elemen
        val longClickNum = extractElementNumber(c, listOf("tekan lama nomor ", "tahan nomor ", "tahan ", "tekan lama "))
        if (longClickNum != null) {
            val nodes = currentOrRefreshNodes()
            val target = NodeIndexer.findByNumber(nodes, longClickNum)
            if (target != null) {
                numberOverlay.hide()
                UnifiedAccessibilityEngine.longClickAt(service, target.centerX.toFloat(), target.centerY.toFloat())
                sayCallback.accept("Menahan ${target.label}.")
                return true
            }
            sayCallback.accept("Nomor $longClickNum tidak ditemukan.")
            return true
        }

        val clickNum = extractElementNumber(c, listOf("klik nomor ", "tekan nomor ", "pilih nomor ", "klik ", "tekan ", "pilih "))
            ?: if (numberOverlay.isShowing()) parsePositiveInt(c) else null
        if (clickNum != null) {
            val nodes = currentOrRefreshNodes()
            val target = NodeIndexer.findByNumber(nodes, clickNum)
            if (target != null) {
                numberOverlay.hide()
                UnifiedAccessibilityEngine.clickAt(service, target.centerX.toFloat(), target.centerY.toFloat())
                sayCallback.accept("Membuka ${target.label}.")
                return true
            }
            sayCallback.accept("Nomor $clickNum tidak ada pada layar.")
            return true
        }

        // 4) Klik berdasarkan Nama/Label
        if (c.startsWith("klik ") || c.startsWith("tekan ") || c.startsWith("pencet ")) {
            val label = raw.replaceFirst(Regex("^(klik|tekan|pencet)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (label.isNotEmpty() && !label.startsWith("layar")) {
                if (UnifiedAccessibilityEngine.clickByNameGlobal(service, label)) {
                    numberOverlay.hide()
                    sayCallback.accept("Mengetuk $label.")
                    return true
                }
                val indexed = NodeIndexer.findByLabel(NodeIndexer.indexScreen(service), label)
                if (indexed != null) {
                    numberOverlay.hide()
                    UnifiedAccessibilityEngine.clickAt(service, indexed.centerX.toFloat(), indexed.centerY.toFloat())
                    sayCallback.accept("Mengetuk ${indexed.label}.")
                    return true
                }
            }
        }

        // 5) Navigasi Sistem & Gulir/Geser
        when {
            c == "kembali" || c == "balik" || c == "back" -> {
                hideAllOverlays()
                UnifiedAccessibilityEngine.performGlobalNavAction(service, AccessibilityService.GLOBAL_ACTION_BACK)
                sayCallback.accept("Kembali.")
                return true
            }
            c == "beranda" || c == "home" || c == "ke layar utama" -> {
                hideAllOverlays()
                UnifiedAccessibilityEngine.performGlobalNavAction(service, AccessibilityService.GLOBAL_ACTION_HOME)
                sayCallback.accept("Beranda.")
                return true
            }
            c == "aplikasi terbaru" || c == "recent apps" || c == "riwayat aplikasi" -> {
                hideAllOverlays()
                UnifiedAccessibilityEngine.performGlobalNavAction(service, AccessibilityService.GLOBAL_ACTION_RECENTS)
                sayCallback.accept("Menampilkan aplikasi terbaru.")
                return true
            }
            c == "buka panel notifikasi" || c == "tarik notifikasi" -> {
                UnifiedAccessibilityEngine.performGlobalNavAction(service, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                sayCallback.accept("Panel notifikasi dibuka.")
                return true
            }
            c == "pengaturan cepat" || c == "quick settings" -> {
                UnifiedAccessibilityEngine.performGlobalNavAction(service, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                sayCallback.accept("Pengaturan cepat dibuka.")
                return true
            }
            c == "gulir bawah" || c == "scroll bawah" || c == "gulir ke bawah" || c == "geser ke bawah" -> {
                UnifiedAccessibilityEngine.scrollGlobal(service, forward = true)
                refreshOverlaysIfActive()
                sayCallback.accept("Menggulir ke bawah.")
                return true
            }
            c == "gulir atas" || c == "scroll atas" || c == "gulir ke atas" || c == "geser ke atas" -> {
                UnifiedAccessibilityEngine.scrollGlobal(service, forward = false)
                refreshOverlaysIfActive()
                sayCallback.accept("Menggulir ke atas.")
                return true
            }
            c == "geser kiri" || c == "swipe kiri" -> {
                val dm = service.resources.displayMetrics
                UnifiedAccessibilityEngine.swipe(service, dm.widthPixels * 0.8f, dm.heightPixels * 0.5f, dm.widthPixels * 0.2f, dm.heightPixels * 0.5f)
                refreshOverlaysIfActive()
                sayCallback.accept("Menggeser ke kiri.")
                return true
            }
            c == "geser kanan" || c == "swipe kanan" -> {
                val dm = service.resources.displayMetrics
                UnifiedAccessibilityEngine.swipe(service, dm.widthPixels * 0.2f, dm.heightPixels * 0.5f, dm.widthPixels * 0.8f, dm.heightPixels * 0.5f)
                refreshOverlaysIfActive()
                sayCallback.accept("Menggeser ke kanan.")
                return true
            }
        }

        // 6) Pengeditan Teks Lengkap (ketik, ganti, hapus kata/semua, pilih semua, salin, potong, tempel, enter)
        if (c.startsWith("ketik ") || c.startsWith("tulis ")) {
            val content = raw.replaceFirst(Regex("^(ketik|tulis)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (content.isNotEmpty()) {
                val ok = appendOrSetText(content)
                sayCallback.accept(if (ok) "Mengetik $content." else "Tidak ada kolom teks aktif untuk mengetik.")
                return true
            }
        }
        if (c.startsWith("ganti dengan ") || c.startsWith("ganti menjadi ")) {
            val content = raw.replaceFirst(Regex("^ganti\\s+(dengan|menjadi)\\s+", RegexOption.IGNORE_CASE), "").trim()
            val ok = UnifiedAccessibilityEngine.typeGlobalText(service, content)
            sayCallback.accept(if (ok) "Teks diganti." else "Kolom teks tidak ditemukan.")
            return true
        }
        if (c == "hapus kata" || c == "hapus satu kata") {
            val ok = deleteLastWord()
            sayCallback.accept(if (ok) "Satu kata terakhir dihapus." else "Tidak ada teks untuk dihapus.")
            return true
        }
        if (c == "hapus semua" || c == "bersihkan teks" || c == "kosongkan kolom") {
            val ok = UnifiedAccessibilityEngine.typeGlobalText(service, "")
            sayCallback.accept(if (ok) "Seluruh teks dikosongkan." else "Kolom teks tidak ditemukan.")
            return true
        }
        if (c == "pilih semua" || c == "blok semua") {
            val ok = performSelectAll()
            sayCallback.accept(if (ok) "Semua teks dipilih." else "Gagal memilih semua teks.")
            return true
        }
        if (c == "salin teks" || c == "salin" || c == "copy") {
            val ok = performFocusNodeAction(AccessibilityNodeInfo.ACTION_COPY)
            sayCallback.accept(if (ok) "Teks disalin ke papan klip." else "Tidak ada teks terpilih untuk disalin.")
            return true
        }
        if (c == "potong teks" || c == "potong" || c == "cut") {
            val ok = performFocusNodeAction(AccessibilityNodeInfo.ACTION_CUT)
            sayCallback.accept(if (ok) "Teks dipotong." else "Gagal memotong teks.")
            return true
        }
        if (c == "tempel teks" || c == "tempel" || c == "paste") {
            val ok = performFocusNodeAction(AccessibilityNodeInfo.ACTION_PASTE)
            sayCallback.accept(if (ok) "Teks ditempelkan." else "Gagal menempelkan teks.")
            return true
        }
        if (c == "tekan enter" || c == "kirim ketikan") {
            val ok = UnifiedAccessibilityEngine.performImeEnter(service)
            sayCallback.accept(if (ok) "Tombol kirim ditekan." else "Tombol kirim papan ketik tidak tersedia.")
            return true
        }

        // 7) Kontrol Volume, Media & Telepon
        if (c.contains("volume naik") || c.contains("besarkan suara") || c.contains("keraskan suara")) {
            adjustVolume(AudioManager.ADJUST_RAISE)
            sayCallback.accept("Volume dinaikkan.")
            return true
        }
        if (c.contains("volume turun") || c.contains("kecilkan suara") || c.contains("pelankan suara")) {
            adjustVolume(AudioManager.ADJUST_LOWER)
            sayCallback.accept("Volume diturunkan.")
            return true
        }
        if (c == "heningkan" || c == "mute suara" || c == "bisukan volume") {
            adjustVolume(AudioManager.ADJUST_MUTE)
            sayCallback.accept("Suara media diheningkan.")
            return true
        }
        if (c == "jawab telepon" || c == "angkat telepon") {
            val ok = answerCall()
            sayCallback.accept(if (ok) "Panggilan dijawab." else "Gagal menjawab panggilan atau izin telepon belum aktif.")
            return true
        }
        if (c == "tutup telepon" || c == "akhiri telepon" || c == "tolak telepon") {
            val ok = endCall()
            sayCallback.accept(if (ok) "Panggilan ditutup." else "Tidak ada panggilan aktif.")
            return true
        }
        if (c == "jeda media" || c == "pause media" || c == "jeda musik") {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            sayCallback.accept("Media dijeda.")
            return true
        }
        if (c == "lanjut media" || c == "putar media" || c == "lanjut musik") {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            sayCallback.accept("Media diputar kembali.")
            return true
        }
        if (c == "lagu berikutnya" || c == "next lagu") {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            sayCallback.accept("Melompat ke media berikutnya.")
            return true
        }

        return false
    }

    private fun currentOrRefreshNodes(): List<NodeIndexer.IndexedNode> {
        if (lastIndexedNodes.isNotEmpty()) return lastIndexedNodes
        return NodeIndexer.indexScreen(service).also { lastIndexedNodes = it }
    }

    private fun appendOrSetText(addition: String): Boolean {
        val root = try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return false

        return try {
            val field = findFocusedOrFirstEditable(root) ?: return false
            val existing = field.text?.toString().orEmpty()
            val combined = if (existing.isEmpty()) addition else "$existing $addition"
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
            }
            if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                true
            } else {
                val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Kobra OS", combined))
                field.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
        } finally {
            safeRecycle(root)
        }
    }

    private fun deleteLastWord(): Boolean {
        val root = try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return false

        return try {
            val field = findFocusedOrFirstEditable(root) ?: return false
            val existing = field.text?.toString()?.trimEnd().orEmpty()
            if (existing.isEmpty()) return false
            val lastSpace = existing.lastIndexOf(' ')
            val updated = if (lastSpace > 0) existing.substring(0, lastSpace) else ""
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
            }
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            safeRecycle(root)
        }
    }

    private fun performSelectAll(): Boolean {
        val root = try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return false

        return try {
            val field = findFocusedOrFirstEditable(root) ?: return false
            val len = field.text?.length ?: 0
            val args = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
            }
            field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
        } finally {
            safeRecycle(root)
        }
    }

    private fun performFocusNodeAction(action: Int): Boolean {
        val root = try {
            service.rootInActiveWindow
        } catch (_: Throwable) {
            null
        } ?: return false

        return try {
            val field = findFocusedOrFirstEditable(root) ?: return false
            field.performAction(action)
        } finally {
            safeRecycle(root)
        }
    }

    private fun findFocusedOrFirstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused
        safeRecycle(focused)
        return findFirstEditableRecursive(root)
    }

    private fun findFirstEditableRecursive(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val found = findFirstEditableRecursive(child)
                if (found != null) return found
            } finally {
                safeRecycle(child)
            }
        }
        return null
    }

    private fun adjustVolume(direction: Int) {
        try {
            val am = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (_: Throwable) {
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        try {
            val am = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Throwable) {
        }
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun answerCall(): Boolean {
        return try {
            val tm = service.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
            tm.acceptRingingCall()
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun endCall(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val tm = service.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
                tm.endCall()
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolvePositionCell(command: String): Int? {
        val c = command.lowercase(Locale.ROOT).trim().replace(" ", "")
            .replace("ketuk", "").replace("klik", "").replace("tekan", "")
            .replace("posisi", "").replace("area", "")
        return when (c) {
            "kiriatas", "pojokkiriatas", "ataskiri" -> 1
            "atas", "tengahatas", "tengahataslayar" -> 2
            "kananatas", "pojokkananatas", "ataskanan" -> 3
            "kiri", "tengahkiri", "kiribagian" -> 4
            "tengah", "tengahlayar", "pusat" -> 5
            "kanan", "tengahkanan", "kananbagian" -> 6
            "kiribawah", "pojokkiribawah", "bawahkiri" -> 7
            "bawah", "tengahbawah" -> 8
            "kananbawah", "pojokkananbawah", "bawahkanan" -> 9
            else -> null
        }
    }

    private fun positionLabel(cell: Int): String = when (cell) {
        1 -> "kiri atas"; 2 -> "atas tengah"; 3 -> "kanan atas"
        4 -> "kiri tengah"; 5 -> "tengah"; 6 -> "kanan tengah"
        7 -> "kiri bawah"; 8 -> "bawah tengah"; else -> "kanan bawah"
    }
    private fun extractElementNumber(cmd: String, prefixes: List<String>): Int? {
        for (p in prefixes) {
            if (cmd.startsWith(p)) {
                val tail = cmd.removePrefix(p).trim()
                parsePositiveInt(tail)?.let { return it }
            }
        }
        return null
    }

    private fun extractGridNumber(cmd: String, prefixes: List<String>): Int? {
        for (p in prefixes) {
            if (cmd.startsWith(p)) {
                val tail = cmd.removePrefix(p).trim()
                parseSingleDigit1To9(tail)?.let { return it }
            }
        }
        return null
    }

    private fun parseSingleDigit1To9(raw: String): Int? {
        val n = parsePositiveInt(raw) ?: return null
        return if (n in 1..9) n else null
    }

    private fun parsePositiveInt(raw: String): Int? {
        val clean = raw.lowercase(Locale.ROOT).trim()
        clean.toIntOrNull()?.let { if (it > 0) return it }
        return when (clean) {
            "satu" -> 1
            "dua" -> 2
            "tiga" -> 3
            "empat" -> 4
            "lima" -> 5
            "enam" -> 6
            "tujuh" -> 7
            "delapan" -> 8
            "sembilan" -> 9
            "sepuluh" -> 10
            else -> null
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
