package io.github.elaaps.respawnalarm

import android.text.format.DateFormat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        RepeatingStepButton(R.drawable.ic_remove, onMinus)
        Text(
            value,
            modifier = Modifier.width(STEPPER_VALUE_WIDTH),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RepeatingStepButton(R.drawable.ic_add, onPlus)
    }
}

@Composable
private fun RepeatingStepButton(icon: Int, onStep: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(REPEAT_AFTER)
        var interval = REPEAT_FIRST_INTERVAL
        while (true) {
            repeated = true
            step()
            delay(interval)
            interval = (interval * REPEAT_SPEEDUP).coerceAtLeast(REPEAT_FASTEST_INTERVAL)
        }
    }
    FilledTonalIconButton(
        onClick = {
            if (!repeated) step()
            repeated = false
        },
        shapes = IconButtonDefaults.shapes(),
        interactionSource = interactions,
    ) { Icon(painterResource(icon), contentDescription = null) }
}

@Composable
fun DayPicker(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit, modifier: Modifier = Modifier) {
    val letters = stringArrayResource(R.array.day_letters)
    val locale = textLocale(LocalContext.current)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        DayOfWeek.entries.forEachIndexed { index, day ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                DayOfWeek.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val name = day.getDisplayName(TextStyle.FULL, locale)
            ToggleButton(
                checked = day in days,
                onCheckedChange = { checked -> onChange(if (checked) days + day else days - day) },
                modifier = Modifier.weight(1f).semantics {
                    role = Role.Checkbox
                    contentDescription = name
                },
                shapes = shapes,
                contentPadding = PaddingValues(0.dp),
            ) { Text(letters[index], fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun AlarmTimePicker(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    var displayMode by rememberSaveable(stateSaver = DisplayModeSaver) { mutableStateOf(TimePickerDisplayMode.Picker) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.done)) } },
        title = { TimePickerDialogDefaults.Title(displayMode) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    displayMode = if (displayMode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
                },
                displayMode = displayMode,
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        if (displayMode == TimePickerDisplayMode.Picker) TimePicker(state) else TimeInput(state)
    }
}

private val DisplayModeSaver = Saver<TimePickerDisplayMode, Boolean>(
    save = { it == TimePickerDisplayMode.Input },
    restore = { input -> if (input) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker },
)

private val STEPPER_VALUE_WIDTH = 64.dp

private val REPEAT_AFTER = 400.milliseconds
private val REPEAT_FIRST_INTERVAL = 300.milliseconds
private val REPEAT_FASTEST_INTERVAL = 100.milliseconds
private const val REPEAT_SPEEDUP = 0.93
