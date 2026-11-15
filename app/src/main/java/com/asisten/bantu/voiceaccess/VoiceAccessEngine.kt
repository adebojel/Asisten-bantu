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
import com.asisten.bantu.OfflineBrain
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
    private val focusOutline = FocusOutlineOverlay(service)

    @Volatile
    private var lastIndexedNodes: List<NodeIndexer.IndexedNode> = emptyList()

    @Volatile
    var isSleepMode: Boolean = false
        private set

    fun onDestroy() {
        numberOverlay.hide()
        gridOverlay.hide()
        focusOutline.hide()
    }

    fun refreshFocusOutline() {
        if (!KobraConfig(service).isFocusOutlineEnabled()) return
        val bounds = NodeIndexer.focusedBounds(service)
        if (bounds != null) focusOutline.show(bounds)
    }

    /** Focus indicator for every app; its transparent overlay never intercepts touch. */
    fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        if (event == null) return
        val cfg = KobraConfig(service)
        when (event.eventType) {
            android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> focusOutline.hide()
            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (!cfg.isFocusOutlineEnabled()) return
                val bounds = NodeIndexer.focusedBounds(service)
                if (bounds != null) focusOutline.show(bounds)
            }
            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> focusOutline.hide()
        }
    }

    private fun focusBoundary(first: Boolean, sayCallback: Consumer<String>) {
        val nodes = NodeIndexer.indexScreen(service)
        if (nodes.isEmpty()) {
            sayCallback.accept("Tidak ada target fokus yang bisa dijelajahi pada layar ini.")
            return
        }
        val target = if (first) nodes.first() else nodes.last()
        if (NodeIndexer.focusAt(service, target.centerX, target.centerY)) {
            lastIndexedNodes = nodes
            sayCallback.accept(target.label)
        } else {
            sayCallback.accept("Android tidak memberikan fokus aksesibilitas pada " + target.label + ".")
        }
    }

    private fun moveAccessibilityFocus(forward: Boolean, sayCallback: Consumer<String>) {
        val nodes = NodeIndexer.indexScreen(service)
        if (nodes.isEmpty()) {
            sayCallback.accept("Tidak ada target fokus yang bisa dijelajahi pada layar ini.")
            return
        }
        val current = NodeIndexer.focusedBounds(service)
        val currentIndex = if (current == null) -1 else nodes.indexOfFirst {
            it.bounds.contains(current.centerX(), current.centerY()) || current.contains(it.centerX, it.centerY)
        }
        val nextIndex = when {
            currentIndex < 0 && forward -> 0
            currentIndex < 0 -> nodes.lastIndex
            forward -> (currentIndex + 1).coerceAtMost(nodes.lastIndex)
            else -> (currentIndex - 1).coerceAtLeast(0)
        }
        val target = nodes[nextIndex]
        if (NodeIndexer.focusAt(service, target.centerX, target.centerY)) {
            lastIndexedNodes = nodes
            sayCallback.accept(target.label)
        } else {
            sayCallback.accept("Android tidak memberikan fokus aksesibilitas pada " + target.label + ".")
        }
    }

    fun isSleeping(): Boolean = isSleepMode

    fun hideAllOverlays() {
        numberOverlay.hide()
        gridOverlay.hide()
        focusOutline.hide()
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

        if (c == "tampilkan garis fokus" || c == "aktifkan garis fokus" || c == "tampilkan bingkai fokus" || c == "bingkai fokus") {
            cfg.setFocusOutlineEnabled(true)
            val bounds = NodeIndexer.focusedBounds(service)
            if (bounds != null) focusOutline.show(bounds)
            sayCallback.accept("Garis fokus aksesibilitas diaktifkan. Bagian tengah tetap transparan dan lapisan tidak menghalangi sentuhan.")
            return true
        }
        if (c == "sembunyikan garis fokus" || c == "matikan garis fokus" || c == "nonaktifkan garis fokus") {
            cfg.setFocusOutlineEnabled(false)
            focusOutline.hide()
            sayCallback.accept("Garis fokus disembunyikan.")
            return true
        }

        if (c == "fokus pertama" || c == "item pertama" || c == "mulai dari awal") {
            focusBoundary(true, sayCallback)
            return true
        }
        if (c == "fokus terakhir" || c == "item terakhir" || c == "ke bagian akhir") {
            focusBoundary(false, sayCallback)
            return true
        }
        if (c == "baca fokus" || c == "apa yang dipilih" || c == "bacakan fokus") {
            val label = NodeIndexer.focusedLabel(service)
            sayCallback.accept(if (label.isNotEmpty()) label else "Belum ada elemen yang memiliki fokus aksesibilitas.")
            return true
        }

        if (c == "item berikutnya" || c == "elemen berikutnya" || c == "fokus berikutnya" || c == "baca item berikutnya") {
            moveAccessibilityFocus(true, sayCallback)
            return true
        }
        if (c == "item sebelumnya" || c == "elemen sebelumnya" || c == "fokus sebelumnya" || c == "baca item sebelumnya") {
            moveAccessibilityFocus(false, sayCallback)
            return true
        }
        if (c == "baca dari atas" || c == "mulai baca layar") {
            val text = if (service is com.asisten.bantu.AsistenAccessibilityService) service.readScreenText()
                else NodeIndexer.buildAudioReadout(NodeIndexer.indexScreen(service))
            sayCallback.accept(text)
            return true
        }

        if (c.contains("tampilkan nomor") || c.contains("munculkan nomor") || c.contains("lihat nomor") || c.contains("tampilkan label") || c.contains("show numbers") || c.contains("show labels")) {
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

        if (c.contains("sembunyikan nomor") || c.contains("tutup nomor") || c.contains("matikan nomor") || c.contains("sembunyikan label") || c.contains("hide numbers") || c.contains("hide labels")) {
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
            // The service applies app-specific privacy rules (for example, names only
            // in the WhatsApp conversation list) before returning visible screen text.
            val screenText = if (service is com.asisten.bantu.AsistenAccessibilityService) {
                service.readScreenText()
            } else {
                NodeIndexer.buildAudioReadout(nodes)
            }
            val readout = if (screenText.contains("tidak punya teks yang bisa dibaca", ignoreCase = true)
                && nodes.isNotEmpty()) {
                NodeIndexer.buildAudioReadout(nodes)
            } else {
                val targetHint = if (nodes.isNotEmpty()) {
                    " Ada ${nodes.size} target suara pada layar. Ucapkan tampilkan nomor untuk memilih elemen tertentu."
                } else {
                    " Tidak ada target yang dapat diindeks dari layar ini."
                }
                screenText + targetHint
            }
            sayCallback.accept(readout)
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
                    val accepted = UnifiedAccessibilityEngine.clickAt(service, center.first, center.second)
                    sayCallback.accept(if (accepted) "Ketukan untuk kotak grid $tapGridNum dikirim ke sistem." else "Sistem menolak ketukan pada kotak grid $tapGridNum.")
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
            val accepted = UnifiedAccessibilityEngine.clickAt(service, x, y)
            sayCallback.accept(if (accepted) "Ketukan pada area " + positionLabel(positionCell) + " dikirim ke sistem. Untuk titik kecil, tampilkan grid lalu zoom nomor."
                else "Sistem menolak ketukan pada area " + positionLabel(positionCell) + ".")
            return true
        }
        // Drag antar elemen bernomor: "seret nomor 3 ke nomor 8".
        if (c.startsWith("seret nomor ") || c.startsWith("drag nomor ")) {
            val match = Regex("^(?:seret|drag) nomor (.+?) ke nomor (.+)$").find(c)
            val fromNumber = match?.groupValues?.getOrNull(1)?.let { parsePositiveInt(it) }
            val toNumber = match?.groupValues?.getOrNull(2)?.let { parsePositiveInt(it) }
            val nodes = currentOrRefreshNodes()
            val from = fromNumber?.let { NodeIndexer.findByNumber(nodes, it) }
            val to = toNumber?.let { NodeIndexer.findByNumber(nodes, it) }
            if (from != null && to != null) {
                val accepted = UnifiedAccessibilityEngine.swipe(
                    service, from.centerX.toFloat(), from.centerY.toFloat(),
                    to.centerX.toFloat(), to.centerY.toFloat(), 850L
                )
                sayCallback.accept(if (accepted) "Gestur seret dari " + from.label + " ke " + to.label + " dikirim ke Android." else "Android menolak gestur seret tersebut.")
            } else {
                sayCallback.accept("Titik awal atau tujuan tidak ditemukan. Ucapkan tampilkan nomor lalu ulangi.")
            }
            return true
        }

        // 3) Klik / Tekan Lama berdasarkan Nomor Elemen
        val longClickNum = extractElementNumber(c, listOf("tekan lama nomor ", "tahan nomor ", "tahan ", "tekan lama "))
        if (longClickNum != null) {
            val nodes = currentOrRefreshNodes()
            val target = NodeIndexer.findByNumber(nodes, longClickNum)
            if (target != null) {
                numberOverlay.hide()
                val accepted = UnifiedAccessibilityEngine.longClickAt(service, target.centerX.toFloat(), target.centerY.toFloat())
                sayCallback.accept(if (accepted) "Perintah tekan lama untuk ${target.label} dikirim ke sistem."
                    else "Sistem menolak perintah tekan lama untuk ${target.label}.")
                return true
            }
            sayCallback.accept("Nomor $longClickNum tidak ditemukan.")
            return true
        }

        val clickNum = extractElementNumber(c, listOf("klik nomor ", "tekan nomor ", "ketuk nomor ", "pilih nomor ", "tap ", "klik ", "tekan ", "ketuk ", "pilih "))
            ?: if (numberOverlay.isShowing()) parsePositiveInt(c) else null
        if (clickNum != null) {
            val nodes = currentOrRefreshNodes()
            val target = NodeIndexer.findByNumber(nodes, clickNum)
            if (target != null) {
                numberOverlay.hide()
                val clicked = NodeIndexer.performClickAt(service, target.centerX, target.centerY)
                val accepted = clicked || UnifiedAccessibilityEngine.clickAt(service, target.centerX.toFloat(), target.centerY.toFloat())
                sayCallback.accept(
                    if (clicked) "Elemen ${target.label} menerima aksi aksesibilitas."
                    else if (accepted) "Ketukan pada ${target.label} dikirim ke Android. Perubahan layar belum diverifikasi."
                    else "Sistem menolak klik pada ${target.label}. Layar mungkin berubah; sebutkan baca layar lalu coba lagi."
                )
                return true
            }
            sayCallback.accept("Nomor $clickNum tidak ada pada layar.")
            return true
        }

        // 4) Klik berdasarkan label layar: tombol, teks, judul, item daftar,
        // dan area visual yang tidak diekspos sebagai tombol semantik.
        if (c.startsWith("klik ") || c.startsWith("tekan ") || c.startsWith("ketuk ") || c.startsWith("pencet ") || c.startsWith("pilih ")) {
            val label = raw.replaceFirst(Regex("^(klik|tekan|ketuk|pencet|pilih)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (label.isNotEmpty() && !label.startsWith("layar")) {
                val freshNodes = NodeIndexer.indexScreen(service)
                lastIndexedNodes = freshNodes
                val indexed = NodeIndexer.findByLabel(freshNodes, label)
                if (indexed != null) {
                    numberOverlay.hide()
                    val semanticClick = NodeIndexer.performClickAt(service, indexed.centerX, indexed.centerY)
                    val gestureAccepted = semanticClick || UnifiedAccessibilityEngine.clickAt(
                        service, indexed.centerX.toFloat(), indexed.centerY.toFloat()
                    )
                    sayCallback.accept(
                        if (semanticClick) "Elemen ${indexed.label} menerima aksi aksesibilitas."
                        else if (gestureAccepted) "Ketukan pada area ${indexed.label} dikirim ke Android. Hasil perubahan layar belum diverifikasi."
                        else "Android menolak ketukan pada ${indexed.label}. Layar mungkin berubah; ucapkan baca layar untuk memperbarui target."
                    )
                    return true
                }
                // Tidak memilih kecocokan parsial pertama jika ada beberapa nama mirip.
                if (freshNodes.any { it.label.contains(label, ignoreCase = true) }) {
                    sayCallback.accept("Ada beberapa elemen yang mirip dengan $label. Ucapkan baca layar, lalu pilih nomor yang tepat.")
                } else {
                    sayCallback.accept("Saya belum menemukan elemen bernama $label pada pohon aksesibilitas layar ini. Coba tampilkan nomor atau grid layar.")
                }
                return true
            }
        }

        if (c == "zoom in" || c == "perbesar" || c == "perbesar layar" || c == "zoom masuk") {
            val accepted = UnifiedAccessibilityEngine.pinch(service, true)
            sayCallback.accept(if (accepted) "Gestur perbesar dikirim." else "Android menolak gestur perbesar.")
            return true
        }
        if (c == "zoom out" || c == "perkecil" || c == "perkecil layar" || c == "zoom keluar") {
            val accepted = UnifiedAccessibilityEngine.pinch(service, false)
            sayCallback.accept(if (accepted) "Gestur perkecil dikirim." else "Android menolak gestur perkecil.")
            return true
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
            c == "menu aksesibilitas" || c == "buka menu aksesibilitas" || c == "tombol aksesibilitas" -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val opened = UnifiedAccessibilityEngine.performGlobalNavAction(
                        service, AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_BUTTON
                    )
                    sayCallback.accept(if (opened) "Aksi tombol aksesibilitas dikirim ke Android." else "Tombol aksesibilitas belum tersedia atau belum dikonfigurasi di Android.")
                } else {
                    sayCallback.accept("Tombol aksesibilitas memerlukan Android 9 atau lebih baru.")
                }
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
        // Re-index on every selection: the app, dialog, or list may have changed
        // since the last spoken readout. Never click using stale screen coordinates.
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
        // Reuse the shared Indonesian number parser so numbered targets above ten
        // work with natural speech ("nomor tiga puluh dua"), not only digits.
        return OfflineBrain.firstNumber(clean)?.takeIf { it > 0 }
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
