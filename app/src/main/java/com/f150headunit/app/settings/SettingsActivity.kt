package com.f150headunit.app.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import com.f150headunit.app.R
import com.f150headunit.app.data.VehicleRepository

/**
 * Configuration for the head unit: pick which installed app the projection
 * panel launches, the CAN data source, and display units.
 */
class SettingsActivity : Activity() {

    private val settings by lazy { Settings.getInstance(this) }
    private val repository by lazy { VehicleRepository.getInstance(this) }

    private lateinit var projectionAppValue: TextView
    private lateinit var dataSourceValue: TextView
    private lateinit var speedUnitValue: TextView
    private lateinit var tempUnitValue: TextView

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // Keeps the live adapter status under the CAN data source row fresh.
    private val statusTicker = object : Runnable {
        override fun run() {
            refreshSummaries()
            handler.postDelayed(this, STATUS_TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        projectionAppValue = findViewById(R.id.text_projection_app_value)
        dataSourceValue = findViewById(R.id.text_data_source_value)
        speedUnitValue = findViewById(R.id.text_speed_unit_value)
        tempUnitValue = findViewById(R.id.text_temp_unit_value)

        findViewById<android.view.View>(R.id.button_back).setOnClickListener { finish() }

        findViewById<android.view.View>(R.id.row_projection_app).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        findViewById<Switch>(R.id.switch_auto_launch).apply {
            isChecked = settings.autoLaunchProjection
            setOnCheckedChangeListener { _, isChecked ->
                settings.autoLaunchProjection = isChecked
            }
        }

        findViewById<android.view.View>(R.id.row_data_source).setOnClickListener { showDataSourceDialog() }
        findViewById<android.view.View>(R.id.row_speed_unit).setOnClickListener { showSpeedUnitDialog() }
        findViewById<android.view.View>(R.id.row_temp_unit).setOnClickListener { showTempUnitDialog() }

        findViewById<TextView>(R.id.text_version_value).text = versionName()
    }

    override fun onResume() {
        super.onResume()
        refreshSummaries()
        handler.post(statusTicker)
    }

    override fun onPause() {
        handler.removeCallbacks(statusTicker)
        super.onPause()
    }

    private fun refreshSummaries() {
        // Projection app summary: label of the chosen app, or "not configured".
        val pkg = settings.projectionPackage
        projectionAppValue.text = if (pkg == null) {
            getString(R.string.settings_projection_app_none)
        } else {
            try {
                val info = applicationInfo(pkg)
                if (info != null) packageManager.getApplicationLabel(info) else pkg
            } catch (e: Exception) {
                pkg
            }
        }
        findViewById<android.widget.ImageView>(R.id.icon_projection_app)?.let { iconView ->
            if (pkg == null) {
                iconView.setImageResource(R.drawable.ic_phone_projection)
            } else {
                try {
                    iconView.setImageDrawable(packageManager.getApplicationIcon(pkg))
                } catch (e: PackageManager.NameNotFoundException) {
                    iconView.setImageResource(R.drawable.ic_phone_projection)
                }
            }
        }

        val sourceLabel = getString(
            when (settings.dataSource) {
                VehicleRepository.Source.SIMULATION -> R.string.data_source_simulation
                VehicleRepository.Source.USB -> R.string.data_source_usb
            },
        )
        val sourceStatus = repository.sourceStatus
        dataSourceValue.text =
            if (sourceStatus != null && repository.activeSource == settings.dataSource) {
                "$sourceLabel — $sourceStatus"
            } else {
                sourceLabel
            }
        speedUnitValue.setText(
            if (settings.speedInKmh) R.string.speed_unit_kmh else R.string.speed_unit_mph,
        )
        tempUnitValue.text = if (settings.temperatureInCelsius) "°C" else "°F"
    }

    private fun showDataSourceDialog() {
        val entries = arrayOf(
            getString(R.string.data_source_simulation),
            getString(R.string.data_source_usb),
        )
        val checked = if (settings.dataSource == VehicleRepository.Source.USB) 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_data_source)
            .setSingleChoiceItems(entries, checked) { dialog, which ->
                val selected =
                    if (which == 1) VehicleRepository.Source.USB else VehicleRepository.Source.SIMULATION
                settings.dataSource = selected
                // Hot-swap the live pipeline.
                repository.start(selected)
                refreshSummaries()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSpeedUnitDialog() {
        val entries = arrayOf(getString(R.string.speed_unit_mph), getString(R.string.speed_unit_kmh))
        val checked = if (settings.speedInKmh) 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_speed_unit)
            .setSingleChoiceItems(entries, checked) { dialog, which ->
                settings.speedInKmh = which == 1
                refreshSummaries()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTempUnitDialog() {
        val entries = arrayOf("°F", "°C")
        val checked = if (settings.temperatureInCelsius) 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_temp_unit)
            .setSingleChoiceItems(entries, checked) { dialog, which ->
                settings.temperatureInCelsius = which == 1
                refreshSummaries()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applicationInfo(pkg: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(pkg, 0)
        }

    private fun versionName(): String = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName ?: "--"
    } catch (e: PackageManager.NameNotFoundException) {
        "--"
    }

    private companion object {
        const val STATUS_TICK_MS = 1_500L
    }
}
