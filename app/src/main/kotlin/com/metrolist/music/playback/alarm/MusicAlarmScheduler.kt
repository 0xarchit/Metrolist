package com.metrolist.music.playback.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.AlarmNextTriggerAtKey
import com.metrolist.music.playback.MusicService
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object MusicAlarmScheduler {
    fun scheduleFromPreferences(context: Context) {
        val alarms = MusicAlarmStore.load(context)
        scheduleAll(context, alarms)
    }

    fun scheduleAll(context: Context, alarms: List<MusicAlarmEntry>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val updated = alarms.map { alarm ->
            cancel(context, alarm.id)
            if (!alarm.enabled || alarm.playlistId.isBlank()) {
                alarm.copy(nextTriggerAt = -1L)
            } else {
                val triggerAtMillis = nextTriggerMillis(alarm.hour, alarm.minute)
                val pendingIntent = alarmPendingIntent(context, alarm)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                alarm.copy(nextTriggerAt = triggerAtMillis)
            }
        }
        MusicAlarmStore.save(context, updated)
    }

    fun cancel(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(alarmPendingIntent(context, alarmId))
    }

    fun cancelAll(context: Context) {
        val existing = MusicAlarmStore.load(context)
        existing.forEach { cancel(context, it.id) }
        runBlocking(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[AlarmNextTriggerAtKey] = -1L
            }
        }
    }

    private fun alarmPendingIntent(
        context: Context,
        alarm: MusicAlarmEntry
    ): PendingIntent {
        val intent = Intent(context, MusicAlarmReceiver::class.java)
            .setAction(MusicAlarmReceiver.ACTION_TRIGGER_ALARM)
            .putExtra(MusicService.EXTRA_ALARM_ID, alarm.id)
            .putExtra(MusicService.EXTRA_ALARM_PLAYLIST_ID, alarm.playlistId)
            .putExtra(MusicService.EXTRA_ALARM_RANDOM_SONG, alarm.randomSong)

        return PendingIntent.getBroadcast(
            context,
            requestCode(alarm.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmPendingIntent(context: Context, alarmId: String): PendingIntent {
        val intent = Intent(context, MusicAlarmReceiver::class.java)
            .setAction(MusicAlarmReceiver.ACTION_TRIGGER_ALARM)
            .putExtra(MusicService.EXTRA_ALARM_ID, alarmId)

        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(alarmId: String): Int {
        return 90_000 + (alarmId.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % 9_000)
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
