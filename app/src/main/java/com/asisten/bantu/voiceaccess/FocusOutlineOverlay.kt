package com.asisten.bantu.voiceaccess

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Transparent TalkBack-like focus outline. The center stays clear and the overlay never intercepts touch. */
class FocusOutlineOverlay(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var view: FocusView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    fun show(bounds: Rect) {
        if (bounds.isEmpty || bounds.width() < 2 || bounds.height() < 2) return
        val copy = Rect(bounds)
        handler.post {
            val wm = windowManager ?: return@post
            try {
                val pad = (3f * service.resources.displayMetrics.density).toInt().coerceAtLeast(2)
                val width = (copy.width() + pad * 2).coerceAtLeast(4)
                val height = (copy.height() + pad * 2).coerceAtLeast(4)
                val target = view ?: FocusView(service).also {
                    val params = WindowManager.LayoutParams(
                        width,
                        height,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = copy.left - pad
                        y = copy.top - pad
                    }
                    wm.addView(it, params)
                    view = it
                    layoutParams = params
                }
                val params = layoutParams ?: return@post
                params.width = width
                params.height = height
                params.x = copy.left - pad
                params.y = copy.top - pad
                target.setBounds(Rect(pad, pad, pad + copy.width(), pad + copy.height()))
                wm.updateViewLayout(target, params)
            } catch (_: Throwable) {
                hideLocked()
            }
        }
    }

    fun hide() { handler.post { hideLocked() } }

    private fun hideLocked() {
        val current = view ?: return
        try { windowManager?.removeView(current) } catch (_: Throwable) {}
        view = null
        layoutParams = null
    }

    private class FocusView(context: Context) : View(context) {
        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            isFocusable = false
            isClickable = false
        }

        private val density = context.resources.displayMetrics.density
        private var focusBounds: Rect? = null
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 0, 210, 235)
            style = Paint.Style.STROKE
            strokeWidth = 1.15f * density
        }

        fun setBounds(bounds: Rect) {
            focusBounds = Rect(bounds)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bounds = focusBounds ?: return
            val inset = 0.6f * density
            val rect = RectF(
                bounds.left.toFloat() + inset,
                bounds.top.toFloat() + inset,
                bounds.right.toFloat() - inset,
                bounds.bottom.toFloat() - inset
            )
            if (rect.width() <= 0f || rect.height() <= 0f) return
            // Tight word/element boundary: no glow, fill, shade, or screen-wide dim layer.
            val radius = 2f * density
            canvas.drawRoundRect(rect, radius, radius, outline)
        }
    }
}
