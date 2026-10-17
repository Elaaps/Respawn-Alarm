package io.github.elaaps.respawnalarm

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.elaaps.respawnalarm.GlobalSettings.Companion.INACTIVITY_RANGE_SEC
import io.github.elaaps.respawnalarm.GlobalSettings.Companion.INACTIVITY_STEP_SEC
import io.github.elaaps.respawnalarm.GlobalSettings.Companion.LOADING_GRACE_RANGE_SEC
import io.github.elaaps.respawnalarm.GlobalSettings.Companion.LOADING_GRACE_STEP_SEC

@Composable
fun SettingsScreen(onBack: () -> Unit, onSetup: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AlarmStore(context) }
    var settings by remember { mutableStateOf(store.settings()) }
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeCount++
        settings = store.settings()
        onPauseOrDispose { }
    }

    fun update(newSettings: GlobalSettings) {
        settings = newSettings
        store.saveSettings(newSettings)
        AlarmScheduler.schedule(context)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StopGroup(settings, ::update)
            RingingGroup(settings, ::update)
            OverlayGroup(settings, ::update, onResetPosition = store::resetOverlayPosition)
            VictoryGroup(settings, ::update)
            ProtectionGroup(resumeCount)
            PermissionsGroup(resumeCount, onSetup)
            TestGroup(settings)
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null) }
}

@Composable
private fun StopGroup(settings: GlobalSettings, onChange: (GlobalSettings) -> Unit) {
    RowGroup(stringResource(R.string.section_stop)) {
        row { shapes ->
            TargetAppRow(settings.targetPackage, defaultPackage = null, shapes) { onChange(settings.copy(targetPackage = it)) }
        }
        row { shapes ->
            PlayTimeRow(settings.durationSec, defaultSec = null, shapes) { sec -> sec?.let { onChange(settings.copy(durationSec = it)) } }
        }
        row { shapes ->
            StepperRow(
                stringResource(R.string.max_inactivity),
                stringResource(R.string.seconds_value, settings.inactivitySec),
                shapes,
                RowIcon(R.drawable.ic_touch_app, IconStyle.STOP),
                onMinus = { onChange(settings.copy(inactivitySec = stepInactivity(settings, -INACTIVITY_STEP_SEC))) },
                onPlus = { onChange(settings.copy(inactivitySec = stepInactivity(settings, INACTIVITY_STEP_SEC))) },
            )
        }
        row { shapes -> LoadingGraceRow(settings, shapes, onChange) }
    }
}

@Composable
private fun RingingGroup(settings: GlobalSettings, onChange: (GlobalSettings) -> Unit) {
    val resolved = settings.resolveDefaults()
    RowGroup(stringResource(R.string.section_sound)) {
        row { shapes ->
            ToneRow(
                settings.tone,
                defaultTone = null,
                soundEnabled = settings.soundEnabled,
                shapes = shapes,
                onSoundChange = { onChange(settings.copy(soundEnabled = it)) },
            ) { tone -> tone?.let { onChange(settings.copy(tone = it)) } }
        }
        row { shapes ->
            ChoiceRow(
                stringResource(R.string.volume),
                VolumeMode.entries.map { Choice(it, volumeLabel(it)) },
                settings.volume,
                shapes,
                RowIcon(R.drawable.ic_volume_up, IconStyle.RINGING),
                enabled = settings.soundEnabled,
            ) { onChange(settings.copy(volume = it)) }
        }
        row { shapes ->
            SwitchRow(
                stringResource(R.string.crescendo),
                settings.crescendo,
                { onChange(settings.copy(crescendo = it)) },
                shapes,
                RowIcon(R.drawable.ic_trending_up, IconStyle.RINGING),
                description = stringResource(R.string.crescendo_desc),
                enabled = settings.soundEnabled,
            )
        }
        row { shapes -> VibrateRow(resolved, shapes) { onChange(settings.copy(vibrate = it)) } }
        row { shapes ->
            SnoozeRow(settings.snooze, defaultSnooze = null, shapes) { snooze -> snooze?.let { onChange(settings.copy(snooze = it)) } }
        }
    }
}

@Composable
private fun OverlayGroup(settings: GlobalSettings, onChange: (GlobalSettings) -> Unit, onResetPosition: () -> Unit) {
    RowGroup(stringResource(R.string.section_overlay)) {
        row { shapes ->
            SwitchRow(
                stringResource(R.string.overlay_enabled),
                settings.overlayEnabled,
                { onChange(settings.copy(overlayEnabled = it)) },
                shapes,
                RowIcon(R.drawable.ic_picture_in_picture, IconStyle.OTHER),
            )
        }
        row { shapes ->
            ChoiceRow(
                stringResource(R.string.overlay_style),
                listOf(
                    Choice(OverlayStyle.HEALTH_BAR, stringResource(R.string.overlay_style_health_bar)),
                    Choice(OverlayStyle.TIMER, stringResource(R.string.overlay_style_timer)),
                ),
                settings.overlayStyle,
                shapes,
                RowIcon(R.drawable.ic_palette, IconStyle.OTHER),
                enabled = settings.overlayEnabled,
            ) { onChange(settings.copy(overlayStyle = it)) }
        }
        row { shapes ->
            ValueRow(
                stringResource(R.string.overlay_reset),
                value = null,
                shapes,
                RowIcon(R.drawable.ic_open_with, IconStyle.OTHER),
                enabled = settings.overlayEnabled,
                onClick = onResetPosition,
            )
        }
    }
}

@Composable
private fun VictoryGroup(settings: GlobalSettings, onChange: (GlobalSettings) -> Unit) {
    RowGroup(stringResource(R.string.section_victory)) {
        row { shapes ->
            SwitchRow(
                stringResource(R.string.victory_on_leave),
                settings.victoryOnLeave,
                { onChange(settings.copy(victoryOnLeave = it)) },
                shapes,
                RowIcon(R.drawable.ic_emoji_events, IconStyle.OTHER),
                description = stringResource(R.string.victory_on_leave_desc),
            )
        }
    }
}

