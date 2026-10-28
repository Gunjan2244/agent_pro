package com.sledje.agentpro.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.designsystem.components.StepProgressBar
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
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
import com.sledje.agentpro.feature.website.ui.TemplateGrid
import com.sledje.agentpro.feature.website.ui.TemplatePageLoader
import com.sledje.agentpro.feature.website.ui.TemplatePreviewBody
import com.sledje.agentpro.feature.website.ui.rememberTemplatePageLoader
import com.sledje.agentpro.resources.template_grid_hint
import com.sledje.agentpro.resources.template_grid_title
import com.sledje.agentpro.resources.template_make_website
import com.sledje.agentpro.resources.template_see_others
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
import com.sledje.agentpro.resources.onboarding_details_intro
import com.sledje.agentpro.resources.onboarding_details_title
import com.sledje.agentpro.resources.form_add_photo
import com.sledje.agentpro.resources.onboarding_photo_intro
import com.sledje.agentpro.resources.onboarding_photo_title
import com.sledje.agentpro.resources.onboarding_site_pending
import com.sledje.agentpro.resources.onboarding_site_text
import com.sledje.agentpro.resources.onboarding_site_title
import com.sledje.agentpro.resources.onboarding_skip
import com.sledje.agentpro.resources.onboarding_mobile_intro
import com.sledje.agentpro.resources.onboarding_mobile_title
import com.sledje.agentpro.resources.ic_arrow_forward
import com.sledje.agentpro.resources.onboarding_template_hint
import com.sledje.agentpro.resources.onboarding_publish_now
import org.jetbrains.compose.resources.stringResource

private val STEPS = OnboardingStep.entries.size

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
 * Step 1: the mobile number. The name (from Google or Create account) shows as a card with the email and **Edit**
 * ([OnboardingUiState.contactSummary]); without one, the name field shows instead. Nothing else is asked here.
 */
@Composable
fun OnboardingMobileScreen(
    form: FormState,
    state: OnboardingUiState,
    onContinue: () -> Unit,
    onEditContact: () -> Unit = {},
) {
    val online = LocalIsOnline.current
    AppScaffold(
        topBar = { StepTopBar(stringResource(Res.string.onboarding_mobile_title), OnboardingStep.Mobile, onBack = null) },
        offline = !online,
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.onboarding_continue),
                onClick = onContinue,
                enabled = online && form.isValid && !state.saving,
                helperText = if (!online) stringResource(Res.string.needs_internet) else null,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (state.contactSummary) ContactSummary(form, state.email, enabled = !state.saving, onEdit = onEditContact)
            Text(stringResource(Res.string.onboarding_mobile_intro), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormRenderer(
                form,
                visibleKeys = OnboardingViewModel.MOBILE_STEP_KEYS,
                hiddenKeys = form.fields.map { it.key }.filterNot { it in OnboardingViewModel.MOBILE_STEP_KEYS }.toSet() +
                    if (state.contactSummary) OnboardingViewModel.CONTACT_KEYS else emptySet(),
                enabled = !state.saving,
                onDone = { if (online && form.isValid) onContinue() },
            )
            state.error?.let { ErrorLine(stringResource(it.message)) }
        }
    }
}

/** Step 1's name (and the account's email), with **Edit**. */
@Composable
private fun ContactSummary(form: FormState, email: String?, enabled: Boolean, onEdit: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(form.text(OnboardingViewModel.NAME), style = AgentProTheme.text.strong)
                if (!email.isNullOrBlank()) Text(email, style = AgentProTheme.text.bodyMuted)
            }
            TertiaryButton(stringResource(Res.string.crm_edit), onEdit, enabled = enabled)
        }
    }
}

/**
 * Step 2: "Choose your template", every template in the [TemplateGrid] (two per row, the third row peeking out, the
 * tiles' full-page pictures taking turns to scroll). A tap opens that template in step 3 ([onOpen]).
 *
 * @param loader the grid's picture loader; screenshots pass one that returns none.
 */
@Composable
fun OnboardingTemplateGridScreen(
    templates: List<TemplateSpec>,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    loader: TemplatePageLoader = rememberTemplatePageLoader(),
) {
    AppScaffold(
        topBar = { StepTopBar(stringResource(Res.string.template_grid_title), OnboardingStep.Intro, onBack) },
        offline = !LocalIsOnline.current,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(Res.string.template_grid_hint),
                style = AgentProTheme.text.bodyMuted,
                modifier = Modifier.padding(horizontal = Spacing.m).padding(top = Spacing.m),
            )
            TemplateGrid(templates, onOpen = onOpen, modifier = Modifier.weight(1f).fillMaxWidth(), loader = loader)
        }
    }
}

/**
 * Step 3: the template opened from step 2, full height ([TemplatePreviewBody]). The app passes [preview] = the live
 * render of the agent's seeded starter content in that template (SitePreview); before step 4 there are no companies,
 * so it uses the seed's default insurance types. The default placeholder shows the name in the template's accent
 * (screenshots). **Make website from this →** (the arrow nudges) takes it ([onUse]); **See other templates** and Back
 * return to the grid.
 */
