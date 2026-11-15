package com.f150headunit.app.can

import com.f150headunit.app.model.DoorState
import com.f150headunit.app.model.Units
import com.f150headunit.app.model.VehicleState

/**
 * Decodes the simple HS-CAN layout that [SimulatedCanDataSource] synthesizes.
 *
 * ⚠️ ADAPT TO YOUR HARDWARE: the arbitration IDs below are a clean, documented
 * placeholder mapping. When wiring a real CAN adapter, update these constants
 * (and the per-byte decoding math) to match the F-150's actual signals — e.g.
 * from a DBC file for HS3-CAN, or values proxied via OBD-II/UDS.
 *
 * Frame layout (as used by this app):
 *  - 0x201 SPEED         byte0            vehicle speed, 1 km/h per bit
 *  - 0x214 TEMPS         byte0            ambient temp, °C, offset −40
 *                        byte1            coolant temp, °C, offset −40
 *  - 0x2A0 BODY STATUS   byte0 bits       ajar flags (see DOOR_* / HOOD / TAILGATE)
 *  - 0x2F0 FUEL          byte0            fuel level, percent 0–100
 *                        byte1..2 (LE)    distance-to-empty, miles
 *  - 0x300 HEADING       byte0..1 (LE)    compass heading, 0.5° per bit
 */
class ExampleHsCanDecoder : CanSignalDecoder {

    companion object {
        const val ID_SPEED = 0x201
        const val ID_TEMPS = 0x214
        const val ID_BODY_STATUS = 0x2A0
        const val ID_FUEL = 0x2F0
        const val ID_HEADING = 0x300

        private const val DOOR_DRIVER = 0x01
        private const val DOOR_PASSENGER = 0x02
        private const val DOOR_REAR_LEFT = 0x04
        private const val DOOR_REAR_RIGHT = 0x08
        private const val HOOD = 0x10
        private const val TAILGATE = 0x20
    }

    override fun decode(frame: CanFrame, current: VehicleState): VehicleState? = when (frame.id) {

        ID_SPEED -> if (frame.hasBytes(1)) {
            current.copy(
                speedMph = Units.kmhToMph(frame.u8(0).toFloat()),
                speedValid = true,
            )
        } else {
            null
        }

        ID_TEMPS -> if (frame.hasBytes(2)) {
            current.copy(
                outsideTempF = Units.celsiusToFahrenheit(frame.u8(0) - 40f),
                outsideTempValid = true,
                coolantTempF = Units.celsiusToFahrenheit(frame.u8(1) - 40f),
                coolantValid = true,
            )
        } else {
            null
        }

        ID_BODY_STATUS -> if (frame.hasBytes(1)) {
            current.copy(
                doors = DoorState(
                    driver = frame.bit(0, DOOR_DRIVER),
                    passenger = frame.bit(0, DOOR_PASSENGER),
                    rearLeft = frame.bit(0, DOOR_REAR_LEFT),
                    rearRight = frame.bit(0, DOOR_REAR_RIGHT),
                    hood = frame.bit(0, HOOD),
                    tailgate = frame.bit(0, TAILGATE),
                ),
            )
        } else {
            null
        }

        ID_FUEL -> if (frame.hasBytes(3)) {
            current.copy(
                fuelLevelPercent = frame.u8(0).coerceIn(0, 100).toFloat(),
                fuelValid = true,
                rangeMiles = frame.u16Le(1),
                rangeValid = true,
            )
        } else {
            null
        }

        ID_HEADING -> if (frame.hasBytes(2)) {
            current.copy(
                headingDegrees = frame.u16Le(0) / 2f,
                headingValid = true,
            )
        } else {
            null
        }

        else -> null
    }
}
