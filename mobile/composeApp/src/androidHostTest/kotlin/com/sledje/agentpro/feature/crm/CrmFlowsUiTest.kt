package com.sledje.agentpro.feature.crm

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sledje.agentpro.app.App
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.enquiries.ui.enquiryRowTag
import com.sledje.agentpro.feature.policies.ui.PolicyDetailScreen
import com.sledje.agentpro.feature.policies.ui.PolicyDetailViewModel
import com.sledje.agentpro.feature.policies.ui.RECORD_BUTTON_TAG
import com.sledje.agentpro.testing.FakeAppDependencies
import com.sledje.agentpro.testing.FakeAuthRepository
import com.sledje.agentpro.testing.FakeCustomerRepository
import com.sledje.agentpro.testing.FakeEnquiryRepository
import com.sledje.agentpro.testing.FakePolicyRepository
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.testCustomer
import com.sledje.agentpro.testing.testEnquiry
import com.sledje.agentpro.testing.testPolicy
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/TESTING.md, Compose UI tests: the Convert-to-customer flow through the real nav graph, and the
 * Undo snackbar after recording a premium. Fakes only.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class CrmFlowsUiTest {
    private fun SemanticsNodeInteractionsProvider.button(text: String) = onNode(hasText(text) and hasClickAction())

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun convertAnEnquiryToACustomer() = runComposeUiTest {
        val enquiries = FakeEnquiryRepository(listOf(testEnquiry("e1", "Sunita Sharma")))
        val deps = FakeAppDependencies(
            auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, "ramesh@example.com")),
            profiles = FakeProfileRepository(testProfile()),
            sites = FakeSiteRepository(testSite()),
            enquiries = enquiries,
        )
        setContent { App(deps) }
        awaitText("Namaste, Ramesh")
        button("Enquiries").performClick()

        // List → detail (opening it marks it seen).
        awaitText("Sunita Sharma")
        onNodeWithTag(enquiryRowTag("e1")).performClick()
        awaitText("Convert to customer")
        waitUntil(timeoutMillis = 5_000) { "seen:e1" in enquiries.calls }

        // Convert → the customer form, prefilled from the enquiry → Save → the new customer's detail.
        button("Convert to customer").performClick()
        awaitText("New customer")
        awaitText("Sunita Sharma")
        button("Save").performClick()
        awaitText("Policies")
        awaitText("Add policy")

        val customer = deps.customers.rows.value.single()
        assertEquals("e1", customer.sourceEnquiryId)
        assertEquals("+919812345678", customer.phone)
        val e = enquiries.rows.value.single()
        assertEquals(EnquiryStatus.Converted, e.status)
        assertEquals(customer.id, e.customerId)

        // Back on the enquiry, the bottom action is now "View customer".
        onNodeWithContentDescription("Back").performClick()
        awaitText("View customer")
    }

    @Test
    fun recordingAPremiumShowsUndoAndUndoRestoresTheDueDate() = runComposeUiTest {
        val policies = FakePolicyRepository(listOf(testPolicy("p1", "c1", nextDue = LocalDate(2026, 10, 15))))
        val customers = FakeCustomerRepository(listOf(testCustomer("c1")))
        val vm = PolicyDetailViewModel("p1", policies, customers)
        setContent {
            CompositionLocalProvider(LocalIsOnline provides true) {
                AgentProTheme(darkTheme = false) {
                    PolicyDetailScreen(vm, onBack = {}, onEdit = { _, _ -> }, onOpenCustomer = {})
                }
            }
        }
        awaitText("Record premium paid")
        button("Record premium paid").performClick()
        awaitText("Next due becomes 15 Jan 2027")
        onNodeWithTag(RECORD_BUTTON_TAG).performClick()
        waitUntil(timeoutMillis = 5_000) { policies.calls.any { it.startsWith("markPaid:p1") } }

        awaitText("Premium recorded")
        assertEquals(LocalDate(2027, 1, 15), policies.rows.value.single().nextDueDate)
        assertEquals(1, policies.paymentRows.value.size)
        button("Undo").performClick()
        waitUntil(timeoutMillis = 5_000) { policies.paymentRows.value.isEmpty() }
        assertEquals(LocalDate(2026, 10, 15), policies.rows.value.single().nextDueDate)
        assertTrue(policies.calls.any { it.startsWith("undo:") })
    }
}
