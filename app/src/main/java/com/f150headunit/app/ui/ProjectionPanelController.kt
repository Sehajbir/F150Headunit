package com.f150headunit.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.f150headunit.app.R
import com.f150headunit.app.settings.Settings
import com.f150headunit.app.settings.SettingsActivity

/**
 * Drives the right two-thirds of the screen — the placeholder that stands in
 * for the unit's CarPlay/Android Auto app (or any other installed app the user
 * picks in Settings). Tapping the panel launches the configured app; the
 * settings chip opens the configuration screen.
 */
class ProjectionPanelController(
    private val activity: Activity,
    root: View,
) {

    private val packageManager = activity.packageManager
    private val settings = Settings.getInstance(activity)

    private val iconRow = root.findViewById<View>(R.id.row_projection_icons)
    private val appIcon = root.findViewById<ImageView>(R.id.icon_projection_app)
    private val title = root.findViewById<TextView>(R.id.text_projection_title)
    private val status = root.findViewById<TextView>(R.id.text_projection_status)
    private val hint = root.findViewById<TextView>(R.id.text_projection_hint)
    private val settingsButton = root.findViewById<TextView>(R.id.button_projection_settings)

    private var launchPackage: String? = null
    private var autoLaunchAttempted = false

    init {
        settingsButton.setOnClickListener {
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }
        // Tapping anywhere on the panel opens the configured projection app.
        root.setOnClickListener { launchConfiguredApp() }
    }

    /** Re-reads Settings and PackageManager state; call on resume. */
    fun refresh() {
        launchPackage = null
        val pkg = settings.projectionPackage
        if (pkg == null) {
            showPlaceholder()
            return
        }
        val appInfo = applicationInfo(pkg)
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (appInfo == null || launchIntent == null) {
            showMissingApp(pkg)
            return
        }
        showConfiguredApp(appInfo)
        launchPackage = pkg
    }

    /** Fires the optional auto-launch — at most once per activity instance. */
    fun maybeAutoLaunch() {
        if (autoLaunchAttempted) return
        autoLaunchAttempted = true
        if (settings.autoLaunchProjection) {
            launchConfiguredApp()
        }
    }

    private fun launchConfiguredApp() {
        val pkg = launchPackage ?: return
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.toast_unable_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Presentation states ----

    private fun showPlaceholder() {
        iconRow.visibility = View.VISIBLE
        appIcon.visibility = View.GONE
        title.setText(R.string.projection_default_title)
        status.visibility = View.VISIBLE
        status.setText(R.string.projection_not_connected)
        hint.setText(R.string.projection_hint)
    }

    private fun showMissingApp(pkg: String) {
        iconRow.visibility = View.VISIBLE
        appIcon.visibility = View.GONE
        title.text = pkg
        status.visibility = View.VISIBLE
        status.setText(R.string.projection_missing)
        hint.setText(R.string.projection_hint)
    }

    private fun showConfiguredApp(appInfo: ApplicationInfo) {
        iconRow.visibility = View.GONE
        appIcon.visibility = View.VISIBLE
        appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
        title.text = packageManager.getApplicationLabel(appInfo)
        status.visibility = View.VISIBLE
        status.setText(R.string.projection_ready)
        hint.setText(R.string.projection_ready_hint)
    }

    // ---- Helpers ----

    private fun applicationInfo(pkg: String): ApplicationInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(pkg, 0)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
