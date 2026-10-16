package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.NetworkImage
import com.sledje.agentpro.core.forms.FormRenderer
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.forms.SectionSpec
import com.sledje.agentpro.core.forms.message
import com.sledje.agentpro.feature.website.data.PublishIssue
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.website_issue_field
import com.sledje.agentpro.resources.website_issue_item
import com.sledje.agentpro.resources.website_issues_title
import com.sledje.agentpro.resources.website_photo_uploading
import com.sledje.agentpro.resources.website_save
import org.jetbrains.compose.resources.stringResource

/** Test tag of the section editor's Save bar. */
const val SECTION_SAVE_TAG = "section_save"

/**
 * The section editor (docs/UX.md): the schema section as a FormRenderer form (list fields as rows with
 * Add up to `maxItems`, image fields through the photo picker), a sticky Save bar (disabled offline:
 * "Needs internet"), and, after a refused publish, the server's errors for this section on top.
 */
@Composable
fun SectionEditorScreen(
    section: SectionSpec,
    form: FormState?,
    state: SectionEditorUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPickImage: (fieldPath: String) -> Unit,
    onSave: () -> Unit,
) {
    val online = isOnline()
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(section.label), onBack = onBack) },
        offline = !online,
        snackbarHostState = snackbar,
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.website_save),
                onClick = onSave,
                enabled = online && form != null && !state.busy,
                helperText = if (!online) stringResource(Res.string.needs_internet) else null,
                modifier = Modifier.testTag(SECTION_SAVE_TAG),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (form == null) {
                ListSkeleton(rows = 4)
                return@Box
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (state.issues.isNotEmpty()) IssuesCard(state.issues)
                FormRenderer(
                    state = form,
                    enabled = !state.saving,
                    onPickImage = { path -> if (online && state.uploadingField == null) onPickImage(path) },
                    imagePreview = { url -> NetworkImage(url) },
                )
                if (state.uploadingField != null) Text(stringResource(Res.string.website_photo_uploading), style = AgentProTheme.text.bodyMuted)
                state.error?.let { ErrorText(websiteErrorText(it)) }
            }
        }
    }
}

/** The fields `publish_site` refused in this section, e.g. "Headline: Required" (docs/BACKEND.md, Publish validation). */
@Composable
private fun IssuesCard(issues: List<PublishIssue>) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.website_issues_title), style = AgentProTheme.text.strong, modifier = Modifier.semantics { heading() })
        issues.forEach { issue ->
            val field = issue.field?.let { stringResource(it.label) } ?: stringResource(issue.section.label)
            val message = issue.error.message()
            val line = if (issue.item != null) {
                stringResource(Res.string.website_issue_item, field, issue.item + 1, message)
            } else {
                stringResource(Res.string.website_issue_field, field, message)
            }
            ErrorText(line, Modifier.padding(top = Spacing.s))
        }
    }
}
