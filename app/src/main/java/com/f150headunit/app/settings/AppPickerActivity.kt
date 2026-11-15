package com.f150headunit.app.settings

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.f150headunit.app.R
import java.util.Locale

/**
 * Lists every launchable app installed on the unit and stores the choice as
 * the projection-panel target (plus a "None" entry that restores the
 * placeholder). Runs the PackageManager scan off the main thread.
 */
class AppPickerActivity : Activity() {

    /** [packageName] == null represents the "None – show placeholder" entry. */
    private data class AppEntry(
        val packageName: String?,
        val label: String,
        val icon: Drawable?,
    )

    private val settings by lazy { Settings.getInstance(this) }
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var listView: ListView
    private lateinit var messageText: TextView
    private val adapter = EntryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        findViewById<View>(R.id.button_back).setOnClickListener { finish() }

        listView = findViewById(R.id.list_apps)
        messageText = findViewById(R.id.text_picker_message)
        listView.adapter = adapter
        listView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val entry = adapter.getItem(position)
                settings.projectionPackage = entry.packageName
                finish()
            }

        loadAppsAsync()
    }

    private fun loadAppsAsync() {
        Thread({
            val pm = packageManager
            val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved: List<ResolveInfo> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(launchIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(launchIntent, 0)
                }

            val entries = ArrayList<AppEntry>(resolved.size + 1)
            entries.add(
                AppEntry(
                    packageName = null,
                    label = getString(R.string.app_picker_none),
                    icon = getDrawable(R.drawable.ic_phone_projection),
                ),
            )
            resolved
                .asSequence()
                .filter { it.activityInfo.packageName != packageName } // never launch ourselves
                .distinctBy { it.activityInfo.packageName }
                .map {
                    AppEntry(
                        packageName = it.activityInfo.packageName,
                        label = it.loadLabel(pm).toString(),
                        icon = it.loadIcon(pm),
                    )
                }
                .sortedBy { it.label.lowercase(Locale.US) }
                .forEach { entries.add(it) }

            handler.post {
                adapter.setEntries(entries)
                if (entries.size <= 1) {
                    messageText.setText(R.string.app_picker_empty)
                    messageText.visibility = View.VISIBLE
                } else {
                    messageText.visibility = View.GONE
                }
            }
        }, "app-picker-scan").start()
    }

    private inner class EntryAdapter : BaseAdapter() {

        private val entries = ArrayList<AppEntry>()
        private val inflater: LayoutInflater = LayoutInflater.from(this@AppPickerActivity)

        fun setEntries(newEntries: List<AppEntry>) {
            entries.clear()
            entries.addAll(newEntries)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): AppEntry = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.item_app_choice, parent, false)
            val entry = getItem(position)

            view.findViewById<ImageView>(R.id.icon_app).apply {
                if (entry.icon != null) setImageDrawable(entry.icon) else setImageDrawable(null)
            }
            view.findViewById<TextView>(R.id.text_app_name).text = entry.label
            view.findViewById<TextView>(R.id.text_app_package).apply {
                text = entry.packageName ?: ""
                visibility = if (entry.packageName == null) View.GONE else View.VISIBLE
            }

            val selected = entry.packageName == settings.projectionPackage
            view.findViewById<ImageView>(R.id.icon_selected).visibility =
                if (selected) View.VISIBLE else View.GONE

            return view
        }
    }
}
