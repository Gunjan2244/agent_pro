package com.sledje.agentpro.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.ErrorState
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.app_name
import com.sledje.agentpro.resources.ic_cloud_off
import com.sledje.agentpro.resources.not_configured
import com.sledje.agentpro.resources.startup_error
import org.jetbrains.compose.resources.stringResource

/** Splash: the name on white while the gate decides (docs/UX.md; there's no logo asset yet). */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.app_name), style = AgentProTheme.text.title)
    }
}

/** Shown when this build has no SUPABASE_URL / SUPABASE_ANON_KEY (mobile/local.properties). */
@Composable
fun NotConfiguredScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EmptyState(Res.drawable.ic_cloud_off, stringResource(Res.string.not_configured))
    }
}

/** The cache is empty and the first sync failed: a message and Retry. */
@Composable
fun StartupErrorScreen(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ErrorState(stringResource(Res.string.startup_error), onRetry)
    }
}
