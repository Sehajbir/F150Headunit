package com.f150headunit.app.can

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.util.Locale

/**
 * Generic USB CDC-ACM transport over Android USB host — covers CAN adapters
 * that expose a serial-style endpoint pair (CANable in SLCAN mode, candleLight
 * CDC variants, and most CH341/CP2102/FT232-based CAN dongles).
 *
 * Flow: locate a bulk IN+OUT endpoint pair (preferring the CDC Data interface),
 * claim it, then best-effort send the standard CDC-ACM SET_LINE_CODING /
 * SET_CONTROL_LINE_STATE requests many adapters expect before streaming.
 */
class UsbAcmTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
) : CanTransport {

    override val description: String
        get() = String.format(
            Locale.US,
            "%s (VID %04X / PID %04X)",
            device.productName ?: device.deviceName,
            device.vendorId,
            device.productId,
        )

    private var connection: UsbDeviceConnection? = null
    private var dataInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    override fun open() {
        val conn = usbManager.openDevice(device)
            ?: throw CanTransport.TransportException("openDevice() returned null (permission?)")

        val selected = selectDataInterface(device)
        if (selected == null) {
            conn.close()
            throw CanTransport.TransportException("no bulk IN+OUT interface found")
        }

        if (!conn.claimInterface(selected.intf, true)) {
            conn.close()
            throw CanTransport.TransportException("claimInterface failed")
        }

        connection = conn
        dataInterface = selected.intf
        inEndpoint = selected.inEp
        outEndpoint = selected.outEp

        // Best-effort CDC-ACM handshake; harmless on adapters that ignore it.
        try {
            val commId = findCommInterfaceId(device)
            // SET_LINE_CODING: 115200 baud, 1 stop bit, no parity, 8 data bits.
            // 115200 = 0x0001C200 → little-endian bytes 00 C2 01 00.
            val lineCoding = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08)
            conn.controlTransfer(USB_RT_CLASS_INTERFACE, REQ_SET_LINE_CODING, 0, commId, lineCoding, lineCoding.size, USB_TIMEOUT_MS)
            // SET_CONTROL_LINE_STATE: assert DTR+RTS so the adapter starts streaming.
            conn.controlTransfer(USB_RT_CLASS_INTERFACE, REQ_SET_CONTROL_LINE_STATE, 0x03, commId, null, 0, USB_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "CDC-ACM handshake failed (continuing anyway): ${e.message}")
        }

        Log.i(TAG, "opened $description")
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val conn = connection ?: throw CanTransport.TransportException("transport is closed")
        val ep = inEndpoint ?: throw CanTransport.TransportException("no IN endpoint")
        val n = conn.bulkTransfer(ep, buffer, buffer.size, timeoutMs)
        if (n < 0) throw CanTransport.TransportException("bulkTransfer(IN) failed")
        return n
    }

    override fun write(bytes: ByteArray) {
        val conn = connection ?: throw CanTransport.TransportException("transport is closed")
        val ep = outEndpoint ?: throw CanTransport.TransportException("no OUT endpoint")
        val n = conn.bulkTransfer(ep, bytes, bytes.size, USB_TIMEOUT_MS)
        if (n != bytes.size) {
            throw CanTransport.TransportException("bulkTransfer(OUT) wrote $n of ${bytes.size} bytes")
        }
    }

    override fun close() {
        val conn = connection
        val intf = dataInterface
        connection = null
        dataInterface = null
        inEndpoint = null
        outEndpoint = null
        if (intf != null) {
            runCatching { conn?.releaseInterface(intf) }
        }
        runCatching { conn?.close() }
    }

    private data class DataEndpoints(
        val intf: UsbInterface,
        val inEp: UsbEndpoint,
        val outEp: UsbEndpoint,
    )

    companion object {
        private const val TAG = "UsbAcmTransport"
        private const val USB_TIMEOUT_MS = 500

        // bmRequestType: host→device, class, interface recipient.
        private const val USB_RT_CLASS_INTERFACE = 0x21
        private const val REQ_SET_LINE_CODING = 0x20
        private const val REQ_SET_CONTROL_LINE_STATE = 0x22

        /**
         * True for adapters we recognise (common CAN dongle chips) or any device
         * exposing a CDC Data interface — gates auto-attach and auto-pick so we
         * don't claim e.g. the vehicle's replay interface by mistake.
         */
        fun isCandidateAdapter(device: UsbDevice): Boolean {
            if (device.vendorId in KNOWN_VENDOR_IDS) return true
            for (i in 0 until device.interfaceCount) {
                if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                    return true
                }
            }
            return false
        }

        /** Prefers the CDC Data interface; falls back to any interface with a bulk pair. */
        private fun selectDataInterface(device: UsbDevice): DataEndpoints? {
            var fallback: DataEndpoints? = null
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    when (ep.direction) {
                        UsbConstants.USB_DIR_IN -> inEp = ep
                        UsbConstants.USB_DIR_OUT -> outEp = ep
                    }
                }
                if (inEp != null && outEp != null) {
                    val candidate = DataEndpoints(intf, inEp, outEp)
                    if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) return candidate
                    if (fallback == null) fallback = candidate
                }
            }
            return fallback
        }

        private fun findCommInterfaceId(device: UsbDevice): Int {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_COMM) return intf.id
            }
            // Many single-interface adapters answer class requests on interface 0.
            return selectDataInterface(device)?.intf?.id ?: 0
        }

        // 1D50 candleLight/CANable · 0483 STM32 · 0403 FTDI · 10C4 CP210x · 1A86 CH341 · 16D0 MCS
        private val KNOWN_VENDOR_IDS = setOf(0x1D50, 0x0483, 0x0403, 0x10C4, 0x1A86, 0x16D0)
    }
}
