package com.sledje.agentpro.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.ui.LocalIsOnline
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The frame of a tab's root screen: its top bar, the offline banner, then [content]. */
@Composable
fun TabScreen(
    title: StringResource,
    titleText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AppScaffold(
        topBar = { AppTopBar(title = titleText ?: stringResource(title), trailing = trailing) },
        offline = !LocalIsOnline.current,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}
