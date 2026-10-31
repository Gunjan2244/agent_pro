package com.sledje.agentpro.feature.offers

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.offers.data.CompanyPlan
import com.sledje.agentpro.feature.offers.data.PlanChoice
import com.sledje.agentpro.feature.offers.data.PlanJson
import com.sledje.agentpro.feature.offers.data.PlansRepository
import com.sledje.agentpro.feature.offers.ui.PlansViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Website → Plans on your site (DECISIONS #129): the `my_company_plans` mapping and the choice screen's logic. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun plan(id: String, company: String = "Acme Life") =
        CompanyPlan(id, "c-$company", company, "Plan $id", planNo = null, uin = null, interest = "term", url = "https://acme.example/$id")

    private class FakePlans(var choice: PlanChoice, var fail: AppError? = null) : PlansRepository {
        val saves = mutableListOf<List<String>?>()
        override suspend fun load(): AppResult<PlanChoice> = fail?.let { Result.Err(it) } ?: Result.Ok(choice)
        override suspend fun save(planIds: List<String>?): AppResult<PlanChoice> {
            saves += planIds
            fail?.let { return Result.Err(it) }
            choice = choice.copy(auto = planIds == null, selected = planIds.orEmpty())
            return Result.Ok(choice)
        }
    }

    private fun TestScope.vm(repo: PlansRepository) = PlansViewModel(repo).also { runCurrent() }

    @Test
    fun theAnswerBecomesModels() {
        val answer = Json.parseToJsonElement(
            """{"choice":"custom","selected":["p2"],"plans":[
              {"id":"p1","company_id":"c1","company":"Acme","name":"Term One","plan_no":"914","uin":"512N339V02","interest":"term","url":"https://acme.example/1"},
              {"id":"p2","company_id":"c1","company":"Acme","name":"Saver","plan_no":null,"uin":null,"interest":"endowment","url":"https://acme.example/2"},
              {"id":"bad","company":"Acme"}]}""",
        ).jsonObject
        val c = PlanJson.choice(answer)
        assertFalse(c.auto)
        assertEquals(listOf("p2"), c.selected)
        assertEquals(listOf("p1", "p2"), c.plans.map { it.id }, "a row without its facts is skipped")
        assertEquals("914", c.plans[0].planNo)
        assertNull(c.plans[1].uin)
        assertTrue(PlanJson.choice(Json.parseToJsonElement("""{"choice":"auto"}""").jsonObject).auto)
    }

    @Test
    fun turningAutomaticOffStartsFromEveryPlanAndSavesThemInOrder() = runTest {
        val repo = FakePlans(PlanChoice(auto = true, selected = emptyList(), plans = listOf(plan("a"), plan("b"), plan("c", "Bharat"))))
        val vm = vm(repo)
        assertTrue(vm.state.value.auto)
        vm.setAuto(false)
        assertEquals(setOf("a", "b", "c"), vm.state.value.selected, "nothing disappears by surprise")
        vm.toggle("b")
        vm.save()
        runCurrent()
        assertEquals(listOf(listOf("a", "c")), repo.saves)
        assertTrue(vm.state.value.saved)
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun automaticSavesNullAndCompanyToggleSelectsOrClearsTheCompany() = runTest {
        val repo = FakePlans(PlanChoice(auto = false, selected = listOf("a"), plans = listOf(plan("a"), plan("b"), plan("c", "Bharat"))))
        val vm = vm(repo)
        vm.toggleCompany("Acme Life")
        assertEquals(setOf("a", "b"), vm.state.value.selected)
        vm.toggleCompany("Acme Life")
        assertTrue(vm.state.value.selected.isEmpty())
        vm.setAuto(true)
        vm.save()
        runCurrent()
        assertEquals(listOf<List<String>?>(null), repo.saves)
        assertEquals(listOf("Acme Life", "Bharat"), vm.state.value.byCompany.map { it.first })
    }

    @Test
    fun aFailedLoadShowsTheErrorAndRetries() = runTest {
        val repo = FakePlans(PlanChoice(true, emptyList(), listOf(plan("a"))), fail = AppError.Offline)
        val vm = vm(repo)
        assertEquals(AppError.Offline, vm.state.value.error)
        repo.fail = null
        vm.load()
        runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.plans.size)
    }
}
