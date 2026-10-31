package io.github.elaaps.respawnalarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSliderState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlin.math.abs
import kotlin.math.roundToInt

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
    enabled: Boolean = true,
    onOpen: () -> Unit,
) {
    SegmentedListItem(
        onClick = onOpen,
        shapes = shapes,
        enabled = enabled,
        leadingContent = icon?.let { { ShapedIcon(it) } },
        supportingContent = { Text(if (checked) summary else stringResource(R.string.off)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerticalDivider(Modifier.height(DETAILS_DIVIDER_HEIGHT).padding(end = 12.dp))
                Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
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
fun DurationRow(
    label: String,
    shapes: ListItemShapes,
    icon: RowIcon,
    choices: List<Int>,
    selected: Int,
    describe: @Composable (Int) -> String,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        enabled = enabled,
        leadingContent = { ShapedIcon(icon) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(describe(selected))
                NumberChoices(choices, selected, describe, enabled, onSelect)
            }
        },
    ) { Text(label) }
}

@Composable
fun LabeledNumberChoices(label: String, choices: List<Int>, selected: Int, describe: @Composable (Int) -> String, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text(describe(selected), style = MaterialTheme.typography.titleMedium)
        }
        NumberChoices(choices, selected, describe, enabled = true, onSelect)
    }
}

@Composable
private fun NumberChoices(
    choices: List<Int>,
    selected: Int,
    describe: @Composable (Int) -> String,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val shown = if (selected in choices) choices else (choices + selected).sorted()
    ConnectedChoices(shown.map { Choice(it, it.toString(), describe(it)) }, selected, enabled, onSelect)
}

@Composable
private fun <T> ConnectedChoices(choices: List<Choice<T>>, selected: T, enabled: Boolean, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        choices.forEachIndexed { index, choice ->
            ToggleButton(
                checked = choice.value == selected,
                onCheckedChange = { onSelect(choice.value) },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = choice.description
                    },
                enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = CONNECTED_CHOICE_PADDING,
            ) { Text(choice.label, maxLines = 1) }
        }
    }
}

private val CONNECTED_CHOICE_PADDING = PaddingValues(horizontal = 4.dp)

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

data class Choice<T>(val value: T, val label: String, val description: String = label)

@Composable
fun <T> ConnectedChoiceRow(
    label: String,
    choices: List<Choice<T>>,
    selected: T,
    shapes: ListItemShapes,
    icon: RowIcon,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        enabled = enabled,
        leadingContent = { ShapedIcon(icon) },
        supportingContent = {
            Box(Modifier.padding(top = 8.dp)) { ConnectedChoices(choices, selected, enabled, onSelect) }
        },
    ) { Text(label) }
}

private fun <T> selectedLabel(choices: List<Choice<T>>, selected: T): String =
    choices.firstOrNull { it.value == selected }?.label.orEmpty()

@Composable
private fun <T> ChoiceSheet(label: String, choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    SettingSheet(label, onDismiss) { close ->
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            choices.forEachIndexed { index, choice ->
                SegmentedListItem(
                    selected = choice.value == selected,
                    onClick = {
                        onSelect(choice.value)
                        close()
                    },
                    shapes = ListItemDefaults.segmentedShapes(index, choices.size),
                    leadingContent = { RadioButton(selected = choice.value == selected, onClick = null) },
                ) { Text(choice.label) }
            }
        }
    }
}

@Composable
fun defaultLabel(value: String): String = stringResource(R.string.use_default, value)

@Composable
fun markDefault(label: String, isDefault: Boolean): String = if (isDefault) defaultLabel(label) else label

@Composable
private fun volumeLabel(volume: AlarmVolume): String = when (volume) {
    AlarmVolume.Phone -> stringResource(R.string.volume_phone)
    is AlarmVolume.Fixed -> stringResource(R.string.percent_value, volume.percent)
}

