package com.f150headunit.app.model

/** Which body panel an ajar alert refers to. Display order = enum order. */
enum class BodyPanel { DRIVER, PASSENGER, REAR_LEFT, REAR_RIGHT, HOOD, TAILGATE }

/** Door / hood / tailgate ajar state. */
data class DoorState(
    val driver: Boolean = false,
    val passenger: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val hood: Boolean = false,
    val tailgate: Boolean = false,
) {
    fun isAnyOpen(): Boolean =
        driver || passenger || rearLeft || rearRight || hood || tailgate

    /** Everything currently open, in display priority order. */
    fun openPanels(): List<BodyPanel> = buildList {
        if (driver) add(BodyPanel.DRIVER)
        if (passenger) add(BodyPanel.PASSENGER)
        if (rearLeft) add(BodyPanel.REAR_LEFT)
        if (rearRight) add(BodyPanel.REAR_RIGHT)
        if (hood) add(BodyPanel.HOOD)
        if (tailgate) add(BodyPanel.TAILGATE)
    }
}

/**
 * Immutable snapshot of every vehicle signal shown on the cluster panel.
 * All values are stored in imperial units (matching the truck's displays) and
 * converted for presentation by [Units]. The `*Valid` flags distinguish
 * "value is 0" from "signal not decoded yet" so the UI can show "--" instead
 * of a bogus zero on startup.
 */
data class VehicleState(
    val speedMph: Float = 0f,
    val speedValid: Boolean = false,
    val headingDegrees: Float = 0f,
    val headingValid: Boolean = false,
    val outsideTempF: Float = 0f,
    val outsideTempValid: Boolean = false,
    val fuelLevelPercent: Float = 0f,
    val fuelValid: Boolean = false,
    val rangeMiles: Int = 0,
    val rangeValid: Boolean = false,
    val coolantTempF: Float = 0f,
    val coolantValid: Boolean = false,
    val doors: DoorState = DoorState(),
) {
    fun isLowFuel(): Boolean = fuelValid && fuelLevelPercent <= LOW_FUEL_PERCENT
    fun isCoolantHot(): Boolean = coolantValid && coolantTempF >= HOT_COOLANT_F

    companion object {
        const val LOW_FUEL_PERCENT = 10f
        const val HOT_COOLANT_F = 235f
        // Span of the C→H coolant segment gauge.
        const val COOLANT_GAUGE_MIN_F = 100f
        const val COOLANT_GAUGE_MAX_F = 260f
    }
}
