package com.metrolist.music.ui.screens.settings

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AlarmEnabledKey
import com.metrolist.music.constants.AlarmHourKey
import com.metrolist.music.constants.AlarmMinuteKey
import com.metrolist.music.constants.AlarmNextTriggerAtKey
import com.metrolist.music.constants.AlarmPlaylistIdKey
import com.metrolist.music.constants.AlarmRandomSongKey
import com.metrolist.music.playback.alarm.MusicAlarmScheduler
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    val playlists by database.playlistsByNameAsc().collectAsState(initial = emptyList())

    val (alarmEnabled, onAlarmEnabledChange) = rememberPreference(AlarmEnabledKey, false)
    val (alarmHour, onAlarmHourChange) = rememberPreference(AlarmHourKey, 7)
    val (alarmMinute, onAlarmMinuteChange) = rememberPreference(AlarmMinuteKey, 0)
    val (alarmPlaylistId, onAlarmPlaylistIdChange) = rememberPreference(AlarmPlaylistIdKey, "")
    val (alarmRandomSong, onAlarmRandomSongChange) = rememberPreference(AlarmRandomSongKey, false)
    val nextTriggerAt by rememberPreference(AlarmNextTriggerAtKey, -1L)

    var showPlaylistDialog by remember { mutableStateOf(false) }
    val selectedPlaylist = playlists.firstOrNull { it.id == alarmPlaylistId }

    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else {
        true
    }
    val powerManager = context.getSystemService(PowerManager::class.java)
    val ignoringBatteryOptimization = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else {
        true
    }

    val nextTriggerText = remember(nextTriggerAt) {
        if (nextTriggerAt <= 0L) {
            null
        } else {
            DateTimeFormatter.ofPattern("EEE, HH:mm", Locale.getDefault())
                .format(Instant.ofEpochMilli(nextTriggerAt).atZone(ZoneId.systemDefault()))
        }
    }

    fun updateSchedule(
        enabled: Boolean = alarmEnabled,
        hour: Int = alarmHour,
        minute: Int = alarmMinute,
        playlistId: String = alarmPlaylistId,
        randomSong: Boolean = alarmRandomSong
    ) {
        scope.launch(Dispatchers.IO) {
            if (!enabled) {
                MusicAlarmScheduler.cancel(context)
                return@launch
            }
            if (playlistId.isBlank()) {
                MusicAlarmScheduler.cancel(context)
                return@launch
            }
            MusicAlarmScheduler.schedule(context, hour, minute, playlistId, randomSong)
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text(stringResource(R.string.alarm_playlist)) },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text(stringResource(R.string.alarm_no_playlists))
                    } else {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    onAlarmPlaylistIdChange(playlist.id)
                                    showPlaylistDialog = false
                                    updateSchedule(playlistId = playlist.id)
                                }
                            ) {
                                Text(playlist.title)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Material3SettingsGroup(
            title = stringResource(R.string.alarm),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.bedtime),
                    title = { Text(stringResource(R.string.alarm_enabled)) },
                    trailingContent = {
                        Switch(
                            checked = alarmEnabled,
                            onCheckedChange = {
                                onAlarmEnabledChange(it)
                                updateSchedule(enabled = it)
                            },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        if (alarmEnabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        val next = !alarmEnabled
                        onAlarmEnabledChange(next)
                        updateSchedule(enabled = next)
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.timer),
                    title = { Text(stringResource(R.string.alarm_time)) },
                    description = {
                        Text(String.format(Locale.getDefault(), "%02d:%02d", alarmHour, alarmMinute))
                    },
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                onAlarmHourChange(hourOfDay)
                                onAlarmMinuteChange(minute)
                                updateSchedule(hour = hourOfDay, minute = minute)
                            },
                            alarmHour,
                            alarmMinute,
                            true
                        ).show()
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.alarm_playlist)) },
                    description = {
                        Text(selectedPlaylist?.title ?: stringResource(R.string.alarm_select_playlist))
                    },
                    onClick = {
                        if (playlists.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.alarm_no_playlists), Toast.LENGTH_SHORT).show()
                        } else {
                            showPlaylistDialog = true
                        }
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.alarm_random_song)) },
                    trailingContent = {
                        Switch(
                            checked = alarmRandomSong,
                            onCheckedChange = {
                                onAlarmRandomSongChange(it)
                                updateSchedule(randomSong = it)
                            },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        if (alarmRandomSong) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        val next = !alarmRandomSong
                        onAlarmRandomSongChange(next)
                        updateSchedule(randomSong = next)
                    }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.update),
                    title = { Text(stringResource(R.string.alarm_next_trigger)) },
                    description = {
                        Text(nextTriggerText ?: stringResource(R.string.alarm_not_scheduled))
                    },
                    onClick = {
                        updateSchedule()
                    }
                )
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_system),
            items = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExact) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.warning),
                            title = { Text(stringResource(R.string.alarm_exact_permission_title)) },
                            description = { Text(stringResource(R.string.alarm_exact_permission_desc)) },
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData("package:${context.packageName}".toUri())
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                }
                            }
                        )
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !ignoringBatteryOptimization) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.warning),
                            title = { Text(stringResource(R.string.alarm_battery_optimization_title)) },
                            description = { Text(stringResource(R.string.alarm_battery_optimization_desc)) },
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                }
                            }
                        )
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.alarm)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
