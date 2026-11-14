package com.asisten.bantu

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom 3D Sonar Radar HUD View untuk Mode Tongkat / NavigationActivity.
 * Memvisualisasikan sapuan radar Cyan dan blip rintangan ML Kit secara real-time.
 */
class SonarRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class ObstacleBlip(
        val nx: Float,
        val ny: Float,
        val highDanger: Boolean,
        val label: String
    )

    private var sweepAngle = 0f
    private var blips: List<ObstacleBlip> = emptyList()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 0, 229, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 229, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
    }
    private val blipDangerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")
        style = Paint.Style.FILL
    }
    private val blipWarnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private val sweepAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 2800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            sweepAngle = it.animatedValue as Float
            invalidate()
        }
    }

    fun updateObstacles(newBlips: List<ObstacleBlip>) {
        blips = newBlips
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!sweepAnimator.isStarted) sweepAnimator.start()
    }

    override fun onDetachedFromWindow() {
        sweepAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) * 0.90f
        if (r <= 12f) return

        // Concentric sonar rings
        canvas.drawCircle(cx, cy, r, ringPaint)
        canvas.drawCircle(cx, cy, r * 0.68f, ringPaint)
        canvas.drawCircle(cx, cy, r * 0.36f, ringPaint)

        // Crosshair axes
        canvas.drawLine(cx - r, cy, cx + r, cy, crossPaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, crossPaint)

        // Rotating sweep sector
        sweepPaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(Color.TRANSPARENT, Color.argb(18, 0, 229, 255), Color.argb(135, 0, 229, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.72f, 0.99f, 1f)
        )
        canvas.save()
        canvas.rotate(sweepAngle, cx, cy)
        canvas.drawCircle(cx, cy, r, sweepPaint)
        val rad = Math.toRadians(0.0)
        canvas.drawLine(cx, cy, cx + (cos(rad) * r).toFloat(), cy + (sin(rad) * r).toFloat(), beamPaint)
        canvas.restore()
        sweepPaint.shader = null

        // Draw obstacle blips
        val dotR = maxOf(8f, r * 0.055f)
        for (blip in blips) {
            val bx = cx + (blip.nx.coerceIn(-0.85f, 0.85f) * r)
            val by = cy + (blip.ny.coerceIn(-0.85f, 0.85f) * r)
            canvas.drawCircle(bx, by, dotR * 1.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (blip.highDanger) Color.argb(75, 255, 82, 82) else Color.argb(75, 255, 214, 0)
            })
            canvas.drawCircle(bx, by, dotR, if (blip.highDanger) blipDangerPaint else blipWarnPaint)
            if (blip.label.isNotEmpty()) {
                canvas.drawText(blip.label.take(16), bx, by - dotR * 1.4f, textPaint)
            }
        }
    }
}
