package com.f150headunit.app.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Horizontal segmented gauge in the E→F / C→H style of the reference UI:
 * rounded segments with gaps, blue fill, turning red in alarm territory
 * ([AlarmMode.LOW] for fuel, [AlarmMode.HIGH] for coolant).
 */
class SegmentGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class AlarmMode { NONE, LOW, HIGH }

    var segmentCount: Int = 8
        set(value) {
            field = value.coerceAtLeast(2)
            requestLayout()
            invalidate()
        }

    var alarmMode: AlarmMode = AlarmMode.NONE
        set(value) {
            field = value
            refreshPaint()
            invalidate()
        }

    /** Current fill level, 0f..1f. */
    var fraction: Float = 0f
        private set

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_FILL
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_TRACK
    }
    private val segmentRect = RectF()
    private var animator: ValueAnimator? = null

    fun setFraction(value: Float, animate: Boolean = true) {
        val target = value.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate) {
            fraction = target
            refreshPaint()
            invalidate()
            return
        }
        val start = fraction
        animator = ValueAnimator.ofFloat(start, target).apply {
            duration = 350
            addUpdateListener { anim ->
                fraction = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
        fraction = target
        refreshPaint()
    }

    private fun isAlarmed(): Boolean = when (alarmMode) {
        AlarmMode.NONE -> false
        AlarmMode.LOW -> fraction <= LOW_ALARM_THRESHOLD
        AlarmMode.HIGH -> fraction >= HIGH_ALARM_THRESHOLD
    }

    private fun refreshPaint() {
        fillPaint.color = if (isAlarmed()) COLOR_ALARM else COLOR_FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = ((SEGMENT_DP + GAP_DP) * segmentCount * density).roundToInt()
        val desiredHeight = (HEIGHT_DP * density).roundToInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentW = width - paddingLeft - paddingRight
        val contentH = height - paddingTop - paddingBottom
        if (contentW <= 0 || contentH <= 0) return

        val gap = min(GAP_DP * density, contentW * 0.04f)
        val segmentW = (contentW - gap * (segmentCount - 1)) / segmentCount
        val radius = min(contentH / 2f, 4f * density)
        val filledSegments = (fraction * segmentCount).roundToInt()

        for (i in 0 until segmentCount) {
            val left = paddingLeft + i * (segmentW + gap)
            segmentRect.set(
                left,
                paddingTop.toFloat(),
                left + segmentW,
                (paddingTop + contentH).toFloat(),
            )
            val paint = if (i < filledSegments) fillPaint else trackPaint
            canvas.drawRoundRect(segmentRect, radius, radius, paint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SEGMENT_DP = 14f
        const val GAP_DP = 3f
        const val HEIGHT_DP = 12f
        const val LOW_ALARM_THRESHOLD = 0.12f
        const val HIGH_ALARM_THRESHOLD = 0.9f

        val COLOR_FILL = 0xFF5B9DFF.toInt() // accent blue
        val COLOR_TRACK = 0xFF333B47.toInt() // empty segment slate
        val COLOR_ALARM = 0xFFE5484D.toInt() // alarm red
    }
}
