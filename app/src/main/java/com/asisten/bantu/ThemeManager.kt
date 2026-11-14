package com.asisten.bantu

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Mesin Tema Terpusat Kobra OS:
 * - 8 Preset Tema Bawaan (termasuk Kobra Cyber, Kontras Tinggi Tunanetra, Daylight Glass, OLED, dll.)
 * - Editor Tema Kustom Lokal (4 Hex warna dengan Auto-Contrast Guard otomatis)
 * - Generator Tema Berbasis AI (OnlineAiRouter 3-Lapis + fallback parser warna lokal cerdas)
 * - Penerapan real-time ke seluruh Activity dan seluruh tombol dengan kontras teks >= 4.5:1
 */
object ThemeManager {

    const val PRESET_KOBRA_CYBER = "kobra_cyber"
    const val PRESET_HIGH_CONTRAST = "high_contrast"
    const val PRESET_ZAMRUD = "zamrud_nusantara"
    const val PRESET_EMAS = "emas_kerajaan"
    const val PRESET_AMETHYST = "amethyst_malam"
    const val PRESET_MERAH = "merah_bara"
    const val PRESET_DAYLIGHT = "daylight_glass"
    const val PRESET_OLED = "oled_hemat"
    const val PRESET_CUSTOM = "custom"

    const val TAG_IGNORE = "theme_preview_card"
    const val TAG_BAR = "theme_bar"
    const val TAG_BACKDROP = "theme_backdrop"
    const val TAG_CARD = "theme_card"
    const val TAG_BTN_PRIMARY = "theme_btn_primary"
    const val TAG_BTN_SECONDARY = "theme_btn_secondary"
    const val TAG_BTN_DANGER = "theme_btn_danger"
    const val TAG_TAB = "theme_tab"
    const val TAG_CHIP = "theme_chip"
    const val TAG_PILL = "theme_pill"

    private const val ROLE_BACKDROP = "role_backdrop"
    private const val ROLE_BAR = "role_bar"
    private const val ROLE_CARD = "role_card"
    private const val ROLE_CARD_ACCENT = "role_card_accent"
    private const val ROLE_CARD_HIGHLIGHT = "role_card_highlight"
    private const val ROLE_PILL = "role_pill"
    private const val ROLE_BADGE = "role_badge"
    private const val ROLE_BTN_PRIMARY = "role_btn_primary"
    private const val ROLE_BTN_SECONDARY = "role_btn_secondary"
    const val ROLE_BTN_DANGER = "role_btn_danger"
    private const val ROLE_TAB = "role_tab"
    private const val ROLE_CHIP = "role_chip"
    private const val ROLE_INPUT = "role_input"
    private const val ROLE_CAMERA_ROOT = "role_camera_root"
    private const val ROLE_PLAIN = "role_plain"

    data class KobraTheme(
        val id: String,
        val name: String,
        val subtitle: String,
        val bgHex: String,
        val cardHex: String,
        val accentHex: String,
        val textHex: String,
        val buttonTextHex: String,
        val borderWidthDp: Float = 1.2f,
        val borderHex: String = "",
        val isHighContrast: Boolean = false
    ) {
        val bgColor: Int get() = parseColorSafe(bgHex, 0xFF06101C.toInt())
        val cardColor: Int get() = parseColorSafe(cardHex, 0xFF0F2138.toInt())
        val accentColor: Int get() = parseColorSafe(accentHex, 0xFF00E5FF.toInt())
        val textColor: Int get() = parseColorSafe(textHex, 0xFFE2E8F0.toInt())
        val buttonTextColor: Int get() = parseColorSafe(buttonTextHex, 0xFF06101C.toInt())
        val effectiveBorderColor: Int
            get() = if (borderHex.isNotEmpty()) parseColorSafe(borderHex, accentColor) else accentColor
    }

    data class PaletteColor(
        val name: String,
        val hex: String
    )

    interface AiThemeCallback {
        fun onThemeGenerated(theme: KobraTheme, sourceLabel: String)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    val builtInThemes: List<KobraTheme> = listOf(
        KobraTheme(
            id = PRESET_KOBRA_CYBER,
            name = "Kobra Cyber",
            subtitle = "Default futuristik cyan",
            bgHex = "#06101C",
            cardHex = "#0F2138",
            accentHex = "#00E5FF",
            textHex = "#E2E8F0",
            buttonTextHex = "#06101C",
            borderWidthDp = 1.2f
        ),
        KobraTheme(
            id = PRESET_HIGH_CONTRAST,
            name = "Kontras Tinggi",
            subtitle = "Ramah low vision maksimal",
            bgHex = "#000000",
            cardHex = "#121212",
            accentHex = "#FFD600",
            textHex = "#FFFFFF",
            buttonTextHex = "#000000",
            borderWidthDp = 2.5f,
            borderHex = "#FFFFFF",
            isHighContrast = true
        ),
        KobraTheme(
            id = PRESET_ZAMRUD,
            name = "Zamrud Nusantara",
            subtitle = "Hijau zamrud tenang",
            bgHex = "#041712",
            cardHex = "#0A2921",
            accentHex = "#00E676",
            textHex = "#E6F7F2",
            buttonTextHex = "#041712",
            borderWidthDp = 1.3f
        ),
        KobraTheme(
            id = PRESET_EMAS,
            name = "Emas Kerajaan",
            subtitle = "Arang mewah aksen emas",
            bgHex = "#120F0A",
            cardHex = "#211B12",
            accentHex = "#FFB300",
            textHex = "#FFF8E7",
            buttonTextHex = "#120F0A",
            borderWidthDp = 1.3f
        ),
        KobraTheme(
            id = PRESET_AMETHYST,
            name = "Amethyst Malam",
            subtitle = "Ungu malam neon elegan",
            bgHex = "#11091F",
            cardHex = "#1E1136",
            accentHex = "#D500F9",
            textHex = "#F3E8FF",
            buttonTextHex = "#FFFFFF",
            borderWidthDp = 1.3f
        ),
        KobraTheme(
            id = PRESET_MERAH,
            name = "Merah Bara",
            subtitle = "Marun tegas aksen koral",
            bgHex = "#1A0709",
            cardHex = "#2E0D11",
            accentHex = "#FF5252",
            textHex = "#FFE8EA",
            buttonTextHex = "#FFFFFF",
            borderWidthDp = 1.3f
        ),
        KobraTheme(
            id = PRESET_DAYLIGHT,
            name = "Mode Terang Siang",
            subtitle = "Daylight Glass bersih",
            bgHex = "#F2F5F9",
            cardHex = "#FFFFFF",
            accentHex = "#0066CC",
            textHex = "#0D1B2A",
            buttonTextHex = "#FFFFFF",
            borderWidthDp = 1.3f
        ),
        KobraTheme(
            id = PRESET_OLED,
            name = "OLED Hemat Baterai",
            subtitle = "Hitam penuh & biru es",
            bgHex = "#000000",
            cardHex = "#0C121D",
            accentHex = "#40C4FF",
            textHex = "#E2E8F0",
            buttonTextHex = "#000000",
            borderWidthDp = 1.2f
        )
    )

