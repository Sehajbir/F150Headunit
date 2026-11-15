package com.f150headunit.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.f150headunit.app.R
import com.f150headunit.app.data.VehicleRepository
import com.f150headunit.app.model.BodyPanel
import com.f150headunit.app.model.Compass
import com.f150headunit.app.model.Units
import com.f150headunit.app.model.VehicleState
import com.f150headunit.app.settings.Settings
import com.f150headunit.app.ui.views.SegmentGaugeView
import com.f150headunit.app.ui.views.TruckOutlineView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wires the left third of the screen (time/heading/temp bar, big speed,
 * door alerts, fuel/range/coolant strip) to the live [VehicleState].
 */
class TruckInfoController(
    root: View,
    private val repository: VehicleRepository,
    private val settings: Settings,
) {

    /** One rendered alert line, e.g. "Driver Door" + "Open". */
    private data class Alert(val title: String, val status: String)

    private val context: Context = root.context

    private val textTime = root.findViewById<TextView>(R.id.text_time)
    private val textHeading = root.findViewById<TextView>(R.id.text_heading)
    private val textOutsideTemp = root.findViewById<TextView>(R.id.text_outside_temp)
    private val textSpeedValue = root.findViewById<TextView>(R.id.text_speed_value)
    private val textSpeedUnit = root.findViewById<TextView>(R.id.text_speed_unit)
    private val textDemoBadge = root.findViewById<TextView>(R.id.text_demo_badge)
    private val truckOutline = root.findViewById<TruckOutlineView>(R.id.view_truck_outline)
    private val textAlertTitle = root.findViewById<TextView>(R.id.text_alert_title)
    private val textAlertStatus = root.findViewById<TextView>(R.id.text_alert_status)
    private val textAlertQueue = root.findViewById<TextView>(R.id.text_alert_queue)
    private val gaugeFuel = root.findViewById<SegmentGaugeView>(R.id.gauge_fuel)
    private val textFuelValue = root.findViewById<TextView>(R.id.text_fuel_value)
    private val textRangeValue = root.findViewById<TextView>(R.id.text_range_value)
    private val gaugeCoolant = root.findViewById<SegmentGaugeView>(R.id.gauge_coolant)
    private val textCoolantValue = root.findViewById<TextView>(R.id.text_coolant_value)

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private var alerts: List<Alert> = emptyList()
    private var alertIndex = 0

    // Ticks once per minute boundary to keep the clock fresh.
    private val clockTicker = object : Runnable {
        override fun run() {
            textTime.text = timeFormat.format(Date())
            handler.postDelayed(this, CLOCK_TICK_MS)
        }
    }

    // Rotates through simultaneous alerts so the driver sees each one.
    private val alertCycler = object : Runnable {
        override fun run() {
            if (alerts.size > 1) {
                alertIndex = (alertIndex + 1) % alerts.size
                showCurrentAlert()
                handler.postDelayed(this, ALERT_CYCLE_MS)
            }
        }
    }

    private val stateListener = object : VehicleRepository.Listener {
        override fun onVehicleStateChanged(state: VehicleState) {
            render(state)
        }
    }

    fun attach() {
        gaugeFuel.alarmMode = SegmentGaugeView.AlarmMode.LOW
        gaugeCoolant.alarmMode = SegmentGaugeView.AlarmMode.HIGH
        textTime.text = timeFormat.format(Date())
        handler.post(clockTicker)
        repository.addListener(stateListener)
    }

    fun detach() {
        handler.removeCallbacks(clockTicker)
        handler.removeCallbacks(alertCycler)
        repository.removeListener(stateListener)
    }

    private fun render(state: VehicleState) {
        // --- Speed ---
        val metric = settings.speedInKmh
        textSpeedValue.text =
            if (state.speedValid) Units.formatSpeedValue(state.speedMph, metric) else "–"
        textSpeedUnit.setText(
            if (metric) R.string.speed_unit_kmh else R.string.speed_unit_mph,
        )

        // --- Top bar ---
        textHeading.text = if (state.headingValid) Compass.fromDegrees(state.headingDegrees) else "—"
        textOutsideTemp.text =
            if (state.outsideTempValid) {
                Units.formatTemperature(state.outsideTempF, settings.temperatureInCelsius)
            } else {
                "--"
            }

        // --- Demo badge (top of the panel, only while simulating) ---
        textDemoBadge.visibility =
            if (repository.activeSource == VehicleRepository.Source.SIMULATION) View.VISIBLE else View.GONE

        // --- Alerts ---
        truckOutline.setDoors(state.doors)
        val newAlerts = buildAlerts(state)
        if (newAlerts != alerts) {
            alerts = newAlerts
            alertIndex = 0
            handler.removeCallbacks(alertCycler)
            if (alerts.size > 1) handler.postDelayed(alertCycler, ALERT_CYCLE_MS)
            showCurrentAlert()
        }

        // --- Bottom strip ---
        if (state.fuelValid) {
            gaugeFuel.setFraction(state.fuelLevelPercent / 100f)
            textFuelValue.text =
                String.format(Locale.US, "%.0f%%", state.fuelLevelPercent)
        } else {
            textFuelValue.setText(R.string.placeholder_dash)
        }
        textRangeValue.text =
            if (state.rangeValid) Units.formatRange(state.rangeMiles, metric) else "--"
        if (state.coolantValid) {
            val span = VehicleState.COOLANT_GAUGE_MAX_F - VehicleState.COOLANT_GAUGE_MIN_F
            val fraction = (state.coolantTempF - VehicleState.COOLANT_GAUGE_MIN_F) / span
            gaugeCoolant.setFraction(fraction)
            textCoolantValue.text = Units.formatTemperature(state.coolantTempF, settings.temperatureInCelsius)
        } else {
            textCoolantValue.setText(R.string.placeholder_dash)
        }
    }

    private fun buildAlerts(state: VehicleState): List<Alert> {
        val result = ArrayList<Alert>(6)
        for (panel in state.doors.openPanels()) {
            result.add(Alert(panelLabel(panel), context.getString(R.string.status_open)))
        }
        if (state.isCoolantHot()) {
            result.add(
                Alert(
                    context.getString(R.string.alert_coolant_temp),
                    context.getString(R.string.status_high),
                ),
            )
        }
        if (state.isLowFuel()) {
            result.add(
                Alert(
                    context.getString(R.string.alert_fuel_level),
                    context.getString(R.string.status_low),
                ),
            )
        }
        return result
    }

    private fun showCurrentAlert() {
        if (alerts.isEmpty()) {
            textAlertTitle.setText(R.string.no_active_alerts)
            textAlertTitle.setTextColor(context.getColor(R.color.text_secondary))
            textAlertStatus.text = ""
            textAlertQueue.visibility = View.GONE
            return
        }
        val alert = alerts[alertIndex % alerts.size]
        textAlertTitle.text = alert.title
        textAlertTitle.setTextColor(context.getColor(R.color.text_primary))
        textAlertStatus.text = alert.status
        if (alerts.size > 1) {
            textAlertQueue.text = context.getString(R.string.alert_more, alerts.size - 1)
            textAlertQueue.visibility = View.VISIBLE
        } else {
            textAlertQueue.visibility = View.GONE
        }
    }

    private fun panelLabel(panel: BodyPanel): String = context.getString(
        when (panel) {
            BodyPanel.DRIVER -> R.string.door_driver
            BodyPanel.PASSENGER -> R.string.door_passenger
            BodyPanel.REAR_LEFT -> R.string.door_rear_left
            BodyPanel.REAR_RIGHT -> R.string.door_rear_right
            BodyPanel.HOOD -> R.string.hood
            BodyPanel.TAILGATE -> R.string.tailgate
        },
    )

    private companion object {
        const val CLOCK_TICK_MS = 1_000L
        const val ALERT_CYCLE_MS = 3_000L
    }
}
