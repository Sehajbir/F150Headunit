package com.f150headunit.app.projection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Watches whether a phone is **connected for projection** on this head unit.
 *
 * Wired Android Auto puts the head unit in USB-host mode with the phone acting
 * as a USB *accessory* (AOA protocol) — `ACTION_USB_ACCESSORY_ATTACHED` fires
 * on connect and `usbManager.accessoryList` stays non-empty for the session.
 * CarPlay-over-dongle boxes manage the phone session inside their own app, so
 * for those the monitor stays "not connected" and the panel remains a tap
 * target (documented behaviour — stock Android cannot peer into another app).
 */
class ConnectionMonitor(private val context: Context) {

    interface Listener {
        fun onConnectionChanged(connected: Boolean)
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var connected: Boolean = false
        private set

    private var listener: Listener? = null
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> update(connected = true)
                UsbManager.ACTION_USB_ACCESSORY_DETACHED -> recheckAccessories()
            }
        }
    }

    fun start(newListener: Listener) {
        if (started) return
        started = true
        listener = newListener
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        recheckAccessories() // emit the initial state
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        listener = null
    }

    private fun recheckAccessories() {
        update(usbManager.accessoryList?.isNotEmpty() == true)
    }

    private fun update(nowConnected: Boolean) {
        connected = nowConnected
        mainHandler.post { listener?.onConnectionChanged(nowConnected) }
    }
}
