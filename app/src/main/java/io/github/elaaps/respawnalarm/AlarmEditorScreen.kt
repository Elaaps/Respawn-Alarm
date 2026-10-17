package io.github.elaaps.respawnalarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

    fun update(updated: Alarm) {
        alarm = updated
        store.put(updated)
        AlarmScheduler.schedule(context)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(stringResource(R.string.editor_title), onBack = onBack) {
                HeaderAction(R.drawable.ic_delete, stringResource(R.string.delete_alarm)) {
                    store.delete(current.id)
                    AlarmScheduler.schedule(context)
                    onBack()
                }
            }
            SectionCard(stringResource(R.string.section_alarm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatClockTime(context, LocalTime.of(current.hour, current.minute)),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                pickTime(context, LocalTime.of(current.hour, current.minute)) {
                                    update(current.copy(hour = it.hour, minute = it.minute, enabled = true, skippedDate = null))
                                }
                            },
                    )
                    Switch(
                        checked = current.enabled,
                        enabled = settings.resolve(current).targetPackage != null,
                        onCheckedChange = { update(current.copy(enabled = it)) },
                    )
                }
                DayPicker(current.days) { update(current.copy(days = it, skippedDate = null)) }
                OutlinedTextField(
                    value = current.label,
                    onValueChange = { update(current.copy(label = it)) },
                    label = { Text(stringResource(R.string.alarm_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(visible = current.enabled && !current.isOneShot) { SkipNextRow(current, ::update) }
            }
            OverridesCard(current, settings, ::update)
        }
    }
}

@Composable
private fun OverridesCard(alarm: Alarm, settings: GlobalSettings, onChange: (Alarm) -> Unit) {
    SectionCard(stringResource(R.string.section_overrides)) {
        TargetAppRow(alarm.targetPackage, settings.targetPackage) {
            onChange(alarm.copy(targetPackage = it))
        }
        PlayTimeRow(alarm.durationSec, settings.durationSec) { onChange(alarm.copy(durationSec = it)) }
        ToneRow(alarm.tone, defaultTone = settings.tone) { onChange(alarm.copy(tone = it)) }
        ChoiceRow(
            stringResource(R.string.volume),
            VolumeMode.entries.map { Choice(it, markDefault(volumeLabel(it), isDefault = it == settings.volume)) },
            alarm.volume ?: settings.volume,
        ) { onChange(alarm.copy(volume = ownValue(it, settings.volume))) }
        SwitchRow(
            stringResource(R.string.vibrate),
            alarm.vibrate ?: settings.vibrate,
            { onChange(alarm.copy(vibrate = ownValue(it, settings.vibrate))) },
        )
        SnoozeRow(alarm.snooze, defaultSnooze = settings.snooze) { onChange(alarm.copy(snooze = it)) }
    }
}

@Composable
private fun SkipNextRow(alarm: Alarm, onChange: (Alarm) -> Unit) {
    val now = ZonedDateTime.now()
    val unskipped = alarm.copy(skippedDate = null)
    val nextDate = nextRing(unskipped, now)?.toLocalDate() ?: return
    val skippedDate = pendingSkip(alarm, now)
    val pattern = DateTimeFormatter.ofPattern(stringResource(R.string.short_date_pattern))
    SwitchRow(
        stringResource(R.string.skip_next),
        checked = skippedDate != null,
        onChange = { onChange(if (it) alarm.copy(skippedDate = nextDate) else unskipped) },
        description = (skippedDate ?: nextDate).format(pattern),
    )
}
