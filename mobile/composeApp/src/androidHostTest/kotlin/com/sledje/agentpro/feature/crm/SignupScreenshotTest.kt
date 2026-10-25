package com.sledje.agentpro.feature.crm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.customers.data.DraftSource
import com.sledje.agentpro.feature.customers.ui.AddCustomerSheetContent
import com.sledje.agentpro.feature.customers.ui.CustomerListContent
import com.sledje.agentpro.feature.customers.ui.CustomerListData
import com.sledje.agentpro.feature.customers.ui.CustomerRow
import com.sledje.agentpro.feature.customers.ui.IncompleteFormsContent
import com.sledje.agentpro.feature.customers.ui.SignupLiveContent
import com.sledje.agentpro.feature.customers.ui.SignupLiveState
import com.sledje.agentpro.feature.customers.ui.SignupNotice
import com.sledje.agentpro.feature.customers.ui.SignupStartContent
import com.sledje.agentpro.feature.customers.ui.SignupStartState
import com.sledje.agentpro.feature.home.TabScreen
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.tab_customers
import com.sledje.agentpro.testing.TEST_NOW
import com.sledje.agentpro.testing.TEST_TODAY
import com.sledje.agentpro.testing.testCustomer
import com.sledje.agentpro.testing.testDraft
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Roborazzi baselines of the customer sign-up link and Incomplete forms screens (DECISIONS #108; docs/TESTING.md)
 * in light/dark x EN/HI with a fixed clock (`TEST_NOW`). Images: screenshots/<variant>/signup/<Screen>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class SignupScreenshotTest {
    private val now = TEST_NOW
    private val expires = now + 6.days
    private val customerFields = mapOf(
        "name" to "Sunita Sharma", "phone" to "+919812345678", "email" to "sunita@example.com", "dob" to "1990-04-12",
    )

    private fun link(
        fields: Map<String, String> = emptyMap(),
        active: String? = null,
        seen: Boolean = false,
        opened: Boolean = seen,
        consented: Boolean = false,
        expiresAt: kotlin.time.Instant = expires,
    ) = testDraft(
        "d-link", DraftSource.Customer, fields, active, seenAt = if (seen) now - 4.seconds else null,
        openedAt = if (opened) now - 12.minutes else null, consented = consented, expiresAt = expiresAt,
    )

    private fun shown(draft: com.sledje.agentpro.feature.customers.data.CustomerDraft, url: String? = null, busy: Boolean = false, notice: SignupNotice? = null) =
        SignupLiveState.Shown(draft, url, busy, notice)

    private val url = "https://agentpro.site/a/ramesh-kumar/join?t=Wq3x8Zk2nB7vT1pLdR5sYhJ0cMfGaUeO4iXoNb6VtKw"

    private val incomplete = listOf(
        testDraft("a1", fields = mapOf("name" to "Kiran Rao", "phone" to "+919876543210"), updatedAt = now - 12.minutes),
        testDraft("a2", fields = mapOf("city" to "Pune"), updatedAt = now - 5.hours),
        link(customerFields, "phone", seen = true, consented = true).copy(id = "l1", updatedAt = now - 4.seconds),
        link(mapOf("name" to "Anil Verma"), opened = true, consented = true).copy(id = "l2", updatedAt = now - 20.minutes),
        link().copy(id = "l3", updatedAt = now - 2.hours),
        link(opened = true, expiresAt = now - 1.days).copy(id = "l4", updatedAt = now - 3.days),
    )

    @Composable
    private fun Sheet(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) { content() }
    }

    private fun screens(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "AddCustomerSheet" to { Sheet { AddCustomerSheetContent({}, {}, onSendLink = {}, online = true) } },
        "AddCustomerSheetOffline" to { Sheet { AddCustomerSheetContent({}, {}, onSendLink = {}, online = false) } },
        "AddCustomerSheetNoLink" to { Sheet { AddCustomerSheetContent({}, {}, onSendLink = null, online = true) } },
        "CustomersWithIncomplete" to {
            TabScreen(Res.string.tab_customers) {
                val rows = listOf(CustomerRow(testCustomer("c1", "Sunita Sharma"), 0, 0.0, null))
                CustomerListContent(
                    UiState.Content(CustomerListData(rows, emptyMap(), 1)), null, {}, {}, {}, {}, {},
                    onSendLink = {}, incompleteCount = 3, today = TEST_TODAY,
                )
            }
        },
        "IncompleteForms" to {
            IncompleteFormsContent(UiState.Content(incomplete), now, remember { SnackbarHostState() }, {}, {}, {})
        },
        "IncompleteFormsEmpty" to {
            IncompleteFormsContent(UiState.Empty, now, remember { SnackbarHostState() }, {}, {}, {})
        },
        "SignupStarting" to { SignupStartContent(SignupStartState.Creating, {}, {}) },
        "SignupStartFailed" to { SignupStartContent(SignupStartState.Failed(SignupNotice.NoSite), {}, {}) },
        "SignupLiveNew" to {
            LiveFrame(shown(link(), url))
        },
        "SignupLiveWaitingConsent" to {
            LiveFrame(shown(link(opened = true, seen = true), null))
        },
        "SignupLiveTyping" to {
            LiveFrame(shown(link(customerFields, "phone", seen = true, consented = true), url))
        },
        "SignupLiveAway" to {
            LiveFrame(shown(link(customerFields, "phone", seen = false, opened = true, consented = true), null))
        },
        "SignupLiveExpired" to {
            LiveFrame(shown(link(customerFields, opened = true, consented = true, expiresAt = now - 1.days), null))
        },
        "SignupLiveRenewError" to {
            LiveFrame(shown(link(opened = true), null, notice = SignupNotice.Failed(AppError.Offline)))
        },
        "SignupLiveCompleted" to {
            LiveFrame(shown(link(consented = true).copy(completedAt = now, customerId = "c9"), null))
        },
        "SignupLiveGone" to { LiveFrame(SignupLiveState.Gone) },
        "SignupLiveLoading" to { LiveFrame(SignupLiveState.Loading) },
    )

    @Composable
    private fun LiveFrame(state: SignupLiveState) {
        SignupLiveContent(state, now, remember { SnackbarHostState() }, {}, {}, {}, {}, {})
    }

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
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/signup/$name.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}
