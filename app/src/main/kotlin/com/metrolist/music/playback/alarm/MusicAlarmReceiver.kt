package com.metrolist.music.playback.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.metrolist.music.playback.MusicService

class MusicAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TRIGGER_ALARM) return
        val playlistId = intent.getStringExtra(MusicService.EXTRA_ALARM_PLAYLIST_ID).orEmpty()
        val randomSong = intent.getBooleanExtra(MusicService.EXTRA_ALARM_RANDOM_SONG, false)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Metrolist:AlarmWakeLock")
        try {
            wakeLock?.acquire(60_000)
            val serviceIntent = Intent(context, MusicService::class.java)
                .setAction(MusicService.ACTION_ALARM_TRIGGER)
                .putExtra(MusicService.EXTRA_ALARM_PLAYLIST_ID, playlistId)
                .putExtra(MusicService.EXTRA_ALARM_RANDOM_SONG, randomSong)
            ContextCompat.startForegroundService(context, serviceIntent)
            MusicAlarmScheduler.scheduleFromPreferences(context)
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.metrolist.music.action.TRIGGER_ALARM"
    }
}
