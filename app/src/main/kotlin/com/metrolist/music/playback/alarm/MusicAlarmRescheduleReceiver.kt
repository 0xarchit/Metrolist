package com.metrolist.music.playback.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MusicAlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> MusicAlarmScheduler.scheduleFromPreferences(context)
        }
    }
}
