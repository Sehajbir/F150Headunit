package com.f150headunit.app.can

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * SKELETON for a real USB CAN adapter (CANable/candleLight, PCAN-USB, value
 * serial CAN dongles based on CH341/CP210x/FTDI, etc.).
 *
 * The plumbing deliberately stops at device discovery + logging so the class
 * compiles and runs harmlessly; fill in the TODO below for your hardware:
 *
 *  1. Ask the user for USB permission: `UsbManager.requestPermission(device, pendingIntent)`
 *     (declare an intent-filter / usb_device_filter.xml for auto-launch if desired).
 *  2. Open the device: `usbManager.openDevice(device)`, claim the data
 *     `UsbInterface`, and locate the IN/OUT bulk endpoints.
 *  3. Configure the adapter for the truck's bus speed (typically 500 kbit/s on
 *     HS-CAN) — e.g. SLCAN "S6" / candleLight bit-timing commands.
 *  4. Spawn a read thread performing `UsbDeviceConnection.bulkTransfer(...)` /
 *     serial reads, parse each adapter record into [CanFrame], and invoke
 *     [listener]. Honor [running] so [stop] terminates the loop cleanly.
 *  5. On [stop]: close the connection and release the interface.
 *
 * If your adapter is a plain UART-style CAN dongle, you can also drop in a
 * serial driver (e.g. usb-serial-for-android) and emit SLCAN-lines as frames.
 */
class UsbCanDataSource : CanDataSource {

    override val sourceName: String = "USB CAN adapter"
    override var listener: ((CanFrame) -> Unit)? = null

    @Volatile
    private var running = false

    override fun start(context: Context) {
        running = true
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val candidates = usbManager.deviceList.values.filter(::isKnownAdapter)
        if (candidates.isEmpty()) {
            Log.w(TAG, "start(): no known USB CAN adapter attached")
        } else {
            // TODO(hardware): permission → open → configure → read loop (see class doc).
            candidates.forEach {
                Log.i(
                    TAG,
                    "adapter candidate: ${it.deviceName} " +
                        "vid=0x${it.vendorId.toString(16)} pid=0x${it.productId.toString(16)}",
                )
            }
        }
    }

    override fun stop() {
        running = false
    }

    private fun isKnownAdapter(device: UsbDevice): Boolean =
        device.vendorId in KNOWN_VENDOR_IDS

    companion object {
        private const val TAG = "UsbCanDataSource"

        // 1D50 candleLight/CANable · 0483 STM32 · 0403 FTDI · 10C4 CP210x · 1A86 CH341 · 16D0 MCS
        private val KNOWN_VENDOR_IDS = setOf(0x1D50, 0x0483, 0x0403, 0x10C4, 0x1A86, 0x16D0)
    }
}
