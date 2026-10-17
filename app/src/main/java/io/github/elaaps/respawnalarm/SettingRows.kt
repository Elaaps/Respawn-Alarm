package io.github.elaaps.respawnalarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@Composable
fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun SwitchWithDetailsRow(label: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onOpen)
                .padding(vertical = 4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VerticalDivider(Modifier.height(DETAILS_DIVIDER_HEIGHT).padding(horizontal = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val DETAILS_DIVIDER_HEIGHT = 32.dp

data class Choice<T>(val value: T, val label: String)

@Composable
fun <T> ChoiceRow(label: String, choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    ValueRow(label, choices.firstOrNull { it.value == selected }?.label.orEmpty()) { choosing = true }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(label) },
            text = {
                Column {
                    choices.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    choosing = false
                                    onSelect(choice.value)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice.value == selected, onClick = null)
                            Text(choice.label, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun defaultLabel(value: String): String = stringResource(R.string.use_default, value)

@Composable
fun onOffLabel(enabled: Boolean): String = stringResource(if (enabled) R.string.on else R.string.off)

@Composable
fun volumeLabel(volume: VolumeMode): String = stringResource(
    when (volume) {
        VolumeMode.PHONE -> R.string.volume_phone
        VolumeMode.MAX -> R.string.volume_max
    },
)

fun toneLabel(context: Context, tone: Tone): String = when (tone) {
    Tone.SystemDefault -> context.getString(R.string.tone_system)
    is Tone.Custom -> RingtoneManager.getRingtone(context, tone.uri.toUri())?.getTitle(context)
        ?: context.getString(R.string.tone_custom)
}

class PickAlarmTone : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.tone_picker_title))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) else null
}

private fun toneOf(uri: Uri): Tone =
    if (uri == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) Tone.SystemDefault else Tone.Custom(uri.toString())

@Composable
fun ToneRow(tone: Tone?, defaultTone: Tone?, onChange: (Tone?) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(PickAlarmTone()) { uri -> uri?.let { onChange(toneOf(it)) } }
    val current = tone ?: defaultTone ?: Tone.SystemDefault
    val choices = buildList {
        if (defaultTone != null) add(Choice(ToneChoice.Default, defaultLabel(toneLabel(context, defaultTone))))
        add(Choice(ToneChoice.System, stringResource(R.string.tone_system)))
        if (current is Tone.Custom) add(Choice(ToneChoice.Current, toneLabel(context, current)))
        add(Choice(ToneChoice.Pick, stringResource(R.string.tone_pick)))
    }
    val selected = when {
        tone == null && defaultTone != null -> ToneChoice.Default
        current is Tone.Custom -> ToneChoice.Current
        else -> ToneChoice.System
    }
    ChoiceRow(stringResource(R.string.tone), choices, selected) { choice ->
        when (choice) {
            ToneChoice.Default -> onChange(null)
            ToneChoice.System -> onChange(Tone.SystemDefault)
            ToneChoice.Current -> Unit
            ToneChoice.Pick -> picker.launch((current as? Tone.Custom)?.uri?.toUri())
        }
    }
}

@Composable
fun PlayTimeRow(durationSec: Int?, defaultSec: Int, onChange: (Int?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val defaultText = formatDuration(defaultSec * MILLIS_PER_SECOND)
    val value = durationSec?.let { formatDuration(it * MILLIS_PER_SECOND) } ?: defaultLabel(defaultText)
    ValueRow(stringResource(R.string.play_time), value) { editing = true }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.play_time)) },
            text = {
                val shownSec = durationSec ?: defaultSec
                Stepper(
                    label = "",
                    value = formatDuration(shownSec * MILLIS_PER_SECOND),
                    onMinus = { onChange(ownValue(stepPlayTime(shownSec, -GlobalSettings.DURATION_STEP_SEC), defaultSec)) },
                    onPlus = { onChange(ownValue(stepPlayTime(shownSec, GlobalSettings.DURATION_STEP_SEC), defaultSec)) },
                )
            },
            confirmButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}

fun stepPlayTime(durationSec: Int, deltaSec: Int): Int = (durationSec + deltaSec).coerceIn(GlobalSettings.DURATION_RANGE_SEC)

private const val MILLIS_PER_SECOND = 1_000L

@Composable
fun snoozeLabel(snooze: Snooze): String =
    if (snooze.isAllowed) stringResource(R.string.snooze_value, snooze.maxCount, snooze.minutes) else onOffLabel(enabled = false)

@Composable
fun SnoozeRow(snooze: Snooze?, defaultSnooze: Snooze?, onChange: (Snooze?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val shown = snooze ?: defaultSnooze ?: Snooze.OFF
    val value = if (snooze == null && defaultSnooze != null) defaultLabel(snoozeLabel(defaultSnooze)) else snoozeLabel(shown)
    ValueRow(stringResource(R.string.snooze), value) { editing = true }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.snooze)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SnoozeSteppers(shown) { onChange(ownValue(it, defaultSnooze)) }
                }
            },
            confirmButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun SnoozeSteppers(shown: Snooze, onChange: (Snooze) -> Unit) {
    Stepper(
        label = stringResource(R.string.snooze_count),
        value = shown.maxCount.toString(),
        onMinus = { onChange(shown.copy(maxCount = (shown.maxCount - 1).coerceIn(Snooze.COUNT_RANGE))) },
        onPlus = { onChange(shown.copy(maxCount = (shown.maxCount + 1).coerceIn(Snooze.COUNT_RANGE))) },
    )
    Stepper(
        label = stringResource(R.string.snooze_minutes),
        value = stringResource(R.string.minutes_value, shown.minutes),
        onMinus = { onChange(shown.copy(minutes = (shown.minutes - 1).coerceIn(Snooze.MINUTES_RANGE))) },
        onPlus = { onChange(shown.copy(minutes = (shown.minutes + 1).coerceIn(Snooze.MINUTES_RANGE))) },
    )
}

private enum class ToneChoice { Default, System, Current, Pick }

@Composable
fun TargetAppRow(packageName: String?, defaultPackage: String?, overridable: Boolean, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val shown = packageName ?: defaultPackage
    Row(
        modifier = Modifier.fillMaxWidth().clickable { if (overridable) choosing = true else picking = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val icon = remember(shown) { appIcon(context, shown) }
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.target_app), style = MaterialTheme.typography.bodyLarge)
            val name = appLabel(context, shown)
            Text(
                if (overridable && packageName == null) defaultLabel(name) else name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.target_app)) },
            text = {
                Column {
                    TextButton(onClick = {
                        choosing = false
                        onChange(null)
                    }) { Text(defaultLabel(appLabel(context, defaultPackage))) }
                    TextButton(onClick = {
                        choosing = false
                        picking = true
                    }) { Text(stringResource(R.string.choose_app)) }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (picking) {
        AppPickerDialog(
            onPick = {
                picking = false
                onChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}
