package com.sledje.agentpro.feature.offers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.ErrorState
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.forms.FormRenderer
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.plans_features_blocked
import com.sledje.agentpro.resources.plans_features_default_note
import com.sledje.agentpro.resources.plans_features_none
import com.sledje.agentpro.resources.plans_features_own_note
import com.sledje.agentpro.resources.plans_features_reset
import com.sledje.agentpro.resources.plans_features_reset_done
import com.sledje.agentpro.resources.plans_features_rules
import com.sledje.agentpro.resources.plans_features_save
import com.sledje.agentpro.resources.plans_features_saved
import com.sledje.agentpro.resources.plans_features_title
import com.sledje.agentpro.resources.plans_no
import org.jetbrains.compose.resources.stringResource

/** What the key-features screen can do (the route wires these to [PlanFeaturesViewModel] and navigation). */
data class PlanFeaturesActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onSave: () -> Unit,
    val onReset: () -> Unit,
    val onSavedShown: () -> Unit,
)

/**
 * Website → Plans on your site → Key features (DECISIONS #130; docs/UX.md). The plan (picture, name, number), a note
 * saying whose words the website shows, up to 5 points (FormRenderer; the insurer's words to start from), "Save my
 * words", and "Go back to <company>'s wording" while the agent's own words are on the site.
 */
@Composable
fun PlanFeaturesScreen(state: PlanFeaturesState, form: FormState, canSave: Boolean, actions: PlanFeaturesActions) {
    val snackbar = remember { SnackbarHostState() }
    val company = state.plan?.company.orEmpty()
    val savedText = stringResource(Res.string.plans_features_saved)
    val resetText = stringResource(Res.string.plans_features_reset_done, company)
    LaunchedEffect(state.saved, state.reset) {
        if (state.saved || state.reset) { val m = if (state.saved) savedText else resetText; actions.onSavedShown(); snackbar.showSnackbar(m) }
    }
    val online = LocalIsOnline.current
    val plan = state.plan
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.plans_features_title), onBack = actions.onBack) },
        offline = !online,
        snackbarHostState = snackbar,
        bottomBar = {
            if (plan != null && state.error == null) {
                val err = state.saveError
                BottomActionBar(
                    text = stringResource(Res.string.plans_features_save),
                    onClick = actions.onSave,
                    enabled = online && canSave,
                    helperText = when {
                        err is AppError.Validation && err.rule == "blocked_term" -> stringResource(Res.string.plans_features_blocked, err.field)
                        err != null -> stringResource(err.message)
                        else -> null
                    },
                    secondaryText = if (state.own) stringResource(Res.string.plans_features_reset, company) else null,
                    onSecondary = actions.onReset,
                    secondaryEnabled = online && !state.saving,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(Modifier.padding(padding))
            state.error != null || plan == null -> ErrorState(stringResource((state.error ?: AppError.Unknown()).message), actions.onRetry, Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlanPicture(plan)
                    Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                        Text(plan.name, style = AgentProTheme.text.strong)
                        Text(listOfNotNull(company, plan.planNo?.let { stringResource(Res.string.plans_no, it) }).joinToString(" · "), style = AgentProTheme.text.bodyMuted)
                    }
                }
                val note = when {
                    state.own -> stringResource(Res.string.plans_features_own_note, company)
                    plan.features.isEmpty() -> stringResource(Res.string.plans_features_none, company)
                    else -> stringResource(Res.string.plans_features_default_note, company)
                }
                Text(note, style = AgentProTheme.text.body, modifier = Modifier.fillMaxWidth())
                Text(stringResource(Res.string.plans_features_rules), style = AgentProTheme.text.bodyMuted)
                FormRenderer(form, enabled = !state.saving, onDone = { if (canSave) actions.onSave() })
            }
        }
    }
}
