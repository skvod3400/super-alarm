package com.skvod3400.superalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Backup path: starts the sound if the ringing screen could not be opened. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmTrigger.fire(context, intent)
    }
}
