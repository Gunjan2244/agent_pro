package com.sledje.agentpro.feature.crm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.sledje.agentpro.core.catalog.DueBucket
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.catalog.PremiumMode
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.SegmentOption
import com.sledje.agentpro.core.designsystem.components.SegmentedControl
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.customers.ui.ActivityItem
import com.sledje.agentpro.feature.customers.ui.CustomerDetail
import com.sledje.agentpro.feature.customers.ui.CustomerDetailContent
import com.sledje.agentpro.feature.customers.ui.CustomerDetailState
import com.sledje.agentpro.feature.customers.ui.CustomerListContent
import com.sledje.agentpro.feature.dues.ui.DuesContent
import com.sledje.agentpro.feature.dues.ui.DuesViewModel
import com.sledje.agentpro.feature.enquiries.ui.EnquiryDetailContent
import com.sledje.agentpro.feature.enquiries.ui.EnquiryDetailState
import com.sledje.agentpro.feature.enquiries.ui.EnquiryListContent
import com.sledje.agentpro.feature.enquiries.ui.EnquiryListData
import com.sledje.agentpro.feature.enquiries.ui.EnquirySort
import com.sledje.agentpro.feature.home.TabScreen
import com.sledje.agentpro.feature.policies.ui.PolicyDetailViewModel
import com.sledje.agentpro.feature.policies.ui.PolicyFormScreen
import com.sledje.agentpro.feature.policies.ui.PolicyFormViewModel
import com.sledje.agentpro.testing.FakePolicyRepository
import com.sledje.agentpro.feature.policies.ui.RecordPaymentSheetContent
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.payment_title
import com.sledje.agentpro.resources.segment_customers
import com.sledje.agentpro.resources.segment_dues
import com.sledje.agentpro.resources.tab_customers
import com.sledje.agentpro.testing.TEST_NOW
import com.sledje.agentpro.testing.TEST_TODAY
import com.sledje.agentpro.testing.testCustomer
import com.sledje.agentpro.testing.testEnquiry
import com.sledje.agentpro.testing.testPolicy
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Roborazzi baselines of the Phase 6–7 screens (docs/TESTING.md) in light/dark × EN/HI, with fixed
 * data and a fixed "now" (16 Oct 2026, 10:00 IST). Images: screenshots/<variant>/crm/<Screen>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class CrmScreenshotTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate(y, m, day)

    private val enquiries = listOf(
        testEnquiry("e1", "Sunita Sharma", EnquiryStatus.New, Interest.Term, createdAt = TEST_NOW - 20.minutes),
        testEnquiry("e2", "Ramesh Patil", EnquiryStatus.FollowUp, Interest.Pension, seen = true, createdAt = TEST_NOW - 3.hours),
        testEnquiry("e3", "Anil Verma", EnquiryStatus.Contacted, Interest.Child, seen = true, createdAt = TEST_NOW - 30.hours),
        testEnquiry("e4", "Meena Joshi", EnquiryStatus.Converted, Interest.Health, seen = true, createdAt = TEST_NOW - 100.hours),
    )
    private val customers = listOf(testCustomer("c1", "Sunita Sharma"), testCustomer("c2", "Ramesh Patil"), testCustomer("c3", "Anil Verma"))
    private val policies = listOf(
        testPolicy("p1", "c1", "Jeevan Anand", 12_000.0, PremiumMode.Quarterly, d(1, 15), d(10, 15)),
        testPolicy("p2", "c1", "Tech Term", 6_000.0, PremiumMode.Yearly, d(11, 10, 2025), d(11, 10)),
        testPolicy("p3", "c2", "Jeevan Labh", 2_500.0, PremiumMode.Monthly, d(1, 20), d(10, 20)),
        testPolicy("p4", "c3", "Bima Jyoti", 45_000.0, PremiumMode.Yearly, d(12, 1, 2023), d(12, 1)),
    )

    @Composable
    private fun CustomersFrame(segment: String, content: @Composable () -> Unit) {
        TabScreen(Res.string.tab_customers) {
            Column {
                SegmentedControl(
                    options = listOf(
                        SegmentOption("customers", stringResource(Res.string.segment_customers)),
                        SegmentOption("dues", stringResource(Res.string.segment_dues)),
                    ),
                    selectedKey = segment,
                    onSelect = {},
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
                content()
            }
        }
    }

    private fun screens(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "EnquiryList" to {
            val data = EnquiryListData(enquiries, enquiries.groupingBy { it.status }.eachCount(), enquiries.size)
            EnquiryListContent(UiState.Content(data), null, EnquirySort.Newest, {}, {}, {}, {}, {}, {}, null, now = TEST_NOW)
        },
        "EnquiriesEmpty" to {
            EnquiryListContent(UiState.Empty, null, EnquirySort.Newest, {}, {}, {}, {}, {}, {}, onShareLink = {}, now = TEST_NOW)
        },
        "EnquiryDetail" to {
            EnquiryDetailContent(
                EnquiryDetailState.Shown(enquiries[0], null), busy = false, snackbar = remember { SnackbarHostState() },
                onBack = {}, onSetStatus = { _, _ -> }, onSetFollowUp = {}, onOpenNotes = {}, onConvert = {}, onViewCustomer = {},
                onCopy = {}, onDelete = {}, onCall = {}, onSms = {}, onEmail = {},
            )
        },
        "CustomerDetail" to {
            val detail = CustomerDetail(
                customers[0],
                policies.filter { it.customerId == "c1" },
                listOf(ActivityItem.Added(TEST_TODAY)),
            )
            CustomerDetailContent(
                CustomerDetailState.Shown(detail), showPolicies = true, deleting = false, snackbar = remember { SnackbarHostState() },
                onBack = {}, onEdit = {}, onDelete = {}, onAddPolicy = {}, onOpenPolicy = {}, onCall = {}, onSms = {}, today = TEST_TODAY,
            )
        },
        "CustomersEmpty" to {
            CustomersFrame("customers") { CustomerListContent(UiState.Empty, null, {}, {}, {}, {}, {}, today = TEST_TODAY) }
        },
        "Dues" to {
            val rows = DuesViewModel.dueRows(policies, customers, TEST_TODAY)
            CustomersFrame("dues") { DuesContent(UiState.Content(DuesViewModel.summarise(rows, null)), null, {}, {}, {}, today = TEST_TODAY) }
        },
        "DuesEmpty" to {
            CustomersFrame("dues") { DuesContent(UiState.Empty, null as DueBucket?, {}, {}, {}, today = TEST_TODAY) }
        },
        "PolicyForm" to {
            val vm = remember { PolicyFormViewModel("c1", "p1", FakePolicyRepository(policies), today = { TEST_TODAY }) }
            PolicyFormScreen(vm, onBack = {}, onSaved = {})
        },
        "RecordPaymentSheet" to {
            val policy = policies[0]
            val form = remember { PolicyDetailViewModel.paymentForm(policy, TEST_TODAY) }
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
                Text(
                    stringResource(Res.string.payment_title),
                    style = AgentProTheme.text.title,
                    modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s),
                )
                RecordPaymentSheetContent(policy, form, recording = false, onRecord = { _, _ -> })
            }
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
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/crm/$name.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}

