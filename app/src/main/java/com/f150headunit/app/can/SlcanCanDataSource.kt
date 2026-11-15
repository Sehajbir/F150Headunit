package com.f150headunit.app.can

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/**
 * Real-bus source: streams frames from a USB SLCAN CAN adapter (CANable /
 * candleLight in SLCAN mode, CH341/CP210x/FTDI serial CAN dongles, …).
 *
 * Handles the whole lifecycle: waits for an adapter, requests USB permission
 * (the system dialog appears over the UI on first insert), opens the link via
 * [UsbAcmTransport], opens the SLCAN channel at 500 kbit/s (HS-CAN), then
 * parses frames on a reader thread until [stop] or an error, after which it
 * backs off and retries — so replugging the adapter self-heals.
 *
 * Adapters with a different wire protocol: implement [CanTransport] and swap
 * the transport construction inside [sessionLoop]; everything else (permission,
 * discovery, reconnect, frame fan-out) stays as-is.
 */
class SlcanCanDataSource : CanDataSource {

    override val sourceName: String = "USB CAN adapter"
    override var listener: ((CanFrame) -> Unit)? = null
    override var statusListener: ((String) -> Unit)? = null

    @Volatile
    private var running = false

    private var appContext: Context? = null
    private var worker: Thread? = null

    @Volatile
    private var permissionRequested = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            permissionRequested = false
            val device: UsbDevice? = parcelableExtra(intent)
            val granted = device != null && usbManager(context).hasPermission(device)
            Log.i(TAG, "permission result for $device: granted=$granted")
            if (!granted) reportStatus("USB permission denied — replug the adapter")
        }
    }

    override fun start(context: Context) {
        if (running) return
        running = true
        val app = context.applicationContext
        appContext = app
        registerReceiverCompat(app)
        worker = Thread({ managerLoop(app) }, "slcan-manager").also { it.start() }
    }

    override fun stop() {
        running = false
        appContext?.let { ctx ->
            runCatching { ctx.unregisterReceiver(permissionReceiver) }
        }
        worker?.interrupt()
        runCatching { worker?.join(1500) }
        worker = null
        appContext = null
        listener = null
        statusListener = null
    }

    // ---- management loop: find → permission → session → (re)try ----

    private fun managerLoop(context: Context) {
        val usb = usbManager(context)
        while (running) {
            try {
                val device = pickAdapter(usb)
                if (device == null) {
                    reportStatus("No USB CAN adapter found — waiting")
                    sleepInterruptibly(RETRY_MS)
                    continue
                }
                if (!usb.hasPermission(device)) {
                    if (!permissionRequested) {
                        permissionRequested = true
                        reportStatus("Requesting USB permission…")
                        requestPermission(context, usb, device)
                    }
                    sleepInterruptibly(RETRY_MS)
                    continue
                }
                sessionLoop(usb, device)
            } catch (e: InterruptedException) {
                // stop() interrupted us — leave.
            } catch (e: Exception) {
                Log.w(TAG, "session error: ${e.message}")
                reportStatus("CAN link error: ${e.message}")
            }
            if (running) sleepInterruptibly(RETRY_MS)
        }
    }

    /** One link session: open → configure → read until failure or stop(). */
    private fun sessionLoop(usb: UsbManager, device: UsbDevice) {
        val transport = UsbAcmTransport(usb, device)
        try {
            transport.open()
            val codec = SlcanCodec { frame -> listener?.invoke(frame) }
            for (command in codec.openChannelBytes()) {
                transport.write(command)
                sleepInterruptibly(60)
            }
            reportStatus("Connected: ${transport.description}")
            Log.i(TAG, "SLCAN channel open on ${device.deviceName}")

            val buffer = ByteArray(4096)
            while (running) {
                val n = transport.read(buffer, READ_TIMEOUT_MS)
                if (n > 0) codec.feed(buffer, n)
            }
        } finally {
            transport.close()
            if (running) reportStatus("Adapter disconnected — waiting")
        }
    }

    // ---- helpers ----

    /** Prefer a known CANable/candleLight adapter; else any CDC candidate. */
    private fun pickAdapter(usb: UsbManager): UsbDevice? {
        val devices = usb.deviceList.values
        return devices.firstOrNull { it.vendorId == CANABLE_VID }
            ?: devices.firstOrNull { UsbAcmTransport.isCandidateAdapter(it) }
    }

    private fun requestPermission(context: Context, usb: UsbManager, device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
        runCatching { usb.requestPermission(device, pending) }
            .onFailure { permissionRequested = false }
    }

    private fun registerReceiverCompat(context: Context) {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(permissionReceiver, filter)
        }
    }

    private fun usbManager(context: Context): UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    @Suppress("DEPRECATION")
    private fun parcelableExtra(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private fun reportStatus(status: String) {
        statusListener?.invoke(status)
    }

    @Throws(InterruptedException::class)
    private fun sleepInterruptibly(ms: Long) {
        Thread.sleep(ms)
        if (!running) throw InterruptedException()
    }

    companion object {
        private const val TAG = "SlcanCanDataSource"
        private const val ACTION_USB_PERMISSION = "com.f150headunit.app.USB_PERMISSION"
        private const val CANABLE_VID = 0x1D50 // candleLight/CANable — preferred when present
        private const val RETRY_MS = 2_000L
        private const val READ_TIMEOUT_MS = 500
    }
}
