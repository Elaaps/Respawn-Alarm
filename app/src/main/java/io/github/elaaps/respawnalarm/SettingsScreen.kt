package io.github.elaaps.respawnalarm

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.elaaps.respawnalarm.GlobalSettings.Companion.DURATION_STEP_SEC
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(stringResource(R.string.settings_title), onBack = onBack)
            SessionCard(settings, ::update)
            SoundCard(settings, ::update)
            OverlayCard(settings, ::update, onResetPosition = store::resetOverlayPosition)
            ProtectionCard(resumeCount)
            PermissionsCard(resumeCount, onSetup)
            TestCard(settings)
        }
    }
}

@Composable
private fun SessionCard(settings: GlobalSettings, onChange: (GlobalSettings) -> Unit) {
    SectionCard(stringResource(R.string.section_stop)) {
        Stepper(
            label = stringResource(R.string.play_time),
            value = formatDuration(settings.resolveDefaults().durationMs),
            onMinus = { onChange(settings.copy(durationSec = stepPlayTime(settings.durationSec, -DURATION_STEP_SEC))) },
            onPlus = { onChange(settings.copy(durationSec = stepPlayTime(settings.durationSec, DURATION_STEP_SEC))) },
        )
        Stepper(
            label = stringResource(R.string.max_inactivity),
            value = stringResource(R.string.seconds_value, settings.inactivitySec),
            onMinus = { onChange(settings.copy(inactivitySec = stepInactivity(settings, -INACTIVITY_STEP_SEC))) },
            onPlus = { onChange(settings.copy(inactivitySec = stepInactivity(settings, INACTIVITY_STEP_SEC))) },
        )
        LoadingGraceRow(settings, onChange)
        TargetAppRow(settings.targetPackage, defaultPackage = null, overridable = false) {
            onChange(settings.copy(targetPackage = it))
        }
        SnoozeRow(settings.snooze, defaultSnooze = null) { snooze -> snooze?.let { onChange(settings.copy(snooze = it)) } }
    }
}

@Composable
private fun OverlayCard(settings: GlobalSettings, onChange: (GlobalSettings) -> Unit, onResetPosition: () -> Unit) {
    SectionCard(stringResource(R.string.section_overlay)) {
        SwitchRow(
            stringResource(R.string.overlay_enabled),
            settings.overlayEnabled,
            { onChange(settings.copy(overlayEnabled = it)) },
        )
        TextButton(onClick = onResetPosition, enabled = settings.overlayEnabled) {
            Text(stringResource(R.string.overlay_reset))
        }
    }
}

@Composable
private fun SoundCard(settings: GlobalSettings, onChange: (GlobalSettings) -> Unit) {
    SectionCard(stringResource(R.string.section_sound)) {
        ToneRow(settings.tone, defaultTone = null) { tone -> tone?.let { onChange(settings.copy(tone = it)) } }
        SwitchRow(stringResource(R.string.vibrate), settings.vibrate, { onChange(settings.copy(vibrate = it)) })
        ChoiceRow(
            stringResource(R.string.volume),
            VolumeMode.entries.map { Choice(it, volumeLabel(it)) },
            settings.volume,
        ) { onChange(settings.copy(volume = it)) }
        SwitchRow(
            stringResource(R.string.crescendo),
            settings.crescendo,
            { onChange(settings.copy(crescendo = it)) },
            description = stringResource(R.string.crescendo_desc),
        )
    }
}

@Composable
private fun LoadingGraceRow(settings: GlobalSettings, onChange: (GlobalSettings) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val length = stringResource(R.string.seconds_value, settings.loadingGraceSec)
    val toggle = { enabled: Boolean -> onChange(settings.copy(loadingGraceEnabled = enabled)) }
    SwitchWithDetailsRow(stringResource(R.string.loading_grace), length, settings.loadingGraceEnabled, toggle) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.loading_grace)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwitchRow(stringResource(R.string.loading_grace_use), settings.loadingGraceEnabled, toggle)
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
private fun ProtectionCard(resumeCount: Int) {
    val context = LocalContext.current
    val state by SessionService.state.collectAsStateWithLifecycle()
    var active by remember(resumeCount) { mutableStateOf(ForceStopGuard.isActive(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        active = ForceStopGuard.isActive(context)
    }

    SectionCard(stringResource(R.string.section_protection)) {
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
            description = stringResource(R.string.force_stop_guard_desc),
            enabled = state?.mode != SessionService.Mode.ALARM,
        )
    }
}

@Composable
private fun PermissionsCard(resumeCount: Int, onSetup: () -> Unit) {
    val context = LocalContext.current
    var requestCount by remember { mutableIntStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestCount++
    }
    val missing = remember(resumeCount, requestCount) {
        Permission.entries.filterNot { Permissions.isGranted(context, it) }
    }

    SectionCard(stringResource(R.string.section_permissions)) {
        if (missing.isEmpty()) {
            Text(stringResource(R.string.all_granted), color = MaterialTheme.colorScheme.primary)
        }
        missing.forEach { permission ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(permission.title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(permission.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = {
                    if (permission == Permission.NOTIFICATIONS) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Permissions.openSettings(context, permission)
                    }
                }) { Text(stringResource(R.string.grant)) }
            }
        }
        Text(
            stringResource(R.string.permissions_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_run_setup))
        }
    }
}

@Composable
private fun TestCard(settings: GlobalSettings) {
    val context = LocalContext.current
    val state by SessionService.state.collectAsStateWithLifecycle()
    val mode = state?.mode
    var confirming by remember { mutableStateOf(false) }

    SectionCard(stringResource(R.string.section_test)) {
        when (mode) {
            SessionService.Mode.ALARM -> Text(stringResource(R.string.test_ringing), color = MaterialTheme.colorScheme.error)
            SessionService.Mode.DIAGNOSTIC -> Text(stringResource(R.string.test_diag_running))
            null -> Unit
        }
        Text(
            stringResource(R.string.test_diag_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { SessionService.start(context, SessionService.Mode.DIAGNOSTIC) },
            enabled = mode == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.test_diag_button)) }
        Button(
            onClick = { confirming = true },
            enabled = mode == null && settings.targetPackage != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.test_ring_button)) }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
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
