package com.f150headunit.app.can

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Bench/demo source: synthesizes the very frames a real adapter would deliver
 * (same IDs + encodings as [ExampleHsCanDecoder]), so the entire decode →
 * repository → UI pipeline is exercised without hardware.
 *
 * Scenario: cruise wobble around ~25 mph, compass hovering around NW, coolant
 * warming from cold soak to operating temp, fuel slowly falling, and doors
 * cycling open/closed so the alert area periodically lights up.
 */
class SimulatedCanDataSource : CanDataSource {

    override val sourceName: String = "Simulation"
    override var listener: ((CanFrame) -> Unit)? = null
    override var statusListener: ((String) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var tick = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            emit()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun start(context: Context) {
        if (running) return
        running = true
        statusListener?.invoke("Bench simulation — no hardware")
        handler.post(ticker)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(ticker)
        listener = null
        statusListener = null
    }

    private fun emit() {
        val t = tick++ * TICK_MS / 1000f // seconds since start

        // --- 0x201 speed: wobbling cruise between ~2 and ~48 mph ---
        val mph = (25.0 + 21.0 * sin(t * 0.13) + 2.5 * sin(t * 1.05)).toFloat().coerceAtLeast(0f)
        val kmhByte = (mph * KMH_PER_MPH).roundToInt().coerceIn(0, 255)
        send(ExampleHsCanDecoder.ID_SPEED, byteArrayOf(kmhByte.toByte()))

        // --- 0x214 temps: ambient steady ~72°F, coolant warms to ~195°F ---
        val ambientC = 22 + (0.5 * sin(t * 0.02)).roundToInt()
        val coolantF = 95f + 100f * minOf(1f, t / 90f) + (2.5f * sin(t * 0.4).toFloat())
        val coolantC = ((coolantF - 32f) * 5f / 9f).roundToInt()
        send(
            ExampleHsCanDecoder.ID_TEMPS,
            byteArrayOf((ambientC + 40).coerceIn(0, 255).toByte(), (coolantC + 40).coerceIn(0, 255).toByte()),
        )

        // --- 0x2A0 body: driver door opens early in each 45 s cycle ---
        var body = 0
        if ((t % 45f) < 14f) body = body or 0x01            // driver door
        if ((t % 45f) in 15f..19f) body = body or 0x02      // passenger door briefly too
        if ((t % 150f) in 70f..79f) body = body or 0x04     // rear-left occasionally
        send(ExampleHsCanDecoder.ID_BODY_STATUS, byteArrayOf(body.toByte()))

        // --- 0x2F0 fuel: ~63% falling very slowly, range follow ---
        val fuelPct = (63f - t * 0.01f).coerceAtLeast(4f).roundToInt()
        val rangeMi = (fuelPct * 4.9f).roundToInt().coerceIn(0, 0xFFFF)
        send(
            ExampleHsCanDecoder.ID_FUEL,
            byteArrayOf(
                fuelPct.toByte(),
                (rangeMi and 0xFF).toByte(),
                (rangeMi shr 8 and 0xFF).toByte(),
            ),
        )

        // --- 0x300 heading: drifting around NW (≈292°) ---
        val headingHalfDeg = ((292f + 10f * sin(t * 0.05).toFloat()) * 2f).roundToInt()
        send(
            ExampleHsCanDecoder.ID_HEADING,
            byteArrayOf((headingHalfDeg and 0xFF).toByte(), (headingHalfDeg shr 8 and 0xFF).toByte()),
        )
    }

    private fun send(id: Int, data: ByteArray) {
        listener?.invoke(CanFrame(id, data))
    }

    companion object {
        private const val TICK_MS = 200L
        private const val KMH_PER_MPH = 1.609344f
    }
}
