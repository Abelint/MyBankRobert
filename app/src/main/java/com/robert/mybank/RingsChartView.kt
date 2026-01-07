package com.robert.mybank

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

class RingsChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // прогресс 0..1
    private var pOuter = 0f
    private var pMiddle = 0f
    private var pInner = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 50
    }

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFF3B30.toInt() // красный
    }

    private val middlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFCC00.toInt() // жёлтый
    }


    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF007AFF.toInt() // синий
    }

    private val rectOuter = RectF()
    private val rectMiddle = RectF()
    private val rectInner = RectF()

    private var stroke = dp(14f)
    private var gap = dp(8f)

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val size = min(w, h).toFloat()
        val cx = w / 2f
        val cy = h / 2f

        // радиусы трёх колец
        val rOuter = size / 2f - stroke / 2f
        val rMiddle = rOuter - stroke - gap
        val rInner = rMiddle - stroke - gap

        rectOuter.set(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter)
        rectMiddle.set(cx - rMiddle, cy - rMiddle, cx + rMiddle, cy + rMiddle)
        rectInner.set(cx - rInner, cy - rInner, cx + rInner, cy + rInner)

        outerPaint.strokeWidth = stroke
        middlePaint.strokeWidth = stroke
        innerPaint.strokeWidth = stroke
        trackPaint.strokeWidth = stroke
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // старт сверху (12 часов) и по часовой
        val startAngle = -90f

        // TRACK (фон) для каждого кольца
        drawTrack(canvas, rectOuter)
        drawTrack(canvas, rectMiddle)
        drawTrack(canvas, rectInner)

        // PROGRESS
        canvas.drawArc(rectOuter, startAngle, 360f * pOuter, false, outerPaint)
        canvas.drawArc(rectMiddle, startAngle, 360f * pMiddle, false, middlePaint)
        canvas.drawArc(rectInner, startAngle, 360f * pInner, false, innerPaint)
    }

    private fun drawTrack(canvas: Canvas, rect: RectF) {
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)
    }

    /**
     * Плавно анимируем к целевым значениям (0..1) за durationMs
     */
    fun animateTo(outer: Float, middle: Float, inner: Float, durationMs: Long = 1000L) {
        val startOuter = pOuter
        val startMiddle = pMiddle
        val startInner = pInner

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                pOuter = startOuter + (outer - startOuter) * t
                pMiddle = startMiddle + (middle - startMiddle) * t
                pInner = startInner + (inner - startInner) * t
                invalidate()
            }
            start()
        }
    }

    fun setProgressInstant(outer: Float, middle: Float, inner: Float) {
        pOuter = outer.coerceIn(0f, 1f)
        pMiddle = middle.coerceIn(0f, 1f)
        pInner = inner.coerceIn(0f, 1f)
        invalidate()
    }
}
