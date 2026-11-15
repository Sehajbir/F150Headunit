package com.f150headunit.app.settings

import android.content.Context
import android.content.SharedPreferences
import com.f150headunit.app.data.VehicleRepository

/**
 * Typed wrapper over SharedPreferences holding every user-configurable option:
 * which installed app the projection panel launches, CAN data source, and
 * display units.
 */
class Settings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Package name of the app represented by the right-hand panel
     * (e.g. the CarPlay/Android Auto client), or `null` to show the
     * "Not Connected" placeholder.
     */
    var projectionPackage: String?
        get() = prefs.getString(KEY_PROJECTION_PACKAGE, null)
        set(value) = prefs.edit().putString(KEY_PROJECTION_PACKAGE, value).apply()

    /** When true, the configured projection app is opened automatically once on start. */
    var autoLaunchProjection: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAUNCH, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH, value).apply()

    /** Where vehicle signals come from. */
    var dataSource: VehicleRepository.Source
        get() = prefs.getString(KEY_DATA_SOURCE, null)
            ?.let { name -> runCatching { VehicleRepository.Source.valueOf(name) }.getOrNull() }
            ?: VehicleRepository.Source.SIMULATION
        set(value) = prefs.edit().putString(KEY_DATA_SOURCE, value.name).apply()

    /** Speed in KM/H when true, MPH when false. */
    var speedInKmh: Boolean
        get() = prefs.getBoolean(KEY_SPEED_KMH, false)
        set(value) = prefs.edit().putBoolean(KEY_SPEED_KMH, value).apply()

    /** Temperatures in °C when true, °F when false. */
    var temperatureInCelsius: Boolean
        get() = prefs.getBoolean(KEY_TEMP_CELSIUS, false)
        set(value) = prefs.edit().putBoolean(KEY_TEMP_CELSIUS, value).apply()

    companion object {
        private const val PREFS_NAME = "f150_head_unit_settings"
        private const val KEY_PROJECTION_PACKAGE = "projection_package"
        private const val KEY_AUTO_LAUNCH = "auto_launch_projection"
        private const val KEY_DATA_SOURCE = "data_source"
        private const val KEY_SPEED_KMH = "speed_kmh"
        private const val KEY_TEMP_CELSIUS = "temp_celsius"

        @Volatile
        private var instance: Settings? = null

        fun getInstance(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(context.applicationContext).also { instance = it }
            }
    }
}