    @JvmStatic
    val paletteChoices: List<PaletteColor> = listOf(
        PaletteColor("Hitam Pekat OLED", "#000000"),
        PaletteColor("Biru Malam Cyber", "#06101C"),
        PaletteColor("Biru Panel Kaca", "#0F2138"),
        PaletteColor("Hijau Hutan Gelap", "#041712"),
        PaletteColor("Arang Hangat", "#120F0A"),
        PaletteColor("Ungu Malam", "#11091F"),
        PaletteColor("Merah Marun Gelap", "#1A0709"),
        PaletteColor("Putih Daylight", "#F2F5F9"),
        PaletteColor("Putih Bersih", "#FFFFFF"),
        PaletteColor("Teks Gelap Navy", "#0D1B2A"),
        PaletteColor("Teks Terang Slate", "#E2E8F0"),
        PaletteColor("Cyan Neon", "#00E5FF"),
        PaletteColor("Biru Es", "#40C4FF"),
        PaletteColor("Biru Tua Klasik", "#0066CC"),
        PaletteColor("Kuning Kontras Tinggi", "#FFD600"),
        PaletteColor("Emas Hangat", "#FFB300"),
        PaletteColor("Hijau Zamrud Terang", "#00E676"),
        PaletteColor("Ungu Amethyst", "#D500F9"),
        PaletteColor("Merah Koral", "#FF5252"),
        PaletteColor("Jingga Senja", "#FF7043")
    )

    private val primaryButtonIds = setOf(
        R.id.btnVoiceGuide,
        R.id.btnStartNav,
        R.id.btnScanVision,
        R.id.btnSaveMed,
        R.id.btnSpeakPrayer,
        R.id.btnSaveSos,
        R.id.btnReturnHome,
        R.id.btnSettingsAccessibility,
        R.id.btnImportModel,
        R.id.btnApplyCustomVoice,
        R.id.btnApplyCustomTheme,
        R.id.btnGenerateAiTheme,
        R.id.btnSaveSettings,
        R.id.btnSetPin,
        R.id.btnCareAddMed,
        R.id.btnSpeakCompass,
        R.id.btnGuidePlace,
        R.id.btnPickDocument,
        R.id.btnDocPlayPause,
        R.id.btnSpeakAgenda,
        R.id.btnAddMemo,
        R.id.btnSaveItem,
        R.id.btnAddExpense,
        R.id.btnMarkMedTaken,
        R.id.btnChatMic,
        R.id.btnMic
    )

    private val dangerButtonIds = setOf(
        R.id.btnResetAiSafeMode,
        R.id.btnEmergencySosNav,
        R.id.btnResetVision,
        R.id.btnResetSafeModeChat,
        R.id.btnSosEmergency,
        R.id.btnDeleteModel,
        R.id.btnClearMemories,
        R.id.btnSettingsSos
    )

    private val tabButtonIds = setOf(
        R.id.tabMain,
        R.id.tabUtils,
        R.id.tabVoice
    )

    private val chipButtonIds = setOf(
        R.id.btnPersonaUtama,
        R.id.btnPersonaPembaca,
        R.id.btnPersonaTutor,
        R.id.btnPersonaKerja,
        R.id.btnPresetKobra,
        R.id.btnPresetArka,
        R.id.btnPresetBima,
        R.id.btnPresetNala,
        R.id.btnPresetCakra,
        R.id.btnPresetTara,
        R.id.btnPresetKustom
    )

    private val cardButtonIds = setOf(
        R.id.cmdNav1, R.id.cmdNav2, R.id.cmdNav3,
        R.id.cmdMedia1, R.id.cmdMedia2, R.id.cmdCam1, R.id.cmdDaily1, R.id.cmdDaily2,
        R.id.btnScanAllObjects, R.id.btnReadTextOcr, R.id.btnDetectMoneyColor, R.id.btnAskAiCamera,
        R.id.btnReadScreen, R.id.btnNavStick, R.id.btnAiChat
    )

