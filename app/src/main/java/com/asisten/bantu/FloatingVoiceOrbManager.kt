package com.asisten.bantu

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pengelola Gelembung Mikrofon 3D Melayang Global (Floating Free-Drag & Edge-Hide 3D Voice Pop-up).
 *
 * Fitur lengkap:
 * - Berjalan secara global di atas seluruh aplikasi via WindowManager (TYPE_ACCESSIBILITY_OVERLAY).
 * - Free Dragging bebas ke posisi mana pun di layar (atas, tengah, bawah, kiri, kanan).
 * - Ikon mikrofon tetap satu-satunya kontrol mengambang; tidak ada tab tepi, auto-hide, atau target tutup.
 * - Desain 3D Glassmorphism tanpa emoji: 44dp x 44dp; hanya ikon mikrofon yang terlihat, dengan indikator cincin sangat tipis saat aktif.
 * - Gestur Ketuk:
 *   1) 1x Ketuk -> Aktifkan / hentikan pengenalan suara (Voice Access + CommandRouter).
 *   2) 2x Ketuk -> Bacakan indeks elemen lewat suara tanpa lencana visual.
 *   3) Tekan Lama -> Bacakan isi elemen layar saat ini lewat TTS ("Baca layar").
 */
class FloatingVoiceOrbManager(
    private val service: AccessibilityService,
    private val listener: OrbActionListener
) {

    enum class OrbState { IDLE, LISTENING, SPEAKING, PAUSED, NO_PERMISSION }

    interface OrbActionListener {
        fun onSingleTap()
        fun onDoubleTap()
        fun onLongPress()
        fun onDismissed()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val density = service.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private val orbSizePx = (44f * density).roundToInt()
    private val tabWidthPx = (8f * density).roundToInt()
    private val edgeMarginPx = (6f * density).roundToInt()
    private val dismissZoneSizePx = (48f * density).roundToInt()
    private val dismissBottomMarginPx = (44f * density).roundToInt()

    private var orbRoot: FrameLayout? = null
    private var orbBody: FrameLayout? = null
    private var orbIcon: ImageView? = null
    private var pulseRingView: PulseRingView? = null
    private var edgeHandleView: EdgeTabView? = null
    private var orbParams: WindowManager.LayoutParams? = null

    private var dismissRoot: FrameLayout? = null
    private var dismissParams: WindowManager.LayoutParams? = null
    private var dismissVisible = false
    private var hoveringDismiss = false

    private var currentState = OrbState.IDLE
    private var isEdgeDocked = false
    private var dockedOnLeft = false
    private var temporarilyDismissed = false

    private var slideAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var pulseFraction = 0f

    private val autoHideRunnable = Runnable {
        val cfg = KobraConfig(service)
        if (cfg.isFloatingOrbAutoHide() && currentState != OrbState.LISTENING && currentState != OrbState.SPEAKING) {
            dockToNearestEdge(animate = true)
        }
    }

    fun isShowing(): Boolean = orbRoot != null

    fun show(forceRestoreFromDismiss: Boolean = false) {
        mainHandler.post {
            val cfg = KobraConfig(service)
            if (!cfg.isFloatingOrbEnabled()) {
                hideInternal(markDismissed = false)
                return@post
            }
            if (forceRestoreFromDismiss) {
                temporarilyDismissed = false
            }
            if (temporarilyDismissed) return@post

            if (orbRoot == null) {
                createAndAttachOrb()
            } else {
                revealFromEdge(animate = true)
                applyVisualAppearance()
            }
            scheduleAutoHideIfEnabled()
        }
    }

    fun hideTemporary() {
        mainHandler.post {
            hideInternal(markDismissed = true)
        }
    }

    fun refreshFromConfig() {
        mainHandler.post {
            val cfg = KobraConfig(service)
            if (!cfg.isFloatingOrbEnabled()) {
                hideInternal(markDismissed = false)
                return@post
            }
            if (orbRoot == null && !temporarilyDismissed) {
                createAndAttachOrb()
            } else if (orbRoot != null) {
                applyVisualAppearance()
                if (!cfg.isFloatingOrbAutoHide() && isEdgeDocked) {
                    revealFromEdge(animate = true)
                }
                scheduleAutoHideIfEnabled()
            }
        }
    }

    fun setOrbState(state: OrbState) {
        mainHandler.post {
            currentState = state
            if (state == OrbState.LISTENING || state == OrbState.SPEAKING) {
                cancelAutoHide()
                if (isEdgeDocked && orbRoot != null) {
                    revealFromEdge(animate = true)
                }
                startPulseAnimation()
            } else {
                stopPulseAnimation()
                scheduleAutoHideIfEnabled()
            }
            applyVisualAppearance()
        }
    }

    fun destroy() {
        mainHandler.post {
            cancelAutoHide()
            slideAnimator?.cancel()
            slideAnimator = null
            stopPulseAnimation()
            hideInternal(markDismissed = false)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createAndAttachOrb() {
        if (wm == null || orbRoot != null) return
        try {
            val screenW = currentScreenWidth()
            val screenH = currentScreenHeight()

            val container = FrameLayout(service).apply {
                clipChildren = false
                clipToPadding = false
                contentDescription = "Mikrofon Kobra. Ketuk untuk bicara, ketuk dua kali untuk membaca indeks target dengan suara, tekan lama untuk membaca layar, geser untuk memindahkan mikrofon."
            }

            val body = FrameLayout(service).apply {
                setBackgroundResource(R.drawable.bg_floating_orb_3d)
            }

            val ring = PulseRingView(service)
            body.addView(ring, FrameLayout.LayoutParams(orbSizePx, orbSizePx))

            val iconPad = (8f * density).roundToInt()
            val icon = ImageView(service).apply {
                setImageResource(R.drawable.ic_3d_mic_orb)
                setPadding(iconPad, iconPad, iconPad, iconPad)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Mikrofon Kobra"
            }
            body.addView(icon, FrameLayout.LayoutParams(orbSizePx, orbSizePx))

            val edgeTab = EdgeTabView(service).apply {
                visibility = View.GONE
            }

            container.addView(body, FrameLayout.LayoutParams(orbSizePx, orbSizePx))
            container.addView(edgeTab, FrameLayout.LayoutParams(tabWidthPx, orbSizePx))

            val lp = WindowManager.LayoutParams(
                orbSizePx,
                orbSizePx,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = max(edgeMarginPx, screenW - orbSizePx - edgeMarginPx)
                y = (screenH * 0.38f).roundToInt()
            }

            val gestureDetector = GestureDetector(
                service,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        HapticFeedback.vibrateAction(service)
                        if (isEdgeDocked) {
                            revealFromEdge(animate = true)
                            scheduleAutoHideIfEnabled()
                        } else {
                            listener.onSingleTap()
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        HapticFeedback.vibrateAction(service)
                        if (isEdgeDocked) {
                            revealFromEdge(animate = true)
                        }
                        listener.onDoubleTap()
                        scheduleAutoHideIfEnabled()
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        HapticFeedback.vibrateSuccess(service)
                        if (isEdgeDocked) {
                            revealFromEdge(animate = true)
                        }
                        listener.onLongPress()
                        scheduleAutoHideIfEnabled()
                    }
                }
            )

            container.setOnTouchListener(object : View.OnTouchListener {
                private var downRawX = 0f
                private var downRawY = 0f
                private var startWinX = 0
                private var startWinY = 0
                private var dragging = false
                private var wasDockedOnTouch = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val params = orbParams ?: return false
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            cancelAutoHide()
                            slideAnimator?.cancel()
                            downRawX = event.rawX
                            downRawY = event.rawY
                            startWinX = params.x
                            startWinY = params.y
                            dragging = false
                            wasDockedOnTouch = isEdgeDocked
                            gestureDetector.onTouchEvent(event)
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downRawX
                            val dy = event.rawY - downRawY
                            if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                dragging = true
                                if (wasDockedOnTouch) {
                                    isEdgeDocked = false
                                    updateEdgeTabVisibility(false, dockedOnLeft)
                                }
                                            }
                            if (dragging) {
                                val sw = currentScreenWidth()
                                val sh = currentScreenHeight()
                                val newX = (startWinX + dx.roundToInt())
                                    .coerceIn(-orbSizePx / 2, sw - orbSizePx / 2)
                                val newY = (startWinY + dy.roundToInt())
                                    .coerceIn((24f * density).roundToInt(), sh - orbSizePx - (24f * density).roundToInt())
                                params.x = newX
                                params.y = newY
                                try {
                                    wm.updateViewLayout(container, params)
                                } catch (_: Throwable) {
                                }
                             } else {
                                gestureDetector.onTouchEvent(event)
                            }
                            return true
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (dragging) {
                                hideDismissZone()

                                val sw = currentScreenWidth()
                                isEdgeDocked = false
                                updateEdgeTabVisibility(false, dockedOnLeft)
                                val clampedX = params.x.coerceIn(edgeMarginPx, max(edgeMarginPx, sw - orbSizePx - edgeMarginPx))
                                if (clampedX != params.x) animateOrbX(clampedX)
                            } else {
                                gestureDetector.onTouchEvent(event)
                                scheduleAutoHideIfEnabled()
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            wm.addView(container, lp)
            orbRoot = container
            orbBody = body
            orbIcon = icon
            pulseRingView = ring
            edgeHandleView = edgeTab
            orbParams = lp
            isEdgeDocked = false

            applyVisualAppearance()
            scheduleAutoHideIfEnabled()
        } catch (_: Throwable) {
            orbRoot = null
        }
    }

    private fun hideInternal(markDismissed: Boolean) {
        cancelAutoHide()
        hideDismissZone()
        slideAnimator?.cancel()
        slideAnimator = null
        stopPulseAnimation()

        val view = orbRoot
        orbRoot = null
        orbBody = null
        orbIcon = null
        pulseRingView = null
        edgeHandleView = null
        orbParams = null
        isEdgeDocked = false
        if (markDismissed) {
            temporarilyDismissed = true
        }
        if (view != null) {
            try {
                wm?.removeView(view)
            } catch (_: Throwable) {
            }
        }
    }

    private fun applyVisualAppearance() {
        val body = orbBody ?: return
        val icon = orbIcon ?: return
        val container = orbRoot ?: return
        val cfg = KobraConfig(service)

        val isActiveVoice = currentState == OrbState.LISTENING || currentState == OrbState.SPEAKING
        body.setBackgroundResource(
            if (isActiveVoice) R.drawable.bg_floating_orb_3d_active else R.drawable.bg_floating_orb_3d
        )

        val idleAlpha = cfg.floatingOrbAlpha
        container.alpha = when {
            isActiveVoice -> 1.0f
            isEdgeDocked -> max(0.55f, idleAlpha)
            currentState == OrbState.PAUSED -> max(0.30f, idleAlpha * 0.78f)
            else -> idleAlpha
        }

        icon.alpha = when (currentState) {
            OrbState.PAUSED -> 0.55f
            OrbState.NO_PERMISSION -> 0.60f
            else -> 1.0f
        }

        pulseRingView?.setOrbState(currentState)
    }

    private fun dockToNearestEdge(animate: Boolean) {
        val params = orbParams ?: return
        val sw = currentScreenWidth()
        val centerX = params.x + orbSizePx / 2
        val onLeft = centerX < sw / 2
        dockedOnLeft = onLeft
        dockToEdge(onLeft, animate)
    }

    private fun dockToEdge(onLeft: Boolean, animate: Boolean) {
        val params = orbParams ?: return
        val sw = currentScreenWidth()
        isEdgeDocked = true
        updateEdgeTabVisibility(true, onLeft)
        applyVisualAppearance()

        // 75% tersembunyi di luar layar, menyisakan tab pegangan lengkung transparan 14dp
        val targetX = if (onLeft) {
            -(orbSizePx - tabWidthPx)
        } else {
            sw - tabWidthPx
        }

        if (animate) {
            animateOrbX(targetX)
        } else {
            params.x = targetX
            try {
                orbRoot?.let { wm?.updateViewLayout(it, params) }
            } catch (_: Throwable) {
            }
        }
    }

    private fun revealFromEdge(animate: Boolean) {
        val params = orbParams ?: return
        if (!isEdgeDocked) return
        val sw = currentScreenWidth()
        isEdgeDocked = false
        updateEdgeTabVisibility(false, dockedOnLeft)
        applyVisualAppearance()

        val targetX = if (dockedOnLeft) {
            edgeMarginPx
        } else {
            max(edgeMarginPx, sw - orbSizePx - edgeMarginPx)
        }

        if (animate) {
            animateOrbX(targetX)
        } else {
            params.x = targetX
            try {
                orbRoot?.let { wm?.updateViewLayout(it, params) }
            } catch (_: Throwable) {
            }
        }
    }

    private fun updateEdgeTabVisibility(docked: Boolean, onLeft: Boolean) {
        val tab = edgeHandleView ?: return
        val body = orbBody ?: return
        if (!docked) {
            tab.visibility = View.GONE
            body.visibility = View.VISIBLE
            return
        }
        tab.setSide(onLeft)
        val lp = (tab.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(tabWidthPx, orbSizePx)
        lp.gravity = if (onLeft) Gravity.END else Gravity.START
        tab.layoutParams = lp
        tab.visibility = View.VISIBLE
        body.visibility = View.VISIBLE
    }

    private fun animateOrbX(targetX: Int) {
        val params = orbParams ?: return
        val container = orbRoot ?: return
        val startX = params.x
        if (startX == targetX) return
        slideAnimator?.cancel()
        slideAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val curContainer = orbRoot ?: return@addUpdateListener
                val curParams = orbParams ?: return@addUpdateListener
                curParams.x = anim.animatedValue as Int
                try {
                    wm?.updateViewLayout(curContainer, curParams)
                } catch (_: Throwable) {
                }
            }
            start()
        }
    }

    private fun scheduleAutoHideIfEnabled() {
        // Keep the sole microphone control visible; never replace it with an edge tab.
        cancelAutoHide()
    }

    private fun cancelAutoHide() {
        mainHandler.removeCallbacks(autoHideRunnable)
    }

    private fun startPulseAnimation() {
        if (pulseAnimator?.isRunning == true) return
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseFraction = it.animatedValue as Float
                pulseRingView?.updatePhase(pulseFraction)
                val scale = 1.0f + 0.06f * kotlin.math.sin(pulseFraction * Math.PI * 2.0).toFloat()
                orbBody?.scaleX = scale
                orbBody?.scaleY = scale
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseFraction = 0f
        orbBody?.scaleX = 1.0f
        orbBody?.scaleY = 1.0f
        pulseRingView?.updatePhase(0f)
    }

    private fun showDismissZone() {
        if (wm == null || dismissVisible) return
        try {
            val zone = FrameLayout(service).apply {
                setBackgroundResource(R.drawable.bg_dismiss_zone_3d)
                alpha = 0.85f
            }
            val iconPad = (18f * density).roundToInt()
            val xIcon = ImageView(service).apply {
                setImageResource(R.drawable.ic_3d_close_x)
                setPadding(iconPad, iconPad, iconPad, iconPad)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Zona Tutup Gelembung"
            }
            zone.addView(xIcon, FrameLayout.LayoutParams(dismissZoneSizePx, dismissZoneSizePx))

            val lp = WindowManager.LayoutParams(
                dismissZoneSizePx,
                dismissZoneSizePx,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dismissBottomMarginPx
            }

            wm.addView(zone, lp)
            dismissRoot = zone
            dismissParams = lp
            dismissVisible = true
            hoveringDismiss = false
        } catch (_: Throwable) {
            dismissVisible = false
        }
    }

    private fun updateDismissHoverState(orbCenterX: Float, orbCenterY: Float) {
        val zone = dismissRoot ?: return
        val inside = isInsideDismissZone(orbCenterX, orbCenterY)
        if (inside != hoveringDismiss) {
            hoveringDismiss = inside
            if (inside) {
                HapticFeedback.vibrateAction(service)
            }
            zone.animate()
                .scaleX(if (inside) 1.22f else 1.0f)
                .scaleY(if (inside) 1.22f else 1.0f)
                .alpha(if (inside) 1.0f else 0.82f)
                .setDuration(140L)
                .start()
        }
    }

    private fun isInsideDismissZone(orbCenterX: Float, orbCenterY: Float): Boolean {
        val sw = currentScreenWidth()
        val sh = currentScreenHeight()
        val zoneCenterX = sw / 2f
        val zoneCenterY = sh - dismissBottomMarginPx - dismissZoneSizePx / 2f
        val dx = orbCenterX - zoneCenterX
        val dy = orbCenterY - zoneCenterY
        val radius = dismissZoneSizePx * 0.88f
        return (dx * dx + dy * dy) <= (radius * radius)
    }

    private fun hideDismissZone() {
        hoveringDismiss = false
        val zone = dismissRoot ?: run {
            dismissVisible = false
            return
        }
        dismissRoot = null
        dismissParams = null
        dismissVisible = false
        try {
            wm?.removeView(zone)
        } catch (_: Throwable) {
        }
    }

    private fun currentScreenWidth(): Int = max(480, service.resources.displayMetrics.widthPixels)
    private fun currentScreenHeight(): Int = max(800, service.resources.displayMetrics.heightPixels)

    /**
     * Cincin denyut neon Cyan/Gold 3D saat mikrofon aktif mendengarkan atau AI berbicara.
     */
    private class PulseRingView(context: Context) : View(context) {
        private var orbState = OrbState.IDLE
        private var phase = 0f
        private val density = context.resources.displayMetrics.density
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

        fun setOrbState(state: OrbState) {
            orbState = state
            invalidate()
        }

        fun updatePhase(p: Float) {
            phase = p
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val baseR = min(cx, cy) - 3f * density
            if (baseR <= 4f) return

            if (orbState == OrbState.LISTENING || orbState == OrbState.SPEAKING) {
                val pulse = kotlin.math.sin(phase * Math.PI * 2.0).toFloat() * 0.5f + 0.5f
                ringPaint.strokeWidth = (2.2f + 1.4f * pulse) * density
                ringPaint.color = if (orbState == OrbState.LISTENING) {
                    Color.argb((140 + (110 * pulse).toInt()).coerceIn(0, 255), 255, 214, 0)
                } else {
                    Color.argb((140 + (110 * pulse).toInt()).coerceIn(0, 255), 0, 229, 255)
                }
                canvas.drawCircle(cx, cy, baseR - (1.5f * density), ringPaint)
            } else if (orbState == OrbState.NO_PERMISSION) {
                ringPaint.strokeWidth = 2.0f * density
                ringPaint.color = Color.argb(210, 255, 82, 82)
                canvas.drawCircle(cx, cy, baseR - (1.5f * density), ringPaint)
            }
        }
    }

    /**
     * Tab pegangan transparan minimal (8dp) ketika gelembung bersembunyi ke pinggir layar.
     */
    private class EdgeTabView(context: Context) : View(context) {
        private var dockedOnLeft = true
        private val density = context.resources.displayMetrics.density
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 11, 24, 46)
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 255, 214, 0)
            style = Paint.Style.FILL
        }

        fun setSide(onLeft: Boolean) {
            dockedOnLeft = onLeft
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 2f || h <= 4f) return
            val padY = 8f * density
            val r = w * 0.9f
            val rect = if (dockedOnLeft) {
                RectF(-r, padY, w - 1.5f * density, h - padY)
            } else {
                RectF(1.5f * density, padY, w + r, h - padY)
            }
            canvas.drawRoundRect(rect, r, r, fillPaint)
            canvas.drawRoundRect(rect, r, r, borderPaint)

            // Garis pegangan neon emas tipis di tengah tab
            val barW = 2.6f * density
            val barH = 18f * density
            val barX = if (dockedOnLeft) w * 0.45f else w * 0.55f - barW
            val barTop = (h - barH) / 2f
            canvas.drawRoundRect(
                RectF(barX, barTop, barX + barW, barTop + barH),
                barW,
                barW,
                barPaint
            )
        }
    }
}
