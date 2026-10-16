package com.sledje.agentpro.feature.enquiries

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.sledje.agentpro.core.catalog.EnquirySource
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.InMemoryKeyValueStore
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.customers.ui.CustomerFormMode
import com.sledje.agentpro.feature.customers.ui.CustomerFormViewModel
import com.sledje.agentpro.feature.enquiries.ui.AddEnquiryViewModel
import com.sledje.agentpro.feature.enquiries.ui.EnquiryDetailEvent
import com.sledje.agentpro.feature.enquiries.ui.EnquiryDetailState
import com.sledje.agentpro.feature.enquiries.ui.EnquiryDetailViewModel
import com.sledje.agentpro.feature.enquiries.ui.EnquiryListData
import com.sledje.agentpro.feature.enquiries.ui.EnquiryListViewModel
import com.sledje.agentpro.feature.enquiries.ui.EnquirySort
import com.sledje.agentpro.feature.enquiries.ui.NotesStatus
import com.sledje.agentpro.feature.enquiries.ui.NotesViewModel
import com.sledje.agentpro.testing.FakeCustomerRepository
import com.sledje.agentpro.testing.FakeEnquiryRepository
import com.sledje.agentpro.testing.TEST_NOW
import com.sledje.agentpro.testing.testEnquiry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Enquiries: list, detail (status, follow-up, notes, delete + Undo), add, and convert to customer. */
@OptIn(ExperimentalCoroutinesApi::class)
class EnquiryViewModelsTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private suspend fun <T> ReceiveTurbine<T>.awaitMatching(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun content(state: UiState<EnquiryListData>) = (state as UiState.Content).data

    private val sample = listOf(
        testEnquiry("a", "Sunita Sharma", EnquiryStatus.New, createdAt = TEST_NOW),
        testEnquiry("b", "Ramesh Patil", EnquiryStatus.Contacted, interest = Interest.Pension, seen = true, createdAt = TEST_NOW - 2.hours),
        testEnquiry("c", "Anil Verma", EnquiryStatus.New, interest = Interest.Child, createdAt = TEST_NOW - 30.hours),
    )

    @Test
    fun listIsEmptyWithoutEnquiries() = runTest {
        val vm = EnquiryListViewModel(FakeEnquiryRepository(), InMemoryKeyValueStore())
        vm.state.test { assertEquals(UiState.Empty, awaitMatching { it != UiState.Loading }) }
    }

    @Test
    fun listCountsFiltersSearchesAndSorts() = runTest {
        val store = InMemoryKeyValueStore()
        val vm = EnquiryListViewModel(FakeEnquiryRepository(sample), store)
        vm.state.test {
            val all = content(awaitMatching { it is UiState.Content })
            assertEquals(listOf("a", "b", "c"), all.rows.map { it.id }, "newest first")
            assertEquals(2, all.counts[EnquiryStatus.New])
            assertEquals(1, all.counts[EnquiryStatus.Contacted])
            assertEquals(3, all.total)

            vm.setFilter(EnquiryStatus.New)
            assertEquals(listOf("a", "c"), content(awaitItem()).rows.map { it.id })
            assertEquals("new", store.get(EnquiryListViewModel.FILTER_KEY), "the filter is remembered")

            vm.setQuery("anil")
            assertEquals(listOf("c"), content(awaitItem()).rows.map { it.id })

            vm.setQuery("zzz")
            val none = content(awaitItem())
            assertTrue(none.rows.isEmpty(), "no match is Content with no rows, not Empty")

            vm.setQuery("")
            awaitItem()
            vm.setFilter(null)
            awaitItem()
            vm.setSort(EnquirySort.Name)
            assertEquals(listOf("c", "b", "a"), content(awaitItem()).rows.map { it.id })
            vm.setSort(EnquirySort.Oldest)
            // Same order as by name here, so the state doesn't change (no new emission).
            assertEquals(listOf("c", "b", "a"), content(vm.state.value).rows.map { it.id })
            vm.setSort(EnquirySort.Newest)
            assertEquals(listOf("a", "b", "c"), content(awaitItem()).rows.map { it.id })
            vm.setSort(EnquirySort.Oldest)
            assertEquals(listOf("c", "b", "a"), content(awaitItem()).rows.map { it.id })
            assertEquals("oldest", store.get(EnquiryListViewModel.SORT_KEY))
        }
    }

    @Test
    fun rememberedFilterAndSortAreRestored() = runTest {
        val store = InMemoryKeyValueStore()
        store.put(EnquiryListViewModel.FILTER_KEY, "contacted")
        store.put(EnquiryListViewModel.SORT_KEY, "name")
        val vm = EnquiryListViewModel(FakeEnquiryRepository(sample), store)
        advanceUntilIdle()
        assertEquals(EnquiryStatus.Contacted, vm.filter.value)
        assertEquals(EnquirySort.Name, vm.sort.value)
    }

    @Test
    fun searchMatchesPhoneDigits() {
        val rows = EnquiryListViewModel.arrange(sample, null, "98123", EnquirySort.Newest)
        assertEquals(3, rows.size, "every sample shares the phone")
        assertTrue(EnquiryListViewModel.arrange(sample, null, "12", EnquirySort.Newest).isEmpty(), "fewer than 3 digits don't match phones")
    }

    @Test
    fun refreshFailureIsShownButOfflineIsNot() = runTest {
        val repo = FakeEnquiryRepository(sample)
        val vm = EnquiryListViewModel(repo, InMemoryKeyValueStore())
        repo.error = AppError.Unknown()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(AppError.Unknown(), vm.error.value)
        vm.errorShown()
        repo.error = AppError.Offline
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.error.value)
    }

    @Test
    fun openingTheDetailMarksItSeen() = runTest {
        val repo = FakeEnquiryRepository(sample)
        val vm = EnquiryDetailViewModel("a", repo, FakeCustomerRepository())
        advanceUntilIdle()
        assertTrue("seen:a" in repo.calls)
        val shown = assertIs<EnquiryDetailState.Shown>(vm.state.value)
        assertTrue(!shown.enquiry.unseen)
        // An already seen enquiry isn't written again.
        EnquiryDetailViewModel("b", repo, FakeCustomerRepository())
        advanceUntilIdle()
        assertTrue("seen:b" !in repo.calls)
    }

    @Test
    fun statusChangeAppliesAtOnceAndUndoRestoresIt() = runTest {
        val repo = FakeEnquiryRepository(sample)
        val vm = EnquiryDetailViewModel("a", repo, FakeCustomerRepository())
        vm.event.test {
            assertNull(awaitItem())
            vm.setStatus(EnquiryStatus.Contacted)
            val event = assertIs<EnquiryDetailEvent.StatusChanged>(awaitItem())
            assertEquals(EnquiryStatus.New, event.previous)
            assertEquals(EnquiryStatus.Contacted, repo.rows.value.first { it.id == "a" }.status)
            vm.eventShown()
            awaitItem()
            vm.undoStatus(event)
            advanceUntilIdle()
            assertEquals(EnquiryStatus.New, repo.rows.value.first { it.id == "a" }.status)
        }
    }

    @Test
    fun followUpStoresTheDateAndMakesItAFollowUp() = runTest {
        val repo = FakeEnquiryRepository(sample)
        val vm = EnquiryDetailViewModel("a", repo, FakeCustomerRepository())
        advanceUntilIdle()
        val at = Instant.parse("2026-10-20T04:30:00Z")
        vm.setStatus(EnquiryStatus.FollowUp, at)
        advanceUntilIdle()
        val e = repo.rows.value.first { it.id == "a" }
        assertEquals(EnquiryStatus.FollowUp, e.status)
        assertEquals(at, e.followUpAt)

        // Setting a date from the Follow-up row on a Contacted enquiry also makes it a Follow-up.
        val vm2 = EnquiryDetailViewModel("b", repo, FakeCustomerRepository())
        advanceUntilIdle()
        vm2.setFollowUp(at)
        advanceUntilIdle()
        assertEquals(EnquiryStatus.FollowUp, repo.rows.value.first { it.id == "b" }.status)
        vm2.setFollowUp(null)
        advanceUntilIdle()
        assertNull(repo.rows.value.first { it.id == "b" }.followUpAt)
    }

    @Test
    fun deleteIsSoftAndUndoable() = runTest {
        val repo = FakeEnquiryRepository(sample)
        val vm = EnquiryDetailViewModel("a", repo, FakeCustomerRepository())
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        assertEquals(EnquiryDetailEvent.Deleted, vm.event.value)
        assertEquals(EnquiryDetailState.Gone(canUndo = true), vm.state.value)
        vm.undoDelete()
        advanceUntilIdle()
        assertIs<EnquiryDetailState.Shown>(vm.state.value)
        assertEquals(listOf("delete:a", "restore:a"), repo.calls.filter { it.startsWith("delete") || it.startsWith("restore") })
    }

    @Test
    fun failedWritesBecomeEvents() = runTest {
        val repo = FakeEnquiryRepository(sample)
        val vm = EnquiryDetailViewModel("a", repo, FakeCustomerRepository())
        advanceUntilIdle()
        repo.error = AppError.Offline
        vm.setStatus(EnquiryStatus.Closed)
        advanceUntilIdle()
        assertEquals(EnquiryDetailEvent.Failed(AppError.Offline), vm.event.value)
    }

    @Test
    fun addEnquiryValidatesAndSavesWithAManualSource() = runTest {
        val repo = FakeEnquiryRepository()
        val vm = AddEnquiryViewModel(repo)
        vm.save()
        advanceUntilIdle()
        assertTrue(repo.calls.isEmpty(), "name is required")
        assertTrue(EnquirySource.Website !in AddEnquiryViewModel.MANUAL_SOURCES)
        vm.form.set(AddEnquiryViewModel.NAME, "Meena Joshi")
        vm.form.set(AddEnquiryViewModel.PHONE, "98765 43210")
        vm.form.set(AddEnquiryViewModel.INTEREST, Interest.Health.key)
        vm.save()
        advanceUntilIdle()
        val saved = repo.rows.value.single()
        assertEquals("Meena Joshi", saved.name)
        assertEquals("+919876543210", saved.phone)
        assertEquals(Interest.Health, saved.interest)
        assertEquals(AddEnquiryViewModel.DEFAULT_SOURCE, saved.source)
        assertEquals(saved.id, vm.state.value.savedId)
    }

    @Test
    fun notesAutosaveAfterAPause() = runTest {
        val repo = FakeEnquiryRepository(sample)
        val vm = NotesViewModel("a", repo)
        advanceUntilIdle()
        vm.onChange("Call after 6 pm")
        advanceTimeBy(NotesViewModel.AUTOSAVE_DELAY_MILLIS - 100)
        assertTrue("notes:a" !in repo.calls)
        advanceTimeBy(200)
        assertEquals("Call after 6 pm", repo.rows.value.first { it.id == "a" }.notes)
        assertEquals(NotesStatus.Saved, vm.status.value)
        vm.flush()
        assertEquals(1, repo.calls.count { it == "notes:a" }, "nothing changed, nothing saved")
    }

    @Test
    fun convertCreatesTheCustomerAndLinksTheEnquiry() = runTest {
        val enquiries = FakeEnquiryRepository(sample)
        val customers = FakeCustomerRepository(enquiries = enquiries)
        val vm = CustomerFormViewModel(CustomerFormMode.Convert("a"), customers, enquiries)
        advanceUntilIdle()
        val form = vm.form.value!!
        assertEquals("Sunita Sharma", form.text(CustomerFormViewModel.NAME), "prefilled from the enquiry")
        assertEquals("+919812345678", form.text(CustomerFormViewModel.PHONE))
        vm.save()
        advanceUntilIdle()
        val customer = customers.rows.value.single()
        assertEquals("a", customer.sourceEnquiryId)
        val e = enquiries.rows.value.first { it.id == "a" }
        assertEquals(EnquiryStatus.Converted, e.status)
        assertEquals(customer.id, e.customerId)
        assertEquals(customer.id, vm.state.value.savedId)

        // The detail screen now offers "View customer".
        val detail = EnquiryDetailViewModel("a", enquiries, customers)
        advanceUntilIdle()
        assertEquals(customer, assertIs<EnquiryDetailState.Shown>(detail.state.value).customer)
    }

    @Test
    fun convertRetryNeverDuplicatesTheCustomer() = runTest {
        val enquiries = FakeEnquiryRepository(sample)
        val customers = FakeCustomerRepository(enquiries = enquiries)
        customers.linkError = AppError.Offline
        val vm = CustomerFormViewModel(CustomerFormMode.Convert("a"), customers, enquiries)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals(AppError.Offline, vm.state.value.error)
        customers.linkError = null
        vm.save()
        advanceUntilIdle()
        assertEquals(1, customers.rows.value.size)
        assertEquals(EnquiryStatus.Converted, enquiries.rows.value.first { it.id == "a" }.status)
    }
}
