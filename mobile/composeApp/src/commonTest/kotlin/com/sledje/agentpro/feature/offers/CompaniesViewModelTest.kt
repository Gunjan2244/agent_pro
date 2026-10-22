package com.sledje.agentpro.feature.offers

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.feature.offers.ui.CompaniesViewModel
import com.sledje.agentpro.feature.offers.ui.CompanyRules
import com.sledje.agentpro.feature.offers.ui.CompanySheet
import com.sledje.agentpro.feature.offers.ui.CompanyTarget
import com.sledje.agentpro.testing.FakeCompanyRepository
import com.sledje.agentpro.testing.testCompany
import com.sledje.agentpro.testing.testMembership
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CompaniesViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeCompanyRepository) = CompaniesViewModel(repo)

    // ---- The rule onboarding step 1 uses ------------------------------------------------------

    @Test
    fun onboardingNeedsOneActiveCompanyWithATypeEach() {
        assertFalse(CompanyRules.hasEnough(emptyList()))
        assertFalse(CompanyRules.hasEnough(listOf(testMembership(status = "rejected"))), "a rejected link doesn't count")
        assertFalse(CompanyRules.hasEnough(listOf(testMembership(status = "revoked"))))
        assertFalse(CompanyRules.hasEnough(listOf(testMembership(types = emptyList()))), "at least one type")
        assertTrue(CompanyRules.hasEnough(listOf(testMembership(status = "pending"))))
        assertTrue(CompanyRules.hasEnough(listOf(testMembership(status = "rejected"), testMembership(id = "m2", status = "approved"))))
    }

    // ---- Validation of the add form ------------------------------------------------------------

    @Test
    fun aListedCompanyNeedsAnAgencyCodeAndAtLeastOneType() = runTest {
        val vm = vm(FakeCompanyRepository())
        runCurrent()
        vm.pick(testCompany())
        val form = (vm.state.value.sheet as CompanySheet.Form).form
        assertEquals(listOf(CompaniesViewModel.AGENCY_CODE, CompaniesViewModel.TYPES), form.fields.map { it.key })
        assertEquals(ValidationError.Required, form.error(CompaniesViewModel.AGENCY_CODE))
        assertEquals(ValidationError.Required, form.error(CompaniesViewModel.TYPES))
        form.set(CompaniesViewModel.AGENCY_CODE, "AG123")
        assertFalse(form.isValid, "types still missing")
        form.set(CompaniesViewModel.TYPES, listOf("life"))
        assertTrue(form.isValid)
        form.set(CompaniesViewModel.AGENCY_CODE, "x".repeat(31))
        assertFalse(form.isValid, "agency code is at most 30 characters")
    }

    @Test
    fun aCompanyThatIsNotListedNeedsANameAndATypeButNoCode() = runTest {
        val vm = vm(FakeCompanyRepository())
        runCurrent()
        vm.pick(null)
        val form = (vm.state.value.sheet as CompanySheet.Form).form
        assertEquals(listOf(CompaniesViewModel.NAME, CompaniesViewModel.TYPES), form.fields.map { it.key })
        assertFalse(form.isValid)
        form.set(CompaniesViewModel.NAME, "Gamma Life")
        form.set(CompaniesViewModel.TYPES, listOf("motor", "home"))
        assertTrue(form.isValid)
    }

    @Test
    fun submitDoesNothingWhileTheFormIsInvalid() = runTest {
        val repo = FakeCompanyRepository()
        val vm = vm(repo)
        runCurrent()
        vm.pick(testCompany())
        vm.submit()
        runCurrent()
        assertTrue(repo.requests.isEmpty())
        assertTrue(vm.state.value.sheet is CompanySheet.Form, "the sheet stays open")
    }

    // ---- Flows ----------------------------------------------------------------------------------

    @Test
    fun theFirstLoadRefreshesTheList() = runTest {
        val repo = FakeCompanyRepository(listOf(testMembership()))
        val vm = vm(repo)
        assertTrue(vm.state.value.loading)
        runCurrent()
        assertFalse(vm.state.value.loading)
        assertEquals(1, repo.refreshes)
        assertEquals(1, vm.state.value.memberships.size)
        assertTrue(vm.state.value.satisfiesOnboarding)
    }

    @Test
    fun aFailedFirstLoadShowsTheErrorAndRetryWorks() = runTest {
        val repo = FakeCompanyRepository().apply { error = AppError.Offline }
        val vm = vm(repo)
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.loadError)
        repo.error = null
        vm.refresh()
        runCurrent()
        assertNull(vm.state.value.loadError)
    }

    @Test
    fun addingAListedCompanyCallsRequestCompanyAndClosesTheSheet() = runTest {
        val repo = FakeCompanyRepository()
        val vm = vm(repo)
        runCurrent()
        vm.openPicker()
        runCurrent()
        assertEquals(2, vm.state.value.companies?.size, "the picker loads list_companies once")
        vm.pick(testCompany("c1"))
        val form = (vm.state.value.sheet as CompanySheet.Form).form
        form.set(CompaniesViewModel.AGENCY_CODE, " AG123 ")
        form.set(CompaniesViewModel.TYPES, listOf("life", "health"))
        vm.submit()
        runCurrent()
        assertEquals(FakeCompanyRepository.Request("c1", null, "AG123", listOf("life", "health")), repo.requests.single())
        assertNull(vm.state.value.sheet)
        assertEquals(listOf("c1"), vm.state.value.memberships.mapNotNull { it.company?.id })
        assertTrue(vm.state.value.satisfiesOnboarding)
    }

    @Test
    fun aCompanyThatIsNotListedSendsTheNameAndNoAgencyCode() = runTest {
        val repo = FakeCompanyRepository()
        val vm = vm(repo)
        runCurrent()
        vm.pick(null)
        val form = (vm.state.value.sheet as CompanySheet.Form).form
        form.set(CompaniesViewModel.NAME, "Gamma Life")
        form.set(CompaniesViewModel.TYPES, listOf("motor"))
        vm.submit()
        runCurrent()
        assertEquals(FakeCompanyRepository.Request(null, "Gamma Life", null, listOf("motor")), repo.requests.single())
    }

    @Test
    fun aCompanyAlreadyLinkedCannotBePickedAgain() = runTest {
        val vm = vm(FakeCompanyRepository(listOf(testMembership(company = testCompany("c1")))))
        runCurrent()
        vm.openPicker()
        vm.pick(testCompany("c1"))
        assertEquals(CompanySheet.Picker, vm.state.value.sheet)
        assertEquals(setOf("c1"), vm.state.value.linkedCompanyIds)
    }

    @Test
    fun aRejectedCompanyCanBeRequestedAgainAndIsPrefilled() = runTest {
        val rejected = testMembership(id = "m9", status = "rejected", agencyCode = "OLD1", types = listOf("life", "motor"), rejectReason = "details_mismatch")
        val repo = FakeCompanyRepository(listOf(rejected))
        val vm = vm(repo)
        runCurrent()
        assertTrue(vm.state.value.linkedCompanyIds.isEmpty(), "a rejected link doesn't block asking again")
        vm.tryAgain(rejected)
        val form = (vm.state.value.sheet as CompanySheet.Form).form
        assertEquals("OLD1", form.text(CompaniesViewModel.AGENCY_CODE))
        form.set(CompaniesViewModel.AGENCY_CODE, "NEW2")
        vm.submit()
        runCurrent()
        assertEquals(FakeCompanyRepository.Request("c1", null, "NEW2", listOf("life", "motor")), repo.requests.single())
    }

    @Test
    fun editingTypesCallsSetMembershipTypesWithOnlyThatField() = runTest {
        val m = testMembership(id = "m1", status = "approved", types = listOf("life"))
        val repo = FakeCompanyRepository(listOf(m))
        val vm = vm(repo)
        runCurrent()
        vm.editTypes(m)
        val sheet = vm.state.value.sheet as CompanySheet.Form
        assertTrue(sheet.target is CompanyTarget.Types)
        assertEquals(listOf(CompaniesViewModel.TYPES), sheet.form.fields.map { it.key })
        sheet.form.set(CompaniesViewModel.TYPES, listOf("life", "travel"))
        vm.submit()
        runCurrent()
        assertEquals("m1" to listOf("life", "travel"), repo.typeChanges.single())
        assertTrue(repo.requests.isEmpty(), "no new request, so no new review")
        assertEquals(listOf("life", "travel"), vm.state.value.memberships.single().insuranceTypes)
    }

    @Test
    fun removingAsksFirstThenWithdraws() = runTest {
        val m = testMembership(id = "m1", status = "approved")
        val repo = FakeCompanyRepository(listOf(m))
        val vm = vm(repo)
        runCurrent()
        vm.askRemove(m)
        assertEquals(m, vm.state.value.confirmRemove)
        vm.cancelRemove()
        runCurrent()
        assertTrue(repo.withdrawn.isEmpty())
        vm.askRemove(m)
        vm.confirmRemove()
        runCurrent()
        assertEquals(listOf("m1"), repo.withdrawn)
        assertTrue(vm.state.value.memberships.isEmpty())
        assertFalse(vm.state.value.satisfiesOnboarding)
    }

    // ---- Errors ---------------------------------------------------------------------------------

    @Test
    fun aFailedSaveKeepsTheSheetOpenWithTheError() = runTest {
        val repo = FakeCompanyRepository()
        val vm = vm(repo)
        runCurrent()
        vm.pick(testCompany())
        val form = (vm.state.value.sheet as CompanySheet.Form).form
        form.set(CompaniesViewModel.AGENCY_CODE, "AG1")
        form.set(CompaniesViewModel.TYPES, listOf("life"))
        repo.error = AppError.Offline
        vm.submit()
        runCurrent()
        val sheet = vm.state.value.sheet as CompanySheet.Form
        assertEquals(AppError.Offline, sheet.error)
        assertFalse(vm.state.value.saving)
        assertTrue(vm.state.value.memberships.isEmpty())
        repo.error = null
        vm.submit()
        runCurrent()
        assertNull(vm.state.value.sheet, "Retry (Save again) works once the error is gone")
    }

    @Test
    fun aFailedRemoveShowsTheErrorOnTheList() = runTest {
        val m = testMembership()
        val repo = FakeCompanyRepository(listOf(m))
        val vm = vm(repo)
        runCurrent()
        repo.error = AppError.ReadOnly
        vm.askRemove(m)
        vm.confirmRemove()
        runCurrent()
        assertEquals(AppError.ReadOnly, vm.state.value.error)
        assertEquals(1, vm.state.value.memberships.size)
    }

    @Test
    fun theCompanyListFailingIsShownInThePickerAndRetries() = runTest {
        val repo = FakeCompanyRepository().apply { listError = AppError.Offline }
        val vm = vm(repo)
        runCurrent()
        vm.openPicker()
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.companiesError)
        assertNull(vm.state.value.companies)
        repo.listError = null
        vm.loadCompanies()
        runCurrent()
        assertEquals(2, vm.state.value.companies?.size)
        assertNull(vm.state.value.companiesError)
        vm.pick(null) // "My company isn't listed" works whatever the list did
        assertTrue(vm.state.value.sheet is CompanySheet.Form)
    }
}
