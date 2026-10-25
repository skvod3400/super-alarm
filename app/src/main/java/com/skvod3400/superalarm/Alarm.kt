package com.skvod3400.superalarm

import org.json.JSONObject
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 means a one-time alarm. */
    val days: Int,
    val enabled: Boolean,
    val label: String,
    val ringtoneUri: String?,
) {
    val isRepeating get() = days != 0

    val timeText get() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    val daysText: String
        get() = when (days) {
            0 -> "Один раз"
            ALL_DAYS -> "Каждый день"
            WEEKDAYS -> "Будни"
            WEEKEND -> "Выходные"
            else -> DAY_NAMES.filterIndexed { i, _ -> isDayEnabled(i) }.joinToString(", ")
        }

    fun isDayEnabled(dayIndex: Int) = (days and (1 shl dayIndex)) != 0

    /** First moment strictly after [now] when this alarm should ring. */
    fun nextTrigger(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val today = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        for (i in 0..7) {
            val candidate = today.plusDays(i.toLong())
            if (!candidate.isAfter(now)) continue
            val dayIndex = candidate.dayOfWeek.value - 1
            if (!isRepeating || isDayEnabled(dayIndex)) return candidate
        }
        return today.plusDays(1)
    }

    fun timeUntilText(): String {
        val minutes = Duration.between(ZonedDateTime.now(), nextTrigger()).toMinutes() + 1
        val parts = buildList {
            val d = minutes / (60 * 24)
            val h = minutes / 60 % 24
            val m = minutes % 60
            if (d > 0) add("$d дн.")
            if (h > 0) add("$h ч")
            if (m > 0) add("$m мин")
        }
        return "Сработает через " + parts.joinToString(" ")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("days", days)
        .put("enabled", enabled)
        .put("label", label)
        .apply { if (ringtoneUri != null) put("ringtone", ringtoneUri) }

    companion object {
        val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000

        fun fromJson(json: JSONObject) = Alarm(
            id = json.getInt("id"),
            hour = json.getInt("hour"),
            minute = json.getInt("minute"),
            days = json.getInt("days"),
            enabled = json.getBoolean("enabled"),
            label = json.optString("label"),
            ringtoneUri = if (json.has("ringtone")) json.getString("ringtone") else null,
        )
    }
}