    @JvmStatic
    fun getActiveTheme(context: Context): KobraTheme {
        val cfg = KobraConfig(context.applicationContext)
        val id = cfg.getThemeId()
        if (id == PRESET_CUSTOM) {
            return buildCustomTheme(
                name = cfg.getCustomThemeName(),
                bgHex = cfg.getCustomBgHex(),
                cardHex = cfg.getCustomCardHex(),
                accentHex = cfg.getCustomAccentHex(),
                textHex = cfg.getCustomTextHex()
            )
        }
        return builtInThemes.firstOrNull { it.id == id } ?: builtInThemes[0]
    }

    @JvmStatic
    fun buildCustomTheme(
        name: String,
        bgHex: String,
        cardHex: String,
        accentHex: String,
        textHex: String
    ): KobraTheme {
        val cleanBg = normalizeHex(bgHex, "#06101C")
        val cleanCard = normalizeHex(cardHex, "#0F2138")
        val cleanAccent = normalizeHex(accentHex, "#00E5FF")
        val rawTextColor = parseColorSafe(normalizeHex(textHex, "#E2E8F0"), 0xFFE2E8F0.toInt())
        val bgColor = parseColorSafe(cleanBg, 0xFF06101C.toInt())
        val cardColor = parseColorSafe(cleanCard, 0xFF0F2138.toInt())
        val accentColor = parseColorSafe(cleanAccent, 0xFF00E5FF.toInt())

        // Auto-Contrast Guard untuk teks utama & teks tombol
        val safeTextColor = ensureContrast(rawTextColor, bgColor, 5.0)
        val safeTextOnCard = ensureContrast(safeTextColor, cardColor, 5.0)
        val finalText = if (contrastRatio(safeTextOnCard, bgColor) >= 4.5) safeTextOnCard else computeContrastTextColor(bgColor)
        val btnText = computeContrastTextColor(accentColor)

        return KobraTheme(
            id = PRESET_CUSTOM,
            name = name.ifBlank { "Tema Kustom" },
            subtitle = "Tema kustom lokal / AI",
            bgHex = cleanBg,
            cardHex = cleanCard,
            accentHex = cleanAccent,
            textHex = colorToHex(finalText),
            buttonTextHex = colorToHex(btnText),
            borderWidthDp = 1.4f
        )
    }

    @JvmStatic
    fun applyPreset(context: Context, themeId: String): KobraTheme {
        val cfg = KobraConfig(context.applicationContext)
        val preset = builtInThemes.firstOrNull { it.id == themeId } ?: builtInThemes[0]
        cfg.setThemeId(preset.id)
        cfg.setHighContrast(preset.isHighContrast)
        cfg.setCustomThemeName(preset.name)
        cfg.setCustomBgHex(preset.bgHex)
        cfg.setCustomCardHex(preset.cardHex)
        cfg.setCustomAccentHex(preset.accentHex)
        cfg.setCustomTextHex(preset.textHex)
        return preset
    }

    @JvmStatic
    fun applyCustomTheme(
        context: Context,
        name: String,
        bgHex: String,
        cardHex: String,
        accentHex: String,
        textHex: String
    ): KobraTheme {
        val theme = buildCustomTheme(name, bgHex, cardHex, accentHex, textHex)
        val cfg = KobraConfig(context.applicationContext)
        cfg.setThemeId(PRESET_CUSTOM)
        cfg.setHighContrast(false)
        cfg.setCustomThemeName(theme.name)
        cfg.setCustomBgHex(theme.bgHex)
        cfg.setCustomCardHex(theme.cardHex)
        cfg.setCustomAccentHex(theme.accentHex)
        cfg.setCustomTextHex(theme.textHex)
        return theme
    }

    /**
     * Auto-Contrast Guard:
     * Menghitung luminance & kecerahan perseptual (YIQ).
     * Latar terang -> teks dipaksa gelap pekat (#06101C).
     * Latar gelap -> teks dipaksa putih bersih (#FFFFFF).
     */
    @JvmStatic
    fun computeContrastTextColor(backgroundColor: Int): Int {
        return if (isColorLight(backgroundColor)) 0xFF06101C.toInt() else 0xFFFFFFFF.toInt()
    }

    @JvmStatic
    fun isColorLight(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val lum = relativeLuminance(color)
        val yiq = (r * 299 + g * 587 + b * 114) / 1000.0
        return lum > 0.42 || yiq >= 145.0
    }

    @JvmStatic
    fun relativeLuminance(color: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    @JvmStatic
    fun contrastRatio(fg: Int, bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val bright = max(l1, l2)
        val dark = min(l1, l2)
        return (bright + 0.05) / (dark + 0.05)
    }

    @JvmStatic
    fun ensureContrast(preferredFg: Int, bg: Int, minRatio: Double = 4.5): Int {
        val opaqueFg = Color.rgb(Color.red(preferredFg), Color.green(preferredFg), Color.blue(preferredFg))
        val opaqueBg = Color.rgb(Color.red(bg), Color.green(bg), Color.blue(bg))
        if (contrastRatio(opaqueFg, opaqueBg) >= minRatio) {
            return opaqueFg
        }
        val bgIsLight = isColorLight(opaqueBg)
        // Coba gelapkan/terangkan warna pilihan dulu agar nuansa aksen tetap terasa sebelum fallback hitam/putih
        var candidate = opaqueFg
        for (step in 1..6) {
            candidate = if (bgIsLight) {
                blendColors(candidate, 0xFF06101C.toInt(), 0.25f)
            } else {
                blendColors(candidate, 0xFFFFFFFF.toInt(), 0.25f)
            }
            if (contrastRatio(candidate, opaqueBg) >= minRatio) {
                return candidate
            }
        }
        return computeContrastTextColor(opaqueBg)
    }

    @JvmStatic
    fun getReadableTextColor(context: Context): Int {
        val theme = getActiveTheme(context)
        return ensureContrast(theme.textColor, theme.bgColor, 5.0)
    }

    @JvmStatic
    fun getReadableAccentColor(context: Context): Int {
        val theme = getActiveTheme(context)
        return ensureContrast(theme.accentColor, theme.cardColor, 4.5)
    }

    @JvmStatic
    fun normalizeHex(raw: String?, fallback: String): String {
        if (raw == null) return fallback
        val clean = raw.trim().uppercase(Locale.ROOT)
        val prefixed = if (clean.startsWith("#")) clean else "#$clean"
        return if (prefixed.matches(Regex("^#[0-9A-F]{6}$"))) prefixed else fallback
    }

    @JvmStatic
    fun parseColorSafe(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            val clean = hex.trim()
            Color.parseColor(if (clean.startsWith("#")) clean else "#$clean")
        } catch (_: Throwable) {
            fallback
        }
    }

