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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
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
import com.sledje.agentpro.resources.ic_web
import com.sledje.agentpro.resources.onboarding_intro_auto
import com.sledje.agentpro.resources.onboarding_intro_bar
import com.sledje.agentpro.resources.onboarding_intro_body
import com.sledje.agentpro.resources.onboarding_intro_title
import com.sledje.agentpro.resources.onboarding_template_hint
import com.sledje.agentpro.resources.onboarding_publish_now
import com.sledje.agentpro.resources.onboarding_template_title
import com.sledje.agentpro.resources.onboarding_use_template
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val STEPS = OnboardingStep.entries.size

/** Test tag key of the link field. */
const val SLUG_FIELD = "slug"

/** Test tag of step 2's Continue. */
const val ONBOARDING_INTRO_CONTINUE_TAG = "onboarding_intro_continue"

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

/** Seconds the template intro waits before it opens the designs by itself. */
const val ONBOARDING_INTRO_SECONDS = 5

/**
 * Step 2: a short intro, "Let's select a template for your website", with **Continue →** (the arrow nudges back and
 * forth). With [autoAdvance] it opens step 3 by itself after [ONBOARDING_INTRO_SECONDS], counting down under the
 * button; the count restarts whenever the agent comes back to it.
 */
@Composable
fun OnboardingIntroScreen(
    templateCount: Int,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    autoAdvance: Boolean = true,
) {
    var secondsLeft by remember { mutableIntStateOf(ONBOARDING_INTRO_SECONDS) }
    if (autoAdvance) {
        LaunchedEffect(Unit) {
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft--
            }
            onContinue()
        }
    }
    AppScaffold(
        topBar = { StepTopBar(stringResource(Res.string.onboarding_intro_bar), OnboardingStep.Intro, onBack) },
        offline = !LocalIsOnline.current,
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.onboarding_continue),
                onClick = onContinue,
                modifier = Modifier.testTag(ONBOARDING_INTRO_CONTINUE_TAG),
                helperText = if (autoAdvance) stringResource(Res.string.onboarding_intro_auto, secondsLeft) else null,
                trailingIcon = Res.drawable.ic_arrow_forward,
                nudgeTrailingIcon = true,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(Dimens.imageSlot).background(MaterialTheme.colorScheme.primaryContainer, AppShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.ic_web), null, Modifier.size(Dimens.icon), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(
                stringResource(Res.string.onboarding_intro_title),
                style = AgentProTheme.text.title,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.onboarding_intro_body, templateCount),
                style = AgentProTheme.text.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Step 3: a full-height pager of the five templates with the template name below and **Use this
 * template**. The app passes [preview] = the live render of the agent's seeded starter content in that template
 * (SitePreview, the bundled web renderer); before step 4 there are no companies, so it uses the seed's default
 * insurance types. The default placeholder shows the name in the template's accent (screenshots). Back returns to
 * step 2.
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
) {
    val chooser = rememberTemplateChooser(templates, selectedKey)
    AppScaffold(
        topBar = { StepTopBar(stringResource(Res.string.onboarding_template_title), OnboardingStep.Template, onBack) },
        offline = !LocalIsOnline.current,
        bottomBar = { chooser.current?.let { current -> BottomActionBar(stringResource(Res.string.onboarding_use_template), onClick = { onUse(current.key) }) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(Res.string.onboarding_template_hint),
                style = AgentProTheme.text.bodyMuted,
                modifier = Modifier.padding(horizontal = Spacing.m).padding(top = Spacing.m),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) { TemplateChooserBody(chooser) { template -> preview(template) } }
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
