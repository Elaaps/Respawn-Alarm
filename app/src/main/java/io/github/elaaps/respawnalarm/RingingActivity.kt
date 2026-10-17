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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val rangAt = formatClockTime(this, LocalTime.now())
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
                    rangAt = rangAt,
                    target = appLabel(this, alarm.targetPackage),
                    durationSec = alarm.durationSec,
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
    rangAt: String,
    target: String,
    durationSec: Int,
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
    BossBackground {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            IconButton(onClick = onEmergency, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(painterResource(R.drawable.ic_support), contentDescription = stringResource(R.string.action_emergency), tint = BossColors.Lilac)
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PixelText(stringResource(R.string.ringing_boss_label, rangAt), 19.sp, BossColors.Gold, Modifier.align(Alignment.Start))
                HpBar(filled = HP_SEGMENTS, blinkLast = true)
                HpCaption(durationSec, durationSec)
                ShadowedPixelText(
                    formatClockTime(LocalContext.current, now),
                    CLOCK_SIZE,
                    BossColors.Pale,
                    BossColors.Purple,
                    BossColors.Ink,
                    Modifier.padding(top = 24.dp),
                )
                if (label.isNotBlank()) Text(label, style = bossBody(), color = BossColors.Lilac)
                Text(
                    stringResource(R.string.ringing_message, formatDuration(durationSec * MILLIS_PER_SECOND), target),
                    style = bossBody(),
                    color = BossColors.Lilac,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                PixelButton(stringResource(R.string.action_launch, target), onLaunch)
                AnimatedVisibility(visible = snoozesLeft > 0) {
                    PixelGhostButton(pluralStringResource(R.plurals.action_snooze, snoozesLeft, snoozesLeft), onSnooze, Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

private fun bossBody() = TextStyle(fontFamily = BossFont, fontSize = 22.sp, lineHeight = 26.sp)

private val CLOCK_REFRESH = 1.seconds
private val CLOCK_SIZE = 104.sp
private const val MILLIS_PER_SECOND = 1_000L