    @JvmStatic
    fun colorToHex(color: Int): String {
        return String.format(Locale.US, "#%06X", 0xFFFFFF and color)
    }

    @JvmStatic
    fun applyTheme(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        val theme = getActiveTheme(activity)
        val window = activity.window
        if (window != null) {
            val barColor = blendColors(theme.bgColor, theme.cardColor, 0.25f)
            try {
                window.statusBarColor = barColor
                window.navigationBarColor = barColor
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                val lightBg = isColorLight(barColor)
                controller.isAppearanceLightStatusBars = lightBg
                controller.isAppearanceLightNavigationBars = lightBg
            } catch (_: Throwable) {
            }
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.childCount == 0) return
        val root = content.getChildAt(0)
        val density = activity.resources.displayMetrics.density
        applyRecursive(activity, root, theme, theme.bgColor, density, isRoot = true)
    }

    @JvmStatic
    fun applyToDynamicView(context: Context?, view: View?) {
        if (context == null || view == null) return
        val theme = getActiveTheme(context)
        val density = context.resources.displayMetrics.density
        applyRecursive(context, view, theme, theme.cardColor, density, isRoot = false)
    }

    private fun applyRecursive(
        context: Context,
        view: View,
        theme: KobraTheme,
        parentSurfaceColor: Int,
        density: Float,
        isRoot: Boolean
    ) {
        if (view.tag?.toString() == TAG_IGNORE) return

        val role = resolveOrRememberRole(context, view, isRoot)
        var currentSurface = parentSurfaceColor

        when (role) {
            ROLE_BACKDROP -> {
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createBackdropDrawable(theme)
                currentSurface = theme.bgColor
            }
            ROLE_CAMERA_ROOT -> {
                currentSurface = 0xFF050811.toInt()
            }
            ROLE_BAR -> {
                val barColor = blendColors(theme.bgColor, theme.cardColor, 0.45f)
                ViewCompat.setBackgroundTintList(view, null)
                view.background = ColorDrawable(adjustAlpha(barColor, 240))
                currentSurface = barColor
            }
            ROLE_CARD -> {
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createCardDrawable(theme, density, accentBorder = false, goldBorder = false)
                currentSurface = theme.cardColor
            }
            ROLE_CARD_ACCENT -> {
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createCardDrawable(theme, density, accentBorder = true, goldBorder = false)
                currentSurface = theme.cardColor
            }
            ROLE_CARD_HIGHLIGHT -> {
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createCardDrawable(theme, density, accentBorder = false, goldBorder = true)
                currentSurface = theme.cardColor
            }
            ROLE_PILL -> {
                val pillColor = blendColors(theme.cardColor, theme.bgColor, 0.35f)
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createPillDrawable(pillColor, theme, density)
                currentSurface = pillColor
            }
            ROLE_BADGE -> {
                val badgeBg = blendColors(theme.cardColor, theme.accentColor, 0.16f)
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createBadgeDrawable(badgeBg, theme, density)
                currentSurface = badgeBg
            }
            ROLE_BTN_PRIMARY -> {
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createPrimaryButtonDrawable(theme, density)
                currentSurface = theme.accentColor
                if (view is Button) {
                    view.setTextColor(theme.buttonTextColor)
                    view.setTypeface(view.typeface, Typeface.BOLD)
                }
            }
            ROLE_BTN_SECONDARY -> {
                val secBg = blendColors(theme.cardColor, theme.accentColor, 0.14f)
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createSecondaryButtonDrawable(secBg, theme, density)
                currentSurface = secBg
                if (view is Button) {
                    val origColor = rememberOriginalTextColor(view)
                    val wantAccent = isAccentColor(origColor)
                    val fg = if (wantAccent) {
                        ensureContrast(theme.accentColor, secBg, 4.8)
                    } else {
                        ensureContrast(theme.textColor, secBg, 5.2)
                    }
                    view.setTextColor(fg)
                    view.setTypeface(view.typeface, Typeface.BOLD)
                }
            }
            ROLE_BTN_DANGER -> {
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createDangerButtonDrawable(theme, density)
                currentSurface = 0xFFDC2626.toInt()
                if (view is Button) {
                    view.setTextColor(0xFFFFFFFF.toInt())
                    view.setTypeface(view.typeface, Typeface.BOLD)
                }
            }
            ROLE_INPUT -> {
                val inputBg = blendColors(theme.bgColor, theme.cardColor, 0.55f)
                ViewCompat.setBackgroundTintList(view, null)
                view.background = createInputDrawable(inputBg, theme, density)
                currentSurface = inputBg
            }
            ROLE_TAB -> {
                // Diatur oleh styleTab(), tapi pastikan kontras aman saat applyTheme
                if (view is Button) {
                    view.setTypeface(view.typeface, Typeface.BOLD)
                }
            }
            ROLE_CHIP -> {
                if (view is Button) {
                    view.setTypeface(view.typeface, Typeface.BOLD)
                }
            }
        }

        // Tangani warna teks untuk seluruh komponen teks (non-tombol yang sudah di-style di atas)
        if (view is EditText) {
            val safeText = ensureContrast(theme.textColor, currentSurface, 5.5)
            val hintColor = ensureContrast(blendColors(safeText, currentSurface, 0.42f), currentSurface, 3.2)
            view.setTextColor(safeText)
            view.setHintTextColor(hintColor)
        } else if (view is CompoundButton) {
            val safeText = ensureContrast(theme.textColor, currentSurface, 5.0)
            view.setTextColor(safeText)
        } else if (view is Button) {
            if (role == ROLE_CARD || role == ROLE_CARD_ACCENT || role == ROLE_CARD_HIGHLIGHT || role == ROLE_PLAIN) {
                val origColor = rememberOriginalTextColor(view)
                val target = if (isAccentColor(origColor)) theme.accentColor else theme.textColor
                view.setTextColor(ensureContrast(target, currentSurface, 5.0))
                view.setTypeface(view.typeface, Typeface.BOLD)
            }
        } else if (view is TextView) {
            val origColor = rememberOriginalTextColor(view)
            when {
                origColor == 0xFF00E5FF.toInt() || origColor == 0xFF40C4FF.toInt() -> {
                    view.setTextColor(ensureContrast(theme.accentColor, currentSurface, 4.6))
                }
                origColor == 0xFFFFD600.toInt() || origColor == 0xFFFFB300.toInt() -> {
                    val goldOrAccent = if (theme.isHighContrast) 0xFFFFD600.toInt()
                    else if (isColorLight(currentSurface)) theme.accentColor
                    else 0xFFFFD600.toInt()
                    view.setTextColor(ensureContrast(goldOrAccent, currentSurface, 4.6))
                }
                origColor == 0xFF94A3B8.toInt() || origColor == 0xFF64748B.toInt() -> {
                    val muted = blendColors(theme.textColor, currentSurface, 0.28f)
                    view.setTextColor(ensureContrast(muted, currentSurface, 4.5))
                }
                origColor == 0xFFFF5252.toInt() -> {
                    view.setTextColor(ensureContrast(0xFFFF5252.toInt(), currentSurface, 4.5))
                }
                else -> {
                    view.setTextColor(ensureContrast(theme.textColor, currentSurface, 5.2))
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyRecursive(context, view.getChildAt(i), theme, currentSurface, density, isRoot = false)
            }
        }
    }

    @JvmStatic
    fun styleTab(tab: Button?, selected: Boolean) {
        if (tab == null) return
        val context = tab.context
        val theme = getActiveTheme(context)
        val density = context.resources.displayMetrics.density
        ViewCompat.setBackgroundTintList(tab, null)
        tab.setTypeface(tab.typeface, Typeface.BOLD)

        if (selected) {
            tab.background = createPrimaryButtonDrawable(theme, density)
            tab.setTextColor(theme.buttonTextColor)
        } else {
            val idleBg = blendColors(theme.cardColor, theme.bgColor, 0.35f)
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(idleBg)
                val border = if (theme.isHighContrast) theme.effectiveBorderColor else adjustAlpha(theme.effectiveBorderColor, 95)
                setStroke((1.1f * density).toInt().coerceAtLeast(1), border)
            }
            tab.background = gd
            tab.setTextColor(ensureContrast(theme.textColor, idleBg, 5.2))
        }
    }

