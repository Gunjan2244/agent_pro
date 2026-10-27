package com.sledje.agentpro.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Motion
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.Avatar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.StepProgressBar
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.website.ui.SiteQrCode
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.launch_done
import com.sledje.agentpro.resources.launch_install_text
import com.sledje.agentpro.resources.launch_install_title
import com.sledje.agentpro.resources.launch_not_now
import com.sledje.agentpro.resources.launch_share_text
import com.sledje.agentpro.resources.launch_share_title
import com.sledje.agentpro.resources.launch_title
import com.sledje.agentpro.resources.website_add_to_home
import com.sledje.agentpro.resources.website_open
import com.sledje.agentpro.resources.website_share
import org.jetbrains.compose.resources.stringResource

/**
 * The two screens after "Publish now" (docs/UX.md, Site is live): [LaunchStep.AddToPhone] and [LaunchStep.Share]
 * (onboarding step 6 already showed the site; DECISIONS #116). One scaffold stays put, so the progress bar slides
 * and only the middle changes, with the shared-axis X motion (forward or back).
 */
@Composable
fun SiteLaunchScreen(
    step: LaunchStep,
    agentName: String,
    url: String,
    display: String,
    onAddToHome: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDone: () -> Unit,
) {
    AppScaffold(
        topBar = {
            Column {
                AppTopBar(title = stringResource(Res.string.launch_title))
                StepProgressBar(step.ordinal + 1, LaunchStep.entries.size)
            }
        },
        offline = !LocalIsOnline.current,
        bottomBar = {
            when (step) {
                LaunchStep.AddToPhone -> BottomActionBar(
                    stringResource(Res.string.website_add_to_home), onAddToHome,
                    secondaryText = stringResource(Res.string.launch_not_now), onSecondary = onNext,
                )
                LaunchStep.Share -> BottomActionBar(
                    stringResource(Res.string.website_share), onShare,
                    secondaryText = stringResource(Res.string.launch_done), onSecondary = onDone,
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = step,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                Motion.sharedAxisXEnter(forward) togetherWith Motion.sharedAxisXExit(forward)
            },
            label = "launch",
        ) { page ->
            when (page) {
                LaunchStep.AddToPhone -> LaunchPage(
                    title = stringResource(Res.string.launch_install_title),
                    text = stringResource(Res.string.launch_install_text),
                ) { HomeScreenTile(agentName, display) }
                LaunchStep.Share -> LaunchPage(
                    title = stringResource(Res.string.launch_share_title),
                    text = stringResource(Res.string.launch_share_text),
                ) {
                    SiteQrCode(url)
                    Text(display, style = AgentProTheme.text.strong, textAlign = TextAlign.Center)
                    TertiaryButton(stringResource(Res.string.website_open), onOpen)
                }
            }
        }
    }
}

@Composable
private fun LaunchHeading(title: String, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = AgentProTheme.text.title, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(text, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** A centred page: heading, then [content] (an illustration or the QR code). */
@Composable
private fun LaunchPage(title: String, text: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
    ) {
        LaunchHeading(title, text)
        content()
    }
}

/** How the site will sit on the home screen: the agent's initials as the icon, with the name under it. */
@Composable
private fun HomeScreenTile(agentName: String, display: String) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Avatar(agentName)
            Text(agentName, style = AgentProTheme.text.strong, textAlign = TextAlign.Center)
            Text(display, style = AgentProTheme.text.bodyMuted, textAlign = TextAlign.Center)
        }
    }
}
