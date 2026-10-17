package io.github.elaaps.respawnalarm

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.LifecycleResumeEffect

private sealed interface SetupStep {
    data object Welcome : SetupStep
    data object Target : SetupStep
    data class Grant(val permission: Permission) : SetupStep

    data class Vendor(val maker: String, val guideUrl: String) : SetupStep
    data object Diagnostic : SetupStep
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AlarmStore(context) }
    val missingAtStart = rememberSaveable {
        Permission.entries.filterNot { Permissions.isGranted(context, it) }.map { it.ordinal }.toIntArray()
    }
    val needsTarget = rememberSaveable { store.settings().targetPackage == null }
    val steps = remember {
        setupSteps(
            firstLaunch = !store.onboardingDone,
            needsTarget = needsTarget,
            missing = missingAtStart.map { Permission.entries[it] },
        )
    }
    val suggestedTarget = remember { store.suggestedTarget() }
    var index by rememberSaveable { mutableIntStateOf(0) }

    fun finish() {
        val targetStep = steps.indexOf(SetupStep.Target)
        if (targetStep >= 0 && store.settings().targetPackage == null) {
            index = targetStep
            return
        }
        store.onboardingDone = true
        onDone()
    }

    fun chooseTarget(target: String) {
        store.saveSettings(store.settings().copy(targetPackage = target))
        AlarmScheduler.schedule(context)
    }

    val shown = index.coerceAtMost(steps.lastIndex)
    val step = steps[shown]

    fun advanceFrom(from: Int) {
        if (index != from) return
        if (index + 1 < steps.size) index++ else finish()
    }

    LifecycleResumeEffect(shown) {
        if (step is SetupStep.Grant && Permissions.isGranted(context, step.permission)) advanceFrom(shown)
        onPauseOrDispose { }
    }

    val progress by animateFloatAsState((shown + 1f) / steps.size, label = "setup progress")
    val stepText = stringResource(R.string.onboarding_step, shown + 1, steps.size)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = stepText },
            )
            Text(stepText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MorphingShape(
                step.shape(),
                step.container(),
                Modifier.size(STEP_SHAPE_SIZE).align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    painterResource(step.icon()),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.contentFor(step.container()),
                    modifier = Modifier.size(STEP_ICON_SIZE),
                )
            }
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                },
                label = "setup page",
            ) { page ->
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val pageStep = steps[page]
                    when (pageStep) {
                        SetupStep.Welcome -> WelcomePage(onStart = { advanceFrom(page) })
                        SetupStep.Target -> TargetPage(suggestedTarget) { target ->
                            chooseTarget(target)
                            advanceFrom(page)
                        }
                        is SetupStep.Grant -> GrantPage(pageStep.permission, onNext = { advanceFrom(page) })
                        is SetupStep.Vendor -> VendorPage(pageStep, onNext = { advanceFrom(page) })
                        SetupStep.Diagnostic -> DiagnosticPage(onFinish = ::finish)
                    }
                    if (pageStep != SetupStep.Target) {
                        TextButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.onboarding_later))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun SetupStep.shape(): RoundedPolygon = when (this) {
    SetupStep.Welcome -> MaterialShapes.Cookie9Sided
    SetupStep.Target -> MaterialShapes.Cookie4Sided
    is SetupStep.Grant -> when (permission) {
        Permission.NOTIFICATIONS -> MaterialShapes.Cookie6Sided
        Permission.USAGE_ACCESS -> MaterialShapes.Gem
        Permission.OVERLAY -> MaterialShapes.Pentagon
        Permission.FULL_SCREEN -> MaterialShapes.Sunny
        Permission.BATTERY -> MaterialShapes.Cookie7Sided
        Permission.EXACT_ALARM -> MaterialShapes.Arch
    }
    is SetupStep.Vendor -> MaterialShapes.SoftBurst
    SetupStep.Diagnostic -> MaterialShapes.Puffy
}

