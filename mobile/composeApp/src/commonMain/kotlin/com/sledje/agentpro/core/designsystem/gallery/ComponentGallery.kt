package com.sledje.agentpro.core.designsystem.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.catalog.PlanStatus
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.catalog.DomainStatus
import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.ChartData
import com.sledje.agentpro.core.designsystem.ChartKind
import com.sledje.agentpro.core.designsystem.ChartSeries
import com.sledje.agentpro.core.designsystem.LocalAppLocale
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.AppFab
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppSnackbar
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.CardSkeleton
import com.sledje.agentpro.core.designsystem.components.ChartCard
import com.sledje.agentpro.core.designsystem.components.ChoiceChips
import com.sledje.agentpro.core.designsystem.components.ChoiceOption
import com.sledje.agentpro.core.designsystem.components.ConfirmDialog
import com.sledje.agentpro.core.designsystem.components.DateField
import com.sledje.agentpro.core.designsystem.components.DestructiveButton
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.ErrorState
import com.sledje.agentpro.core.designsystem.components.FilterChipRow
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.KpiDelta
import com.sledje.agentpro.core.designsystem.components.KpiTile
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.ListRowSkeleton
import com.sledje.agentpro.core.designsystem.components.MoneyField
import com.sledje.agentpro.core.designsystem.components.MoneyText
import com.sledje.agentpro.core.designsystem.components.NavRow
import com.sledje.agentpro.core.designsystem.components.OfflineBanner
import com.sledje.agentpro.core.designsystem.components.OverflowItem
import com.sledje.agentpro.core.designsystem.components.PriceLine
import com.sledje.agentpro.core.designsystem.components.PriceSummary
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.ProgressStepper
import com.sledje.agentpro.core.designsystem.components.SearchField
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.designsystem.components.SectionHeader
import com.sledje.agentpro.core.designsystem.components.SelectField
import com.sledje.agentpro.core.designsystem.components.StatusPill
import com.sledje.agentpro.core.designsystem.components.Step
import com.sledje.agentpro.core.designsystem.components.StepStatus
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.designsystem.components.ToggleRow
import com.sledje.agentpro.core.designsystem.components.TopBarAction
import com.sledje.agentpro.core.designsystem.components.UrgencyChip
import com.sledje.agentpro.core.forms.FormRenderer
import com.sledje.agentpro.core.forms.rememberFormState
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.Money
import com.sledje.agentpro.core.format.rememberMonthNames
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_add
import com.sledje.agentpro.resources.action_back
import com.sledje.agentpro.resources.action_discard
import com.sledje.agentpro.resources.action_retry
import com.sledje.agentpro.resources.action_undo
import com.sledje.agentpro.resources.back_online
import com.sledje.agentpro.resources.chart_empty
import com.sledje.agentpro.resources.discard_message
import com.sledje.agentpro.resources.discard_title
import com.sledje.agentpro.resources.error_generic
import com.sledje.agentpro.resources.gallery_dark
import com.sledje.agentpro.resources.gallery_hindi
import com.sledje.agentpro.resources.gallery_large_text
import com.sledje.agentpro.resources.gallery_title
import com.sledje.agentpro.resources.ic_add
import com.sledje.agentpro.resources.ic_inbox
import com.sledje.agentpro.resources.ic_search
import com.sledje.agentpro.resources.needs_internet
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

/*
 * Debug-only component gallery (docs/DESIGN_SYSTEM.md, Components): every component in light, dark,
 * EN, HI and at 200% font scale. It is the baseline of the Roborazzi screenshot tests
 * (composeApp/src/androidHostTest). It is reachable only from the androidApp debug source set
 * (GalleryActivity); release builds have no entry point to it.
 *
 * UI copy comes from the real string resources and catalog labels. The names, amounts and dates
 * below are sample agent data (never translated in the app either).
 */

