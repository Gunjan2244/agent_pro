package com.sledje.agentpro.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.catalog.TemplateFamily
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.catalog.color
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.Tokens
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTextField
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.StepProgressBar
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.format.Phone
import com.sledje.agentpro.core.forms.FormRenderer
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.forms.fieldTestTag
import com.sledje.agentpro.core.forms.message
import androidx.compose.ui.platform.testTag
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.core.designsystem.components.NetworkImage
import com.sledje.agentpro.feature.offers.ui.CompaniesBlock
import com.sledje.agentpro.feature.offers.ui.CompaniesUiState
import com.sledje.agentpro.feature.offers.ui.CompanyActions
import com.sledje.agentpro.feature.offers.ui.CompanySheets
import com.sledje.agentpro.feature.website.data.ImageUnreadable
import com.sledje.agentpro.feature.website.data.SiteLinks
import com.sledje.agentpro.feature.website.ui.TemplateChooserBackHandler
import com.sledje.agentpro.feature.website.ui.TemplateChooserBody
import com.sledje.agentpro.feature.website.ui.rememberTemplateChooser
import com.sledje.agentpro.resources.website_photo_unreadable
import com.sledje.agentpro.resources.website_photo_uploading
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.cat_site_cta_default
import com.sledje.agentpro.resources.crm_edit
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.onboarding_continue
import com.sledje.agentpro.resources.onboarding_later
import com.sledje.agentpro.resources.onboarding_link_available
import com.sledje.agentpro.resources.onboarding_link_checking
import com.sledje.agentpro.resources.onboarding_link_label
import com.sledje.agentpro.resources.onboarding_link_preview
import com.sledje.agentpro.resources.onboarding_link_taken
import com.sledje.agentpro.resources.onboarding_link_title
import com.sledje.agentpro.resources.onboarding_profile_title
import com.sledje.agentpro.resources.onboarding_publish_now
import com.sledje.agentpro.resources.onboarding_template_title
import com.sledje.agentpro.resources.onboarding_use_template
import org.jetbrains.compose.resources.stringResource

private const val STEPS = 3

/** Test tag key of the link field. */
const val SLUG_FIELD = "slug"

/** Top bar plus the thin step progress bar (docs/UX.md: each step has a progress bar at the top). */
@Composable
private fun StepTopBar(title: String, step: OnboardingStep, onBack: (() -> Unit)?) {
    Column {
        AppTopBar(title = title, onBack = onBack)
        StepProgressBar(step.number, STEPS)
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text,
        style = AgentProTheme.text.body,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Step 1: name, mobile, designation and city; agency code and branch under More details. When sign-up
 * already gave the name and mobile ([OnboardingUiState.contactSummary]), they show as a card with
 * **Edit** (which brings back their fields) and aren't asked again. After the profile fields comes the required
 * "Your companies" block ([companies]; docs/UX.md); Continue waits for at least one company.
 */
@Composable
fun OnboardingProfileScreen(
    form: FormState,
    state: OnboardingUiState,
    onContinue: () -> Unit,
    onEditContact: () -> Unit = {},
    /** Opens the photo picker (MediaPicker); the view model uploads the result. */
    onPickPhoto: () -> Unit = {},
    /** The "Your companies" block's state; null hides the block (and its rule) for previews that predate it. */
    companies: CompaniesUiState? = null,
    companyActions: CompanyActions = CompanyActions.None,
) {
    val online = LocalIsOnline.current
    AppScaffold(
        topBar = { StepTopBar(stringResource(Res.string.onboarding_profile_title), OnboardingStep.Profile, onBack = null) },
        offline = !online,
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.onboarding_continue),
                onClick = onContinue,
                enabled = online && form.isValid && !state.saving && !state.uploadingPhoto && (companies == null || companies.satisfiesOnboarding),
                helperText = if (!online) stringResource(Res.string.needs_internet) else null,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (state.contactSummary) ContactSummary(form, enabled = !state.saving, onEdit = onEditContact)
            FormRenderer(
                form,
                visibleKeys = OnboardingViewModel.VISIBLE_PROFILE_KEYS,
                hiddenKeys = if (state.contactSummary) OnboardingViewModel.CONTACT_KEYS else emptySet(),
                enabled = !state.saving && !state.uploadingPhoto,
                onPickImage = { if (online) onPickPhoto() },
                imagePreview = { url -> NetworkImage(url) },
            )
            if (companies != null) CompaniesBlock(companies, companyActions, requiredHint = true)
            if (state.uploadingPhoto) Text(stringResource(Res.string.website_photo_uploading), style = AgentProTheme.text.bodyMuted)
            state.error?.let { ErrorLine(stringResource(if (it == ImageUnreadable) Res.string.website_photo_unreadable else it.message)) }
        }
    }
    if (companies != null) CompanySheets(companies, companyActions)
}

