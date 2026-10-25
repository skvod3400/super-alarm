package com.skvod3400.superalarm

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.skvod3400.superalarm.databinding.ActivityEditBinding

class EditAlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditBinding
    private var existing: Alarm? = null
    private var ringtoneUri: Uri? = null
    private val dayChips = mutableListOf<Chip>()

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                ringtoneUri = IntentCompat.getParcelableExtra(
                    data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java,
                )
                updateRingtoneText()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.timePicker.setIs24HourView(true)
        for (name in Alarm.DAY_NAMES) {
            val chip = layoutInflater.inflate(R.layout.chip_day, binding.days, false) as Chip
            chip.text = name
            binding.days.addView(chip)
            dayChips += chip
        }

        val alarm = AlarmRepository.get(this, intent.getIntExtra(EXTRA_ALARM_ID, -1))
        existing = alarm
        if (alarm != null) {
            setTitle(R.string.edit_alarm)
            binding.timePicker.hour = alarm.hour
            binding.timePicker.minute = alarm.minute
            dayChips.forEachIndexed { i, chip -> chip.isChecked = alarm.isDayEnabled(i) }
            binding.label.setText(alarm.label)
            ringtoneUri = alarm.ringtoneUri?.let(Uri::parse)
            binding.delete.isVisible = true
        } else {
            setTitle(R.string.new_alarm)
            setDays(Alarm.WEEKDAYS)
        }
        updateRingtoneText()

        binding.weekdays.setOnClickListener { setDays(Alarm.WEEKDAYS) }
        binding.everyDay.setOnClickListener { setDays(Alarm.ALL_DAYS) }
        binding.ringtone.setOnClickListener { pickRingtone() }
        binding.save.setOnClickListener { save() }
        binding.delete.setOnClickListener { delete() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setDays(days: Int) {
        dayChips.forEachIndexed { i, chip -> chip.isChecked = (days and (1 shl i)) != 0 }
    }

    private fun pickRingtone() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.ringtone))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                ringtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            )
        ringtonePicker.launch(intent)
    }

    private fun updateRingtoneText() {
        val title = ringtoneUri?.let { uri ->
            runCatching { RingtoneManager.getRingtone(this, uri)?.getTitle(this) }.getOrNull()
        }
        binding.ringtone.text = title ?: getString(R.string.ringtone_default)
    }

    private fun save() {
        binding.timePicker.clearFocus()
        var days = 0
        dayChips.forEachIndexed { i, chip -> if (chip.isChecked) days = days or (1 shl i) }

        val alarm = Alarm(
            id = existing?.id ?: AlarmRepository.newId(this),
            hour = binding.timePicker.hour,
            minute = binding.timePicker.minute,
            days = days,
            enabled = true,
            label = binding.label.text?.toString()?.trim().orEmpty(),
            ringtoneUri = ringtoneUri?.toString(),
        )
        AlarmRepository.save(this, alarm)
        AlarmScheduler.schedule(this, alarm)
        Toast.makeText(this, alarm.timeUntilText(), Toast.LENGTH_LONG).show()
        finish()
    }

    private fun delete() {
        val alarm = existing ?: return
        AlarmScheduler.cancel(this, alarm.id)
        AlarmRepository.delete(this, alarm.id)
        finish()
    }
}
