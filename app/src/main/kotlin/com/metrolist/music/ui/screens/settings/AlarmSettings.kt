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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
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
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.playback.alarm.MusicAlarmEntry
import com.metrolist.music.playback.alarm.MusicAlarmScheduler
import com.metrolist.music.playback.alarm.MusicAlarmStore
import com.metrolist.music.ui.component.IconButton as AppIconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
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

    var alarms by remember { mutableStateOf(emptyList<MusicAlarmEntry>()) }
    var showEditor by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<MusicAlarmEntry?>(null) }

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

    fun refreshAlarms() {
        alarms = MusicAlarmStore.load(context).sortedBy { it.hour * 60 + it.minute }
    }

    fun persistAndSchedule(newList: List<MusicAlarmEntry>) {
        scope.launch(Dispatchers.IO) {
            MusicAlarmScheduler.scheduleAll(context, newList)
            refreshAlarms()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        refreshAlarms()
    }

    if (showEditor) {
        AlarmEditorDialog(
            existing = editorTarget,
            allAlarms = alarms,
            playlists = playlists,
            onDismiss = {
                showEditor = false
                editorTarget = null
            },
            onSave = { updated ->
                val merged = alarms.filterNot { it.id == updated.id } + updated
                persistAndSchedule(merged)
                showEditor = false
                editorTarget = null
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
                    icon = painterResource(R.drawable.add_circle),
                    title = { Text(stringResource(R.string.alarm_add)) },
                    onClick = {
                        editorTarget = null
                        showEditor = true
                    }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (alarms.isEmpty()) {
            Text(
                text = stringResource(R.string.alarm_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        } else {
            Material3SettingsGroup(
                items = alarms.map { alarm ->
                    val playlistTitle = playlists.firstOrNull { it.id == alarm.playlistId }?.title
                        ?: stringResource(R.string.alarm_select_playlist)
                    val triggerText = if (alarm.nextTriggerAt > 0L) {
                        DateTimeFormatter.ofPattern("EEE, HH:mm", Locale.getDefault())
                            .format(Instant.ofEpochMilli(alarm.nextTriggerAt).atZone(ZoneId.systemDefault()))
                    } else {
                        stringResource(R.string.alarm_not_scheduled)
                    }
                    val description = buildString {
                        append(playlistTitle)
                        append(" • ")
                        append(
                            if (alarm.randomSong) {
                                context.getString(R.string.alarm_random_enabled)
                            } else {
                                context.getString(R.string.alarm_random_disabled)
                            }
                        )
                        append("\n")
                        append(context.getString(R.string.alarm_next_prefix, triggerText))
                    }

                    Material3SettingsItem(
                        icon = painterResource(R.drawable.bedtime),
                        title = {
                            Text(
                                String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute) +
                                    if (alarm.enabled) "" else " (${stringResource(R.string.alarm_disabled)})"
                            )
                        },
                        description = { Text(description) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = alarm.enabled,
                                    onCheckedChange = { enabled ->
                                        val updated = alarms.map {
                                            if (it.id == alarm.id) it.copy(enabled = enabled) else it
                                        }
                                        persistAndSchedule(updated)
                                    }
                                )
                                IconButton(
                                    onClick = {
                                        val updated = alarms.filterNot { it.id == alarm.id }
                                        persistAndSchedule(updated)
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = stringResource(R.string.alarm_delete)
                                    )
                                }
                            }
                        },
                        onClick = {
                            editorTarget = alarm
                            showEditor = true
                        }
                    )
                }
            )
        }

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
            AppIconButton(
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

@Composable
private fun AlarmEditorDialog(
    existing: MusicAlarmEntry?,
    allAlarms: List<MusicAlarmEntry>,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSave: (MusicAlarmEntry) -> Unit
) {
    val context = LocalContext.current
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var hour by remember { mutableStateOf(existing?.hour ?: 7) }
    var minute by remember { mutableStateOf(existing?.minute ?: 0) }
    var playlistId by remember { mutableStateOf(existing?.playlistId.orEmpty()) }
    var randomSong by remember { mutableStateOf(existing?.randomSong ?: false) }

    val hasSameTimeAlarm = remember(hour, minute, existing, allAlarms) {
        allAlarms.any {
            it.id != existing?.id && it.hour == hour && it.minute == minute
        }
    }

    val selectedPlaylist = playlists.firstOrNull { it.id == playlistId }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text(stringResource(R.string.alarm_playlist)) },
            text = {
                if (playlists.isEmpty()) {
                    Text(stringResource(R.string.alarm_no_playlists))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        items(items = playlists, key = { it.id }) { playlist ->
                            val selected = playlist.id == playlistId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    }
                                ),
                                onClick = {
                                    playlistId = playlist.id
                                    showPlaylistDialog = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.title,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = stringResource(R.string.alarm_playlist_song_count, playlist.songCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            painter = painterResource(R.drawable.check),
                                            contentDescription = stringResource(R.string.alarm_selected),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) {
                    stringResource(R.string.alarm_new)
                } else {
                    stringResource(R.string.alarm_edit)
                }
            )
        },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.alarm_enabled),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                TextButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, selectedHour, selectedMinute ->
                            hour = selectedHour
                            minute = selectedMinute
                        },
                        hour,
                        minute,
                        true
                    ).show()
                }) {
                    Text(
                        text = stringResource(
                            R.string.alarm_time_picker_value,
                            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                        )
                    )
                }

                TextButton(onClick = {
                    if (playlists.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.alarm_no_playlists), Toast.LENGTH_SHORT).show()
                    } else {
                        showPlaylistDialog = true
                    }
                }) {
                    Text(
                        text = selectedPlaylist?.title ?: stringResource(R.string.alarm_select_playlist)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.alarm_random_song),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = randomSong, onCheckedChange = { randomSong = it })
                }

                if (hasSameTimeAlarm) {
                    Text(
                        text = stringResource(R.string.alarm_duplicate_time_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (playlistId.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.alarm_select_playlist), Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    onSave(
                        (existing ?: MusicAlarmStore.createEmpty()).copy(
                            enabled = enabled,
                            hour = hour,
                            minute = minute,
                            playlistId = playlistId,
                            randomSong = randomSong
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.alarm_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