@DrawableRes
private fun SetupStep.icon(): Int = when (this) {
    SetupStep.Welcome, SetupStep.Target -> R.drawable.ic_sports_esports
    is SetupStep.Grant -> permission.icon
    is SetupStep.Vendor -> R.drawable.ic_phone_android
    SetupStep.Diagnostic -> R.drawable.ic_monitoring
}

@Composable
private fun SetupStep.container(): Color = when (this) {
    SetupStep.Welcome, SetupStep.Target -> MaterialTheme.colorScheme.primaryContainer
    is SetupStep.Grant -> MaterialTheme.colorScheme.tertiaryContainer
    is SetupStep.Vendor, SetupStep.Diagnostic -> MaterialTheme.colorScheme.secondaryContainer
}

private fun ColorScheme.contentFor(container: Color): Color = when (container) {
    primaryContainer -> onPrimaryContainer
    tertiaryContainer -> onTertiaryContainer
    else -> onSecondaryContainer
}

private fun setupSteps(firstLaunch: Boolean, needsTarget: Boolean, missing: List<Permission>): List<SetupStep> = buildList {
    if (firstLaunch) add(SetupStep.Welcome)
    if (needsTarget) add(SetupStep.Target)
    missing.forEach { add(SetupStep.Grant(it)) }
    backgroundGuideUrl(Build.MANUFACTURER)?.let { url ->
        add(SetupStep.Vendor(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, url))
    }
    add(SetupStep.Diagnostic)
}

@Composable
private fun PageTitle(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium)

@Composable
private fun PillButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(PILL_HEIGHT)) { Text(text) }
}

@Composable
private fun TonalPillButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(PILL_HEIGHT)) { Text(text, maxLines = 1) }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    PageTitle(stringResource(R.string.onboarding_welcome_title))
    Text(stringResource(R.string.onboarding_welcome_text))
    PillButton(stringResource(R.string.onboarding_start), onStart)
}

@Composable
private fun TargetPage(suggested: String?, onChoose: (String) -> Unit) {
    var target by rememberSaveable { mutableStateOf(suggested) }
    PageTitle(stringResource(R.string.target_app))
    Text(stringResource(R.string.onboarding_target_text))
    RowGroup {
        row { shapes -> TargetAppRow(target, defaultPackage = null, shapes) { target = it } }
    }
    PillButton(stringResource(R.string.onboarding_next), onClick = { target?.let(onChoose) }, enabled = target != null)
}

@Composable
private fun GrantPage(permission: Permission, onNext: () -> Unit) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onNext()
    }
    PageTitle(stringResource(permission.title))
    Text(stringResource(permission.description))
    PillButton(stringResource(R.string.grant), onClick = {
        if (permission == Permission.NOTIFICATIONS) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Permissions.openSettings(context, permission)
        }
    })
    TonalPillButton(stringResource(R.string.onboarding_next), onNext)
    Text(
        stringResource(R.string.permissions_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun VendorPage(step: SetupStep.Vendor, onNext: () -> Unit) {
    val context = LocalContext.current
    PageTitle(stringResource(R.string.vendor_title, step.maker))
    Text(stringResource(R.string.vendor_text))
    TonalPillButton(stringResource(R.string.vendor_open_guide)) { openUrl(context, step.guideUrl) }
    PillButton(stringResource(R.string.onboarding_next), onNext)
}

@Composable
private fun DiagnosticPage(onFinish: () -> Unit) {
    val context = LocalContext.current
    PageTitle(stringResource(R.string.diag_step_title))
    Text(stringResource(R.string.diag_step_text))
    TonalPillButton(stringResource(R.string.diag_step_button)) { SessionService.start(context, SessionService.Mode.DIAGNOSTIC) }
    PillButton(stringResource(R.string.onboarding_finish), onFinish)
}

private fun openUrl(context: Context, url: String) {
    ignoring<ActivityNotFoundException> { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

private val STEP_SHAPE_SIZE = 168.dp
private val STEP_ICON_SIZE = 72.dp
private val PILL_HEIGHT = 56.dp
