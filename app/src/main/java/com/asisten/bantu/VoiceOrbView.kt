package com.asisten.bantu

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom 3D Holographic Voice Orb View bergaya Cybernetic HUD (Cyan #00E5FF & Gold #FFD600)
 * dengan animasi gelombang suara real-time untuk MainActivity & ChatActivity.
 */
class VoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class OrbState { IDLE, LISTENING, SPEAKING }

    private var state: OrbState = OrbState.IDLE
    private var pulsePhase = 0f
    private var amplitude = 0.2f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val animator = ValueAnimator.ofFloat(0f, (Math.PI * 2.0).toFloat()).apply {
        duration = 2600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulsePhase = it.animatedValue as Float
            invalidate()
        }
    }

    fun setOrbState(newState: OrbState) {
        if (state == newState) return
        state = newState
        if (newState == OrbState.IDLE) {
            amplitude = 0.18f
        }
        invalidate()
    }

    fun updateRmsDb(rmsDb: Float) {
        val norm = ((rmsDb + 2f) / 12f).coerceIn(0.15f, 1.0f)
        amplitude = amplitude * 0.55f + norm * 0.45f
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(cx, cy) * 0.92f
        if (maxR <= 8f) return

        val breathe = (sin(pulsePhase.toDouble()).toFloat() * 0.5f + 0.5f)
        val activeBoost = when (state) {
            OrbState.IDLE -> 0.06f * breathe
            OrbState.LISTENING -> 0.14f * amplitude
            OrbState.SPEAKING -> 0.12f * (0.4f + 0.6f * breathe)
        }

        val coreRadius = maxR * (0.46f + activeBoost * 0.4f)

        // 1) Outer halo glow
        val glowColor = when (state) {
            OrbState.LISTENING -> Color.argb(110, 255, 214, 0)
            OrbState.SPEAKING -> Color.argb(120, 0, 229, 255)
            OrbState.IDLE -> Color.argb(80, 0, 229, 255)
        }
        corePaint.shader = RadialGradient(
            cx,
            cy,
            maxR,
            intArrayOf(glowColor, Color.argb(24, 0, 229, 255), Color.TRANSPARENT),
            floatArrayOf(0.25f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, maxR, corePaint)
        corePaint.shader = null

        // 2) Dual orbital HUD rings
        ringPaint.strokeWidth = maxR * 0.032f
        ringPaint.color = Color.argb(135, 0, 229, 255)
        canvas.drawCircle(cx, cy, maxR * (0.76f + 0.05f * breathe), ringPaint)

        ringPaint.strokeWidth = maxR * 0.022f
        ringPaint.color = if (state == OrbState.LISTENING) Color.argb(200, 255, 214, 0) else Color.argb(115, 255, 214, 0)
        canvas.drawCircle(cx, cy, maxR * (0.63f + activeBoost * 0.45f), ringPaint)

        // 3) Radial waveform spokes
        val spokeCount = 24
        wavePaint.strokeWidth = maxR * 0.035f
        for (i in 0 until spokeCount) {
            val angle = (i.toFloat() / spokeCount) * (Math.PI * 2.0) + pulsePhase * 0.35
            val wave = sin(angle * 3.0 + pulsePhase * 2.0).toFloat() * 0.5f + 0.5f
            val lenFactor = when (state) {
                OrbState.IDLE -> 0.05f + 0.04f * wave
                OrbState.LISTENING -> 0.08f + 0.16f * amplitude * wave
                OrbState.SPEAKING -> 0.08f + 0.14f * wave
            }
            val inner = maxR * 0.54f
            val outer = inner + maxR * lenFactor
            wavePaint.color = if (i % 3 == 0 && state == OrbState.LISTENING) {
                Color.argb(220, 255, 214, 0)
            } else {
                Color.argb(185, 0, 229, 255)
            }
            canvas.drawLine(
                cx + (cos(angle) * inner).toFloat(),
                cy + (sin(angle) * inner).toFloat(),
                cx + (cos(angle) * outer).toFloat(),
                cy + (sin(angle) * outer).toFloat(),
                wavePaint
            )
        }

        // 4) 3D Glassmorphic Core Sphere
        val startCol = when (state) {
            OrbState.LISTENING -> Color.parseColor("#FFF59D")
            OrbState.SPEAKING -> Color.parseColor("#B2FEFF")
            OrbState.IDLE -> Color.parseColor("#80F3FF")
        }
        val midCol = when (state) {
            OrbState.LISTENING -> Color.parseColor("#FFD600")
            else -> Color.parseColor("#00E5FF")
        }
        val endCol = Color.parseColor("#0A1D38")

        corePaint.shader = RadialGradient(
            cx - coreRadius * 0.25f,
            cy - coreRadius * 0.28f,
            coreRadius * 1.15f,
            intArrayOf(startCol, midCol, endCol),
            floatArrayOf(0.05f, 0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius, corePaint)
        corePaint.shader = null

        // Top specular glass highlight
        canvas.drawOval(
            cx - coreRadius * 0.55f,
            cy - coreRadius * 0.72f,
            cx + coreRadius * 0.45f,
            cy - coreRadius * 0.18f,
            highlightPaint
        )
    }
}
