package com.skvod3400.superalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms are lost on reboot or clock changes, so put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
