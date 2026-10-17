package io.github.elaaps.respawnalarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

class VictoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val victory = victoryOf(intent) ?: return finish()
        startService(Intent(this, SessionService::class.java).setAction(SessionService.ACTION_VICTORY_SHOWN))
        val streak = winStreak(SessionLog(this).records(), LocalDate.now(), ZoneId.systemDefault())
        enableEdgeToEdge()
        setContent { AppTheme { VictoryScreen(victory, streak, onContinue = ::finish) } }
    }

    companion object {
        private const val EXTRA_UP_AT = "up_at"
        private const val EXTRA_DURATION = "duration_sec"
        private const val EXTRA_RESETS = "resets"
        private const val EXTRA_SNOOZES = "snoozes"

        fun intent(context: Context, victory: Victory): Intent = Intent(context, VictoryActivity::class.java)
            .putExtra(EXTRA_UP_AT, victory.upAt.toEpochMilli())
            .putExtra(EXTRA_DURATION, victory.durationSec)
            .putExtra(EXTRA_RESETS, victory.resets)
            .putExtra(EXTRA_SNOOZES, victory.snoozes)

        private fun victoryOf(intent: Intent): Victory? {
            if (!intent.hasExtra(EXTRA_UP_AT) || !intent.hasExtra(EXTRA_DURATION)) return null
            val durationSec = intent.getIntExtra(EXTRA_DURATION, 0).takeIf { it in GlobalSettings.DURATION_RANGE_SEC } ?: return null
            return Victory(
                upAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_UP_AT, 0)),
                durationSec = durationSec,
                resets = intent.getIntExtra(EXTRA_RESETS, 0).coerceAtLeast(0),
                snoozes = intent.getIntExtra(EXTRA_SNOOZES, 0).coerceAtLeast(0),
            )
        }
    }
}

@Composable
private fun VictoryScreen(victory: Victory, streak: Int, onContinue: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var filled by remember { mutableIntStateOf(HP_SEGMENTS) }
    var knockedOut by remember { mutableStateOf(false) }
    val drop = remember { Animatable(KO_DROP_FROM) }
    LaunchedEffect(Unit) {
        delay(DRAIN_START_MS)
        while (filled > 0) {
            filled--
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            delay(DRAIN_STEP_MS)
        }
        knockedOut = true
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        drop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val upAt = formatClockTime(context, victory.upAt.atZone(ZoneId.systemDefault()).toLocalTime())
    BossBackground {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShadowedPixelText(
                stringResource(R.string.victory_ko),
                KO_SIZE,
                BossColors.Gold,
                BossColors.Hp,
                BossColors.Ink,
                Modifier.graphicsLayer {
                    alpha = if (knockedOut) 1f else 0f
                    translationY = drop.value * density
                },
            )
            PixelText(stringResource(R.string.victory_title), 22.sp, BossColors.Gold)
            HpBar(filled = filled)
            HpCaption(victory.durationSec * filled / HP_SEGMENTS, victory.durationSec)
            AnimatedVisibility(knockedOut, enter = fadeIn() + slideInVertically { it / 2 }) {
                PixelPanel {
                    StatRow(stringResource(R.string.victory_up_at), upAt)
                    StatRow(stringResource(R.string.victory_attempts), victory.attempts.toString())
                    StatRow(stringResource(R.string.victory_snoozes), victory.snoozes.toString())
                    StatRow(stringResource(R.string.victory_streak), pluralStringResource(R.plurals.streak_days, streak, streak))
                }
            }
            Spacer(Modifier.weight(1f))
            PixelButton(stringResource(R.string.action_continue), onContinue)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        PixelText(label, 19.sp, BossColors.Pale, Modifier.weight(1f))
        PixelText(value, 19.sp, BossColors.Gold)
    }
}

private val KO_SIZE = 108.sp
private const val KO_DROP_FROM = -240f
private const val DRAIN_START_MS = 400L
private const val DRAIN_STEP_MS = 110L
