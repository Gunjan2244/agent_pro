package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.catalog.TemplateFamily
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.onboarding_template_position
import com.sledje.agentpro.resources.template_family_prompt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Test tag of a family card (`template_family_personal`, `template_family_offers`). */
fun templateFamilyTag(family: TemplateFamily) = "template_family_${family.key}"

/**
 * The two-step template choice shared by onboarding step 2 and Website → Appearance → Template
 * (docs/UX.md, Template picker): first the family on its cards ("Meet me" / "Best offers", from the
 * catalog's `templateFamilies`), then a pager of that family's templates. [family] is null on the first step.
 */
@Stable
class TemplateChooserState internal constructor(
    val templates: List<TemplateSpec>,
    val pager: PagerState,
    private val familyState: androidx.compose.runtime.MutableState<TemplateFamily?>,
) {
    /** Families that have at least one template, in the catalog's order. */
    val families: List<TemplateFamily> = TemplateFamily.active.filter { f -> templates.any { it.family == f } }

    var family: TemplateFamily? by familyState
        private set

    val inFamily: List<TemplateSpec> get() = templates.filter { it.family == family }

    /** The template on show: null while choosing the family. */
    val current: TemplateSpec? get() = if (family == null) null else inFamily.getOrNull(pager.currentPage)

    fun choose(chosen: TemplateFamily) { family = chosen }

    /** Back from the pager to the family cards; false when already on the cards. */
    fun back(): Boolean {
        if (family == null) return false
        family = null
        return true
    }
}

/** [selectedKey]'s template is the page the pager opens on, when it is in the chosen family. */
@Composable
fun rememberTemplateChooser(templates: List<TemplateSpec>, selectedKey: String, initialFamily: TemplateFamily? = null): TemplateChooserState {
    val familyState = remember { mutableStateOf(initialFamily) }
    val startPage = remember {
        initialFamily?.let { f -> templates.filter { it.family == f }.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0) } ?: 0
    }
    val pager = rememberPagerState(initialPage = startPage) { templates.count { it.family == familyState.value } }
    val state = remember(templates) { TemplateChooserState(templates, pager, familyState) }
    LaunchedEffect(state.family) {
        val start = state.inFamily.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
        if (state.family != null && pager.currentPage != start) pager.scrollToPage(start)
    }
    return state
}

/** System Back closes the pager before it leaves the screen. */
@OptIn(ExperimentalComposeUiApi::class) // BackHandler (ui-backhandler)
@Composable
fun TemplateChooserBackHandler(state: TemplateChooserState) {
    BackHandler(enabled = state.family != null) { state.back() }
}

/** The family cards, or the pager of the chosen family with the template's name and description below. */
@Composable
fun TemplateChooserBody(state: TemplateChooserState, pageContent: @Composable (TemplateSpec) -> Unit) {
    if (state.family == null) {
        TemplateFamilyCards(state.families, onChoose = state::choose)
    } else {
        TemplatePager(state, pageContent)
    }
}

@Composable
private fun TemplateFamilyCards(families: List<TemplateFamily>, onChoose: (TemplateFamily) -> Unit) {
    Column(Modifier.fillMaxSize().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(Res.string.template_family_prompt), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        families.forEach { family ->
            AppCard(Modifier.fillMaxWidth().testTag(templateFamilyTag(family)), onClick = { onChoose(family) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Icon(painterResource(family.icon), null, Modifier.size(Dimens.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(stringResource(family.label), style = AgentProTheme.text.strong, modifier = Modifier.semantics { heading() })
                        Text(stringResource(family.description), style = AgentProTheme.text.bodyMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplatePager(state: TemplateChooserState, pageContent: @Composable (TemplateSpec) -> Unit) {
    val templates = state.inFamily
    val current = state.current ?: templates.firstOrNull() ?: return
    Column(Modifier.fillMaxSize().padding(vertical = Spacing.m)) {
        HorizontalPager(
            state = state.pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.l),
            pageSpacing = Spacing.m,
        ) { page -> templates.getOrNull(page)?.let { pageContent(it) } }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(top = Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(current.name), style = AgentProTheme.text.title, modifier = Modifier.semantics { heading() })
            Text(stringResource(current.description), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(stringResource(Res.string.onboarding_template_position, state.pager.currentPage + 1, templates.size), style = AgentProTheme.text.bodyMuted)
        }
    }
}
