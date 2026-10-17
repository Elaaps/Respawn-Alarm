package io.github.elaaps.respawnalarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

class RowGroupScope {
    val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun row(content: @Composable (ListItemShapes) -> Unit) {
        rows += content
    }
}

@Composable
fun RowGroup(title: String? = null, build: RowGroupScope.() -> Unit) {
    val rows = RowGroupScope().apply(build).rows
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
            )
        }
        rows.forEachIndexed { index, row ->
            key(index) { row(ListItemDefaults.segmentedShapes(index, rows.size)) }
        }
    }
}

@Composable
fun ValueRow(
    label: String,
    value: String?,
    shapes: ListItemShapes,
    icon: RowIcon? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        enabled = enabled,
        leadingContent = icon?.let { { ShapedIcon(it) } },
        supportingContent = value?.let { { Text(it) } },
    ) { Text(label) }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    icon: RowIcon? = null,
    description: String? = null,
    enabled: Boolean = true,
    greyedOut: Boolean = !enabled,
) {
    val leading: (@Composable () -> Unit)? = icon?.let { { ShapedIcon(it) } }
    val supporting: (@Composable () -> Unit)? = description?.let { { Text(it) } }
    val trailing = @Composable { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    if (enabled) {
        SegmentedListItem(
            checked = checked,
            onCheckedChange = onChange,
            shapes = shapes,
            colors = steadyColors(),
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
        ) { Text(label) }
    } else {
        SegmentedListItem(
            shapes = shapes,
            enabled = !greyedOut,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
        ) { Text(label) }
    }
}

@Composable
fun SwitchWithDetailsRow(
    label: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    icon: RowIcon? = null,
    onOpen: () -> Unit,
) {
    SegmentedListItem(
        onClick = onOpen,
        shapes = shapes,
        leadingContent = icon?.let { { ShapedIcon(it) } },
        supportingContent = { Text(if (checked) summary else stringResource(R.string.off)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerticalDivider(Modifier.height(DETAILS_DIVIDER_HEIGHT).padding(end = 12.dp))
                Switch(checked = checked, onCheckedChange = onChange)
            }
        },
    ) { Text(label) }
}

private val DETAILS_DIVIDER_HEIGHT = 32.dp

@Composable
private fun steadyColors(): ListItemColors {
    val base = ListItemDefaults.segmentedColors()
    return ListItemDefaults.segmentedColors(
        selectedContainerColor = base.containerColor,
        selectedContentColor = base.contentColor,
        selectedLeadingContentColor = base.leadingContentColor,
        selectedTrailingContentColor = base.trailingContentColor,
        selectedOverlineContentColor = base.overlineContentColor,
        selectedSupportingContentColor = base.supportingContentColor,
    )
}

@Composable
fun StepperRow(label: String, value: String, shapes: ListItemShapes, icon: RowIcon, onMinus: () -> Unit, onPlus: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    ValueRow(label, value, shapes, icon) { editing = true }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = { Stepper(label = "", value = value, onMinus = onMinus, onPlus = onPlus) },
            confirmButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
fun VibrateRow(alarm: ResolvedAlarm, shapes: ListItemShapes, onChange: (Boolean) -> Unit) {
    SwitchRow(
        stringResource(R.string.vibrate),
        alarm.vibrate,
        onChange,
        shapes,
        icon = RowIcon(R.drawable.ic_vibration, IconStyle.RINGING),
        description = if (alarm.soundEnabled) null else stringResource(R.string.vibrate_forced),
        enabled = alarm.soundEnabled,
        greyedOut = false,
    )
}

data class Choice<T>(val value: T, val label: String)

@Composable
fun <T> ChoiceRow(
    label: String,
    choices: List<Choice<T>>,
    selected: T,
    shapes: ListItemShapes,
    icon: RowIcon? = null,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    ValueRow(label, selectedLabel(choices, selected), shapes, icon, enabled) { choosing = true }
    if (choosing) ChoiceDialog(label, choices, selected, onSelect) { choosing = false }
}

private fun <T> selectedLabel(choices: List<Choice<T>>, selected: T): String =
    choices.firstOrNull { it.value == selected }?.label.orEmpty()

@Composable
private fun <T> ChoiceDialog(label: String, choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun defaultLabel(value: String): String = stringResource(R.string.use_default, value)

@Composable
fun markDefault(label: String, isDefault: Boolean): String = if (isDefault) defaultLabel(label) else label

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
fun ToneRow(
    tone: Tone?,
    defaultTone: Tone?,
    soundEnabled: Boolean,
    shapes: ListItemShapes,
    onSoundChange: (Boolean) -> Unit,
    onChange: (Tone?) -> Unit,
) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(PickAlarmTone()) { uri -> uri?.let { onChange(ownValue(toneOf(it), defaultTone)) } }
    val current = tone ?: defaultTone ?: Tone.SystemDefault
    val customDefault = defaultTone as? Tone.Custom
    val ownCustom = (current as? Tone.Custom)?.takeIf { it != customDefault }
    val choices = buildList {
        val system = toneLabel(context, Tone.SystemDefault)
        add(Choice(ToneChoice.System, markDefault(system, isDefault = defaultTone == Tone.SystemDefault)))
        if (customDefault != null) add(Choice(ToneChoice.Default, defaultLabel(toneLabel(context, customDefault))))
        if (ownCustom != null) add(Choice(ToneChoice.Current, toneLabel(context, ownCustom)))
        add(Choice(ToneChoice.Pick, stringResource(R.string.tone_pick)))
    }
    val selected = when (current) {
        Tone.SystemDefault -> ToneChoice.System
        customDefault -> ToneChoice.Default
        else -> ToneChoice.Current
    }
    val label = stringResource(R.string.tone)
    SwitchWithDetailsRow(
        label,
        selectedLabel(choices, selected),
        soundEnabled,
        onSoundChange,
        shapes,
        RowIcon(R.drawable.ic_music_note, IconStyle.RINGING),
    ) { choosing = true }
    if (choosing) {
        ChoiceDialog(label, choices, selected, onDismiss = { choosing = false }, onSelect = { choice ->
            when (choice) {
                ToneChoice.Default -> onChange(null)
                ToneChoice.System -> onChange(ownValue(Tone.SystemDefault, defaultTone))
                ToneChoice.Current -> Unit
                ToneChoice.Pick -> picker.launch((current as? Tone.Custom)?.uri?.toUri())
            }
        })
    }
}

@Composable
fun PlayTimeRow(durationSec: Int?, defaultSec: Int?, shapes: ListItemShapes, onChange: (Int?) -> Unit) {
    val shownSec = durationSec ?: defaultSec ?: GlobalSettings().durationSec
    val text = formatDuration(shownSec * MILLIS_PER_SECOND)
    StepperRow(
        stringResource(R.string.play_time),
        markDefault(text, isDefault = durationSec == null && defaultSec != null),
        shapes,
        RowIcon(R.drawable.ic_timer, IconStyle.STOP),
        onMinus = { onChange(ownValue(stepPlayTime(shownSec, -GlobalSettings.DURATION_STEP_SEC), defaultSec)) },
        onPlus = { onChange(ownValue(stepPlayTime(shownSec, GlobalSettings.DURATION_STEP_SEC), defaultSec)) },
    )
}

fun stepPlayTime(durationSec: Int, deltaSec: Int): Int = (durationSec + deltaSec).coerceIn(GlobalSettings.DURATION_RANGE_SEC)

private const val MILLIS_PER_SECOND = 1_000L

@Composable
fun SnoozeRow(snooze: Snooze?, defaultSnooze: Snooze?, shapes: ListItemShapes, onChange: (Snooze?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val shown = snooze ?: defaultSnooze ?: Snooze.OFF
    val length = stringResource(R.string.snooze_value, shown.maxCount, shown.minutes)
    val summary = markDefault(length, isDefault = snooze == null && defaultSnooze != null)
    val change = { updated: Snooze -> onChange(ownValue(updated, defaultSnooze)) }
    val toggle = { enabled: Boolean -> change(shown.copy(enabled = enabled)) }
    SwitchWithDetailsRow(
        stringResource(R.string.snooze),
        summary,
        shown.enabled,
        toggle,
        shapes,
        RowIcon(R.drawable.ic_snooze, IconStyle.RINGING),
    ) { editing = true }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.snooze)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RowGroup {
                        row { SwitchRow(stringResource(R.string.snooze_use), shown.enabled, toggle, it) }
                    }
                    SnoozeSteppers(shown, change)
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
fun TargetAppRow(packageName: String?, defaultPackage: String?, shapes: ListItemShapes, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val shown = packageName ?: defaultPackage
    SegmentedListItem(
        onClick = { picking = true },
        shapes = shapes,
        leadingContent = { ShapedIcon(RowIcon(R.drawable.ic_sports_esports, IconStyle.STOP)) },
        supportingContent = {
            Text(
                markDefault(appLabel(context, shown), isDefault = packageName == null && defaultPackage != null),
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) { Text(stringResource(R.string.target_app)) }
    if (picking) {
        AppPickerDialog(
            selectedPackage = shown,
            defaultPackage = defaultPackage,
            onPick = {
                picking = false
                onChange(ownValue(it, defaultPackage))
            },
            onDismiss = { picking = false },
        )
    }
}
