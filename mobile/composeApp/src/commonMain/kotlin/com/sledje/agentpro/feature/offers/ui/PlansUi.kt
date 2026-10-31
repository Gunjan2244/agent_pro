package com.sledje.agentpro.feature.offers.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.ErrorState
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.designsystem.components.ToggleRow
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.offers.data.CompanyPlan
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.ic_description
import com.sledje.agentpro.resources.plans_auto
import com.sledje.agentpro.resources.plans_auto_help
import com.sledje.agentpro.resources.plans_clear_all
import com.sledje.agentpro.resources.plans_empty
import com.sledje.agentpro.resources.plans_intro
import com.sledje.agentpro.resources.plans_no
import com.sledje.agentpro.resources.plans_none_selected
import com.sledje.agentpro.resources.plans_save
import com.sledje.agentpro.resources.plans_saved
import com.sledje.agentpro.resources.plans_select_all
import com.sledje.agentpro.resources.plans_title
import com.sledje.agentpro.resources.plans_uin
import org.jetbrains.compose.resources.stringResource

/** What the plans screen can do (the route wires these to [PlansViewModel] and navigation). */
data class PlansActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onAuto: (Boolean) -> Unit,
    val onToggle: (String) -> Unit,
    val onToggleCompany: (String) -> Unit,
    val onSave: () -> Unit,
    val onSavedShown: () -> Unit,
)

/**
 * Website → Plans on your site (DECISIONS #129; docs/UX.md). A switch for "all my companies' plans" (new plans appear
 * by themselves), or one checkbox per plan, grouped by company. Save applies it to the live site at once.
 */
@Composable
fun PlansScreen(state: PlansState, actions: PlansActions) {
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(Res.string.plans_saved)
    LaunchedEffect(state.saved) {
        if (state.saved) { actions.onSavedShown(); snackbar.showSnackbar(savedText) }
    }
    val online = LocalIsOnline.current
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.plans_title), onBack = actions.onBack) },
        offline = !online,
        snackbarHostState = snackbar,
        bottomBar = {
            if (!state.loading && state.error == null && state.plans.isNotEmpty()) {
                BottomActionBar(
                    text = stringResource(Res.string.plans_save),
                    onClick = actions.onSave,
                    enabled = online && state.dirty && !state.saving,
                    helperText = when {
                        state.saveError != null -> stringResource(state.saveError.message)
                        !state.auto && state.selected.isEmpty() -> stringResource(Res.string.plans_none_selected)
                        else -> null
                    },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> ListSkeleton(Modifier.padding(padding))
            state.error != null -> ErrorState(stringResource(state.error.message), actions.onRetry, Modifier.padding(padding))
            state.plans.isEmpty() -> EmptyState(Res.drawable.ic_description, stringResource(Res.string.plans_empty), Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        stringResource(Res.string.plans_intro),
                        style = AgentProTheme.text.bodyMuted,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m),
                    )
                }
                item {
                    ToggleRow(
                        label = stringResource(Res.string.plans_auto),
                        checked = state.auto,
                        onCheckedChange = actions.onAuto,
                        supporting = stringResource(Res.string.plans_auto_help),
                    )
                    HairlineDivider()
                }
                state.byCompany.forEach { (company, plans) ->
                    item(key = "h-$company") {
                        val all = plans.all { it.id in state.selected }
                        CompanyHeader(company, if (state.auto) null else stringResource(if (all) Res.string.plans_clear_all else Res.string.plans_select_all)) {
                            actions.onToggleCompany(company)
                        }
                    }
                    items(plans, key = { it.id }) { plan ->
                        PlanRow(plan, checked = state.auto || plan.id in state.selected, enabled = !state.auto, onToggle = { actions.onToggle(plan.id) })
                        HairlineDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanRow(plan: CompanyPlan, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val facts = listOfNotNull(
        plan.planNo?.let { stringResource(Res.string.plans_no, it) },
        plan.uin?.let { stringResource(Res.string.plans_uin, it) },
    ).joinToString(" · ").ifBlank { null }
    ListRow(
        title = plan.name,
        supporting = facts,
        onClick = if (enabled) onToggle else null,
        trailing = { Checkbox(checked = checked, onCheckedChange = if (enabled) ({ onToggle() }) else null, enabled = enabled) },
    )
}

/** A company's name over its plans, with "Select all" / "Clear" while the agent chooses plans one by one. */
@Composable
private fun CompanyHeader(company: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.m, top = Spacing.l, end = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            company,
            style = AgentProTheme.text.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null) TertiaryButton(action, onAction)
    }
}
