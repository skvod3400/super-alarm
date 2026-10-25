package com.skvod3400.superalarm

import android.content.Context
import org.json.JSONArray

/** Stores alarms as a JSON array in SharedPreferences. */
object AlarmRepository {
    private const val PREFS = "alarms"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_NEXT_ID = "next_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<Alarm> {
        val json = prefs(context).getString(KEY_ALARMS, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length())
            .map { Alarm.fromJson(array.getJSONObject(it)) }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    fun get(context: Context, id: Int): Alarm? = getAll(context).find { it.id == id }

    fun save(context: Context, alarm: Alarm) {
        write(context, getAll(context).filter { it.id != alarm.id } + alarm)
    }

    fun delete(context: Context, id: Int) {
        write(context, getAll(context).filter { it.id != id })
    }

    fun newId(context: Context): Int {
        val prefs = prefs(context)
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    private fun write(context: Context, alarms: List<Alarm>) {
        val json = JSONArray(alarms.map { it.toJson() }).toString()
        prefs(context).edit().putString(KEY_ALARMS, json).apply()
    }
}
