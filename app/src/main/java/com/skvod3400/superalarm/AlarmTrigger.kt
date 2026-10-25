package com.skvod3400.superalarm

import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.ZonedDateTime

/**
 * Handles a fired alarm. Both the ringing screen and the backup broadcast call it,
 * so it remembers each trigger and runs only once per trigger.
 */
object AlarmTrigger {
    private const val PREFS = "fired"

    @Synchronized
    fun fire(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = alarmId.toString()
        if (prefs.getLong(key, -1L) == triggerAt) return

        val alarm = AlarmRepository.get(context, alarmId)
        if (alarm == null && alarmId != TEST_ALARM_ID) return

        try {
            AlarmService.start(context, alarmId)
        } catch (e: Exception) {
            // Leave the trigger unmarked so the backup broadcast can try again.
            Log.e("AlarmTrigger", "Cannot start alarm service", e)
            return
        }
        prefs.edit().putLong(key, triggerAt).commit()

        if (alarm != null && !snooze) {
            if (alarm.isRepeating) {
                // Look one minute ahead so the alarm that has just fired is not set again for today.
                AlarmScheduler.schedule(context, alarm, ZonedDateTime.now().plusMinutes(1))
            } else {
                AlarmRepository.save(context, alarm.copy(enabled = false))
            }
        }
    }
}
