package com.f150headunit.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.f150headunit.app.model.DoorState
import kotlin.math.min

/**
 * Minimal top-down pickup schematic used in the alert area. Closed doors are
 * drawn as subtle slate lines along the body edge; an open door swings out in
 * red with a dashed swing arc — matching the "Driver Door Open" graphic from
 * the reference UI. Hood and tailgate highlight their respective body edges.
 */
class TruckOutlineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var doors = DoorState()

    private val density = resources.displayMetrics.density

    private val bodyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_BODY_FILL
    }
    private val bodyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = COLOR_BODY_STROKE
    }
    private val cabinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_CABIN
    }
    private val bedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = COLOR_BED
    }
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_WHEEL
    }
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_LIGHT
    }
    private val closedDoorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        color = COLOR_CLOSED_DOOR
    }
    private val openDoorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        strokeCap = Paint.Cap.ROUND
        color = COLOR_OPEN_DOOR
    }
    private val swingArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(3f * density, 4f * density), 0f)
        color = COLOR_SWING_ARC
    }

    private val rect = RectF()
    private val arcPath = Path()

    fun setDoors(newDoors: DoorState) {
        if (newDoors != doors) {
            doors = newDoors
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (110f * density).toInt()
        val desiredHeight = (165f * density).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return
        val ox = paddingLeft.toFloat()
        val oy = paddingTop.toFloat()

        // Body occupies the middle of the canvas; truck faces UP.
        val bodyL = ox + 0.24f * w
        val bodyR = ox + 0.76f * w
        val bodyT = oy + 0.05f * h
        val bodyB = oy + 0.95f * h
        val bodyRadius = 0.12f * w

        // Wheels (behind the body).
        val wheelW = 0.075f * w
        val wheelH = 0.115f * h
        drawWheel(canvas, bodyL - wheelW * 0.85f, bodyT + 0.09f * h, wheelW, wheelH)
        drawWheel(canvas, bodyR - wheelW * 0.15f, bodyT + 0.09f * h, wheelW, wheelH)
        drawWheel(canvas, bodyL - wheelW * 0.85f, bodyB - 0.09f * h - wheelH, wheelW, wheelH)
        drawWheel(canvas, bodyR - wheelW * 0.15f, bodyB - 0.09f * h - wheelH, wheelW, wheelH)

        // Body shell.
        rect.set(bodyL, bodyT, bodyR, bodyB)
        canvas.drawRoundRect(rect, bodyRadius, bodyRadius, bodyFillPaint)
        canvas.drawRoundRect(rect, bodyRadius, bodyRadius, bodyStrokePaint)

        // Headlights.
        val lightW = 0.055f * w
        val lightH = 0.018f * h
        rect.set(bodyL + 0.035f * w, bodyT - lightH * 0.4f, bodyL + 0.035f * w + lightW, bodyT + lightH)
        canvas.drawRoundRect(rect, lightH / 2f, lightH / 2f, lightPaint)
        rect.set(bodyR - 0.035f * w - lightW, bodyT - lightH * 0.4f, bodyR - 0.035f * w, bodyT + lightH)
        canvas.drawRoundRect(rect, lightH / 2f, lightH / 2f, lightPaint)

        // Cabin (windshield line at its top edge is implied by the inset).
        val cabinT = bodyT + 0.10f * h
        val cabinB = bodyT + 0.36f * h
        rect.set(bodyL + 0.045f * w, cabinT, bodyR - 0.045f * w, cabinB)
        canvas.drawRoundRect(rect, 0.06f * w, 0.06f * w, cabinPaint)

        // Pickup bed walls.
        val bedT = bodyT + 0.44f * h
        rect.set(bodyL + 0.03f * w, bedT, bodyR - 0.03f * w, bodyB - 0.025f * h)
        canvas.drawRoundRect(rect, 0.04f * w, 0.04f * w, bedPaint)

        // Doors as short lines along the flanks. Front axle of the cabin is up.
        val frontDoorT = bodyT + 0.075f * h
        val frontDoorB = bodyT + 0.245f * h
        val rearDoorT = bodyT + 0.265f * h
        val rearDoorB = bodyT + 0.42f * h

        drawSideDoor(canvas, bodyL, frontDoorT, frontDoorB, left = true, open = doors.driver)
        drawSideDoor(canvas, bodyR, frontDoorT, frontDoorB, left = false, open = doors.passenger)
        drawSideDoor(canvas, bodyL, rearDoorT, rearDoorB, left = true, open = doors.rearLeft)
        drawSideDoor(canvas, bodyR, rearDoorT, rearDoorB, left = false, open = doors.rearRight)

        // Hood: red slash across the top edge when open.
        if (doors.hood) {
            canvas.drawLine(bodyL + 0.04f * w, bodyT + 0.005f * h, bodyR - 0.04f * w, bodyT + 0.005f * h, openDoorPaint)
        }

        // Tailgate: lowered flap = red bar below the rear edge.
        if (doors.tailgate) {
            val flapT = bodyB + 0.012f * h
            rect.set(bodyL + 0.03f * w, flapT, bodyR - 0.03f * w, flapT + 0.03f * h)
            canvas.drawRoundRect(rect, 0.015f * h, 0.015f * h, openDoorPaint)
        }
    }

    private fun drawWheel(canvas: Canvas, left: Float, top: Float, w: Float, h: Float) {
        rect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, min(w, h) * 0.35f, min(w, h) * 0.35f, wheelPaint)
    }

    /**
     * Draws one door along a body side. The hinge is at the front (top, since
     * the truck faces up); when open the panel swings outward with a dashed arc.
     */
    private fun drawSideDoor(
        canvas: Canvas,
        x: Float,
        top: Float,
        bottom: Float,
        left: Boolean,
        open: Boolean,
    ) {
        if (!open) {
            canvas.drawLine(x, top, x, bottom, closedDoorPaint)
            return
        }
        val length = bottom - top
        val hingeX = x
        val hingeY = top
        val sweep = SWING_DEGREES * (if (left) -1f else 1f)

        // Dashed swing arc centered on the hinge.
        arcPath.reset()
        rect.set(hingeX - length, hingeY - length, hingeX + length, hingeY + length)
        arcPath.arcTo(rect, 90f, sweep)
        canvas.drawPath(arcPath, swingArcPaint)

        // Door panel rotated outward around the hinge.
        canvas.save()
        canvas.rotate(sweep, hingeX, hingeY)
        canvas.drawLine(hingeX, hingeY, hingeX, hingeY + length, openDoorPaint)
        canvas.restore()
    }

    private companion object {
        const val SWING_DEGREES = 34f

        val COLOR_BODY_FILL = 0xFF1C2129.toInt()
        val COLOR_BODY_STROKE = 0xFF3E4854.toInt()
        val COLOR_CABIN = 0xFF2B333E.toInt()
        val COLOR_BED = 0xFF39434F.toInt()
        val COLOR_WHEEL = 0xFF12161C.toInt()
        val COLOR_LIGHT = 0xFF56616E.toInt()
        val COLOR_CLOSED_DOOR = 0xFF5B6470.toInt()
        val COLOR_OPEN_DOOR = 0xFFE5484D.toInt()
        val COLOR_SWING_ARC = 0x80E5484D.toInt()
    }
}
