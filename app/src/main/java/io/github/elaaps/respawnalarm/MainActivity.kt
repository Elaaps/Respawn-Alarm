package io.github.elaaps.respawnalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { App() } }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Editor(val alarmId: Long, val pickTimeFirst: Boolean = false) : Screen
    data object Settings : Screen
}

private val ScreenSaver = Saver<Screen, String>(
    save = { screen ->
        when (screen) {
            Screen.Home -> SCREEN_HOME
            is Screen.Editor -> SCREEN_EDITOR_PREFIX + screen.alarmId
            Screen.Settings -> SCREEN_SETTINGS
        }
    },
    restore = { saved ->
        when {
            saved == SCREEN_SETTINGS -> Screen.Settings
            saved.startsWith(SCREEN_EDITOR_PREFIX) ->
                saved.removePrefix(SCREEN_EDITOR_PREFIX).toLongOrNull()?.let { Screen.Editor(it) } ?: Screen.Home
            else -> Screen.Home
        }
    },
)

private const val SCREEN_HOME = "home"
private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_EDITOR_PREFIX = "editor:"

@Composable
private fun App() {
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backFromLeft by remember { mutableStateOf(true) }
    PredictiveBackHandler(enabled = screen != Screen.Home) { events ->
        try {
            events.collect { event ->
                backProgress = event.progress
                backFromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
            }
            screen = Screen.Home
        } catch (cancelled: CancellationException) {
            backProgress = 0f
            throw cancelled
        }
    }
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState.depth > initialState.depth
            val direction = if (forward) 1 else -1
            (slideInHorizontally { width -> direction * width } + fadeIn()) togetherWith
                (slideOutHorizontally { width -> -direction * width / SLIDE_OUT_FRACTION } + fadeOut())
        },
        label = "screen",
    ) { current ->
        Box(
            Modifier.graphicsLayer {
                if (current.depth > 0) {
                    val scale = 1f - backProgress * BACK_SCALE
                    scaleX = scale
                    scaleY = scale
                    translationX = backProgress * size.width * BACK_SHIFT * if (backFromLeft) 1 else -1
                    shape = RoundedCornerShape(BACK_CORNER * backProgress)
                    clip = true
                }
            },
        ) {
            ScreenContent(
                current,
                onNavigate = { next ->
                    if (next.depth > 0) backProgress = 0f
                    screen = next
                },
            )
        }
    }
}

private const val BACK_SCALE = 0.1f
private const val BACK_SHIFT = 0.05f
private val BACK_CORNER = 32.dp

private val Screen.depth: Int get() = if (this == Screen.Home) 0 else 1

private const val SLIDE_OUT_FRACTION = 3

@Composable
private fun ScreenContent(current: Screen, onNavigate: (Screen) -> Unit) {
    when (current) {
        Screen.Home -> HomeScreen(
            onEdit = { onNavigate(Screen.Editor(it)) },
            onAdd = { onNavigate(Screen.Editor(it, pickTimeFirst = true)) },
            onSettings = { onNavigate(Screen.Settings) },
        )
        is Screen.Editor -> AlarmEditorScreen(current.alarmId, current.pickTimeFirst, onBack = { onNavigate(Screen.Home) })
        Screen.Settings -> SettingsScreen(onBack = { onNavigate(Screen.Home) })
    }
}