@Composable
fun VolumeRow(volume: AlarmVolume?, defaultVolume: AlarmVolume?, enabled: Boolean, shapes: ListItemShapes, onChange: (AlarmVolume?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val shown = volume ?: defaultVolume ?: GlobalSettings().volume
    val label = stringResource(R.string.volume)
    ValueRow(
        label,
        markDefault(volumeLabel(shown), isDefault = volume == null && defaultVolume != null),
        shapes,
        RowIcon(R.drawable.ic_volume_up, IconStyle.RINGING),
        enabled,
    ) { editing = true }
    if (editing) {
        SettingSheet(label, onDismiss = { editing = false }) { VolumeEditor(shown) { onChange(ownValue(it, defaultVolume)) } }
    }
}

@Composable
private fun VolumeEditor(shown: AlarmVolume, onChange: (AlarmVolume) -> Unit) {
    val stops = AlarmVolume.Fixed.PERCENT_STOPS
    val usesPhone = shown == AlarmVolume.Phone
    val shownPercent = (shown as? AlarmVolume.Fixed)?.percent ?: AlarmVolume.Fixed.MAX_PERCENT
    val slider = rememberSliderState(
        value = nearestStopIndex(stops, shownPercent).toFloat(),
        steps = stops.size - 2,
        trackRange = 0f..stops.lastIndex.toFloat(),
    )
    val pickedPercent = stops[slider.value.roundToInt()]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowGroup {
            row { shapes ->
                SwitchRow(
                    stringResource(R.string.volume_use_phone),
                    usesPhone,
                    { useIt -> onChange(if (useIt) AlarmVolume.Phone else AlarmVolume.Fixed(pickedPercent)) },
                    shapes,
                )
            }
        }
        Row {
            Text(stringResource(R.string.volume_custom), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.percent_value, pickedPercent), style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            state = slider,
            enabled = !usesPhone,
            onValueChange = { slider.value = it },
            onValueChangeFinished = { onChange(AlarmVolume.Fixed(stops[slider.value.roundToInt()])) },
        )
    }
}

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
        ChoiceSheet(label, choices, selected, onDismiss = { choosing = false }, onSelect = { choice ->
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
    val stops = GlobalSettings.PLAY_TIME_STOPS_SEC
    val shownSec = durationSec ?: defaultSec ?: GlobalSettings().durationSec
    val shownIndex = nearestStopIndex(stops, shownSec)
    val slider = rememberSliderState(value = shownIndex.toFloat(), steps = stops.size - 2, trackRange = 0f..stops.lastIndex.toFloat())
    val pickedIndex = slider.value.roundToInt()
    val pickedSec = if (pickedIndex == shownIndex) shownSec else stops[pickedIndex]
    val text = formatDuration(pickedSec * MILLIS_PER_SECOND)
    SegmentedListItem(
        shapes = shapes,
        leadingContent = { ShapedIcon(RowIcon(R.drawable.ic_timer, IconStyle.STOP)) },
        supportingContent = {
            Column {
                Text(markDefault(text, isDefault = durationSec == null && defaultSec != null && pickedSec == shownSec))
                Slider(
                    state = slider,
                    onValueChange = { slider.value = it },
                    onValueChangeFinished = {
                        val index = slider.value.roundToInt()
                        if (index != shownIndex) onChange(ownValue(stops[index], defaultSec))
                    },
                )
            }
        },
    ) { Text(stringResource(R.string.play_time)) }
}

fun nearestStopIndex(stops: List<Int>, value: Int): Int = stops.indices.minBy { abs(stops[it] - value) }

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
        SettingSheet(stringResource(R.string.snooze), onDismiss = { editing = false }) {
            RowGroup {
                row { SwitchRow(stringResource(R.string.snooze_use), shown.enabled, toggle, it) }
            }
            SnoozeChoices(shown, change)
        }
    }
}

@Composable
private fun SnoozeChoices(shown: Snooze, onChange: (Snooze) -> Unit) {
    LabeledNumberChoices(
        stringResource(R.string.snooze_count),
        Snooze.COUNT_CHOICES,
        shown.maxCount,
        describe = { it.toString() },
    ) { onChange(shown.copy(maxCount = it)) }
    LabeledNumberChoices(
        stringResource(R.string.snooze_minutes),
        Snooze.MINUTES_CHOICES,
        shown.minutes,
        describe = { stringResource(R.string.minutes_value, it) },
    ) { onChange(shown.copy(minutes = it)) }
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
