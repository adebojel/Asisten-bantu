package com.asisten.bantu.voiceaccess

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Menampilkan lencana nomor transparan Cyan/Gold di atas setiap elemen layar terindeks
 * (mirip Google Voice Access) via TYPE_ACCESSIBILITY_OVERLAY tanpa menyentuh aplikasi target.
 */
class NumberBadgeOverlay(private val service: AccessibilityService) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var overlayView: BadgeCanvasView? = null
    @Volatile
    private var showing = false

    fun isShowing(): Boolean = showing

    fun show(nodes: List<NodeIndexer.IndexedNode>) {
        mainHandler.post {
            if (wm == null) return@post
            if (nodes.isEmpty()) {
                hide()
                return@post
            }
            try {
                val view = overlayView ?: BadgeCanvasView(service).also { canvasView ->
                    val lp = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                    }
                    wm.addView(canvasView, lp)
                    overlayView = canvasView
                }
                view.updateNodes(nodes)
                showing = true
            } catch (_: Throwable) {
                showing = false
            }
        }
    }

    fun hide() {
        mainHandler.post {
            val view = overlayView ?: run {
                showing = false
                return@post
            }
            try {
                wm?.removeView(view)
            } catch (_: Throwable) {
            }
            overlayView = null
            showing = false
        }
    }

    private class BadgeCanvasView(context: Context) : View(context) {
        private var nodes: List<NodeIndexer.IndexedNode> = emptyList()
        private val density = context.resources.displayMetrics.density

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(228, 10, 18, 34)
            style = Paint.Style.FILL
        }
        private val borderCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }
        private val borderGoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD600")
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        fun updateNodes(newNodes: List<NodeIndexer.IndexedNode>) {
            nodes = newNodes
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val padH = 8f * density
            val badgeH = 20f * density
            val corner = 8f * density
            val fm = textPaint.fontMetrics
            val textOffset = -(fm.ascent + fm.descent) / 2f

            for (item in nodes) {
                val label = item.number.toString()
                val textW = textPaint.measureText(label)
                val badgeW = maxOf(22f * density, textW + padH * 1.6f)
                val left = item.bounds.left.toFloat().coerceIn(4f * density, (width - badgeW - 4f * density).coerceAtLeast(4f))
                val top = item.bounds.top.toFloat().coerceIn(4f * density, (height - badgeH - 4f * density).coerceAtLeast(4f))
                val rect = RectF(left, top, left + badgeW, top + badgeH)

                canvas.drawRoundRect(rect, corner, corner, bgPaint)
                canvas.drawRoundRect(
                    rect,
                    corner,
                    corner,
                    if (item.isEditable) borderGoldPaint else borderCyanPaint
                )
                canvas.drawText(label, rect.centerX(), rect.centerY() + textOffset, textPaint)
            }
        }
    }
}
