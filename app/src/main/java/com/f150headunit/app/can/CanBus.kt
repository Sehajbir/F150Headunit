package com.f150headunit.app.can

import android.content.Context
import com.f150headunit.app.model.VehicleState

/**
 * A single classic-CAN frame. [id] is the 11/29-bit arbitration ID and [data]
 * the up-to-8 payload bytes. Frames are produced by a [CanDataSource] and
 * consumed by a [CanSignalDecoder].
 */
class CanFrame(
    val id: Int,
    val data: ByteArray,
    val timestampNanos: Long = System.nanoTime(),
) {
    init {
        require(data.size <= 8) { "Classic CAN frames carry at most 8 data bytes" }
    }

    val dataLength: Int get() = data.size

    /** Unsigned byte at [index]. */
    fun u8(index: Int): Int = data[index].toInt() and 0xFF

    /** Unsigned little-endian 16-bit value starting at [index]. */
    fun u16Le(index: Int): Int = u8(index) or (u8(index + 1) shl 8)

    /** True when all bits in [mask] of byte [index] are set. */
    fun bit(index: Int, mask: Int): Boolean = (u8(index) and mask) != 0

    fun hasBytes(count: Int): Boolean = data.size >= count

    override fun equals(other: Any?): Boolean =
        other is CanFrame && other.id == id && other.data.contentEquals(data)

    override fun hashCode(): Int = 31 * id + data.contentHashCode()

    override fun toString(): String =
        "CanFrame(id=0x${id.toString(16).uppercase()}, data=" +
            data.joinToString(" ") { "%02X".format(it) } + ")"
}

/**
 * Origin of CAN frames — e.g. a real bus adapter, or the bench simulator.
 * Frames may be delivered from any thread; [com.f150headunit.app.data.VehicleRepository]
 * marshals them onto the main thread before decoding.
 */
interface CanDataSource {
    /** Human-readable name (shown in Settings and logs). */
    val sourceName: String

    /** Receives every frame produced by this source. */
    var listener: ((CanFrame) -> Unit)?

    /** Optional one-line status reports (e.g. "waiting for adapter", "connected"). */
    var statusListener: ((String) -> Unit)?

    fun start(context: Context)
    fun stop()
}

/**
 * Translates raw frames into vehicle state. Remap/replace the IDs in the
 * provided [ExampleHsCanDecoder] implementation to match the truck's actual
 * DBC, or compose several decoders for different bus segments.
 */
interface CanSignalDecoder {
    /**
     * @return the merged [VehicleState], or `null` if the frame carries no
     * signal understood by this decoder.
     */
    fun decode(frame: CanFrame, current: VehicleState): VehicleState?
}