private object Samples {
    const val NAME_LATIN = "Ramesh Kumar"
    const val NAME_DEVANAGARI = "सुनीता शर्मा"
    const val PHONE = "+91 98765 43210"
    const val PREMIUM = 125_000.0
    const val BIG_PREMIUM = 12_34_567.0
    const val PLAN_PRICE = 499.0
    const val DOMAIN_PRICE = 799.0
    const val DOMAIN_FEE = 99.0
    const val DOMAIN = "rameshkumar.in"
    val TODAY = LocalDate(2026, 10, 15)
}

/** The gallery's sections; each is captured as its own screenshot. */
enum class GallerySection { Buttons, Bars, Lists, Status, Data, Charts, States, Inputs, Form, Feedback }

/**
 * Applies a gallery variant: [hindi] forces the `hi` locale (strings, Noto Sans Devanagari, +2sp line
 * heights), [largeText] sets a 200% font scale, [dark] the dark theme.
 */
@Composable
fun GalleryFrame(dark: Boolean, hindi: Boolean, largeText: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides (if (hindi) AppLocale.Hi.key else null)) {
        key(hindi) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 2f else 1f)) {
                AgentProTheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.background, content = content)
                }
            }
        }
    }
}

/** One section of the gallery, rendered on its own (screenshot tests use this). */
@Composable
fun GallerySectionContent(section: GallerySection, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag("gallery_${section.name}").padding(vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(section.name, style = AgentProTheme.text.title, modifier = Modifier.padding(horizontal = Spacing.m))
        when (section) {
            GallerySection.Buttons -> ButtonsSection()
            GallerySection.Bars -> BarsSection()
            GallerySection.Lists -> ListsSection()
            GallerySection.Status -> StatusSection()
            GallerySection.Data -> DataSection()
            GallerySection.Charts -> ChartsSection()
            GallerySection.States -> StatesSection()
            GallerySection.Inputs -> InputsSection()
            GallerySection.Form -> FormSection()
            GallerySection.Feedback -> FeedbackSection()
        }
    }
}

