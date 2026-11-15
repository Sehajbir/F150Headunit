package com.f150headunit.app.can

/**
 * Minimal Lawicel / SLCAN ASCII protocol codec, as spoken by CANable-in-SLCAN
 * mode, SLCAN-compatible firmware on STM32 dongles, and most serial CAN
 * adapters ("slcan" on Linux).
 *
 * Host→adapter setup commands (see [openChannelBytes]):
 *   C\r            close channel
 *   S<n>\r         bus speed (6 = 500 kbit/s — HS-CAN of the F-150)
 *   O\r            open channel
 *
 * Adapter→host frames, CR-terminated:
 *   tIII<len><2·len hex>          standard 11-bit frame
 *   TIIIIIIII<len><2·len hex>     extended 29-bit frame
 * Lines starting with 'z'/'Z' (ACK), 'F' (status) or '\a' (bell/error)
 * are ignored.
 */
class SlcanCodec(private val emit: (CanFrame) -> Unit) {

    private val line = StringBuilder(MAX_LINE + 16)

    /** Setup bytes for [speedKbps] (10/20/50/125/250/500/800/1000 → S0..S8). */
    fun openChannelBytes(speedKbps: Int = DEFAULT_SPEED_KBPS): List<ByteArray> {
        val speedCmd = when (speedKbps) {
            10 -> "S0"; 20 -> "S1"; 50 -> "S2"; 100 -> "S3"; 125 -> "S4"
            250 -> "S5"; 500 -> "S6"; 800 -> "S7"; 1000 -> "S8"
            else -> DEFAULT_SPEED_CMD
        }
        return listOf(
            "C\r".toByteArray(Charsets.US_ASCII),
            "$speedCmd\r".toByteArray(Charsets.US_ASCII),
            "O\r".toByteArray(Charsets.US_ASCII),
        )
    }

    /** Feeds raw bytes; complete CR/LF-terminated lines are parsed immediately. */
    fun feed(bytes: ByteArray, length: Int) {
        for (i in 0 until length) {
            val ch = bytes[i].toInt().toChar()
            when {
                ch == '\r' || ch == '\n' -> {
                    processLine()
                    line.setLength(0)
                }
                ch == '\u0007' -> line.setLength(0) // BEL = adapter error, drop partial line
                line.length > MAX_LINE -> line.setLength(0) // garbage storm guard
                else -> line.append(ch)
            }
        }
    }

    private fun processLine() {
        if (line.isEmpty()) return
        when (line[0]) {
            't' -> parseFrame(extended = false)
            'T' -> parseFrame(extended = true)
            // 'r'/'R' (RTR) and everything else ignored.
        }
    }

    private fun parseFrame(extended: Boolean) {
        val idChars = if (extended) 8 else 3
        val header = 1 + idChars + 1 // type + id + length nibble
        if (line.length < header) return

        val id = line.substring(1, 1 + idChars).toIntOrNull(16) ?: return
        val length = line[1 + idChars].digitToIntOrNull(16) ?: return
        if (length > 8) return
        if (line.length < header + length * 2) return

        val data = ByteArray(length) { i ->
            val hi = line[header + i * 2].digitToIntOrNull(16) ?: return
            val lo = line[header + i * 2 + 1].digitToIntOrNull(16) ?: return
            ((hi shl 4) or lo).toByte()
        }
        emit(CanFrame(id, data))
    }

    private companion object {
        // t + 8 id chars + 1 len + 16 hex + margin.
        const val MAX_LINE = 64
        const val DEFAULT_SPEED_KBPS = 500
        const val DEFAULT_SPEED_CMD = "S6"
    }
}