@Composable
private fun LoadingGraceRow(settings: GlobalSettings, shapes: ListItemShapes, onChange: (GlobalSettings) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val length = stringResource(R.string.seconds_value, settings.loadingGraceSec)
    val toggle = { enabled: Boolean -> onChange(settings.copy(loadingGraceEnabled = enabled)) }
    SwitchWithDetailsRow(
        stringResource(R.string.loading_grace),
        length,
        settings.loadingGraceEnabled,
        toggle,
        shapes,
        RowIcon(R.drawable.ic_hourglass_top, IconStyle.STOP),
    ) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.loading_grace)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RowGroup {
                        row { rowShapes -> SwitchRow(stringResource(R.string.loading_grace_use), settings.loadingGraceEnabled, toggle, rowShapes) }
                    }
                    Stepper(
                        label = stringResource(R.string.loading_grace_length),
                        value = length,
                        onMinus = { onChange(settings.copy(loadingGraceSec = stepLoadingGrace(settings, -LOADING_GRACE_STEP_SEC))) },
                        onPlus = { onChange(settings.copy(loadingGraceSec = stepLoadingGrace(settings, LOADING_GRACE_STEP_SEC))) },
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.done)) } },
        )
    }
}

private fun stepLoadingGrace(settings: GlobalSettings, deltaSec: Int): Int =
    (settings.loadingGraceSec + deltaSec).coerceIn(LOADING_GRACE_RANGE_SEC)

private fun stepInactivity(settings: GlobalSettings, deltaSec: Int): Int =
    (settings.inactivitySec + deltaSec).coerceIn(INACTIVITY_RANGE_SEC)

@Composable
private fun ProtectionGroup(resumeCount: Int) {
    val context = LocalContext.current
    val state by SessionService.state.collectAsStateWithLifecycle()
    var active by remember(resumeCount) { mutableStateOf(ForceStopGuard.isActive(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        active = ForceStopGuard.isActive(context)
    }

    RowGroup(stringResource(R.string.section_protection)) {
        row { shapes ->
            SwitchRow(
                stringResource(R.string.force_stop_guard),
                active,
                { enable ->
                    if (enable) {
                        launcher.launch(ForceStopGuard.enableIntent(context))
                    } else {
                        ForceStopGuard.disable(context)
                        // The admin stays active until the system has delivered DEVICE_ADMIN_DISABLED.
                        active = false
                    }
                },
                shapes,
                RowIcon(R.drawable.ic_shield_lock, IconStyle.OTHER),
                description = stringResource(R.string.force_stop_guard_desc),
                enabled = state?.mode != SessionService.Mode.ALARM,
            )
        }
    }
}

@Composable
private fun PermissionsGroup(resumeCount: Int, onSetup: () -> Unit) {
    val context = LocalContext.current
    var requestCount by remember { mutableIntStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestCount++
    }
    val missing = remember(resumeCount, requestCount) {
        Permission.entries.filterNot { Permissions.isGranted(context, it) }
    }

    RowGroup(stringResource(R.string.section_permissions)) {
        if (missing.isEmpty()) {
            row { shapes ->
                SegmentedListItem(
                    shapes = shapes,
                    leadingContent = { ShapedIcon(RowIcon(R.drawable.ic_verified, IconStyle.OTHER)) },
                ) { Text(stringResource(R.string.all_granted)) }
            }
        }
        missing.forEach { permission ->
            row { shapes ->
                ValueRow(
                    stringResource(permission.title),
                    stringResource(permission.description),
                    shapes,
                    RowIcon(permission.icon, IconStyle.OTHER),
                ) {
                    if (permission == Permission.NOTIFICATIONS) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Permissions.openSettings(context, permission)
                    }
                }
            }
        }
        row { shapes ->
            ValueRow(
                stringResource(R.string.settings_run_setup),
                value = null,
                shapes,
                RowIcon(R.drawable.ic_restart_alt, IconStyle.OTHER),
                onClick = onSetup,
            )
        }
    }
    Text(
        stringResource(R.string.permissions_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun TestGroup(settings: GlobalSettings) {
    val context = LocalContext.current
    val state by SessionService.state.collectAsStateWithLifecycle()
    val mode = state?.mode
    var confirming by remember { mutableStateOf(false) }

    RowGroup(stringResource(R.string.section_test)) {
        row { shapes ->
            ValueRow(
                stringResource(R.string.test_diag_button),
                stringResource(if (mode == SessionService.Mode.DIAGNOSTIC) R.string.test_diag_running else R.string.test_diag_hint),
                shapes,
                RowIcon(R.drawable.ic_monitoring, IconStyle.OTHER),
                enabled = mode == null,
            ) { SessionService.start(context, SessionService.Mode.DIAGNOSTIC) }
        }
        row { shapes ->
            ValueRow(
                stringResource(R.string.test_ring_button),
                if (mode == SessionService.Mode.ALARM) stringResource(R.string.test_ringing) else null,
                shapes,
                RowIcon(R.drawable.ic_alarm_on, IconStyle.OTHER),
                enabled = mode == null && settings.targetPackage != null,
            ) { confirming = true }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(painterResource(R.drawable.ic_alarm_on), contentDescription = null) },
            title = { Text(stringResource(R.string.ring_now_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ring_now_text,
                        formatDuration(settings.resolveDefaults().durationMs),
                        appLabel(context, settings.targetPackage),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    SessionService.start(context, SessionService.Mode.ALARM)
                }) { Text(stringResource(R.string.ring)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
