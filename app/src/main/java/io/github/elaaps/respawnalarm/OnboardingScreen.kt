package io.github.elaaps.respawnalarm

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

private sealed interface SetupStep {
    data object Welcome : SetupStep
    data class Grant(val permission: Permission) : SetupStep

    data class Vendor(val maker: String, val guideUrl: String) : SetupStep
    data object Diagnostic : SetupStep
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AlarmStore(context) }
    val steps = remember { setupSteps(context, firstLaunch = !store.onboardingDone) }
    var index by rememberSaveable { mutableIntStateOf(0) }

    fun finish() {
        store.onboardingDone = true
        onDone()
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
    val progressDescription = stringResource(R.string.onboarding_step, shown + 1, steps.size)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressDescription },
            )
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
                    when (val pageStep = steps[page]) {
                        SetupStep.Welcome -> WelcomePage(onStart = { advanceFrom(page) })
                        is SetupStep.Grant -> GrantPage(pageStep.permission, onNext = { advanceFrom(page) })
                        is SetupStep.Vendor -> VendorPage(pageStep, onNext = { advanceFrom(page) })
                        SetupStep.Diagnostic -> DiagnosticPage(onFinish = ::finish)
                    }
                    TextButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_later))
                    }
                }
            }
        }
    }
}

private fun setupSteps(context: Context, firstLaunch: Boolean): List<SetupStep> = buildList {
    if (firstLaunch) add(SetupStep.Welcome)
    Permission.entries.filterNot { Permissions.isGranted(context, it) }.forEach { add(SetupStep.Grant(it)) }
    backgroundGuideUrl(Build.MANUFACTURER)?.let { url ->
        add(SetupStep.Vendor(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, url))
    }
    add(SetupStep.Diagnostic)
}

@Composable
private fun PageTitle(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall)

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    PageTitle(stringResource(R.string.onboarding_welcome_title))
    Text(stringResource(R.string.onboarding_welcome_text))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_start)) }
}

@Composable
private fun GrantPage(permission: Permission, onNext: () -> Unit) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onNext()
    }
    PageTitle(stringResource(permission.title))
    Text(stringResource(permission.description))
    Button(
        onClick = {
            if (permission == Permission.NOTIFICATIONS) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Permissions.openSettings(context, permission)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.grant)) }
    Text(
        stringResource(R.string.permissions_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_next)) }
}

@Composable
private fun VendorPage(step: SetupStep.Vendor, onNext: () -> Unit) {
    val context = LocalContext.current
    PageTitle(stringResource(R.string.vendor_title, step.maker))
    Text(stringResource(R.string.vendor_text))
    OutlinedButton(onClick = { openUrl(context, step.guideUrl) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.vendor_open_guide), maxLines = 1)
    }
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_next)) }
}

@Composable
private fun DiagnosticPage(onFinish: () -> Unit) {
    val context = LocalContext.current
    PageTitle(stringResource(R.string.diag_step_title))
    Text(stringResource(R.string.diag_step_text))
    OutlinedButton(
        onClick = { SessionService.start(context, SessionService.Mode.DIAGNOSTIC) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.diag_step_button)) }
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_finish)) }
}

private fun openUrl(context: Context, url: String) {
    ignoring<ActivityNotFoundException> { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