    @JvmStatic
    @JvmOverloads
    fun styleChip(chip: Button?, selected: Boolean, highlightGold: Boolean = false) {
        if (chip == null) return
        val context = chip.context
        val theme = getActiveTheme(context)
        val density = context.resources.displayMetrics.density
        ViewCompat.setBackgroundTintList(chip, null)
        chip.setTypeface(chip.typeface, Typeface.BOLD)

        if (selected) {
            val activeBg = blendColors(theme.cardColor, theme.accentColor, 0.28f)
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(activeBg)
                setStroke((1.8f * density).toInt().coerceAtLeast(2), theme.accentColor)
            }
            chip.background = gd
            chip.setTextColor(ensureContrast(theme.accentColor, activeBg, 4.8))
        } else {
            val idleBg = theme.cardColor
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(idleBg)
                val strokeColor = if (theme.isHighContrast) 0xFFFFFFFF.toInt() else adjustAlpha(theme.effectiveBorderColor, 85)
                setStroke((1.1f * density).toInt().coerceAtLeast(1), strokeColor)
            }
            chip.background = gd
            val baseText = if (highlightGold && !isColorLight(idleBg)) 0xFFFFD600.toInt() else theme.textColor
            chip.setTextColor(ensureContrast(baseText, idleBg, 4.8))
        }
    }

    @JvmStatic
    fun styleChatBubble(
        context: Context,
        bubbleLayout: View,
        metaView: TextView?,
        textView: TextView?,
        replayLabel: TextView?,
        fromUser: Boolean
    ) {
        val theme = getActiveTheme(context)
        val density = context.resources.displayMetrics.density
        ViewCompat.setBackgroundTintList(bubbleLayout, null)

        if (fromUser) {
            val userBg = blendColors(theme.cardColor, theme.accentColor, 0.34f)
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(userBg)
                setStroke((1.3f * density).toInt().coerceAtLeast(1), theme.effectiveBorderColor)
            }
            bubbleLayout.background = gd
            textView?.setTextColor(ensureContrast(theme.textColor, userBg, 5.2))
        } else {
            val aiBg = theme.cardColor
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(aiBg)
                val border = if (theme.isHighContrast) 0xFFFFFFFF.toInt() else adjustAlpha(theme.effectiveBorderColor, 110)
                setStroke((theme.borderWidthDp * density).toInt().coerceAtLeast(1), border)
            }
            bubbleLayout.background = gd
            metaView?.setTextColor(ensureContrast(theme.accentColor, aiBg, 4.6))
            textView?.setTextColor(ensureContrast(theme.textColor, aiBg, 5.2))
            replayLabel?.setTextColor(ensureContrast(blendColors(theme.textColor, aiBg, 0.3f), aiBg, 4.5))
        }
    }

    @JvmStatic
    fun generateThemeWithAi(context: Context, rawDescription: String, callback: AiThemeCallback) {
        val desc = rawDescription.trim()
        if (desc.isEmpty()) {
            mainHandler.post {
                callback.onError("Tulis deskripsi tema singkat terlebih dahulu.")
            }
            return
        }

        val appCtx = context.applicationContext
        if (!NetworkState.isOnline(appCtx)) {
            val offlineTheme = generateOfflineSmartTheme(desc)
            val saved = applyCustomTheme(
                appCtx,
                offlineTheme.name,
                offlineTheme.bgHex,
                offlineTheme.cardHex,
                offlineTheme.accentHex,
                offlineTheme.textHex
            )
            mainHandler.post {
                callback.onThemeGenerated(saved, "Generator Tema Lokal (Offline)")
            }
            return
        }

        val sysPrompt = "Kamu adalah desainer UI aksesibilitas tunanetra dan palet warna profesional. " +
            "Balas HANYA 1 objek JSON valid tanpa markdown dengan 5 field persis: " +
            "{\"name\":\"Nama Tema Singkat\",\"bg\":\"#RRGGBB\",\"card\":\"#RRGGBB\",\"accent\":\"#RRGGBB\",\"text\":\"#RRGGBB\"}. " +
            "Pastikan kontras warna sangat tajam (>= 7:1 antara bg/card dan text, serta accent cerah yang serasi)."
        val userPrompt = "Buat palet warna harmonis bertema: \"$desc\". " +
            "Berikan JSON valid saja tanpa penjelasan tambahan."

        OnlineAiRouter.askAsync(appCtx, userPrompt, sysPrompt, object : OnlineAiRouter.Callback {
            override fun onResult(text: String, providerLabel: String) {
                val parsed = parseAiThemeJson(text, desc)
                val finalTheme = parsed ?: generateOfflineSmartTheme(desc)
                val saved = applyCustomTheme(
                    appCtx,
                    finalTheme.name,
                    finalTheme.bgHex,
                    finalTheme.cardHex,
                    finalTheme.accentHex,
                    finalTheme.textHex
                )
                val label = if (parsed != null) "$providerLabel" else "Parser Tema Lokal"
                callback.onThemeGenerated(saved, label)
            }

            override fun onError(message: String) {
                val offlineTheme = generateOfflineSmartTheme(desc)
                val saved = applyCustomTheme(
                    appCtx,
                    offlineTheme.name,
                    offlineTheme.bgHex,
                    offlineTheme.cardHex,
                    offlineTheme.accentHex,
                    offlineTheme.textHex
                )
                callback.onThemeGenerated(saved, "Generator Tema Lokal")
            }
        })
    }

    private fun parseAiThemeJson(raw: String, fallbackDesc: String): KobraTheme? {
        return try {
            val cleaned = raw.replace("```json", "", ignoreCase = true)
                .replace("```", "")
                .trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val obj = JSONObject(cleaned.substring(start, end + 1))
            val bg = obj.optString("bg", "")
            val card = obj.optString("card", "")
            val accent = obj.optString("accent", "")
            val text = obj.optString("text", "")
            if (!isValidHex(bg) || !isValidHex(card) || !isValidHex(accent) || !isValidHex(text)) {
                return null
            }
            val rawName = obj.optString("name", "").trim()
            val name = if (rawName.isNotEmpty()) rawName.take(28) else "Tema AI: ${fallbackDesc.take(18)}"
            buildCustomTheme(name, bg, card, accent, text)
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun generateOfflineSmartTheme(description: String): KobraTheme {
        val d = description.lowercase(Locale.ROOT)
        val wantLight = d.contains("terang") || d.contains("siang") || d.contains("putih") ||
            d.contains("kertas") || d.contains("cerah") || d.contains("pagi") || d.contains("light")

        return when {
            d.contains("hutan") || d.contains("alam") || d.contains("daun") || d.contains("hijau") || d.contains("rimba") -> {
                if (wantLight) buildCustomTheme("Hutan Pagi", "#EDF7F1", "#FFFFFF", "#008955", "#0B291C")
                else buildCustomTheme("Hutan Malam", "#05140E", "#0C291E", "#00E676", "#E6F7EE")
            }
            d.contains("sunset") || d.contains("senja") || d.contains("jingga") || d.contains("oranye") || d.contains("hangat") -> {
                if (wantLight) buildCustomTheme("Senja Cerah", "#FFF5EE", "#FFFFFF", "#D84315", "#2B1209")
                else buildCustomTheme("Senja Jingga", "#170B08", "#2B1610", "#FF7043", "#FFF1EB")
            }
            d.contains("laut") || d.contains("samudra") || d.contains("biru") || d.contains("langit") || d.contains("es") -> {
                if (wantLight) buildCustomTheme("Langit Siang", "#EEF6FF", "#FFFFFF", "#0277BD", "#0A1E33")
                else buildCustomTheme("Samudra Dalam", "#04101D", "#0B2239", "#38BDF8", "#E6F4FE")
            }
            d.contains("emas") || d.contains("mewah") || d.contains("kerajaan") || d.contains("kuning") -> {
                if (wantLight) buildCustomTheme("Krem Emas", "#FAF6ED", "#FFFFFF", "#B26A00", "#1F1607")
                else buildCustomTheme("Emas Malam", "#120F0A", "#221B11", "#FFB300", "#FFF8E6")
            }
            d.contains("ungu") || d.contains("lavender") || d.contains("galaxy") || d.contains("galaksi") || d.contains("neon") -> {
                if (wantLight) buildCustomTheme("Lavender Pagi", "#F5F0FF", "#FFFFFF", "#7B1FA2", "#1B0C29")
                else buildCustomTheme("Galaksi Neon", "#10081E", "#1F1138", "#E040FB", "#F4EBFF")
            }
            d.contains("merah") || d.contains("api") || d.contains("bara") || d.contains("mawar") || d.contains("pink") -> {
                if (wantLight) buildCustomTheme("Mawar Pagi", "#FFF0F2", "#FFFFFF", "#C62828", "#2B090D")
                else buildCustomTheme("Bara Merah", "#19070A", "#2C0E14", "#FF5252", "#FFE9EC")
            }
            d.contains("kopi") || d.contains("coklat") || d.contains("kayu") -> {
                if (wantLight) buildCustomTheme("Kopi Susu", "#F7F2EC", "#FFFFFF", "#6D4C41", "#241814")
                else buildCustomTheme("Espresso Malam", "#140E0A", "#241913", "#FFB74D", "#FBEFE6")
            }
            wantLight -> buildCustomTheme("Siang Kontras", "#F2F5F9", "#FFFFFF", "#0066CC", "#0D1B2A")
            else -> buildCustomTheme("Cyber Kustom", "#06101C", "#0F2138", "#00E5FF", "#E2E8F0")
        }
    }

    private fun isValidHex(hex: String?): Boolean {
        if (hex == null) return false
        val clean = hex.trim().uppercase(Locale.ROOT)
        val prefixed = if (clean.startsWith("#")) clean else "#$clean"
        return prefixed.matches(Regex("^#[0-9A-F]{6}$"))
    }

    private fun resolveOrRememberRole(context: Context, view: View, isRoot: Boolean): String {
        val cached = view.getTag(R.id.tag_kobra_theme_role) as? String
        if (cached != null) return cached

        val role = detectInitialRole(context, view, isRoot)
        view.setTag(R.id.tag_kobra_theme_role, role)
        return role
    }

    private fun detectInitialRole(context: Context, view: View, isRoot: Boolean): String {
        val strTag = view.tag?.toString().orEmpty()
        when (strTag) {
            TAG_BACKDROP -> return ROLE_BACKDROP
            TAG_BAR -> return ROLE_BAR
            TAG_CARD -> return ROLE_CARD
            TAG_BTN_PRIMARY -> return ROLE_BTN_PRIMARY
            TAG_BTN_SECONDARY -> return ROLE_BTN_SECONDARY
            TAG_BTN_DANGER -> return ROLE_BTN_DANGER
            TAG_TAB -> return ROLE_TAB
            TAG_CHIP -> return ROLE_CHIP
            TAG_PILL -> return ROLE_PILL
        }

        val id = view.id
        if (id in primaryButtonIds) return ROLE_BTN_PRIMARY
        if (id in dangerButtonIds) return ROLE_BTN_DANGER
        if (id in tabButtonIds) return ROLE_TAB
        if (id in chipButtonIds) return ROLE_CHIP
        if (id in cardButtonIds) return ROLE_CARD

        val bg = view.background
        if (isRoot) {
            if (bg is ColorDrawable && bg.color == 0xFF050811.toInt()) {
                return ROLE_CAMERA_ROOT
            }
            return ROLE_BACKDROP
        }

        if (view is EditText) return ROLE_INPUT

        val cs = bg?.constantState
        if (cs != null) {
            if (matchesDrawable(context, cs, R.drawable.bg_hud_backdrop)) return ROLE_BACKDROP
            if (matchesDrawable(context, cs, R.drawable.bg_glass_card)) return ROLE_CARD
            if (matchesDrawable(context, cs, R.drawable.bg_glass_card_cyan)) return ROLE_CARD_ACCENT
            if (matchesDrawable(context, cs, R.drawable.bg_glass_card_gold)) return ROLE_CARD_HIGHLIGHT
            if (matchesDrawable(context, cs, R.drawable.bg_telemetry_pill)) return ROLE_PILL
            if (matchesDrawable(context, cs, R.drawable.bg_badge_status)) return ROLE_BADGE
            if (matchesDrawable(context, cs, R.drawable.bg_glass_button_primary)) return ROLE_BTN_PRIMARY
            if (matchesDrawable(context, cs, R.drawable.bg_glass_button_secondary)) return ROLE_BTN_SECONDARY
            if (matchesDrawable(context, cs, R.drawable.bg_glass_button_danger)) return ROLE_BTN_DANGER
            if (matchesDrawable(context, cs, R.drawable.bg_glass_input)) return ROLE_INPUT
            if (matchesDrawable(context, cs, R.drawable.bg_tab_active) ||
                matchesDrawable(context, cs, R.drawable.bg_tab_inactive)) return ROLE_TAB
            if (matchesDrawable(context, cs, R.drawable.bg_persona_chip_active) ||
                matchesDrawable(context, cs, R.drawable.bg_persona_chip_idle)) return ROLE_CHIP
        }

        if (bg is ColorDrawable) {
            val c = bg.color
            if (c == 0xE6081020.toInt() || c == 0xE6070D19.toInt() || c == 0xEE070D1A.toInt() ||
                c == 0xCC060B16.toInt() || c == 0xEB060B16.toInt() || c == 0xEE060B16.toInt()
            ) {
                return ROLE_BAR
            }
        }

        if (view is Button && view !is CompoundButton) {
            return ROLE_BTN_SECONDARY
        }

        return ROLE_PLAIN
    }

    private fun matchesDrawable(context: Context, target: Drawable.ConstantState, resId: Int): Boolean {
        return try {
            ContextCompat.getDrawable(context, resId)?.constantState == target
        } catch (_: Throwable) {
            false
        }
    }

    private fun rememberOriginalTextColor(tv: TextView): Int {
        val existing = tv.getTag(R.id.tag_kobra_orig_text_color) as? Int
        if (existing != null) return existing
        val current = tv.currentTextColor
        tv.setTag(R.id.tag_kobra_orig_text_color, current)
        return current
    }

    private fun isAccentColor(color: Int): Boolean {
        return color == 0xFF00E5FF.toInt() || color == 0xFFFFD600.toInt() || color == 0xFF40C4FF.toInt()
    }

    private fun createBackdropDrawable(theme: KobraTheme): Drawable {
        val start = theme.bgColor
        val end = if (theme.id == PRESET_OLED || theme.isHighContrast) {
            theme.bgColor
        } else {
            blendColors(theme.bgColor, theme.accentColor, 0.08f)
        }
        val base = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end))
        if (theme.id == PRESET_OLED || theme.isHighContrast) {
            return base
        }
        val glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 900f
            setGradientCenter(0.5f, 0.16f)
            colors = intArrayOf(adjustAlpha(theme.accentColor, 34), 0x00000000)
        }
        return LayerDrawable(arrayOf(base, glow))
    }

    private fun createCardDrawable(
        theme: KobraTheme,
        density: Float,
        accentBorder: Boolean,
        goldBorder: Boolean
    ): Drawable {
        val borderColor = when {
            theme.isHighContrast -> 0xFFFFFFFF.toInt()
            goldBorder && !isColorLight(theme.cardColor) -> 0x99FFD600.toInt()
            accentBorder -> adjustAlpha(theme.accentColor, 175)
            else -> adjustAlpha(theme.effectiveBorderColor, 95)
        }
        val topTint = blendColors(theme.cardColor, theme.bgColor, 0.06f)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(theme.cardColor, topTint)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density
            setStroke((theme.borderWidthDp * density).toInt().coerceAtLeast(1), borderColor)
        }
    }

    private fun createPillDrawable(pillColor: Int, theme: KobraTheme, density: Float): Drawable {
        val border = if (theme.isHighContrast) 0xFFFFFFFF.toInt() else adjustAlpha(theme.effectiveBorderColor, 85)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            setColor(pillColor)
            setStroke((1.1f * density).toInt().coerceAtLeast(1), border)
        }
    }

    private fun createBadgeDrawable(badgeBg: Int, theme: KobraTheme, density: Float): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(badgeBg)
            setStroke((1.1f * density).toInt().coerceAtLeast(1), adjustAlpha(theme.accentColor, 155))
        }
    }

    private fun createPrimaryButtonDrawable(theme: KobraTheme, density: Float): Drawable {
        val c1 = theme.accentColor
        val c2 = blendColors(theme.accentColor, 0xFF000000.toInt(), 0.22f)
        val strokeColor = if (theme.isHighContrast) 0xFFFFFFFF.toInt() else adjustAlpha(0xFFFFFFFF.toInt(), 120)

        val normal = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setStroke((theme.borderWidthDp * density).toInt().coerceAtLeast(1), strokeColor)
        }
        val pressed = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c2, c1)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setStroke((1.8f * density).toInt().coerceAtLeast(2), 0xFFFFFFFF.toInt())
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun createSecondaryButtonDrawable(secBg: Int, theme: KobraTheme, density: Float): Drawable {
        val secDark = blendColors(secBg, theme.bgColor, 0.18f)
        val border = if (theme.isHighContrast) 0xFFFFFFFF.toInt() else adjustAlpha(theme.accentColor, 140)
        val normal = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(secBg, secDark)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setStroke((theme.borderWidthDp * density).toInt().coerceAtLeast(1), border)
        }
        val pressedBg = blendColors(secBg, theme.accentColor, 0.25f)
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setColor(pressedBg)
            setStroke((1.6f * density).toInt().coerceAtLeast(2), theme.accentColor)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun createDangerButtonDrawable(theme: KobraTheme, density: Float): Drawable {
        val strokeColor = if (theme.isHighContrast) 0xFFFFFFFF.toInt() else 0x99FFD600.toInt()
        val normal = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFEF4444.toInt(), 0xFFDC2626.toInt(), 0xFF991B1B.toInt())
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setStroke((1.5f * density).toInt().coerceAtLeast(1), strokeColor)
        }
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setColor(0xFF991B1B.toInt())
            setStroke((1.8f * density).toInt().coerceAtLeast(2), 0xFFFFFFFF.toInt())
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun createInputDrawable(inputBg: Int, theme: KobraTheme, density: Float): Drawable {
        val border = if (theme.isHighContrast) 0xFFFFFFFF.toInt() else adjustAlpha(theme.accentColor, 130)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * density
            setColor(inputBg)
            setStroke((1.2f * density).toInt().coerceAtLeast(1), border)
        }
    }

    @JvmStatic
    fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val inv = 1f - r
        val red = (Color.red(c1) * inv + Color.red(c2) * r).toInt()
        val green = (Color.green(c1) * inv + Color.green(c2) * r).toInt()
        val blue = (Color.blue(c1) * inv + Color.blue(c2) * r).toInt()
        return Color.rgb(red, green, blue)
    }

    @JvmStatic
    fun adjustAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
