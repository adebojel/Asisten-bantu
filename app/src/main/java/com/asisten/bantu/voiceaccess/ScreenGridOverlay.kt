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
 * Grid Koordinat Presisi 3x3 (Nomor 1-9) dengan dukungan Nested Zoom/Drill-Down
 * untuk WebView, Peta, PDF, dan layar yang tidak memiliki label tombol standar.
 */
class ScreenGridOverlay(private val service: AccessibilityService) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private var gridView: GridCanvasView? = null
    @Volatile
    private var showing = false
    private var activeRegion: RectF? = null

    fun isShowing(): Boolean = showing

    fun showFullGrid() {
        mainHandler.post {
            val dm = service.resources.displayMetrics
            activeRegion = RectF(0f, 0f, dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
            attachOrUpdateLocked()
        }
    }

    fun zoomIntoCell(cellNumber: Int): Boolean {
        if (cellNumber !in 1..9) return false
        val region = currentRegionOrScreen()
        val sub = computeCellRect(region, cellNumber)
        activeRegion = sub
        mainHandler.post { attachOrUpdateLocked() }
        return true
    }

    fun getCellCenter(cellNumber: Int): Pair<Float, Float>? {
        if (cellNumber !in 1..9) return null
        val cell = computeCellRect(currentRegionOrScreen(), cellNumber)
        return Pair(cell.centerX(), cell.centerY())
    }

    fun hide() {
        mainHandler.post {
            val view = gridView ?: run {
                showing = false
                return@post
            }
            try {
                wm?.removeView(view)
            } catch (_: Throwable) {
            }
            gridView = null
            showing = false
            activeRegion = null
        }
    }

    private fun attachOrUpdateLocked() {
        if (wm == null) return
        try {
            val view = gridView ?: GridCanvasView(service).also { gv ->
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
                wm.addView(gv, lp)
                gridView = gv
            }
            view.setRegion(currentRegionOrScreen())
            showing = true
        } catch (_: Throwable) {
            showing = false
        }
    }

    private fun currentRegionOrScreen(): RectF {
        val current = activeRegion
        if (current != null) return current
        val dm = service.resources.displayMetrics
        return RectF(0f, 0f, dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
    }

    private fun computeCellRect(region: RectF, cellNumber: Int): RectF {
        val idx = cellNumber - 1
        val row = idx / 3
        val col = idx % 3
        val cellW = region.width() / 3f
        val cellH = region.height() / 3f
        val l = region.left + col * cellW
        val t = region.top + row * cellH
        return RectF(l, t, l + cellW, t + cellH)
    }

    private class GridCanvasView(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density
        private var region = RectF(0f, 0f, 1080f, 1920f)

        private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 5, 8, 17)
            style = Paint.Style.FILL
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 0, 229, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
        }
        private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 11, 19, 36)
            style = Paint.Style.FILL
        }
        private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD600")
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }
        private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD600")
            textSize = 18f * density
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        fun setRegion(r: RectF) {
            region = RectF(r)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(region, shadePaint)
            canvas.drawRect(region, linePaint)

            val cellW = region.width() / 3f
            val cellH = region.height() / 3f

            for (i in 1..2) {
                val vx = region.left + i * cellW
                canvas.drawLine(vx, region.top, vx, region.bottom, linePaint)
                val hy = region.top + i * cellH
                canvas.drawLine(region.left, hy, region.right, hy, linePaint)
            }

            val r = 18f * density
            val fm = numberPaint.fontMetrics
            val dy = -(fm.ascent + fm.descent) / 2f

            for (row in 0..2) {
                for (col in 0..2) {
                    val num = row * 3 + col + 1
                    val cx = region.left + col * cellW + cellW / 2f
                    val cy = region.top + row * cellH + cellH / 2f
                    canvas.drawCircle(cx, cy, r, badgeBgPaint)
                    canvas.drawCircle(cx, cy, r, badgeBorderPaint)
                    canvas.drawText(num.toString(), cx, cy + dy, numberPaint)
                }
            }
        }
    }
}
