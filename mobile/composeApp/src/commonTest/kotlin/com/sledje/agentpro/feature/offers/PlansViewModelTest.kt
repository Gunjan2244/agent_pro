package com.sledje.agentpro.feature.offers

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.offers.data.CompanyPlan
import com.sledje.agentpro.feature.offers.data.PlanChoice
import com.sledje.agentpro.feature.offers.data.PlanJson
import com.sledje.agentpro.feature.offers.data.PlansRepository
import com.sledje.agentpro.feature.offers.ui.PlanFeaturesViewModel
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

/**
 * Website → Plans on your site (DECISIONS #129, #130): the `my_company_plans` mapping, the choice screen's logic
 * (featured plans included) and the key-features screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun plan(id: String, company: String = "Acme Life") =
        CompanyPlan(id, "c-$company", company, "Plan $id", planNo = null, uin = null, interest = "term", url = "https://acme.example/$id")

    private class FakePlans(var choice: PlanChoice, var fail: AppError? = null) : PlansRepository {
        val saves = mutableListOf<List<String>?>()
        val featuredSaves = mutableListOf<List<String>?>()
        val featureSaves = mutableListOf<Pair<String, List<String>?>>()
        override suspend fun load(): AppResult<PlanChoice> = fail?.let { Result.Err(it) } ?: Result.Ok(choice)
        override suspend fun save(planIds: List<String>?): AppResult<PlanChoice> {
            saves += planIds
            fail?.let { return Result.Err(it) }
            choice = choice.copy(auto = planIds == null, selected = planIds.orEmpty())
            return Result.Ok(choice)
        }
        override suspend fun saveFeatured(planIds: List<String>?): AppResult<PlanChoice> {
            featuredSaves += planIds
            fail?.let { return Result.Err(it) }
            choice = choice.copy(featuredAuto = planIds == null, featured = planIds.orEmpty())
            return Result.Ok(choice)
        }
        override suspend fun saveFeatures(planId: String, features: List<String>?): AppResult<PlanChoice> {
            featureSaves += planId to features
            fail?.let { return Result.Err(it) }
            choice = choice.copy(plans = choice.plans.map { if (it.id == planId) it.copy(ownFeatures = features) else it })
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
    fun planPagesFieldsBecomeModels() {
        val answer = Json.parseToJsonElement(
            """{"choice":"auto","selected":[],"featured":"custom","featured_ids":["p2"],"plans":[
              {"id":"p1","company_id":"c1","company":"Acme","name":"Term One","interest":"term","url":"https://acme.example/1","slug":"term-one",
               "image_url":null,"features":["Pure protection","Cover to 85"],"own_features":null},
              {"id":"p2","company_id":"c1","company":"Acme","name":"Saver","interest":"endowment","url":"https://acme.example/2",
               "image_url":"https://cdn.example/s.webp","features":[],"own_features":["My words"]}]}""",
        ).jsonObject
        val c = PlanJson.choice(answer)
        assertFalse(c.featuredAuto)
        assertEquals(listOf("p2"), c.featured)
        assertEquals("term-one", c.plans[0].slug)
        assertEquals(listOf("Pure protection", "Cover to 85"), c.plans[0].features)
        assertNull(c.plans[0].ownFeatures, "null = the insurer's words")
        assertNull(c.plans[0].imageUrl, "no picture: the default picture of the type")
        assertEquals(listOf("My words"), c.plans[1].ownFeatures)
        assertEquals("https://cdn.example/s.webp", c.plans[1].imageUrl)
        assertTrue(PlanJson.choice(Json.parseToJsonElement("""{"choice":"auto"}""").jsonObject).featuredAuto)
    }

    @Test
    fun starringStartsFromTheAutomaticFeaturedPlansAndSavesThem() = runTest {
        val plans = (1..8).map { plan("p$it") }
        val repo = FakePlans(PlanChoice(auto = true, selected = emptyList(), plans = plans))
        val vm = vm(repo)
        assertEquals((1..6).map { "p$it" }, vm.state.value.starred, "automatic: the first 6 the site shows")
        vm.toggleFeatured("p7")
        assertTrue(vm.state.value.featuredFull, "a seventh star is refused")
        assertFalse(vm.state.value.dirty)
        vm.consumeFeaturedFull()
        vm.toggleFeatured("p2")
        assertEquals(listOf("p1", "p3", "p4", "p5", "p6"), vm.state.value.starred)
        vm.toggleFeatured("p8")
        vm.save()
        runCurrent()
        assertEquals(listOf<List<String>?>(null), repo.saves, "the plan choice stays automatic")
        assertEquals(listOf<List<String>?>(listOf("p1", "p3", "p4", "p5", "p6", "p8")), repo.featuredSaves)
        assertFalse(vm.state.value.featuredAuto)
    }

    @Test
    fun aPlanTheSiteDoesNotShowCanNotBeStarredAndUnchangedStarsAreNotSaved() = runTest {
        val repo = FakePlans(PlanChoice(auto = false, selected = listOf("a"), plans = listOf(plan("a"), plan("b")), featuredAuto = false, featured = listOf("a")))
        val vm = vm(repo)
        vm.toggleFeatured("b")
        assertEquals(listOf("a"), vm.state.value.starred)
        vm.toggle("b")
        vm.save()
        runCurrent()
        assertEquals(listOf<List<String>?>(listOf("a", "b")), repo.saves)
        assertTrue(repo.featuredSaves.isEmpty(), "the stars didn't change")
    }

    @Test
    fun keyFeatures_startFromTheInsurersWordsSaveTheAgentsAndGoBack() = runTest {
        val p = plan("a").copy(features = listOf("Pure protection for the family", "Cover up to age 85"))
        val repo = FakePlans(PlanChoice(auto = true, selected = emptyList(), plans = listOf(p)))
        val vm = PlanFeaturesViewModel(repo, "a").also { runCurrent() }
        assertFalse(vm.state.value.own)
        assertEquals("Pure protection for the family", vm.form.text("point1"))
        assertFalse(vm.canSave(), "nothing changed yet")
        vm.form.set("point1", "  Simple   cover for your family ")
        vm.form.set("point2", "")
        vm.form.set("point3", "Ask me about riders")
        assertTrue(vm.canSave())
        vm.save()
        runCurrent()
        assertEquals("a" to listOf("Simple cover for your family", "Ask me about riders"), repo.featureSaves.single())
        assertTrue(vm.state.value.own && vm.state.value.saved)
        vm.resetToInsurer()
        runCurrent()
        assertEquals("a" to null, repo.featureSaves.last())
        assertFalse(vm.state.value.own)
        assertTrue(vm.state.value.reset)
        assertEquals("Pure protection for the family", vm.form.text("point1"), "the insurer's words are back in the form")
    }

    @Test
    fun keyFeatures_aTooShortPointCanNotBeSavedAndAMissingPlanIsAnError() = runTest {
        val repo = FakePlans(PlanChoice(auto = true, selected = emptyList(), plans = listOf(plan("a"))))
        val vm = PlanFeaturesViewModel(repo, "a").also { runCurrent() }
        vm.form.set("point1", "ok")
        assertFalse(vm.canSave())
        vm.save()
        runCurrent()
        assertTrue(repo.featureSaves.isEmpty())
        val gone = PlanFeaturesViewModel(repo, "zzz").also { runCurrent() }
        assertTrue(gone.state.value.error != null)
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
