package com.sledje.agentpro.feature.offers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.offers.data.OfferRef
import com.sledje.agentpro.feature.offers.ui.OfferFlag
import com.sledje.agentpro.feature.offers.ui.OfferPickerActions
import com.sledje.agentpro.feature.offers.ui.OfferPickerScreen
import com.sledje.agentpro.feature.offers.ui.OfferPickerState
import com.sledje.agentpro.feature.offers.ui.OfferRow
import com.sledje.agentpro.feature.offers.ui.PickerMode
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of the offer picker (DECISIONS #104): offers on the site (with featured, ended and unavailable chips),
 * empty, at the cap, the company offers loading / offline / no company, and the featured single choice, in light/dark x EN/HI.
 * Images: composeApp/src/androidHostTest/screenshots/<variant>/offers/OfferPicker<Name>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class OfferPickerScreenshotTest {
    private fun row(id: String, source: String = OfferRef.COMPANY, featured: Boolean = false, flag: OfferFlag? = null, company: String? = "Alpha Insurance", day: Int = 31) =
        OfferRow(
            OfferRef(source, id), "Protect your family $id", "A simple plan with cover for the whole family",
            LocalDate(2026, 12, day), "term", company, flag, featured, editable = true,
        )

    private val onSite = listOf(
        row("1", featured = true),
        row("2", OfferRef.AGENT, company = null, day = 15),
        row("3", flag = OfferFlag.Ended),
        OfferRow(OfferRef.company("4"), "", "", null, null, null, OfferFlag.Unavailable),
    )
    private val available = listOf(row("5", company = "Beta Life"), row("6", company = "Beta Life", day = 20))
    private val base = OfferPickerState(PickerMode.Items, loading = false, hasApprovedCompany = true)

    private val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
        "Items" to { OfferPickerScreen(base.copy(onSite = onSite, available = available), OfferPickerActions.None) },
        "ItemsEmpty" to { OfferPickerScreen(base.copy(available = available), OfferPickerActions.None) },
        "ItemsFull" to { OfferPickerScreen(base.copy(onSite = onSite, available = available, cap = 4), OfferPickerActions.None) },
        "ItemsNoCompany" to { OfferPickerScreen(base.copy(hasApprovedCompany = false), OfferPickerActions.None) },
        "ItemsOffline" to { OfferPickerScreen(base.copy(onSite = onSite.take(2), loadError = AppError.Offline), OfferPickerActions.None) },
        "ItemsLoading" to { OfferPickerScreen(base.copy(onSite = onSite.take(2), hasApprovedCompany = null), OfferPickerActions.None) },
        "ItemsError" to { OfferPickerScreen(base.copy(loadError = AppError.Unknown("x")), OfferPickerActions.None) },
        "Featured" to {
            OfferPickerScreen(
                base.copy(mode = PickerMode.Featured, onSite = onSite, available = available, featured = OfferRef.company("1")),
                OfferPickerActions.None,
            )
        },
    )

    private fun capture(variant: String, dark: Boolean, hindi: Boolean) = runComposeUiTest {
        var index by mutableStateOf(0)
        setContent {
            CompositionLocalProvider(LocalIsOnline provides true) {
                GalleryFrame(dark = dark, hindi = hindi, largeText = false) { key(index) { screens[index].second() } }
            }
        }
        screens.forEachIndexed { i, (name, _) ->
            index = i
            waitForIdle()
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/offers/OfferPicker$name.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}
