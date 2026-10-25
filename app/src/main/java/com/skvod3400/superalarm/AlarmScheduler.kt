package com.skvod3400.superalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

const val EXTRA_ALARM_ID = "alarm_id"
const val EXTRA_SNOOZE = "snooze"

object AlarmScheduler {
    const val SNOOZE_MINUTES = 5L
    private const val SNOOZE_REQUEST_OFFSET = 1_000_000

    fun schedule(context: Context, alarm: Alarm, from: ZonedDateTime = ZonedDateTime.now()) {
        cancel(context, alarm.id)
        if (!alarm.enabled) return
        val next = alarm.nextTrigger(from)
        setAlarm(context, next.toInstant().toEpochMilli(), alarm.id, snooze = false)
    }

    fun snooze(context: Context, alarmId: Int) {
        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000
        setAlarm(context, triggerAt, alarmId, snooze = true)
    }

    fun cancel(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(operation(context, alarmId, snooze = false))
        alarmManager.cancel(operation(context, alarmId, snooze = true))
    }

    fun rescheduleAll(context: Context) {
        AlarmRepository.getAll(context).forEach { schedule(context, it) }
    }

    private fun setAlarm(context: Context, triggerAt: Long, alarmId: Int, snooze: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val op = operation(context, alarmId, snooze)
        if (alarmManager.canScheduleExactAlarms()) {
            val showIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), op)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
        }
    }

    private fun operation(context: Context, alarmId: Int, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_SNOOZE, snooze)
        val requestCode = if (snooze) alarmId + SNOOZE_REQUEST_OFFSET else alarmId
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
