package com.nova.assistant

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

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING, WAKE_ACTIVE, PAUSED, ERROR }

/**
 * Nova's reactive orb. Deliberately built with 2D Canvas + gradients + animator,
 * NOT a real 3D/OpenGL mesh — a true 3D renderer is exactly the kind of thing
 * that can lag or drain battery on an average phone, which the spec explicitly
 * asked to avoid. This gives a genuinely animated glow/pulse/rotating-ring look
 * (a real, honest "2.5D" effect) that reacts to state, at negligible GPU cost.
 */
class NovaOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var state: OrbState = OrbState.IDLE
    private var pulsePhase = 0f
    private var ringRotation = 0f

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulsePhase = it.animatedValue as Float
            invalidate()
        }
    }

    private val rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            ringRotation = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        rotateAnimator.cancel()
        super.onDetachedFromWindow()
    }

    fun setState(newState: OrbState) {
        if (state == newState) return
        state = newState
        if (state == OrbState.THINKING || state == OrbState.WAKE_ACTIVE) {
            if (!rotateAnimator.isStarted) rotateAnimator.start()
        } else {
            rotateAnimator.cancel()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (minOf(width, height) / 2f) * 0.55f
        if (baseRadius <= 0f) return

        val (coreColor, glowColor) = colorsFor(state)

        val pulseScale = when (state) {
            OrbState.LISTENING -> 1f + 0.18f * pulsePhase
            OrbState.SPEAKING -> 1f + 0.12f * pulsePhase
            OrbState.WAKE_ACTIVE -> 1f + 0.06f * pulsePhase
            OrbState.PAUSED -> 0.88f
            else -> 1f + 0.04f * pulsePhase
        }
        val radius = baseRadius * pulseScale

        glowPaint.shader = RadialGradient(
            cx, cy, radius * 2.2f,
            intArrayOf(
                Color.argb(90, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 2.2f, glowPaint)

        corePaint.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.6f,
            intArrayOf(lighten(coreColor), coreColor, darken(coreColor)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, corePaint)

        if (state == OrbState.THINKING || state == OrbState.WAKE_ACTIVE) {
            ringPaint.color = glowColor
            ringPaint.alpha = 210
            val ringRadius = radius * 1.35f
            val sweep = if (state == OrbState.THINKING) 100f else 45f
            canvas.drawArc(
                cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius,
                ringRotation, sweep, false, ringPaint
            )
        }

        if (state == OrbState.ERROR) {
            ringPaint.color = Color.parseColor("#FF5C5C")
            ringPaint.alpha = (150 + 100 * pulsePhase).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, radius * 1.15f, ringPaint)
        }
    }

    private fun colorsFor(s: OrbState): Pair<Int, Int> {
        val cyan = Color.parseColor("#4CE0D2")
        val violet = Color.parseColor("#8B5CF6")
        val amber = Color.parseColor("#FFB86B")
        val dim = Color.parseColor("#5B6478")
        val red = Color.parseColor("#FF5C5C")
        return when (s) {
            OrbState.IDLE -> cyan to cyan
            OrbState.LISTENING -> cyan to Color.parseColor("#7CF5E8")
            OrbState.THINKING -> violet to violet
            OrbState.SPEAKING -> amber to amber
            OrbState.WAKE_ACTIVE -> violet to cyan
            OrbState.PAUSED -> dim to dim
            OrbState.ERROR -> red to red
        }
    }

    private fun lighten(color: Int): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * 0.4f).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * 0.4f).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * 0.4f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun darken(color: Int): Int {
        val r = (Color.red(color) * 0.5f).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * 0.5f).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * 0.5f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
