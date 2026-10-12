package io.github.elaaps.respawnalarm

import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

class RingingActivity : ComponentActivity() {
    private var confirmingEmergency by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = AlarmStore(this)
        val ringingAlarmId = store.ringingAlarmId
        val alarm = store.resolve(ringingAlarmId)
        var restarting = intent.getBooleanExtra(EXTRA_RESTART_ALARM, false) &&
            SessionService.state.value == null
        if (restarting) SessionService.start(this, SessionService.Mode.ALARM, ringingAlarmId)
        setContent {
            AppTheme {
                val state by SessionService.state.collectAsStateWithLifecycle()
                LaunchedEffect(state?.mode) {
                    if (state?.mode == SessionService.Mode.ALARM) {
                        restarting = false
                    } else if (!restarting) {
                        finish()
                    }
                }
                RingingScreen(
                    label = alarm.label,
                    target = appLabel(this, alarm.targetPackage),
                    duration = formatDuration(alarm.durationMs),
                    snoozesLeft = state?.snoozesLeft ?: 0,
                    onLaunch = { launchTarget(alarm.targetPackage) },
                    onSnooze = ::snooze,
                    onEmergency = ::requestEmergencyStop,
                )
                if (confirmingEmergency) {
                    AlertDialog(
                        onDismissRequest = { confirmingEmergency = false },
                        title = { Text(stringResource(R.string.emergency_title)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmingEmergency = false
                                emergencyStop()
                            }) { Text(stringResource(R.string.emergency_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmingEmergency = false }) { Text(stringResource(R.string.cancel)) }
                        },
                    )
                }
            }
        }
    }

    private fun launchTarget(target: String?) = whenUnlocked {
        launchIntent(this, target)?.let { startActivity(it) }
        finish()
    }

    private fun snooze() {
        startService(Intent(this, SessionService::class.java).setAction(SessionService.ACTION_SNOOZE))
        finish()
    }

    private fun requestEmergencyStop() = whenUnlocked {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) {
            confirmingEmergency = true
            return@whenUnlocked
        }
        BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.emergency_title))
            .setSubtitle(getString(R.string.emergency_subtitle))
            .setAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL)
            .build()
            .authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = emergencyStop()
                },
            )
    }

    private fun emergencyStop() {
        startService(Intent(this, SessionService::class.java).setAction(SessionService.ACTION_EMERGENCY_STOP))
        finish()
    }

    private fun whenUnlocked(action: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) return action()
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = action()
            },
        )
    }

    companion object {
        const val EXTRA_RESTART_ALARM = "io.github.elaaps.respawnalarm.RESTART_ALARM"
    }
}

@Composable
private fun RingingScreen(
    label: String,
    target: String,
    duration: String,
    snoozesLeft: Int,
    onLaunch: () -> Unit,
    onSnooze: () -> Unit,
    onEmergency: () -> Unit,
) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(CLOCK_REFRESH)
            value = LocalTime.now()
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.safeDrawingPadding()) {
            val emergencyDescription = stringResource(R.string.action_emergency)
            IconButton(
                onClick = onEmergency,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .semantics { contentDescription = emergencyDescription },
            ) { Text(EMERGENCY_ICON) }
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(formatClockTime(LocalContext.current, now), style = MaterialTheme.typography.displayLarge)
                if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.ringing_message, duration, target),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onLaunch,
                    modifier = Modifier.fillMaxWidth().height(LAUNCH_HEIGHT),
                    colors = ButtonDefaults.buttonColors(containerColor = STOP_RED, contentColor = Color.White),
                ) { Text(stringResource(R.string.action_launch, target), style = MaterialTheme.typography.titleLarge) }
                AnimatedVisibility(visible = snoozesLeft > 0) {
                    Button(
                        onClick = onSnooze,
                        modifier = Modifier.fillMaxWidth(SNOOZE_WIDTH_FRACTION).height(SNOOZE_HEIGHT),
                        colors = ButtonDefaults.buttonColors(containerColor = SNOOZE_GREEN, contentColor = Color.White),
                    ) { Text(pluralStringResource(R.plurals.action_snooze, snoozesLeft, snoozesLeft)) }
                }
            }
        }
    }
}

private val CLOCK_REFRESH = 1.seconds

private const val EMERGENCY_ICON = "🛟"

private val STOP_RED = Color(0xFFC62828)
private val SNOOZE_GREEN = Color(0xFF2E7D32)
private val LAUNCH_HEIGHT = 80.dp
private val SNOOZE_HEIGHT = 56.dp
private const val SNOOZE_WIDTH_FRACTION = 0.7f
