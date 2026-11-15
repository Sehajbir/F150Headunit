package com.f150headunit.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.f150headunit.app.data.VehicleRepository
import com.f150headunit.app.settings.Settings
import com.f150headunit.app.ui.ProjectionPanelController
import com.f150headunit.app.ui.TruckInfoController

/**
 * Fullscreen, always-landscape cluster UI:
 *  - left third  → live truck information (fed from the CAN bus)
 *  - right panel → CarPlay/Android Auto projection placeholder/launcher
 */
class MainActivity : Activity() {

    private var truckInfo: TruckInfoController? = null
    private var projectionPanel: ProjectionPanelController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        setContentView(R.layout.activity_main)

        val repository = VehicleRepository.getInstance(this)
        val settings = Settings.getInstance(this)

        truckInfo = TruckInfoController(findViewById(R.id.panel_truck_info), repository, settings)
            .also { it.attach() }
        projectionPanel = ProjectionPanelController(this, findViewById(R.id.panel_projection))
            .also {
                it.refresh()
                it.maybeAutoLaunch()
            }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        // Projection app may have been re-configured/uninstalled while away.
        projectionPanel?.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        truckInfo?.detach()
        truckInfo = null
        projectionPanel = null
        super.onDestroy()
    }

    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}
