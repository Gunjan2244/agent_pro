package com.sledje.agentpro.feature.website

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import com.sledje.agentpro.core.catalog.AccentSwatch
import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.website.data.PublishError
import com.sledje.agentpro.feature.website.data.PublishIssues
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.feature.website.data.SiteLinks
import com.sledje.agentpro.feature.website.data.SiteStatsAggregator
import com.sledje.agentpro.feature.website.data.DayViews
import com.sledje.agentpro.feature.website.data.StatsPeriod
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.feature.website.ui.AppearanceScreen
import com.sledje.agentpro.feature.website.ui.AppearanceUiState
import com.sledje.agentpro.feature.website.ui.ContentScreen
import com.sledje.agentpro.feature.website.ui.ContentUiState
import com.sledje.agentpro.feature.website.ui.ServicesMatchContent
import com.sledje.agentpro.feature.website.ui.ServicesMatchUiState
import com.sledje.agentpro.feature.website.ui.ServicesMatchViewModel
import com.sledje.agentpro.feature.website.data.ServicesUpdate
import com.sledje.agentpro.resources.website_match_title
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.sledje.agentpro.feature.website.ui.HubRow
import com.sledje.agentpro.feature.website.ui.LinkScreen
import com.sledje.agentpro.feature.website.ui.LinkUiState
import com.sledje.agentpro.feature.website.ui.SectionEditorScreen
import com.sledje.agentpro.feature.website.ui.TemplatePickerScreen
import com.sledje.agentpro.feature.website.ui.TemplatePreviewScreen
import com.sledje.agentpro.feature.website.ui.SectionEditorUiState
import com.sledje.agentpro.feature.website.ui.SectionRow
import com.sledje.agentpro.feature.website.ui.StatsScreen
import com.sledje.agentpro.feature.website.ui.WebsiteHubScreen
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.ic_bar_chart
import com.sledje.agentpro.resources.ic_language
import com.sledje.agentpro.resources.ic_public
import com.sledje.agentpro.resources.ic_web
import com.sledje.agentpro.resources.website_hub_appearance
import com.sledje.agentpro.resources.website_hub_appearance_summary
import com.sledje.agentpro.resources.website_hub_content
import com.sledje.agentpro.resources.website_hub_content_summary
import com.sledje.agentpro.resources.website_hub_link
import com.sledje.agentpro.resources.website_hub_stats
import com.sledje.agentpro.resources.website_hub_stats_summary
import com.sledje.agentpro.resources.cat_template_classic_name
import com.sledje.agentpro.resources.cat_swatch_indigo
import com.sledje.agentpro.testing.testSite
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/**
 * Roborazzi baselines of the Website hub (Phase 8; docs/TESTING.md): the hub, Content, a section editor,
 * Appearance, Link + QR, Stats and a refused publish (Content with its fixes), in light/dark × EN/HI.
 * The site preview (WebView) can't render under Robolectric; its renderSite JSON is unit-tested instead.
 * Images: composeApp/src/androidHostTest/screenshots/<variant>/website/<Screen>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class WebsiteScreenshotTest {
    private val base = "https://agentpro.site"
    private val content: JsonObject = buildJsonObject {
        put("hero", buildJsonObject { put("headline", "Ramesh Kumar"); put("subheadline", "Life insurance and retirement plans for families in Pune") })
        put("about", buildJsonObject { put("bio", "Helping families plan for 18 years"); put("yearsExperience", 18) })
        put("services", buildJsonObject { put("offered", buildJsonArray { add("term"); add("retirement") }) })
        put("faq", buildJsonObject { put("items", buildJsonArray { add(buildJsonObject { put("question", "Q"); put("answer", "A") }) }) })
        put("contact", buildJsonObject { put("phone", "+919876543210") })
        put(SiteContent.HIDDEN, buildJsonArray { add("testimonials") })
    }
    private val site = testSite().copy(draftContent = content, accent = AccentSwatch.Indigo, isPublished = true, publishedContent = JsonObject(emptyMap()))
    private val rows = SiteContent.orderedSections(content).map { SectionRow(it, SiteContent.isHidden(content, it), SiteContent.summary(content, it)) }
    private val today = LocalDate(2026, 10, 16)
    private val stats = SiteStatsAggregator.aggregate(
        listOf(12, 30, 18, 0, 25, 41, 34).mapIndexed { i, v -> DayViews(today.minus(DatePeriod(days = 6 - i)), v) },
        enquiryDays = listOf(today, today, today.minus(DatePeriod(days = 2))),
        period = StatsPeriod.Week,
        today = today,
    )

    @Composable
    private fun hubRows(): List<HubRow> = listOf(
        HubRow("content", stringResource(Res.string.website_hub_content), stringResource(Res.string.website_hub_content_summary, 7), Res.drawable.ic_web) {},
        HubRow(
            "appearance",
            stringResource(Res.string.website_hub_appearance),
            stringResource(Res.string.website_hub_appearance_summary, stringResource(Res.string.cat_template_classic_name), stringResource(Res.string.cat_swatch_indigo)),
            Res.drawable.ic_language,
        ) {},
        HubRow("link", stringResource(Res.string.website_hub_link), SiteLinks.display(site.slug, base), Res.drawable.ic_public) {},
        HubRow("stats", stringResource(Res.string.website_hub_stats), stringResource(Res.string.website_hub_stats_summary, stats.views), Res.drawable.ic_bar_chart) {},
    )

    private fun screens(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "WebsiteHub" to {
            WebsiteHubScreen(
                site = UiState.Content(site), display = { SiteLinks.display(it.slug, base) }, rows = hubRows(), publishing = false,
                snackbar = remember { SnackbarHostState() }, onShare = {}, onCopy = {}, onOpen = {}, onAddToHome = {}, onShowQr = {}, onPreview = {}, onPublish = {}, onRetry = {},
            )
        },
        "WebsiteContent" to {
            ContentScreen(ContentUiState(loading = false, rows = rows), emptySet(), remember { SnackbarHostState() }, {}, {}, { _, _ -> }, {}, { _, _ -> }, {})
        },
        "WebsiteSectionEditor" to {
            val hero = SiteSchema.section("hero")!!
            val form = remember { FormState(hero.fields, SiteContent.sectionValues(content, hero)) }
            SectionEditorScreen(hero, form, SectionEditorUiState(loading = false), remember { SnackbarHostState() }, {}, {}, {})
        },
        "WebsiteAppearance" to {
            AppearanceScreen(AppearanceUiState(site = site), remember { SnackbarHostState() }, {}, {}, {}, {})
        },
        // Website → Appearance → Template: the grid of the five templates, the current one marked (placeholders, still);
        // then one template's preview, a surface standing in for the live render.
        "WebsiteTemplatePicker" to {
            TemplatePickerScreen(Templates.all, "spotlight", onBack = {}, onOpen = {}, loader = { emptyList() })
        },
        "WebsiteTemplatePreview" to {
            TemplatePreviewScreen(Templates.fromKey("guide")!!, saving = false, onBack = {}, onUse = {}) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
            }
        },
        "WebsiteLinkQr" to {
            LinkScreen(LinkUiState(site = site), { SiteLinks.url(it, base) }, { SiteLinks.display(it, base) }, remember { SnackbarHostState() }, {}, {}, {}, {})
        },
        "WebsiteStats" to {
            StatsScreen(StatsPeriod.Week, stats, onBack = {}, onPeriod = {})
        },
        "WebsitePublishFixes" to {
            val issues = PublishIssues.resolve(listOf(PublishError("hero", "headline", null, "required"), PublishError("faq", "answer", 0, "maxLength")))
            ContentScreen(
                ContentUiState(loading = false, rows = rows), issues.map { it.section.key }.toSet(), remember { SnackbarHostState() },
                {}, {}, { _, _ -> }, {}, { _, _ -> }, {},
            )
        },
        // DECISIONS #93: the hint while the services don't match the agent's insurance types, and the update sheet.
        "WebsiteContentServicesHint" to {
            ContentScreen(ContentUiState(loading = false, rows = rows), emptySet(), remember { SnackbarHostState() }, {}, {}, { _, _ -> }, {}, { _, _ -> }, {}, servicesMismatch = true)
        },
        "WebsiteServicesMatch" to {
            val state = remember {
                ServicesMatchUiState(
                    update = ServicesUpdate(add = listOf("motor", "travel"), remove = listOf("child", "pension")), sheetOpen = true,
                    removeForm = FormState(listOf(ServicesMatchViewModel.removeField(listOf("child", "pension")))),
                )
            }
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
                Text(stringResource(Res.string.website_match_title), style = AgentProTheme.text.title, modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s))
                ServicesMatchContent(state, {}, {})
            }
        },
        "WebsiteSectionIssues" to {
            val hero = SiteSchema.section("hero")!!
            val form = remember { FormState(hero.fields, emptyMap()).also { it.validateAll() } }
            val issues = PublishIssues.resolve(listOf(PublishError("hero", "headline", null, "required")))
            SectionEditorScreen(hero, form, SectionEditorUiState(loading = false, issues = issues), remember { SnackbarHostState() }, {}, {}, {})
        },
    )

    private fun capture(variant: String, dark: Boolean, hindi: Boolean) = runComposeUiTest {
        val screens = screens()
        var index by mutableStateOf(0)
        setContent {
            CompositionLocalProvider(LocalIsOnline provides true) {
                GalleryFrame(dark = dark, hindi = hindi, largeText = false) {
                    key(index) { screens[index].second() }
                }
            }
        }
        screens.forEachIndexed { i, (name, _) ->
            index = i
            waitForIdle()
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/website/$name.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}