/** Step 1's name and mobile from sign-up, with **Edit**. */
@Composable
private fun ContactSummary(form: FormState, enabled: Boolean, onEdit: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(form.text(OnboardingViewModel.NAME), style = AgentProTheme.text.strong)
                Text(Phone.display(form.text(OnboardingViewModel.MOBILE)), style = AgentProTheme.text.bodyMuted)
            }
            TertiaryButton(stringResource(Res.string.crm_edit), onEdit, enabled = enabled)
        }
    }
}

/**
 * Step 2: first the family on two cards ("Meet me" / "Best offers"), then a full-height pager of that family's
 * templates with the template name below and **Use this template**. The app passes [preview] = the live render
 * of the agent's seeded starter content in that template (SitePreview, the bundled web renderer); the default
 * placeholder shows the name in the template's accent (screenshots). Back from the pager returns to the cards.
 */
@Composable
fun OnboardingTemplateScreen(
    templates: List<TemplateSpec>,
    selectedKey: String,
    agentName: String,
    agentLine: String,
    onUse: (String) -> Unit,
    onBack: (() -> Unit)?,
    preview: @Composable (TemplateSpec) -> Unit = { TemplatePlaceholder(it, agentName, agentLine) },
    initialFamily: TemplateFamily? = null,
) {
    val chooser = rememberTemplateChooser(templates, selectedKey, initialFamily)
    TemplateChooserBackHandler(chooser)
    AppScaffold(
        topBar = {
            StepTopBar(
                stringResource(Res.string.onboarding_template_title),
                OnboardingStep.Template,
                onBack = if (chooser.family != null || onBack != null) ({ if (!chooser.back()) onBack?.invoke() }) else null,
            )
        },
        offline = !LocalIsOnline.current,
        bottomBar = { chooser.current?.let { current -> BottomActionBar(stringResource(Res.string.onboarding_use_template), onClick = { onUse(current.key) }) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { TemplateChooserBody(chooser) { template -> preview(template) } }
    }
}

/** Placeholder page: the agent's name and line with a button in the template's default accent. */
@Composable
fun TemplatePlaceholder(template: TemplateSpec, agentName: String, agentLine: String) {
    AppCard(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(agentName, style = AgentProTheme.text.title, textAlign = TextAlign.Center)
            if (agentLine.isNotBlank()) {
                Text(agentLine, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Box(
                Modifier.background(template.defaultAccent.color(), AppShapes.md).padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                // Accent swatches all keep ≥ 4.5:1 with white text (verifyShared), as on the website.
                Text(stringResource(Res.string.cat_site_cta_default), style = AgentProTheme.text.label, color = Tokens.ColorLight.onPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Box(Modifier.size(Dimens.iconSmall).background(template.defaultAccent.color(), AppShapes.full))
                Text(template.defaultAccent.label(), style = AgentProTheme.text.bodyMuted)
            }
        }
    }
}

/**
 * Step 3: the suggested link with live validation and the availability check, a preview of the full
 * URL, then **Publish now** (primary) or **Later** (text button). Both need internet.
 */
@Composable
fun OnboardingLinkScreen(
    state: OnboardingUiState,
    onSlugChange: (String) -> Unit,
    onPublishNow: () -> Unit,
    onLater: () -> Unit,
    onBack: () -> Unit,
    /** The short address shown under the field (SITE_BASE_URL + /a/slug); injectable for screenshots. */
    linkDisplay: (String) -> String = { SiteLinks.display(it) },
) {
    val online = LocalIsOnline.current
    val enabled = online && state.canSubmitLink
    val (helper, error) = when (val s = state.slugStatus) {
        SlugStatus.Empty -> null to null
        is SlugStatus.Invalid -> null to s.error.message()
        SlugStatus.Checking -> stringResource(Res.string.onboarding_link_checking) to null
        SlugStatus.Available -> stringResource(Res.string.onboarding_link_available) to null
        SlugStatus.Taken -> null to stringResource(Res.string.onboarding_link_taken)
        is SlugStatus.Unchecked -> null to null
    }
    AppScaffold(
        topBar = { StepTopBar(stringResource(Res.string.onboarding_link_title), OnboardingStep.Link, onBack) },
        offline = !online,
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.onboarding_publish_now),
                onClick = onPublishNow,
                enabled = enabled,
                helperText = if (!online) stringResource(Res.string.needs_internet) else null,
                secondaryText = stringResource(Res.string.onboarding_later),
                onSecondary = onLater,
                secondaryEnabled = enabled,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            AppTextField(
                value = state.slug,
                onValueChange = onSlugChange,
                label = stringResource(Res.string.onboarding_link_label),
                error = error,
                helper = helper,
                enabled = !state.saving,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                inputModifier = Modifier.testTag(fieldTestTag(SLUG_FIELD)),
            )
            if (state.slug.isNotEmpty()) {
                Text(
                    stringResource(Res.string.onboarding_link_preview, linkDisplay(state.slug)),
                    style = AgentProTheme.text.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error?.let { ErrorLine(stringResource(it.message)) }
        }
    }
}
