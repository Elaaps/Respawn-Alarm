package io.github.elaaps.respawnalarm

import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onEdit: (Long) -> Unit, onAdd: (Long) -> Unit, onSettings: () -> Unit, onSetup: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AlarmStore(context) }
    var alarms by remember { mutableStateOf(store.alarms()) }
    var settings by remember { mutableStateOf(store.settings()) }
    var next by remember { mutableStateOf(AlarmScheduler.nextScheduled(context)) }
    var confirmingDeleteAll by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(missingPermissions(context)) }
    var now by remember { mutableStateOf(currentMinute()) }

    fun tick() {
        now = currentMinute()
        if (next?.at?.isAfter(now) == false) next = AlarmScheduler.nextScheduled(context)
    }

    LifecycleResumeEffect(Unit) {
        missing = missingPermissions(context)
        alarms = store.alarms()
        settings = store.settings()
        next = AlarmScheduler.nextScheduled(context)
        now = currentMinute()
        val clock = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = tick()
        }
        val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply { addAction(Intent.ACTION_TIME_CHANGED) }
        context.registerReceiver(clock, filter, Context.RECEIVER_NOT_EXPORTED)
        onPauseOrDispose { context.unregisterReceiver(clock) }
    }

    fun refresh() {
        next = AlarmScheduler.schedule(context)
        now = currentMinute()
        alarms = store.alarms()
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.alarm_deleted)
    val undoLabel = stringResource(R.string.undo)

    fun delete(alarm: Alarm) {
        store.delete(alarm.id)
        refresh()
        scope.launch {
            val result = snackbar.showSnackbar(deletedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                store.put(alarm)
                refresh()
            }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp)) {
                val (title, subtitle) = nextAlarmHeader(context, next, now, hasTarget = settings.targetPackage != null)
                ScreenHeader(title, subtitle) {
                    HeaderAction(
                        R.drawable.ic_delete_sweep,
                        stringResource(R.string.action_delete_all),
                        enabled = alarms.isNotEmpty(),
                    ) { confirmingDeleteAll = true }
                    HeaderAction(R.drawable.ic_settings, stringResource(R.string.settings_title), onClick = onSettings)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val alarm = Alarm(id = store.newId(), hour = NEW_ALARM_HOUR, minute = 0)
                store.put(alarm)
                refresh()
                onAdd(alarm.id)
            }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add_alarm))
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + LIST_BOTTOM_SPACE,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (missing.isNotEmpty()) {
                item(key = KEY_BANNER) { MissingPermissionsBanner(missing, onClick = onSetup, Modifier.animateItem()) }
            }
            items(alarms, key = { it.id }) { alarm ->
                SwipeToDelete(onDelete = { delete(alarm) }, modifier = Modifier.animateItem()) {
                    AlarmRow(
                        alarm = alarm,
                        canRing = settings.resolve(alarm).targetPackage != null,
                        onClick = { onEdit(alarm.id) },
                        onDelete = { delete(alarm) },
                        onToggle = { enabled ->
                            store.put(alarm.copy(enabled = enabled))
                            refresh()
                        },
                    )
                }
            }
        }
    }

    if (confirmingDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteAll = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(pluralStringResource(R.plurals.delete_all_text, alarms.size, alarms.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeleteAll = false
                    store.deleteAll()
                    refresh()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmingDeleteAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MissingPermissionsBanner(missing: List<Permission>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.banner_missing_title), style = MaterialTheme.typography.titleMedium)
            Text(missing.map { stringResource(it.title) }.joinToString(", "))
            Text(stringResource(R.string.banner_missing_action), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun missingPermissions(context: Context): List<Permission> =
    Permission.entries.filterNot { Permissions.isGranted(context, it) }

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    // Not rememberSwipeToDismissBoxState(), saved per list key: an alarm brought back by
    // undo keeps its key, and would come back already swiped away.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val fromStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CardDefaults.shape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        onDismiss = { onDelete() },
    ) { content() }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    canRing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        AlarmCard(
            alarm,
            canRing,
            onToggle,
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
                onLongClickLabel = stringResource(R.string.delete),
            ),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, canRing: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().clip(CardDefaults.shape).then(modifier)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatClockTime(context, LocalTime.of(alarm.hour, alarm.minute)),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
                val details = listOf(alarm.label, daysSummary(context, alarm.days)).filter { it.isNotBlank() }
                Text(details.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                alarm.skippedDate?.let { date ->
                    val pattern = DateTimeFormatter.ofPattern(stringResource(R.string.short_date_pattern))
                    Text(
                        stringResource(R.string.alarm_skipped, date.format(pattern)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Switch(checked = alarm.enabled, enabled = canRing, onCheckedChange = onToggle)
        }
    }
}

private fun nextAlarmHeader(
    context: Context,
    next: ScheduledAlarm?,
    now: ZonedDateTime,
    hasTarget: Boolean,
): Pair<String, String?> {
    if (!hasTarget) return context.getString(R.string.home_no_alarm) to context.getString(R.string.next_need_target)
    if (next == null) return context.getString(R.string.home_no_alarm) to null
    val until = Duration.between(now, next.at)
    val title = context.getString(R.string.home_next_alarm, until.toHours(), until.toMinutesPart())
    val date = next.at.format(DateTimeFormatter.ofPattern(context.getString(R.string.next_date_pattern)))
    return title to context.getString(R.string.home_next_alarm_at, date, formatClockTime(context, next.at.toLocalTime()))
}

private fun currentMinute(): ZonedDateTime = ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES)

fun pickTime(context: Context, initial: LocalTime, onPick: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPick(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        DateFormat.is24HourFormat(context),
    ).show()
}

private const val NEW_ALARM_HOUR = 7

private const val KEY_BANNER = "banner"
private val LIST_BOTTOM_SPACE = 88.dp