/** The in-app gallery screen with Dark / Hindi / 200% switches. */
@Composable
fun ComponentGalleryScreen(onBack: () -> Unit) {
    var dark by rememberSaveable { mutableStateOf(false) }
    var hindi by rememberSaveable { mutableStateOf(false) }
    var largeText by rememberSaveable { mutableStateOf(false) }
    GalleryFrame(dark, hindi, largeText) {
        AppScaffold(topBar = { AppTopBar(stringResource(Res.string.gallery_title), onBack = onBack) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(Modifier.padding(horizontal = Spacing.m)) {
                        ToggleRow(stringResource(Res.string.gallery_dark), dark, { dark = it })
                        ToggleRow(stringResource(Res.string.gallery_hindi), hindi, { hindi = it })
                        ToggleRow(stringResource(Res.string.gallery_large_text), largeText, { largeText = it })
                    }
                }
                items(GallerySection.entries) { section ->
                    HairlineDivider()
                    GallerySectionContent(section)
                }
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) { content() }
}

@Composable
private fun ButtonsSection() = Padded {
    PrimaryButton(stringResource(Res.string.action_add), {}, Modifier.fillMaxWidth(), icon = Res.drawable.ic_add)
    PrimaryButton(stringResource(Res.string.action_add), {}, Modifier.fillMaxWidth(), enabled = false)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SecondaryButton(stringResource(Res.string.action_retry), {})
        TertiaryButton(stringResource(Res.string.action_undo), {})
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        DestructiveButton(stringResource(Res.string.action_discard), {})
        DestructiveButton(stringResource(Res.string.action_discard), {}, filled = true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AppFab(Res.drawable.ic_add, stringResource(Res.string.action_add), {})
        AppFab(Res.drawable.ic_add, stringResource(Res.string.action_add), {}, text = stringResource(Res.string.action_add))
    }
}

@Composable
private fun BarsSection() {
    val back = stringResource(Res.string.action_back)
    AppTopBar(
        title = FeatureId.Enquiries.label(),
        onBack = {},
        actions = listOf(TopBarAction(Res.drawable.ic_search, back) {}),
        overflow = listOf(OverflowItem(back) {}),
        scrolled = true,
    )
    OfflineBanner()
    SearchField("", {}, {}, Modifier.padding(horizontal = Spacing.m))
    FilterChipRow(
        entries = EnquiryStatus.active,
        selected = EnquiryStatus.New,
        onSelect = {},
        counts = mapOf(EnquiryStatus.New to 3, EnquiryStatus.FollowUp to 2),
        allCount = 12,
    )
}

@Composable
private fun ListsSection() = Column {
    SectionHeader(FeatureId.Dues.label(), onSeeAll = {})
    ListRow(
        title = Samples.NAME_LATIN,
        supporting = Samples.PHONE,
        leading = ListLeading.Avatar(Samples.NAME_LATIN),
        onClick = {},
        trailing = { StatusPill(EnquiryStatus.FollowUp) },
    )
    HairlineDivider()
    ListRow(
        title = Samples.NAME_DEVANAGARI,
        supporting = Money.format(Samples.PREMIUM),
        leading = ListLeading.Avatar(Samples.NAME_DEVANAGARI),
        selected = true,
        onClick = {},
        trailing = { UrgencyChip(Samples.TODAY.plus(DatePeriod(days = 5)), today = Samples.TODAY) },
    )
    HairlineDivider()
    ListRow(title = FeatureId.Insights.label(), leading = ListLeading.Icon(FeatureId.Insights.icon), onClick = {})
    HairlineDivider()
    NavRow(FeatureId.CustomDomains.label(), onClick = {}, supporting = Samples.DOMAIN, icon = FeatureId.CustomDomains.icon)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSection() = Padded {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        EnquiryStatus.active.forEach { StatusPill(it) }
        PolicyStatus.active.forEach { StatusPill(it) }
        PlanStatus.active.forEach { StatusPill(it) }
        DomainStatus.active.forEach { StatusPill(it) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        listOf(-3, 0, 5, 20, 90).forEach { days ->
            UrgencyChip(Samples.TODAY.plus(DatePeriod(days = days)), today = Samples.TODAY)
        }
    }
}

@Composable
private fun DataSection() = Padded {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        KpiTile(FeatureId.Enquiries.label(), "24", Modifier.weight(1f), KpiDelta(up = true, text = "+6"))
        KpiTile(FeatureId.Dues.label(), Money.compact(Samples.BIG_PREMIUM), Modifier.weight(1f), KpiDelta(up = false, text = Money.compact(Samples.PREMIUM)))
    }
    MoneyText(Samples.BIG_PREMIUM)
    MoneyText(Samples.BIG_PREMIUM, compact = true)
    PriceSummary(
        lines = listOf(PriceLine(Samples.DOMAIN, Samples.DOMAIN_PRICE), PriceLine(FeatureId.CustomDomains.label(), Samples.DOMAIN_FEE)),
        total = Samples.DOMAIN_PRICE + Samples.DOMAIN_FEE,
        renewalNote = PlanStatus.Active.label() + " · " + Money.format(Samples.PLAN_PRICE),
    )
}

@Composable
private fun ChartsSection() = Padded {
    val months = rememberMonthNames()
    val weeks = (0 until 6).map { Dates.formatDayMonth(Samples.TODAY.plus(DatePeriod(days = 7 * (it - 5))), months) }
    ChartCard(
        title = FeatureId.Enquiries.label(),
        takeaway = "+6",
        data = ChartData(ChartKind.Column, weeks, listOf(ChartSeries(FeatureId.Enquiries.label(), listOf(4.0, 6.0, 3.0, 8.0, 5.0, 9.0)))),
    )
    ChartCard(
        title = FeatureId.Dues.label(),
        takeaway = Money.compact(Samples.BIG_PREMIUM),
        data = ChartData(
            ChartKind.Line,
            weeks,
            listOf(
                ChartSeries(PolicyStatus.Active.label(), listOf(120_000.0, 90_000.0, 150_000.0, 110_000.0, 170_000.0, 140_000.0)),
                ChartSeries(FeatureId.Dues.label(), listOf(150_000.0, 130_000.0, 160_000.0, 150_000.0, 180_000.0, 200_000.0)),
            ),
            money = true,
        ),
    )
    ChartCard(
        title = FeatureId.Enquiries.label(),
        takeaway = EnquiryStatus.Converted.label(),
        data = ChartData(
            ChartKind.Ordinal,
            EnquiryStatus.active.take(4).map { it.label() },
            listOf(ChartSeries(FeatureId.Enquiries.label(), listOf(24.0, 15.0, 8.0, 5.0))),
        ),
    )
    ChartCard(
        title = FeatureId.Insights.label(),
        takeaway = stringResource(Res.string.chart_empty),
        data = ChartData(ChartKind.Column, emptyList(), emptyList()),
    )
}

@Composable
private fun StatesSection() {
    ListRowSkeleton()
    Padded { CardSkeleton() }
    Column(Modifier.fillMaxWidth().height(Dimensions.stateHeight)) {
        EmptyState(Res.drawable.ic_inbox, stringResource(Res.string.chart_empty), actionLabel = stringResource(Res.string.action_add), onAction = {})
    }
    Column(Modifier.fillMaxWidth().height(Dimensions.stateHeight)) {
        ErrorState(stringResource(Res.string.error_generic), onRetry = {})
    }
}

private object Dimensions {
    val stateHeight = com.sledje.agentpro.core.designsystem.Dimens.skeletonBlock * 3
}

@Composable
private fun InputsSection() = Padded {
    var money by remember { mutableStateOf("125000") }
    MoneyField(money, { money = it }, FeatureId.Dues.label())
    DateField(Samples.TODAY, {}, FeatureId.Reminders.label())
    var interest by remember { mutableStateOf<String?>(null) }
    val interests = com.sledje.agentpro.core.catalog.Interest.active.map { ChoiceOption(it.key, it.label(), it.icon) }
    SelectField(interests, interest, { interest = it }, FeatureId.Policies.label())
    var chosen by remember { mutableStateOf(setOf("term")) }
    ChoiceChips(interests.take(4), chosen, { k -> chosen = if (k in chosen) chosen - k else chosen + k })
    var on by remember { mutableStateOf(true) }
    ToggleRow(FeatureId.Reminders.label(), on, { on = it })
}

@Composable
private fun FormSection() = Padded {
    val hero = SiteSchema.sections.first { it.key == "hero" }
    val contact = SiteSchema.sections.first { it.key == "contact" }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(stringResource(hero.label), style = AgentProTheme.text.strong)
            FormRenderer(rememberFormState(hero.fields))
        }
    }
    val state = rememberFormState(contact.fields, mapOf("phone" to "98765", "email" to "ramesh@"))
    // Show the inline errors as they look after Save.
    LaunchedEffect(state) { state.validateAll() }
    FormRenderer(state)
}

@Composable
private fun FeedbackSection() {
    Padded {
        AppSnackbar(stringResource(Res.string.back_online))
        AppSnackbar(EnquiryStatus.Closed.label(), actionLabel = stringResource(Res.string.action_undo))
        ProgressStepper(
            listOf(
                Step(DomainStatus.PendingDns.label(), StepStatus.Done),
                Step(DomainStatus.Verifying.label(), StepStatus.Current, Samples.DOMAIN),
                Step(DomainStatus.Active.label(), StepStatus.Pending),
                Step(DomainStatus.Failed.label(), StepStatus.Failed),
            ),
        )
        var confirm by remember { mutableStateOf(false) }
        SecondaryButton(stringResource(Res.string.discard_title), { confirm = true })
        if (confirm) {
            ConfirmDialog(
                title = stringResource(Res.string.discard_title),
                message = stringResource(Res.string.discard_message),
                confirmLabel = stringResource(Res.string.action_discard),
                onConfirm = { confirm = false },
                onDismiss = { confirm = false },
            )
        }
    }
    BottomActionBar(stringResource(Res.string.action_add), {}, enabled = false, helperText = stringResource(Res.string.needs_internet))
}
