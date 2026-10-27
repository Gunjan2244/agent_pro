package com.sledje.agentpro.feature.offers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.offers.data.Company
import com.sledje.agentpro.feature.offers.ui.CompaniesUiState
import com.sledje.agentpro.feature.offers.ui.CompaniesViewModel
import com.sledje.agentpro.feature.offers.ui.CompanyActions
import com.sledje.agentpro.feature.offers.ui.CompanyFormContent
import com.sledje.agentpro.feature.offers.ui.CompanyPickerContent
import com.sledje.agentpro.feature.offers.ui.CompanySheet
import com.sledje.agentpro.feature.offers.ui.CompanyTarget
import com.sledje.agentpro.feature.onboarding.ui.OnboardingDetailsScreen
import com.sledje.agentpro.feature.onboarding.ui.OnboardingUiState
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import com.sledje.agentpro.feature.settings.ProfileScreen
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.company_picker_title
import com.sledje.agentpro.testing.testCompany
import com.sledje.agentpro.testing.testMembership
import org.jetbrains.compose.resources.stringResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of "Your companies" (docs/TESTING.md; DECISIONS #89): onboarding step 1 with the block (empty and
 * with several statuses), the picker sheet (short list, long list with search, loading error), the add form (empty with
 * errors showing, filled) and Settings → Profile with every status, in light/dark x EN/HI.
 * Images: composeApp/src/androidHostTest/screenshots/<variant>/offers/<Name>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class CompaniesScreenshotTest {
    private val alpha = testCompany("c1", "Alpha Insurance", "अल्फा बीमा")
    private val beta = testCompany("c2", "Beta Life", "बीटा लाइफ")

    private val allStatuses = listOf(
        testMembership("m1", "approved", alpha, agencyCode = "AG123", types = listOf("life", "health")),
        testMembership("m2", "pending", beta, agencyCode = "B-77", types = listOf("motor")),
        testMembership("m3", "pending", null, suggestedName = "Gamma Insurance", agencyCode = null, types = listOf("home", "travel")),
        testMembership(
            "m4", "rejected", testCompany("c3", "Delta General", "डेल्टा जनरल"), agencyCode = "D-1", types = listOf("life"),
            rejectReason = "details_mismatch", reviewNote = "The code doesn't match our records",
        ),
        testMembership("m5", "revoked", testCompany("c4", "Epsilon Health", "एप्सिलॉन हेल्थ"), agencyCode = "E-5", types = listOf("health"), reviewNote = "Agency ended"),
    )

    private fun profileForm() = FormState(
        OnboardingViewModel.PROFILE_FIELDS,
        mapOf(OnboardingViewModel.NAME to "Ramesh Kumar", OnboardingViewModel.MOBILE to "+919876543210", OnboardingViewModel.CITY to "Pune"),
    )

    private val manyCompanies: List<Company> = (1..12).map { testCompany("x$it", "Company number $it", "कंपनी संख्या $it") }

    private fun sheet(title: @Composable () -> String, content: @Composable () -> Unit): @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
            Text(title(), style = AgentProTheme.text.title, modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s).semantics { heading() })
            content()
        }
    }

    private val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
        "OnboardingCompaniesEmpty" to {
            OnboardingDetailsScreen(profileForm(), OnboardingUiState(), onContinue = {}, onBack = {}, companies = CompaniesUiState(loading = false))
        },
        "OnboardingCompanies" to {
            OnboardingDetailsScreen(
                profileForm(), OnboardingUiState(), onContinue = {}, onBack = {},
                companies = CompaniesUiState(memberships = allStatuses.take(3), loading = false),
            )
        },
        "ProfileCompanies" to {
            ProfileScreen(CompaniesUiState(memberships = allStatuses, loading = false), CompanyActions.None, onBack = {})
        },
        "PickerSheet" to sheet({ stringResource(Res.string.company_picker_title) }) {
            CompanyPickerContent(listOf(alpha, beta, testCompany("c3", "Delta General", "डेल्टा जनरल")), null, setOf("c1"), AgentLocale.current(), {}, {})
        },
        "PickerSheetSearch" to sheet({ stringResource(Res.string.company_picker_title) }) {
            CompanyPickerContent(manyCompanies, null, emptySet(), AgentLocale.current(), {}, {})
        },
        "PickerSheetError" to sheet({ stringResource(Res.string.company_picker_title) }) {
            CompanyPickerContent(null, AppError.Offline, emptySet(), AgentLocale.current(), {}, {})
        },
        "FormEmptyErrors" to sheet({ "Alpha Insurance" }) {
            val form = remember { FormState(CompaniesViewModel.fieldsFor(listed = true)).also { it.validateAll() } }
            CompanyFormContent(CompanySheet.Form(CompanyTarget.New(alpha), form), saving = false, onSave = {}, onBack = {})
        },
        "FormFilled" to sheet({ "Alpha Insurance" }) {
            val form = remember {
                FormState(CompaniesViewModel.fieldsFor(listed = true), mapOf(CompaniesViewModel.AGENCY_CODE to "AG123", CompaniesViewModel.TYPES to listOf("life", "health")))
            }
            CompanyFormContent(CompanySheet.Form(CompanyTarget.New(alpha), form), saving = false, onSave = {}, onBack = {})
        },
        "FormNotListed" to sheet({ "Add company" }) {
            val form = remember {
                FormState(CompaniesViewModel.fieldsFor(listed = false), mapOf(CompaniesViewModel.NAME to "Gamma Insurance", CompaniesViewModel.TYPES to listOf("motor")))
            }
            CompanyFormContent(CompanySheet.Form(CompanyTarget.New(null), form, error = AppError.Offline), saving = false, onSave = {}, onBack = {})
        },
    )

    private fun capture(variant: String, dark: Boolean, hindi: Boolean) = runComposeUiTest {
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
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/offers/$name.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}

/** The language key of the frame being captured. */
private object AgentLocale {
    @Composable
    fun current(): String = com.sledje.agentpro.core.designsystem.currentAppLocale().key
}
