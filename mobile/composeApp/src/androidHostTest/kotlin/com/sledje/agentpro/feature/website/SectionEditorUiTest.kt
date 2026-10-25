package com.sledje.agentpro.feature.website

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onFirst
import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.forms.fieldTestTag
import com.sledje.agentpro.feature.website.ui.SectionEditorUiState
import kotlinx.serialization.json.JsonArray
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.feature.website.ui.ContentScreen
import com.sledje.agentpro.feature.website.ui.ContentViewModel
import com.sledje.agentpro.feature.website.ui.SectionEditorScreen
import com.sledje.agentpro.feature.website.ui.SectionEditorViewModel
import com.sledje.agentpro.feature.website.ui.sectionRowTag
import com.sledje.agentpro.feature.website.ui.sectionSwitchTag
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.FakeWebsite
import com.sledje.agentpro.testing.testSite
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The section editor and the Content list on Robolectric (docs/TESTING.md, Compose UI tests). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SectionEditorUiTest {
    private val sites = FakeSiteRepository(
        testSite().copy(draftContent = buildJsonObject { put("hero", buildJsonObject { put("headline", "Ramesh Kumar") }) }),
    )
    private val website = FakeWebsite()

    @Test
    fun heroEditorValidatesThenSavesTheDraft() = runComposeUiTest {
        val vm = SectionEditorViewModel(SiteSchema.section("hero")!!, sites, website.services.imageUploader)
        setContent {
            AgentProTheme(darkTheme = false) {
                val state by vm.state.collectAsState()
                SectionEditorScreen(SiteSchema.section("hero")!!, if (state.loading) null else vm.form, state, remember { SnackbarHostState() }, {}, {}, vm::save)
            }
        }
        waitForIdle()
        onNodeWithTag(fieldTestTag("headline")).performTextClearance()
        onNodeWithText("Save").performClick()
        waitForIdle()
        onAllNodesWithText("Required").assertCountEquals(1)
        assertTrue(sites.drafts.isEmpty(), "an invalid form isn't saved")

        onNodeWithTag(fieldTestTag("headline")).performTextInput("Ramesh Kumar, LIC advisor")
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals("Ramesh Kumar, LIC advisor", sites.drafts.single()["hero"]!!.jsonObject["headline"]!!.jsonPrimitive.content)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun saveNeedsInternet() = runComposeUiTest {
        val vm = SectionEditorViewModel(SiteSchema.section("hero")!!, sites, website.services.imageUploader)
        setContent {
            CompositionLocalProvider(LocalIsOnline provides false) {
                AgentProTheme(darkTheme = false) {
                    val state by vm.state.collectAsState()
                    SectionEditorScreen(SiteSchema.section("hero")!!, if (state.loading) null else vm.form, state, remember { SnackbarHostState() }, {}, {}, vm::save)
                }
            }
        }
        waitForIdle()
        onNode(hasText("Save") and hasClickAction()).assertIsNotEnabled()
        onAllNodesWithText("Needs internet").onFirst().assertExists()
    }

    @Test
    fun contentSwitchHidesASectionAndPinnedSectionsStayOn() = runComposeUiTest {
        val vm = ContentViewModel(sites)
        setContent {
            AgentProTheme(darkTheme = false) {
                val state by vm.state.collectAsState()
                ContentScreen(state, emptySet(), remember { SnackbarHostState() }, {}, {}, { row, visible -> vm.setHidden(row.section, !visible) }, vm::startReorder, vm::move, vm::finishReorder)
            }
        }
        waitForIdle()
        onNodeWithTag(sectionSwitchTag("hero")).assertIsOn().assertIsNotEnabled()
        onNodeWithTag(sectionSwitchTag("about")).assertIsOn().assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(sectionSwitchTag("about")).assertIsOff()
        val hidden = sites.drafts.single()[SiteContent.HIDDEN]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("about"), hidden)
    }

    @Test
    fun portalRowShowsItsOwnNoteNotTheCompaniesOne() = runComposeUiTest {
        val vm = ContentViewModel(sites)
        setContent {
            AgentProTheme(darkTheme = false) {
                val state by vm.state.collectAsState()
                ContentScreen(state, emptySet(), remember { SnackbarHostState() }, {}, {}, { _, _ -> }, vm::startReorder, vm::move, vm::finishReorder)
            }
        }
        waitForIdle()
        onNodeWithTag(sectionRowTag("portal")).assertExists()
        onNode(hasText("Tells visitors your customers can sign in", substring = true)).assertExists()
        onNodeWithTag(sectionRowTag("companies")).assertExists()
        onNode(hasText("Shows the companies an admin has approved", substring = true)).assertExists()
    }

    @Test
    fun offerFieldsShowSummariesAndOpenThePicker() = runComposeUiTest {
        val offers = SiteSchema.section("offers")!!
        val opened = mutableListOf<String>()
        val ref = buildJsonObject { put("source", "company"); put("id", "o1") }
        val form = FormState(offers.fields, mapOf("items" to JsonArray(listOf(ref, ref))))
        setContent {
            AgentProTheme(darkTheme = false) {
                SectionEditorScreen(offers, form, SectionEditorUiState(loading = false), remember { SnackbarHostState() }, {}, {}, {}, onOfferField = { opened += it })
            }
        }
        waitForIdle()
        onNodeWithText("2 on your site").assertExists()
        onNodeWithTag(fieldTestTag("items")).performClick()
        assertEquals(listOf("items"), opened)
    }

    @Test
    fun emptyOfferListSaysAutomaticAndNoCallbackDisablesTheRow() = runComposeUiTest {
        val offers = SiteSchema.section("offers")!!
        val form = FormState(offers.fields)
        setContent {
            AgentProTheme(darkTheme = false) {
                SectionEditorScreen(offers, form, SectionEditorUiState(loading = false), remember { SnackbarHostState() }, {}, {}, {})
            }
        }
        waitForIdle()
        onNodeWithText("Automatic: your companies' live offers").assertExists()
    }

    @Test
    fun heroFeaturedOfferSummaryAndKey() = runComposeUiTest {
        val hero = SiteSchema.section("hero")!!
        val opened = mutableListOf<String>()
        val form = FormState(hero.fields)
        setContent {
            AgentProTheme(darkTheme = false) {
                SectionEditorScreen(hero, form, SectionEditorUiState(loading = false), remember { SnackbarHostState() }, {}, {}, {}, onOfferField = { opened += it })
            }
        }
        waitForIdle()
        onNodeWithTag(fieldTestTag("featuredOffer")).performScrollTo().assertExists()
        onNodeWithText("None").assertExists()
        onNodeWithTag(fieldTestTag("featuredOffer")).performClick()
        assertEquals(listOf("featuredOffer"), opened)
        form.set("featuredOffer", buildJsonObject { put("source", "agent"); put("id", "x") })
        waitForIdle()
        onNodeWithText("Chosen").assertExists()
    }

    @Test
    fun contentListIncludesOffers() = runComposeUiTest {
        assertTrue(SiteContent.sections.any { it.key == "offers" })
    }
}
