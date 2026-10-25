package com.skvod3400.superalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.ZonedDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val alarm = AlarmRepository.get(context, alarmId) ?: return

        if (!snooze) {
            if (alarm.isRepeating) {
                // Look one minute ahead so the alarm that has just fired is not set again for today.
                AlarmScheduler.schedule(context, alarm, ZonedDateTime.now().plusMinutes(1))
            } else {
                AlarmRepository.save(context, alarm.copy(enabled = false))
            }
        }

        try {
            AlarmService.start(context, alarm.id)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Cannot start alarm service", e)
        }
    }
}
