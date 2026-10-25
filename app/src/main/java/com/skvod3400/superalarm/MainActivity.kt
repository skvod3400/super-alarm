package com.skvod3400.superalarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skvod3400.superalarm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val adapter = AlarmAdapter(
        onClick = { openEditor(it.id) },
        onToggle = { alarm, enabled -> setEnabled(alarm, enabled) },
        onLongClick = { confirmDelete(it) },
    )

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.add.setOnClickListener { openEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        refreshAlarms()
        refreshPermissions()
    }

    private fun refreshAlarms() {
        val alarms = AlarmRepository.getAll(this)
        adapter.submit(alarms)
        binding.empty.isVisible = alarms.isEmpty()
    }

    private fun openEditor(alarmId: Int?) {
        val intent = Intent(this, EditAlarmActivity::class.java)
        if (alarmId != null) intent.putExtra(EXTRA_ALARM_ID, alarmId)
        startActivity(intent)
    }

    private fun setEnabled(alarm: Alarm, enabled: Boolean) {
        val updated = alarm.copy(enabled = enabled)
        AlarmRepository.save(this, updated)
        AlarmScheduler.schedule(this, updated)
        if (enabled) Toast.makeText(this, updated.timeUntilText(), Toast.LENGTH_LONG).show()
        binding.list.post { refreshAlarms() }
    }

    private fun confirmDelete(alarm: Alarm) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage("${alarm.timeText} · ${alarm.daysText}")
            .setPositiveButton(R.string.delete) { _, _ ->
                AlarmScheduler.cancel(this, alarm.id)
                AlarmRepository.delete(this, alarm.id)
                refreshAlarms()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Shows a button for every permission the alarm still needs to work reliably. */
    private fun refreshPermissions() {
        val container = binding.permissions
        container.removeAllViews()
        val missing = missingPermissions()
        container.isVisible = missing.isNotEmpty()
        if (missing.isEmpty()) return

        container.addView(TextView(this).apply { setText(R.string.permissions_title) })
        for ((titleRes, request) in missing) {
            container.addView(MaterialButton(this).apply {
                setText(titleRes)
                setOnClickListener { request() }
            })
        }
    }

    private fun missingPermissions() = buildList<Pair<Int, () -> Unit>> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            add(R.string.perm_notifications to { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
        }
        if (!getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            add(R.string.perm_exact to { openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            add(R.string.perm_full_screen to { openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) })
        }
        if (!Settings.canDrawOverlays(this@MainActivity)) {
            add(R.string.perm_overlay to { openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION) })
        }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            add(R.string.perm_battery to { openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) })
        }
    }

    private fun openSettings(action: String) {
        val packageUri = Uri.parse("package:$packageName")
        try {
            startActivity(Intent(action, packageUri))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }
    }
}
