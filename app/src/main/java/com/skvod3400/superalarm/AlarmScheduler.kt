package com.skvod3400.superalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

const val EXTRA_ALARM_ID = "alarm_id"
const val EXTRA_SNOOZE = "snooze"
const val EXTRA_TRIGGER_AT = "trigger_at"
const val ACTION_FIRE = "com.skvod3400.superalarm.FIRE"

/** Id of the test alarm from the main screen menu; it is not stored in [AlarmRepository]. */
const val TEST_ALARM_ID = 0

object AlarmScheduler {
    const val SNOOZE_MINUTES = 5L
    private const val SNOOZE_REQUEST_OFFSET = 1_000_000
    private const val BACKUP_DELAY_MS = 1500L
    private const val TEST_DELAY_MS = 10_000L

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

    fun test(context: Context) {
        setAlarm(context, System.currentTimeMillis() + TEST_DELAY_MS, TEST_ALARM_ID, snooze = true)
    }

    fun cancel(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (snooze in listOf(false, true)) {
            val flags = PendingIntent.FLAG_NO_CREATE
            listOfNotNull(
                screenIntent(context, alarmId, snooze, 0, flags),
                backupIntent(context, alarmId, snooze, 0, flags),
            ).forEach {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    fun rescheduleAll(context: Context) {
        AlarmRepository.getAll(context).forEach { schedule(context, it) }
    }

    private fun setAlarm(context: Context, triggerAt: Long, alarmId: Int, snooze: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val backup = backupIntent(context, alarmId, snooze, triggerAt)!!
        if (!alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, backup)
            return
        }

        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        // The alarm clock opens the ringing screen directly. Unlike the full-screen notification,
        // this works on the lock screen without extra permissions.
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            screenIntent(context, alarmId, snooze, triggerAt)!!,
        )
        // If the system refuses to open the screen, this broadcast still starts the sound.
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAt + BACKUP_DELAY_MS, backup,
        )
    }

    private fun requestCode(alarmId: Int, snooze: Boolean) =
        if (snooze) alarmId + SNOOZE_REQUEST_OFFSET else alarmId

    private fun fireIntent(intent: Intent, alarmId: Int, snooze: Boolean, triggerAt: Long) = intent
        .setAction(ACTION_FIRE)
        .putExtra(EXTRA_ALARM_ID, alarmId)
        .putExtra(EXTRA_SNOOZE, snooze)
        .putExtra(EXTRA_TRIGGER_AT, triggerAt)

    private fun screenIntent(
        context: Context, alarmId: Int, snooze: Boolean, triggerAt: Long,
        flags: Int = PendingIntent.FLAG_UPDATE_CURRENT,
    ): PendingIntent? = PendingIntent.getActivity(
        context, requestCode(alarmId, snooze),
        fireIntent(AlarmActivity.intent(context, alarmId), alarmId, snooze, triggerAt),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun backupIntent(
        context: Context, alarmId: Int, snooze: Boolean, triggerAt: Long,
        flags: Int = PendingIntent.FLAG_UPDATE_CURRENT,
    ): PendingIntent? = PendingIntent.getBroadcast(
        context, requestCode(alarmId, snooze),
        fireIntent(Intent(context, AlarmReceiver::class.java), alarmId, snooze, triggerAt),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )
}
