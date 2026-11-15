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

    fun show(bounds: Rect) {
        if (bounds.isEmpty || bounds.width() < 2 || bounds.height() < 2) return
        val copy = Rect(bounds)
        handler.post {
            val wm = windowManager ?: return@post
            try {
                val target = view ?: FocusView(service).also {
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply { gravity = Gravity.TOP or Gravity.START }
                    wm.addView(it, params)
                    view = it
                }
                target.setBounds(copy)
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
    }

    private class FocusView(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density
        private var focusBounds: Rect? = null
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = 7f * density
        }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
        }

        fun setBounds(bounds: Rect) {
            focusBounds = Rect(bounds)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bounds = focusBounds ?: return
            val inset = 2f * density
            val rect = RectF(
                bounds.left.toFloat() + inset,
                bounds.top.toFloat() + inset,
                bounds.right.toFloat() - inset,
                bounds.bottom.toFloat() - inset
            )
            if (rect.width() <= 0f || rect.height() <= 0f) return
            val radius = 7f * density
            canvas.drawRoundRect(rect, radius, radius, glow)
            canvas.drawRoundRect(rect, radius, radius, outline)
        }
    }
}
