package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.website_preview
import com.sledje.agentpro.resources.website_publish
import org.jetbrains.compose.resources.stringResource

/**
 * Preview (docs/UX.md): a full-screen render of the draft (SitePreview, injected as [preview]) with
 * **Publish** in the top bar (disabled offline or while publishing).
 */
@Composable
fun PreviewScreen(
    publishing: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    val online = isOnline()
    AppScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.website_preview),
                onBack = onBack,
                trailing = { TertiaryButton(stringResource(Res.string.website_publish), onPublish, enabled = online && !publishing) },
            )
        },
        offline = !online,
        snackbarHostState = snackbar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { preview(Modifier.fillMaxSize()) }
    }
}
