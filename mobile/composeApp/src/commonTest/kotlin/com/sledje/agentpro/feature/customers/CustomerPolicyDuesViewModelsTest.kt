package com.sledje.agentpro.feature.customers

import app.cash.turbine.test
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.testing.FakeConsentRepository
import com.sledje.agentpro.core.catalog.DueBucket
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.catalog.PremiumMode
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.InMemoryKeyValueStore
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.forms.matchingSuggestions
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.customers.ui.ActivityItem
import com.sledje.agentpro.feature.customers.ui.CustomerDetailState
import com.sledje.agentpro.feature.customers.ui.CustomerDetailViewModel
import com.sledje.agentpro.feature.customers.ui.CustomerFilter
import com.sledje.agentpro.feature.customers.ui.CustomerFormMode
import com.sledje.agentpro.feature.customers.ui.CustomerFormViewModel
import com.sledje.agentpro.feature.customers.ui.CustomerListData
import com.sledje.agentpro.feature.customers.ui.CustomerListViewModel
import com.sledje.agentpro.feature.dues.ui.DuesData
import com.sledje.agentpro.feature.dues.ui.DuesViewModel
import com.sledje.agentpro.feature.policies.data.PremiumPayment
import com.sledje.agentpro.feature.policies.ui.PaymentEvent
import com.sledje.agentpro.feature.policies.ui.PolicyDetailState
import com.sledje.agentpro.feature.policies.ui.PolicyDetailViewModel
import com.sledje.agentpro.feature.policies.ui.PolicyFormViewModel
import com.sledje.agentpro.testing.FakeCustomerRepository
import com.sledje.agentpro.testing.FakeEnquiryRepository
import com.sledje.agentpro.testing.FakePolicyRepository
import com.sledje.agentpro.testing.TEST_NOW
import com.sledje.agentpro.testing.TEST_TODAY
import com.sledje.agentpro.testing.testCustomer
import com.sledje.agentpro.testing.testEnquiry
import com.sledje.agentpro.testing.testPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Customers (list, detail, form), policies (form, detail, record payment + Undo) and Dues. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerPolicyDuesViewModelsTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate(y, m, day)

    // Today is 16 Oct 2026. Sunita: overdue (15 Oct) + this month (10 Nov); Ramesh: this week (20 Oct);
    // Anil: no policy; Meena: a lapsed policy only.
    private val customers = listOf(
        testCustomer("c1", "Sunita Sharma"),
        testCustomer("c2", "Ramesh Patil"),
        testCustomer("c3", "Anil Verma"),
        testCustomer("c4", "Meena Joshi"),
    )
    private val policies = listOf(
        testPolicy("p1", "c1", "Jeevan Anand", premium = 12_000.0, mode = PremiumMode.Quarterly, start = d(1, 15), nextDue = d(10, 15)),
        testPolicy("p2", "c1", "Tech Term", premium = 6_000.0, mode = PremiumMode.Yearly, start = d(11, 10, 2025), nextDue = d(11, 10)),
        testPolicy("p3", "c2", "Jeevan Labh", premium = 2_500.0, mode = PremiumMode.Monthly, start = d(1, 20), nextDue = d(10, 20)),
        testPolicy("p4", "c4", "Bima Jyoti", premium = 9_000.0, mode = PremiumMode.Yearly, nextDue = d(10, 1), status = PolicyStatus.Lapsed),
        testPolicy("p5", "c2", "Jeevan Umang", premium = 30_000.0, mode = PremiumMode.Yearly, start = d(3, 1, 2020), nextDue = d(3, 1, 2027)),
    )

    private fun data(s: UiState<CustomerListData>) = (s as UiState.Content).data

    @Test
    fun customerRowsSummariseActivePoliciesAndFilter() = runTest {
        val vm = CustomerListViewModel(FakeCustomerRepository(customers), FakePolicyRepository(policies), InMemoryKeyValueStore(), today = { TEST_TODAY })
        vm.state.test {
            var s = awaitItem()
            while (s !is UiState.Content) s = awaitItem()
            val all = data(s)
            assertEquals(listOf("Anil Verma", "Meena Joshi", "Ramesh Patil", "Sunita Sharma"), all.rows.map { it.customer.name })
            val sunita = all.rows.first { it.customer.id == "c1" }
            assertEquals(2, sunita.policyCount)
            assertEquals(12_000.0 * 4 + 6_000.0, sunita.annualPremium, "quarterly ×4 + yearly")
            assertEquals(d(10, 15), sunita.nextDue)
            assertNull(all.rows.first { it.customer.id == "c4" }.nextDue, "lapsed policies have no due chip")
            assertEquals(1, all.counts[CustomerFilter.Overdue])
            assertEquals(1, all.counts[CustomerFilter.DueThisMonth], "Ramesh (20 Oct); Sunita's earliest is overdue")
            assertEquals(1, all.counts[CustomerFilter.NoPolicy])

            vm.setFilter(CustomerFilter.Overdue)
            assertEquals(listOf("c1"), data(awaitItem()).rows.map { it.customer.id })
            vm.setFilter(CustomerFilter.NoPolicy)
            assertEquals(listOf("c3"), data(awaitItem()).rows.map { it.customer.id })
            vm.setFilter(null)
            awaitItem()
            vm.setQuery("ramesh")
            assertEquals(listOf("c2"), data(awaitItem()).rows.map { it.customer.id })
        }
    }

    @Test
    fun customerFormRequiresNameAndMobileAndPrefillsAPickedContact() = runTest {
        val repo = FakeCustomerRepository()
        val vm = CustomerFormViewModel(CustomerFormMode.Add(name = "Kiran Rao", phone = "098765 43210"), repo, FakeEnquiryRepository(), FakeConsentRepository())
        advanceUntilIdle()
        val form = vm.form.value!!
        assertEquals("Kiran Rao", form.text(CustomerFormViewModel.NAME))
        assertEquals("+919876543210", form.text(CustomerFormViewModel.PHONE), "a contact's number is normalised to +91")
        form.set(CustomerFormViewModel.PHONE, "")
        vm.save(AppLocale.En)
        advanceUntilIdle()
        assertTrue(repo.calls.isEmpty(), "mobile is required")
        form.set(CustomerFormViewModel.PHONE, "9876543210")
        form.set(CustomerFormViewModel.CITY, "Nagpur")
        vm.save(AppLocale.En)
        advanceUntilIdle()
        val c = repo.rows.value.single()
        assertEquals("+919876543210", c.phone)
        assertEquals("Nagpur", c.city)
        assertEquals(setOf(CustomerFormViewModel.NAME, CustomerFormViewModel.PHONE), CustomerFormViewModel.VISIBLE)
    }

    @Test
    fun customerFormEditsAnExistingCustomer() = runTest {
        val repo = FakeCustomerRepository(customers)
        val vm = CustomerFormViewModel(CustomerFormMode.Edit("c1"), repo, FakeEnquiryRepository(), FakeConsentRepository())
        advanceUntilIdle()
        val form = vm.form.value!!
        assertEquals("Sunita Sharma", form.text(CustomerFormViewModel.NAME))
        assertFalse(form.isDirty)
        form.set(CustomerFormViewModel.NOMINEE, "Rahul Sharma")
        assertTrue(form.isDirty, "leaving now asks Discard changes?")
        vm.save(AppLocale.En)
        advanceUntilIdle()
        assertEquals("Rahul Sharma", repo.rows.value.first { it.id == "c1" }.nominee)
    }

    @Test
    fun customerDetailShowsActivityAndDeletesWithItsPolicies() = runTest {
        val enquiries = FakeEnquiryRepository(listOf(testEnquiry("e1", interest = Interest.Term)))
        val policyRepo = FakePolicyRepository(
            policies,
            payments = listOf(PremiumPayment("pay1", "p1", 12_000.0, d(7, 14), d(7, 15), TEST_NOW)),
        )
        val customerRepo = FakeCustomerRepository(listOf(testCustomer("c1", sourceEnquiryId = "e1")) + customers.drop(1), policies = policyRepo)
        val vm = CustomerDetailViewModel("c1", customerRepo, policyRepo, enquiries, dateOf = Dates::dateOf)
        advanceUntilIdle()
        val shown = assertIs<CustomerDetailState.Shown>(vm.state.value)
        assertEquals(listOf("p1", "p2"), shown.detail.policies.map { it.id })
        val activity = shown.detail.activity
        assertIs<ActivityItem.Added>(activity[0], "added today (16 Oct), newest first")
        assertIs<ActivityItem.FromEnquiry>(activity[1])
        assertEquals(ActivityItem.Payment(d(7, 14), 12_000.0, "Jeevan Anand"), activity[2])

        vm.delete()
        advanceUntilIdle()
        assertTrue(vm.deleted.value)
        assertEquals(CustomerDetailState.Gone, vm.state.value)
        assertTrue(policyRepo.rows.value.none { it.customerId == "c1" }, "its policies go with it")
    }

    @Test
    fun customerDeleteFailureIsShown() = runTest {
        val repo = FakeCustomerRepository(customers)
        val vm = CustomerDetailViewModel("c1", repo, FakePolicyRepository(), FakeEnquiryRepository(), dateOf = Dates::dateOf)
        repo.error = AppError.ReadOnly
        vm.delete()
        advanceUntilIdle()
        assertFalse(vm.deleted.value)
        assertEquals(AppError.ReadOnly, vm.error.value)
    }

    @Test
    fun policyFormComputesTheNextDueAndSaves() = runTest {
        val repo = FakePolicyRepository()
        val vm = PolicyFormViewModel("c1", null, repo, today = { TEST_TODAY })
        advanceUntilIdle()
        val form = vm.form.value!!
        assertEquals(PremiumMode.Yearly.key, form.text(PolicyFormViewModel.MODE), "the first catalog mode by default")
        assertNull(vm.summary(form))
        vm.save()
        advanceUntilIdle()
        assertTrue(repo.calls.isEmpty(), "plan, number, premium and start date are required")

        form.set(PolicyFormViewModel.PLAN, "Jeevan Anand")
        form.set(PolicyFormViewModel.NUMBER, "987654321")
        form.set(PolicyFormViewModel.PREMIUM, "12000")
        form.set(PolicyFormViewModel.MODE, PremiumMode.Quarterly.key)
        form.set(PolicyFormViewModel.START, d(10, 15))
        val summary = vm.summary(form)!!
        assertEquals(12_000.0, summary.premium)
        assertEquals(PremiumMode.Quarterly, summary.mode)
        assertEquals(d(1, 15, 2027), summary.nextDue, "₹12,000 · Quarterly · next due 15 Jan 2027")
        vm.save()
        advanceUntilIdle()
        val p = repo.rows.value.single()
        assertEquals(d(1, 15, 2027), p.nextDueDate)
        assertEquals(PolicyStatus.Active, p.status)
        assertEquals(p.id, vm.state.value.savedId)
    }

    @Test
    fun policyFormOverrideAndEdit() = runTest {
        val repo = FakePolicyRepository(policies)
        val vm = PolicyFormViewModel("c1", "p1", repo, today = { TEST_TODAY })
        advanceUntilIdle()
        val form = vm.form.value!!
        assertEquals("12000", form.text(PolicyFormViewModel.PREMIUM))
        assertEquals(d(10, 15), form.value(PolicyFormViewModel.NEXT_DUE), "an edit shows the stored due date")
        form.set(PolicyFormViewModel.NEXT_DUE, d(12, 1))
        assertEquals(d(12, 1), vm.summary(form)!!.nextDue)
        vm.save()
        advanceUntilIdle()
        assertEquals(d(12, 1), repo.rows.value.first { it.id == "p1" }.nextDueDate)
        assertEquals(listOf("update:p1"), repo.calls)
    }

    @Test
    fun planNameSuggestionsComeFromTheCatalog() {
        val spec = PolicyFormViewModel.FIELDS.first { it.key == PolicyFormViewModel.PLAN }
        val hits = matchingSuggestions(spec, "jeev")
        assertTrue(hits.isNotEmpty() && hits.all { it.startsWith("Jeevan") })
        assertTrue(hits.size <= 5)
        assertTrue(matchingSuggestions(spec, "").isEmpty())
        assertTrue(matchingSuggestions(spec, "Jeevan Anand").none { it == "Jeevan Anand" }, "an exact match isn't suggested again")
    }

    @Test
    fun recordPaymentMovesTheDueDateAndUndoRestoresIt() = runTest {
        val repo = FakePolicyRepository(policies)
        val vm = PolicyDetailViewModel("p1", repo, FakeCustomerRepository(customers))
        advanceUntilIdle()
        val shown = assertIs<PolicyDetailState.Shown>(vm.state.value)
        assertEquals("Sunita Sharma", shown.customer?.name)
        assertEquals(d(1, 15, 2027), PolicyDetailViewModel.nextDueAfterPayment(shown.policy), "Next due becomes 15 Jan 2027")
        val form = PolicyDetailViewModel.paymentForm(shown.policy, TEST_TODAY)
        assertEquals("12000", form.text(PolicyDetailViewModel.AMOUNT), "amount prefilled with the premium")
        assertEquals(TEST_TODAY, form.value(PolicyDetailViewModel.PAID_ON), "date is today")

        vm.event.test {
            assertNull(awaitItem())
            vm.record(12_000.0, TEST_TODAY)
            val recorded = assertIs<PaymentEvent.Recorded>(awaitItem())
            assertEquals("2027-01-15", recorded.result.nextDueDate)
            assertEquals(d(1, 15, 2027), repo.rows.value.first { it.id == "p1" }.nextDueDate)
            assertEquals(1, repo.paymentRows.value.size)
            vm.eventShown()
            awaitItem()
            vm.undo(recorded)
            advanceUntilIdle()
            assertEquals(d(10, 15), repo.rows.value.first { it.id == "p1" }.nextDueDate)
            assertTrue(repo.paymentRows.value.isEmpty())
            expectNoEvents()
        }
    }

    @Test
    fun undoOfAnOlderPaymentIsRefused() = runTest {
        val repo = FakePolicyRepository(policies)
        val vm = PolicyDetailViewModel("p1", repo, FakeCustomerRepository(customers))
        advanceUntilIdle()
        vm.record(12_000.0, TEST_TODAY)
        advanceUntilIdle()
        val first = assertIs<PaymentEvent.Recorded>(vm.event.value)
        vm.eventShown()
        vm.record(12_000.0, TEST_TODAY)
        advanceUntilIdle()
        vm.eventShown()
        vm.undo(first)
        advanceUntilIdle()
        assertEquals(PaymentEvent.UndoRefused, vm.event.value)
        assertEquals(2, repo.paymentRows.value.size)
    }

    @Test
    fun recordFailureIsAnEvent() = runTest {
        val repo = FakePolicyRepository(policies)
        val vm = PolicyDetailViewModel("p1", repo, FakeCustomerRepository(customers))
        repo.error = AppError.ReadOnly
        vm.record(12_000.0, TEST_TODAY)
        advanceUntilIdle()
        assertEquals(PaymentEvent.Failed(AppError.ReadOnly), vm.event.value)
        assertFalse(vm.recording.value)
    }

    private fun dues(s: UiState<DuesData>) = (s as UiState.Content).data

    @Test
    fun duesBucketsTotalsAndGroups() = runTest {
        val vm = DuesViewModel(FakePolicyRepository(policies), FakeCustomerRepository(customers), InMemoryKeyValueStore(), today = { TEST_TODAY })
        vm.state.test {
            var s = awaitItem()
            while (s !is UiState.Content) s = awaitItem()
            val all = dues(s)
            // p4 is lapsed and p5 is beyond 90 days: neither is due.
            assertEquals(listOf(d(10, 15), d(10, 20), d(11, 10)), all.groups.map { it.date })
            assertEquals(3, all.count)
            assertEquals(12_000.0 + 2_500.0 + 6_000.0, all.total)
            assertEquals(1, all.counts[DueBucket.Overdue])
            assertEquals(1, all.counts[DueBucket.ThisWeek])
            assertEquals(1, all.counts[DueBucket.ThisMonth])
            assertEquals(0, all.counts[DueBucket.Next3Months])

            vm.setBucket(DueBucket.Overdue)
            val overdue = dues(awaitItem())
            assertEquals(1, overdue.count)
            assertEquals(12_000.0, overdue.total)
            assertEquals("Sunita Sharma", overdue.groups.single().rows.single().customer?.name)
        }
    }

    @Test
    fun duesDeepLinkBucketAndEmptyState() = runTest {
        val store = InMemoryKeyValueStore()
        val vm = DuesViewModel(FakePolicyRepository(policies), FakeCustomerRepository(customers), store, today = { TEST_TODAY }, initialBucket = "this_week")
        assertEquals(DueBucket.ThisWeek, vm.bucket.value)
        val empty = DuesViewModel(FakePolicyRepository(), FakeCustomerRepository(customers), store, today = { TEST_TODAY })
        empty.state.test {
            var s = awaitItem()
            while (s == UiState.Loading) s = awaitItem()
            assertEquals(UiState.Empty, s, "All caught up")
        }
    }
}
