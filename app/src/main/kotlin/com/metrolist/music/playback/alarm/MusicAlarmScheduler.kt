package com.metrolist.music.playback.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.AlarmEnabledKey
import com.metrolist.music.constants.AlarmHourKey
import com.metrolist.music.constants.AlarmMinuteKey
import com.metrolist.music.constants.AlarmNextTriggerAtKey
import com.metrolist.music.constants.AlarmPlaylistIdKey
import com.metrolist.music.constants.AlarmRandomSongKey
import com.metrolist.music.playback.MusicService
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object MusicAlarmScheduler {
    private const val REQUEST_CODE = 90421

    fun scheduleFromPreferences(context: Context) {
        runBlocking(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()
            val enabled = prefs[AlarmEnabledKey] ?: false
            val playlistId = prefs[AlarmPlaylistIdKey].orEmpty()
            val hour = prefs[AlarmHourKey] ?: 7
            val minute = prefs[AlarmMinuteKey] ?: 0
            val random = prefs[AlarmRandomSongKey] ?: false
            if (!enabled || playlistId.isBlank()) {
                cancel(context)
                return@runBlocking
            }
            schedule(context, hour, minute, playlistId, random)
        }
    }

    fun schedule(
        context: Context,
        hour: Int,
        minute: Int,
        playlistId: String,
        randomSong: Boolean
    ) {
        if (playlistId.isBlank()) {
            cancel(context)
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = nextTriggerMillis(hour, minute)
        val pendingIntent = alarmPendingIntent(context, playlistId, randomSong)
        alarmManager.cancel(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }

        runBlocking(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[AlarmNextTriggerAtKey] = triggerAtMillis
            }
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(alarmPendingIntent(context, "", false))
        runBlocking(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[AlarmNextTriggerAtKey] = -1L
            }
        }
    }

    private fun alarmPendingIntent(
        context: Context,
        playlistId: String,
        randomSong: Boolean
    ): PendingIntent {
        val intent = Intent(context, MusicAlarmReceiver::class.java)
            .setAction(MusicAlarmReceiver.ACTION_TRIGGER_ALARM)
            .putExtra(MusicService.EXTRA_ALARM_PLAYLIST_ID, playlistId)
            .putExtra(MusicService.EXTRA_ALARM_RANDOM_SONG, randomSong)

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
