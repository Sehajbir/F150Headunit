package com.f150headunit.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.f150headunit.app.can.CanDataSource
import com.f150headunit.app.can.CanSignalDecoder
import com.f150headunit.app.can.ExampleHsCanDecoder
import com.f150headunit.app.can.SimulatedCanDataSource
import com.f150headunit.app.can.UsbCanDataSource
import com.f150headunit.app.model.VehicleState
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Single owner of the live [VehicleState]. Frames from the configured
 * [CanDataSource] are marshalled to the main thread, decoded, merged into the
 * current state, and fanned out to UI listeners.
 *
 * Lifecycle: started once from `F150Application` with the source chosen in
 * Settings; [start] can be called again any time to hot-swap sources.
 */
class VehicleRepository private constructor(private val appContext: Context) {

    enum class Source { SIMULATION, USB }

    interface Listener {
        fun onVehicleStateChanged(state: VehicleState)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val decoder: CanSignalDecoder = ExampleHsCanDecoder()

    @Volatile
    private var dataSource: CanDataSource? = null

    @Volatile
    var state: VehicleState = VehicleState()
        private set

    @Volatile
    var activeSource: Source = Source.SIMULATION
        private set

    /** Starts delivering state from [source]; stops any previously active source. */
    fun start(source: Source) {
        stop()
        activeSource = source
        val newSource: CanDataSource = when (source) {
            Source.SIMULATION -> SimulatedCanDataSource()
            Source.USB -> UsbCanDataSource()
        }
        dataSource = newSource
        newSource.listener = { frame ->
            mainHandler.post {
                val decoded = decoder.decode(frame, state)
                if (decoded != null && decoded != state) {
                    state = decoded
                    notifyListeners(decoded)
                }
            }
        }
        newSource.start(appContext)
    }

    fun stop() {
        dataSource?.stop()
        dataSource = null
    }

    /** Registers [listener] and immediately replays the current state to it. */
    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
        mainHandler.post { listener.onVehicleStateChanged(state) }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(newState: VehicleState) {
        for (listener in listeners) {
            listener.onVehicleStateChanged(newState)
        }
    }

    companion object {
        @Volatile
        private var instance: VehicleRepository? = null

        fun getInstance(context: Context): VehicleRepository =
            instance ?: synchronized(this) {
                instance ?: VehicleRepository(context.applicationContext).also { instance = it }
            }
    }
}
