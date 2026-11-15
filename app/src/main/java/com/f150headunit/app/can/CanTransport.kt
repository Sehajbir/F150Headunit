package com.f150headunit.app.can

/**
 * Byte pipe between the app and a physical CAN adapter
 * (USB CDC-ACM dongle, TCP bridge on a CAN-capable MCU, …).
 *
 * Implementations must tolerate being closed from another thread while
 * [read] is blocked: closing should make an in-flight [read] return or throw.
 */
interface CanTransport {

    /** Human-readable identifier of the underlying link (shown in Settings/logs). */
    val description: String

    @Throws(TransportException::class)
    fun open()

    /**
     * Copies up to [buffer].size bytes into [buffer].
     * @return number of bytes read, or 0 when [timeoutMs] elapsed with no data.
     */
    @Throws(TransportException::class)
    fun read(buffer: ByteArray, timeoutMs: Int): Int

    @Throws(TransportException::class)
    fun write(bytes: ByteArray)

    fun close()

    class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
