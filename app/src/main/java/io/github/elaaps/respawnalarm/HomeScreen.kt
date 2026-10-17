package io.github.elaaps.respawnalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onEdit: (Long) -> Unit, onSettings: () -> Unit, onSetup: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AlarmStore(context) }
    var alarms by remember { mutableStateOf(store.alarms()) }
    var settings by remember { mutableStateOf(store.settings()) }
    var next by remember { mutableStateOf(AlarmScheduler.nextScheduled(context)) }
    var confirmingDeleteAll by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
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
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { confirmingDeleteAll = true }, enabled = alarms.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = stringResource(R.string.action_delete_all))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            MediumFloatingActionButton(onClick = { pickingTime = true }) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.action_add_alarm),
                    modifier = Modifier.size(FAB_ICON_SIZE),
                )
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (missing.isNotEmpty()) {
                item(key = KEY_BANNER) { MissingPermissionsBanner(missing, onClick = onSetup, Modifier.animateItem()) }
            }
            item(key = KEY_HERO) {
                NextAlarmHero(next, now, hasTarget = settings.targetPackage != null, hasAlarms = alarms.isNotEmpty(), Modifier.animateItem())
            }
            items(alarms.sortedBy { LocalTime.of(it.hour, it.minute) }, key = { it.id }) { alarm ->
                SwipeToDelete(onDelete = { delete(alarm) }, modifier = Modifier.animateItem()) {
                    AlarmRow(
                        alarm = alarm,
                        canRing = settings.resolve(alarm).targetPackage != null,
                        skippedDate = pendingSkip(alarm, now),
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

    if (pickingTime) {
        AlarmTimePicker(
            LocalTime.of(NEW_ALARM_HOUR, 0),
            onPick = { time ->
                pickingTime = false
                val alarm = Alarm(id = store.newId(), hour = time.hour, minute = time.minute)
                store.put(alarm)
                refresh()
                onEdit(alarm.id)
            },
            onDismiss = { pickingTime = false },
        )
    }

    if (confirmingDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmingDeleteAll = false },
            icon = { Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = null) },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NextAlarmHero(next: ScheduledAlarm?, now: ZonedDateTime, hasTarget: Boolean, hasAlarms: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scheduled = next?.takeIf { hasTarget }
    Column(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (scheduled == null) {
            Box(Modifier.size(HERO_SIZE).background(colors.surfaceContainerHigh, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_alarm_add), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(HERO_ICON_SIZE))
            }
            Text(stringResource(R.string.home_no_alarm), style = MaterialTheme.typography.titleMedium)
            val hint = when {
                !hasTarget -> stringResource(R.string.next_need_target)
                !hasAlarms -> stringResource(R.string.home_empty)
                else -> null
            }
            hint?.let { Text(it, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp)) }
            return@Column
        }
        val until = Duration.between(now, scheduled.at)
        val sentence = if (until.toDays() > 0) {
            stringResource(R.string.home_next_alarm_days, until.toDays(), until.toHoursPart())
        } else {
            stringResource(R.string.home_next_alarm, until.toHours(), until.toMinutesPart())
        }
        val countdown = if (until.toDays() > 0) {
            stringResource(R.string.countdown_days, until.toDays(), until.toHoursPart())
        } else {
            stringResource(R.string.countdown_hours, until.toHours(), until.toMinutesPart())
        }
        val spin = rememberInfiniteTransition(label = "hero spin")
        val angle by spin.animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(HERO_TURN_MS, easing = LinearEasing), RepeatMode.Restart), label = "angle")
        Box(Modifier.size(HERO_SIZE).clearAndSetSemantics { contentDescription = sentence }, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = angle }
                    .background(colors.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            )
            Column(Modifier.widthIn(max = HERO_TEXT_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(countdown, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
                Text(
                    stringResource(R.string.countdown_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val date = scheduled.at.format(dateFormatter(R.string.next_date_pattern))
        Text(
            stringResource(R.string.home_next_alarm_at, date, formatClockTime(context, scheduled.at.toLocalTime())),
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissingPermissionsBanner(missing: List<Permission>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.banner_missing_title), style = MaterialTheme.typography.titleSmall)
                Text(missing.map { stringResource(it.title) }.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.banner_missing_action), style = MaterialTheme.typography.labelLarge)
            }
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
                    .clip(RoundedCornerShape(CARD_CORNER))
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
    skippedDate: LocalDate?,
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
            skippedDate,
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
private fun AlarmCard(
    alarm: Alarm,
    canRing: Boolean,
    skippedDate: LocalDate?,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CARD_CORNER)).then(modifier),
        shape = RoundedCornerShape(CARD_CORNER),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.enabled) colors.secondaryContainer else colors.surfaceContainer,
            contentColor = if (alarm.enabled) colors.onSecondaryContainer else colors.onSurfaceVariant,
        ),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatClockTime(context, LocalTime.of(alarm.hour, alarm.minute)),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (alarm.enabled) colors.onSecondaryContainer else colors.outline,
                )
                if (alarm.label.isNotBlank()) Text(alarm.label, style = MaterialTheme.typography.bodyMedium)
                if (!alarm.isOneShot) DayChips(alarm.days)
                skippedDate?.let { date ->
                    val pattern = dateFormatter(R.string.short_date_pattern)
                    Text(
                        stringResource(R.string.alarm_skipped, date.format(pattern)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.tertiary,
                    )
                }
            }
            Switch(checked = alarm.enabled, enabled = canRing, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DayChips(days: Set<DayOfWeek>) {
    val context = LocalContext.current
    val letters = stringArrayResource(R.array.day_letters)
    val summary = daysSummary(context, days)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DayOfWeek.entries.forEachIndexed { index, day ->
            val selected = day in days
            Box(
                Modifier
                    .size(DAY_CHIP_SIZE)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letters[index],
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun currentMinute(): ZonedDateTime = ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES)

private const val NEW_ALARM_HOUR = 7

private const val KEY_BANNER = "banner"
private const val KEY_HERO = "hero"
private val LIST_BOTTOM_SPACE = 112.dp
private val CARD_CORNER = 28.dp
private val HERO_SIZE = 184.dp
private val HERO_TEXT_WIDTH = 124.dp
private val HERO_ICON_SIZE = 64.dp
private val FAB_ICON_SIZE = 28.dp
private val DAY_CHIP_SIZE = 20.dp
private const val FULL_TURN = 360f
private const val HERO_TURN_MS = 40_000
