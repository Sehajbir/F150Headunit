package com.f150headunit.app

import android.app.Application
import com.f150headunit.app.data.VehicleRepository
import com.f150headunit.app.settings.Settings

/**
 * Boots the vehicle-data pipeline (configured CAN source → decoder →
 * [VehicleRepository]) for the life of the process.
 */
class F150Application : Application() {

    override fun onCreate() {
        super.onCreate()
        val settings = Settings.getInstance(this)
        VehicleRepository.getInstance(this).start(settings.dataSource)
    }
}
