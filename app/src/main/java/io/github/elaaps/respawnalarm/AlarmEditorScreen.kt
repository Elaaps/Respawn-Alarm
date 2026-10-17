package io.github.elaaps.respawnalarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.ZonedDateTime

@Composable
fun AlarmEditorScreen(alarmId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AlarmStore(context) }
    var alarm by remember { mutableStateOf(store.alarm(alarmId)) }
    val current = alarm
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val settings = remember { store.settings() }
    var pickingTime by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val collapsed by remember { derivedStateOf { scroll.value > COLLAPSE_AFTER_PX } }

    fun update(updated: Alarm) {
        alarm = updated
        store.put(updated)
        AlarmScheduler.schedule(context)
    }

    val time = formatClockTime(context, LocalTime.of(current.hour, current.minute))
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(collapsed, enter = fadeIn(), exit = fadeOut()) {
                        Text(listOf(time, current.label).filter { it.isNotBlank() }.joinToString(" · "))
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = {
                        store.delete(current.id)
                        AlarmScheduler.schedule(context)
                        onBack()
                    }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete_alarm)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimePill(
                time,
                enabled = current.enabled,
                canRing = settings.resolve(current).targetPackage != null,
                onClick = { pickingTime = true },
                onToggle = { update(current.copy(enabled = it)) },
            )
            DayPicker(current.days, { update(current.copy(days = it, skippedDate = null)) })
            RowGroup {
                row { shapes -> NameRow(current.label, shapes) { update(current.copy(label = it)) } }
                if (current.enabled && !current.isOneShot) row { shapes -> SkipNextRow(current, shapes, ::update) }
            }
            OverrideGroups(current, settings, ::update)
        }
    }

    if (pickingTime) {
        AlarmTimePicker(
            LocalTime.of(current.hour, current.minute),
            onPick = {
                pickingTime = false
                update(current.copy(hour = it.hour, minute = it.minute, enabled = true, skippedDate = null))
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun TimePill(time: String, enabled: Boolean, canRing: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(TIME_PILL_CORNER),
        color = if (enabled) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 28.dp, end = 20.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(time, style = MaterialTheme.typography.displayLarge, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = canRing)
        }
    }
}

@Composable
private fun NameRow(label: String, shapes: ListItemShapes, onChange: (String) -> Unit) {
    SegmentedListItem(
        shapes = shapes,
        leadingContent = { ShapedIcon(RowIcon(R.drawable.ic_label, IconStyle.OTHER)) },
        overlineContent = { Text(stringResource(R.string.alarm_label)) },
    ) {
        BasicTextField(
            value = label,
            onValueChange = onChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OverrideGroups(alarm: Alarm, settings: GlobalSettings, onChange: (Alarm) -> Unit) {
    val resolved = settings.resolve(alarm)
    RowGroup(stringResource(R.string.section_overrides)) {
        row { shapes ->
            TargetAppRow(alarm.targetPackage, settings.targetPackage, shapes) { onChange(alarm.copy(targetPackage = it)) }
        }
        row { shapes ->
            PlayTimeRow(alarm.durationSec, settings.durationSec, shapes) { onChange(alarm.copy(durationSec = it)) }
        }
    }
    RowGroup {
        row { shapes ->
            ToneRow(
                alarm.tone,
                defaultTone = settings.tone,
                soundEnabled = resolved.soundEnabled,
                shapes = shapes,
                onSoundChange = { onChange(alarm.copy(soundEnabled = ownValue(it, settings.soundEnabled))) },
            ) { onChange(alarm.copy(tone = it)) }
        }
        row { shapes ->
            ChoiceRow(
                stringResource(R.string.volume),
                VolumeMode.entries.map { Choice(it, markDefault(volumeLabel(it), isDefault = it == settings.volume)) },
                resolved.volume,
                shapes,
                RowIcon(R.drawable.ic_volume_up, IconStyle.RINGING),
                enabled = resolved.soundEnabled,
            ) { onChange(alarm.copy(volume = ownValue(it, settings.volume))) }
        }
        row { shapes -> VibrateRow(resolved, shapes) { onChange(alarm.copy(vibrate = ownValue(it, settings.vibrate))) } }
        row { shapes -> SnoozeRow(alarm.snooze, defaultSnooze = settings.snooze, shapes) { onChange(alarm.copy(snooze = it)) } }
    }
}

@Composable
private fun SkipNextRow(alarm: Alarm, shapes: ListItemShapes, onChange: (Alarm) -> Unit) {
    val now = ZonedDateTime.now()
    val unskipped = alarm.copy(skippedDate = null)
    val nextDate = nextRing(unskipped, now)?.toLocalDate() ?: return
    val skippedDate = pendingSkip(alarm, now)
    val pattern = dateFormatter(R.string.short_date_pattern)
    SwitchRow(
        stringResource(R.string.skip_next),
        checked = skippedDate != null,
        onChange = { onChange(if (it) alarm.copy(skippedDate = nextDate) else unskipped) },
        shapes = shapes,
        icon = RowIcon(R.drawable.ic_event_busy, IconStyle.OTHER),
        description = (skippedDate ?: nextDate).format(pattern),
    )
}

private const val COLLAPSE_AFTER_PX = 200
private val TIME_PILL_CORNER = 40.dp