@Composable
fun OnboardingTemplateScreen(
    template: TemplateSpec,
    agentName: String,
    agentLine: String,
    onUse: (String) -> Unit,
    onBack: () -> Unit,
    preview: @Composable (TemplateSpec) -> Unit = { TemplatePlaceholder(it, agentName, agentLine) },
) {
    AppScaffold(
        topBar = { StepTopBar(stringResource(template.name), OnboardingStep.Template, onBack) },
        offline = !LocalIsOnline.current,
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.template_make_website),
                onClick = { onUse(template.key) },
                trailingIcon = Res.drawable.ic_arrow_forward,
                nudgeTrailingIcon = true,
                secondaryText = stringResource(Res.string.template_see_others),
                onSecondary = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(Res.string.onboarding_template_hint),
                style = AgentProTheme.text.bodyMuted,
                modifier = Modifier.padding(horizontal = Spacing.m).padding(top = Spacing.m),
            )
            TemplatePreviewBody(template, Modifier.weight(1f)) { preview(template) }
        }
    }
}

/**
 * Step 4: the agent's work. First the required "Your companies" block ([companies]; docs/UX.md; Continue waits for
 * at least one company; each company carries its own agency code), then the city. No More details (DECISIONS #116).
 */
@Composable
fun OnboardingDetailsScreen(
    form: FormState,
    state: OnboardingUiState,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    /** The "Your companies" block's state; null hides the block (and its rule) for previews that predate it. */
    companies: CompaniesUiState? = null,
    companyActions: CompanyActions = CompanyActions.None,
) {
    val online = LocalIsOnline.current
    AppScaffold(
        topBar = { StepTopBar(stringResource(Res.string.onboarding_details_title), OnboardingStep.Details, onBack) },
        offline = !online,
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.onboarding_continue),
                onClick = onContinue,
                enabled = online && form.isValid && !state.saving && (companies == null || companies.satisfiesOnboarding),
                helperText = if (!online) stringResource(Res.string.needs_internet) else null,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(Res.string.onboarding_details_intro), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (companies != null) CompaniesBlock(companies, companyActions, requiredHint = true)
            FormRenderer(
                form,
                visibleKeys = OnboardingViewModel.DETAILS_VISIBLE_KEYS,
                hiddenKeys = OnboardingViewModel.MOBILE_STEP_KEYS + OnboardingViewModel.PHOTO_STEP_KEYS,
                enabled = !state.saving,
                onDone = { if (online && form.isValid && (companies == null || companies.satisfiesOnboarding)) onContinue() },
            )
            state.error?.let { ErrorLine(stringResource(it.message)) }
        }
    }
    if (companies != null) CompanySheets(companies, companyActions)
}

/**
 * Step 5: the agent's photo on its own screen, with the photo tips. With no photo the button is **Add photo** (the
 * picker) and **Skip** goes on without one; once one is uploaded it reads **Continue**. Back returns to step 4.
 */
@Composable
fun OnboardingPhotoScreen(
    form: FormState,
    state: OnboardingUiState,
    onPickPhoto: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val online = LocalIsOnline.current
    val hasPhoto = form.text(OnboardingViewModel.PHOTO).isNotBlank()
    val busy = state.saving || state.uploadingPhoto
    AppScaffold(
        topBar = { StepTopBar(stringResource(Res.string.onboarding_photo_title), OnboardingStep.Photo, onBack) },
        offline = !online,
        bottomBar = {
            BottomActionBar(
                text = stringResource(if (hasPhoto) Res.string.onboarding_continue else Res.string.form_add_photo),
                onClick = if (hasPhoto) onContinue else onPickPhoto,
                enabled = online && !busy,
                helperText = if (!online) stringResource(Res.string.needs_internet) else null,
                secondaryText = if (hasPhoto) null else stringResource(Res.string.onboarding_skip),
                onSecondary = onSkip,
                secondaryEnabled = !busy,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(Res.string.onboarding_photo_intro), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormRenderer(
                form,
                visibleKeys = OnboardingViewModel.PHOTO_STEP_KEYS,
                hiddenKeys = form.fields.map { it.key }.filterNot { it in OnboardingViewModel.PHOTO_STEP_KEYS }.toSet(),
                enabled = !busy,
                onPickImage = { if (online) onPickPhoto() },
                imagePreview = { url -> NetworkImage(url) },
            )
            if (state.uploadingPhoto) Text(stringResource(Res.string.website_photo_uploading), style = AgentProTheme.text.bodyMuted)
            state.error?.let { ErrorLine(stringResource(if (it == ImageUnreadable) Res.string.website_photo_unreadable else it.message)) }
        }
    }
}

/**
 * Step 6: the finished website before it is created: the agent's starter content in step 3's design, with the step-5
 * photo and step 4's companies, rendered full width so it scrolls like the real page. While a company is still
 * waiting for confirmation ([pendingCompanies]) a line says it shows on the live site once confirmed. **Continue**
 * opens step 7.
 *
 * @param preview the live render (SitePreview); screenshots pass a surface.
 */
@Composable
fun OnboardingPreviewScreen(
    pendingCompanies: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    AppScaffold(
        topBar = { StepTopBar(stringResource(Res.string.onboarding_site_title), OnboardingStep.Preview, onBack) },
        offline = !LocalIsOnline.current,
        bottomBar = { BottomActionBar(stringResource(Res.string.onboarding_continue), onClick = onContinue) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(Res.string.onboarding_site_text), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (pendingCompanies) Text(stringResource(Res.string.onboarding_site_pending), style = AgentProTheme.text.bodyMuted)
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .clip(AppShapes.lg)
                    .border(Dimens.hairline, AgentProTheme.colors.cardBorder, AppShapes.lg),
            ) { preview(Modifier.fillMaxSize()) }
        }
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
 * Step 7: the suggested link with live validation and the availability check, a preview of the full
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
