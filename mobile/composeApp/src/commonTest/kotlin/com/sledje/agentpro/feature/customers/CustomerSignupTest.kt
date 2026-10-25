package com.sledje.agentpro.feature.customers

import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.CustomerSignup
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.InMemoryKeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.customers.data.CustomerDraftDto
import com.sledje.agentpro.feature.customers.data.CustomerSignupJson
import com.sledje.agentpro.feature.customers.data.DraftJson
import com.sledje.agentpro.feature.customers.data.DraftSource
import com.sledje.agentpro.feature.customers.data.SignupLinkResult
import com.sledje.agentpro.feature.customers.data.SignupLinks
import com.sledje.agentpro.feature.customers.data.toEntity
import com.sledje.agentpro.feature.customers.data.toModel
import com.sledje.agentpro.feature.customers.ui.AutosaveStatus
import com.sledje.agentpro.feature.customers.ui.CustomerFormMode
import com.sledje.agentpro.feature.customers.ui.CustomerFormViewModel
import com.sledje.agentpro.feature.customers.ui.CustomerListViewModel
import com.sledje.agentpro.feature.customers.ui.DraftLine
import com.sledje.agentpro.feature.customers.ui.IncompleteFormsEvent
import com.sledje.agentpro.feature.customers.ui.IncompleteFormsViewModel
import com.sledje.agentpro.feature.customers.ui.IncompleteForms
import com.sledje.agentpro.feature.customers.ui.Presence
import com.sledje.agentpro.feature.customers.ui.SignupLiveState
import com.sledje.agentpro.feature.customers.ui.SignupLiveViewModel
import com.sledje.agentpro.feature.customers.ui.SignupNotice
import com.sledje.agentpro.feature.customers.ui.SignupStartState
import com.sledje.agentpro.feature.customers.ui.SignupStartViewModel
import com.sledje.agentpro.testing.FakeConsentRepository
import com.sledje.agentpro.testing.FakeCustomerDraftRepository
import com.sledje.agentpro.testing.FakeCustomerRepository
import com.sledje.agentpro.testing.FakeEnquiryRepository
import com.sledje.agentpro.testing.FakePolicyRepository
import com.sledje.agentpro.testing.TEST_NOW
import com.sledje.agentpro.testing.TEST_TODAY
import com.sledje.agentpro.testing.testDraft
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Customer sign-up link and Incomplete forms (DECISIONS #108): RPC parsing, URL, autosave, rows, live screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerSignupTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun json(text: String) = RowJson.parseToJsonElement(text)

    // --- data ---------------------------------------------------------------------------------

    @Test
    fun linkJsonParsesTheCreatedAnswerAndEveryRefusal() {
        val created = CustomerSignupJson.link(json("""{"ok":true,"draft_id":"d1","token":"abc","expires_at":"2026-10-30T10:00:00+00:00"}"""))
        assertEquals(SignupLinkResult.Created("d1", "abc", Instant.parse("2026-10-30T10:00:00Z")), created)
        assertEquals(SignupLinkResult.Unavailable, CustomerSignupJson.link(json("""{"ok":false,"error":"unavailable"}""")))
        assertEquals(SignupLinkResult.NoSite, CustomerSignupJson.link(json("""{"ok":false,"error":"no_site"}""")))
        assertEquals(SignupLinkResult.TooMany, CustomerSignupJson.link(json("""{"ok":false,"error":"too_many"}""")))
        assertNull(CustomerSignupJson.link(json("""{"ok":false,"error":"something_new"}""")), "an unknown refusal is not guessed")
        assertNull(CustomerSignupJson.link(json("""{"ok":true,"draft_id":"d1"}""")), "no token")
        assertNull(CustomerSignupJson.link(json("""{"ok":true,"token":"abc"}""")), "no draft")
        assertNull(CustomerSignupJson.link(json("[]")))
        assertNull(CustomerSignupJson.link(null))
    }

    @Test
    fun joinUrlPutsTheTokenInTheQueryOfTheJoinPage() {
        assertEquals("https://agentpro.site/a/ramesh/join?t=tok", SignupLinks.joinUrl("https://agentpro.site/a/ramesh", "tok"))
        assertEquals("https://agentpro.site/a/ramesh/join?t=tok", SignupLinks.joinUrl("https://agentpro.site/a/ramesh/", "tok"))
    }

    @Test
    fun aServerRowBecomesAModelAndFieldsKeepOnlyStrings() {
        val dto = RowJson.decodeFromJsonElement(
            CustomerDraftDto.serializer(),
            json(
                """{"id":"d1","agent_id":"a","source":"customer","fields":{"name":"Sunita","phone":"+919812345678","x":3},
                    "active_field":"phone","customer_seen_at":"2026-10-16T04:29:50+00:00","opened_at":"2026-10-16T04:20:00+00:00",
                    "consent":{"notice_key":"customer_signup","version":1},"link_expires_at":"2026-10-23T04:20:00+00:00",
                    "completed_at":null,"customer_id":null,"created_at":"2026-10-16T04:00:00+00:00","updated_at":"2026-10-16T04:29:50+00:00","deleted_at":null}""",
            ),
        )
        val draft = dto.toEntity().toModel()
        assertEquals(DraftSource.Customer, draft.source)
        assertEquals(mapOf("name" to "Sunita", "phone" to "+919812345678"), draft.fields)
        assertEquals("Sunita", draft.name)
        assertTrue(draft.consented)
        assertEquals("phone", draft.activeField)
        assertEquals(Instant.parse("2026-10-16T04:29:50Z"), draft.customerSeenAt)
        assertFalse(draft.isCompleted)
        val before = dto.copy(consent = null, fields = JsonObject(emptyMap()))
        assertFalse(before.toEntity().toModel().consented)
        assertEquals(emptyMap(), DraftJson.fields("not json"))
        assertEquals(setOf("name"), DraftJson.toJson(mapOf("name" to "A", "city" to " ")).keys)
    }

    // --- Incomplete forms rows ------------------------------------------------------------------

    private val now = TEST_NOW

    @Test
    fun anAgentDraftSaysWhenItWasFilledIn() {
        val d = testDraft(updatedAt = now - 90.seconds)
        assertEquals(DraftLine.ByYou(d.updatedAt), IncompleteForms.line(d, now))
    }

    @Test
    fun aCustomerLinkShowsPresenceFromTheLastSeenTime() {
        val link = testDraft(source = DraftSource.Customer, expiresAt = now + 5.days)
        assertEquals(Presence.NotOpened, IncompleteForms.presence(link, now))

        val opened = link.copy(openedAt = now - 600.seconds)
        assertEquals(Presence.LastSeen(opened.openedAt!!, opened.openedAt), IncompleteForms.presence(opened, now))

        val limit = CustomerSignup.presenceSeconds.seconds
        assertEquals(Presence.OnFormNow, IncompleteForms.presence(opened.copy(customerSeenAt = now - limit), now), "exactly at the limit is still on the form")
        val gone = opened.copy(customerSeenAt = now - limit - 1.seconds)
        assertEquals(Presence.LastSeen(gone.customerSeenAt!!, gone.openedAt), IncompleteForms.presence(gone, now))
        assertTrue(IncompleteForms.isOnForm(opened.copy(customerSeenAt = now - 5.seconds), now))
        assertFalse(IncompleteForms.isOnForm(opened, now))
        assertEquals(DraftLine.CustomerLink(Presence.NotOpened), IncompleteForms.line(link, now))
    }

    @Test
    fun anExpiredLinkSaysSoUnlessTheCustomerIsOnTheFormRightNow() {
        val expired = testDraft(source = DraftSource.Customer, openedAt = now - 3600.seconds, expiresAt = now - 60.seconds)
        assertEquals(Presence.Expired(expired.linkExpiresAt!!), IncompleteForms.presence(expired, now))
        assertEquals(Presence.OnFormNow, IncompleteForms.presence(expired.copy(customerSeenAt = now - 3.seconds), now))
    }

    // --- Incomplete forms screen ----------------------------------------------------------------

    @Test
    fun incompleteFormsListNewestFirstAndDeleteCanBeUndone() = runTest {
        val repo = FakeCustomerDraftRepository(
            listOf(
                testDraft("old", updatedAt = now - 3600.seconds),
                testDraft("new", updatedAt = now),
                testDraft("done", completedAt = now, customerId = "c1"),
            ),
        )
        val vm = IncompleteFormsViewModel(repo)
        val job = backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertEquals(listOf("new", "old"), (vm.state.value as UiState.Content).data.map { it.id })

        vm.delete("new")
        runCurrent()
        assertEquals(IncompleteFormsEvent.Deleted("new"), vm.event.value)
        assertEquals(listOf("old"), (vm.state.value as UiState.Content).data.map { it.id })
        vm.eventShown()
        vm.undoDelete("new")
        runCurrent()
        assertEquals(listOf("new", "old"), (vm.state.value as UiState.Content).data.map { it.id })

        repo.error = AppError.Offline
        vm.delete("old")
        runCurrent()
        assertEquals(IncompleteFormsEvent.Failed(AppError.Offline), vm.event.value)
        job.cancel()
    }

    @Test
    fun noUnfinishedFormsIsEmptyAndTheCustomersListCountsThem() = runTest {
        val repo = FakeCustomerDraftRepository()
        val incomplete = IncompleteFormsViewModel(repo)
        backgroundScope.launch { incomplete.state.collect {} }
        val list = CustomerListViewModel(FakeCustomerRepository(), FakePolicyRepository(), InMemoryKeyValueStore(), today = { TEST_TODAY }, drafts = repo)
        backgroundScope.launch { list.incompleteCount.collect {} }
        runCurrent()
        assertEquals(UiState.Empty, incomplete.state.value)
        assertEquals(0, list.incompleteCount.value)
        repo.rows.value = listOf(testDraft("a"), testDraft("b"), testDraft("c", completedAt = now))
        runCurrent()
        assertEquals(2, list.incompleteCount.value, "a completed form is not incomplete")
    }

    // --- the customer form's autosave -----------------------------------------------------------

    private class FormRig(
        val drafts: FakeCustomerDraftRepository = FakeCustomerDraftRepository(),
        val customers: FakeCustomerRepository = FakeCustomerRepository(),
        mode: CustomerFormMode = CustomerFormMode.Add(),
    ) {
        val vm = CustomerFormViewModel(mode, customers, FakeEnquiryRepository(), FakeConsentRepository(), today = { TEST_TODAY }, drafts = drafts)
        val form get() = vm.form.value!!

        /** Types [value] into [key] the way the screen does: set the field, then tell the ViewModel. */
        fun type(key: String, value: String) {
            form.set(key, value)
            vm.onValuesChanged(form.values())
        }
    }

    @Test
    fun autosaveWaitsForTheFirstNonEmptyChangeAndThePause() = runTest {
        val rig = FormRig()
        runCurrent()
        rig.vm.onValuesChanged(rig.form.values())
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(rig.drafts.calls.isEmpty(), "an empty form saves nothing")
        assertEquals(AutosaveStatus.Idle, rig.vm.autosave.value)

        rig.type("name", "Sun")
        advanceTimeBy(CustomerFormViewModel.AUTOSAVE_DELAY_MS - 100)
        runCurrent()
        assertTrue(rig.drafts.calls.isEmpty(), "still within the pause")
        rig.type("name", "Sunita")
        advanceTimeBy(CustomerFormViewModel.AUTOSAVE_DELAY_MS - 100)
        runCurrent()
        assertTrue(rig.drafts.calls.isEmpty(), "each change restarts the pause")
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("create:{name=Sunita}"), rig.drafts.calls, "one insert with the last value")
        assertEquals(AutosaveStatus.Saved, rig.vm.autosave.value)
    }

    @Test
    fun theFirstSaveInsertsAndLaterOnesUpdateTheSameDraft() = runTest {
        val rig = FormRig()
        runCurrent()
        rig.type("name", "Sunita")
        advanceTimeBy(2_000); runCurrent()
        rig.type("phone", "9812345678")
        advanceTimeBy(2_000); runCurrent()
        rig.type("city", "Pune")
        advanceTimeBy(2_000); runCurrent()
        assertEquals(
            listOf(
                "create:{name=Sunita}",
                "update:draft-1:{name=Sunita, phone=9812345678}",
                "update:draft-1:{city=Pune, name=Sunita, phone=9812345678}",
            ),
            rig.drafts.calls,
        )
        rig.type("city", "Pune")
        advanceTimeBy(2_000); runCurrent()
        assertEquals(3, rig.drafts.calls.size, "an unchanged form saves nothing")
    }

    @Test
    fun aPickedContactIsOnlySavedOnceTheAgentChangesSomething() = runTest {
        val rig = FormRig(mode = CustomerFormMode.Add(name = "Kiran Rao", phone = "098765 43210"))
        runCurrent()
        rig.vm.onValuesChanged(rig.form.values())
        advanceTimeBy(5_000); runCurrent()
        assertTrue(rig.drafts.calls.isEmpty())
        rig.type("city", "Nagpur")
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("create:{city=Nagpur, name=Kiran Rao, phone=+919876543210}"), rig.drafts.calls)
    }

    @Test
    fun aFailedAutosaveShowsNotSavedAndTheNextChangeRetries() = runTest {
        val rig = FormRig()
        runCurrent()
        rig.drafts.failWrites = 1
        rig.type("name", "Sunita")
        advanceTimeBy(2_000); runCurrent()
        assertEquals(AutosaveStatus.NotSavedOffline, rig.vm.autosave.value)
        rig.type("name", "Sunita S")
        advanceTimeBy(2_000); runCurrent()
        assertEquals(AutosaveStatus.Saved, rig.vm.autosave.value)
        assertEquals(listOf("create:{name=Sunita}", "create:{name=Sunita S}"), rig.drafts.calls, "still no draft, so it inserts again")

        rig.drafts.failWrites = 1
        rig.drafts.error = AppError.Unknown("boom")
        rig.type("city", "Pune")
        advanceTimeBy(2_000); runCurrent()
        assertEquals(AutosaveStatus.NotSaved, rig.vm.autosave.value, "a non-network failure is not called offline")
    }

    @Test
    fun leavingTheFormSavesAPendingChangeAtOnce() = runTest {
        val rig = FormRig()
        runCurrent()
        rig.type("name", "Sunita")
        rig.vm.flushNow()
        runCurrent()
        assertEquals(listOf("create:{name=Sunita}"), rig.drafts.calls)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, rig.drafts.calls.size, "the cancelled pause does not save again")
    }

    @Test
    fun savingTheCustomerCompletesTheDraftAndStopsAutosaving() = runTest {
        val rig = FormRig()
        runCurrent()
        rig.type("name", "Sunita")
        rig.type("phone", "9812345678")
        advanceTimeBy(2_000); runCurrent()
        rig.vm.save(AppLocale.En)
        runCurrent()
        val customer = rig.customers.rows.value.single()
        assertEquals(customer.id, rig.vm.state.value.savedId)
        assertEquals("complete:draft-1:${customer.id}", rig.drafts.calls.last())
        assertTrue(rig.drafts.rows.value.single().isCompleted)
        rig.type("city", "Pune")
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, rig.drafts.calls.size, "nothing is autosaved after the customer exists")
    }

    @Test
    fun savingBeforeAnyAutosaveHasNoDraftToComplete() = runTest {
        val rig = FormRig()
        runCurrent()
        rig.type("name", "Sunita")
        rig.type("phone", "9812345678")
        rig.vm.save(AppLocale.En)
        runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertNull(rig.vm.state.value.error)
        assertTrue(rig.vm.state.value.savedId != null)
        assertTrue(rig.drafts.calls.isEmpty(), "the pause was cancelled and there was no draft")
    }

    @Test
    fun aFailedCompletionIsRetriedOnce() = runTest {
        val rig = FormRig()
        runCurrent()
        rig.type("name", "Sunita")
        rig.type("phone", "9812345678")
        advanceTimeBy(2_000); runCurrent()
        rig.drafts.failWrites = 1
        rig.vm.save(AppLocale.En)
        runCurrent()
        assertEquals(1, rig.customers.rows.value.size)
        assertTrue(rig.vm.state.value.savedId != null)
        assertTrue(rig.drafts.rows.value.single().isCompleted)
    }

    @Test
    fun aCompletionThatKeepsFailingNeverHoldsTheSavedCustomerOnTheForm() = runTest {
        val rig = FormRig()
        runCurrent()
        rig.type("name", "Sunita")
        rig.type("phone", "9812345678")
        advanceTimeBy(2_000); runCurrent()
        rig.drafts.failWrites = CustomerFormViewModel.COMPLETE_ATTEMPTS
        rig.vm.save(AppLocale.En)
        runCurrent()
        assertEquals(1, rig.customers.rows.value.size)
        assertTrue(rig.vm.state.value.savedId != null, "the customer exists, so the form closes")
        assertTrue(rig.vm.state.value.error == null)
        assertFalse(rig.drafts.rows.value.single().isCompleted, "the draft stays under Incomplete forms")
    }

    @Test
    fun resumingADraftPrefillsItAndKeepsSavingIntoTheSameOne() = runTest {
        val drafts = FakeCustomerDraftRepository(listOf(testDraft("d9", fields = mapOf("name" to "Sunita", "dob" to "2001-02-03"))))
        val rig = FormRig(drafts = drafts, mode = CustomerFormMode.Add(draftId = "d9"))
        runCurrent()
        assertEquals("Sunita", rig.form.text("name"))
        assertEquals(kotlinx.datetime.LocalDate(2001, 2, 3), rig.form.value("dob"))
        assertFalse(rig.form.isDirty, "a resumed form is not 'changed'")
        rig.vm.onValuesChanged(rig.form.values())
        advanceTimeBy(5_000); runCurrent()
        assertTrue(drafts.calls.isEmpty())
        rig.type("city", "Pune")
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("update:d9:{city=Pune, dob=2001-02-03, name=Sunita}"), drafts.calls)
    }

    @Test
    fun aDraftThatIsGoneOrNotTheAgentsOwnStartsAFreshForm() = runTest {
        val drafts = FakeCustomerDraftRepository(listOf(testDraft("link", source = DraftSource.Customer, fields = mapOf("name" to "X"))))
        val missing = FormRig(drafts = drafts, mode = CustomerFormMode.Add(draftId = "nope"))
        val link = FormRig(drafts = drafts, mode = CustomerFormMode.Add(draftId = "link"))
        runCurrent()
        assertEquals("", missing.form.text("name"))
        assertEquals("", link.form.text("name"))
        missing.type("name", "New")
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("create:{name=New}"), drafts.calls)
    }

    @Test
    fun editAndConvertDoNotAutosave() = runTest {
        val customers = FakeCustomerRepository(listOf(com.sledje.agentpro.testing.testCustomer("c1")))
        val drafts = FakeCustomerDraftRepository()
        val rig = FormRig(drafts, customers, CustomerFormMode.Edit("c1"))
        runCurrent()
        rig.type("city", "Nagpur")
        advanceTimeBy(5_000); runCurrent()
        assertTrue(drafts.calls.isEmpty())
    }

    // --- start and live screens -----------------------------------------------------------------

    @Test
    fun startMakesTheLinkAndHandsOverTheTokenOrExplainsWhyNot() = runTest {
        val repo = FakeCustomerDraftRepository()
        val ok = SignupStartViewModel(repo)
        runCurrent()
        assertEquals(SignupStartState.Created("draft-link", "tok123"), ok.state.value)

        repo.linkResult = Result.Ok(SignupLinkResult.NoSite)
        ok.create(); runCurrent()
        assertEquals(SignupStartState.Failed(SignupNotice.NoSite), ok.state.value)
        repo.linkResult = Result.Ok(SignupLinkResult.TooMany)
        ok.create(); runCurrent()
        assertEquals(SignupStartState.Failed(SignupNotice.TooMany), ok.state.value)
        repo.linkResult = Result.Ok(SignupLinkResult.Unavailable)
        ok.create(); runCurrent()
        assertEquals(SignupStartState.Failed(SignupNotice.Unavailable), ok.state.value)
        repo.linkResult = Result.Err(AppError.Offline)
        ok.create(); runCurrent()
        assertEquals(SignupStartState.Failed(SignupNotice.Failed(AppError.Offline)), ok.state.value)
    }

    private fun live(repo: FakeCustomerDraftRepository, token: String?, site: com.sledje.agentpro.feature.website.data.Site? = testSite("ramesh", published = true)) =
        SignupLiveViewModel("d1", token, repo, MutableStateFlow(site))

    @Test
    fun theLiveScreenShowsTheLinkOnlyWhileTheTokenIsKnown() = runTest {
        val repo = FakeCustomerDraftRepository(listOf(testDraft("d1", source = DraftSource.Customer)))
        val fresh = live(repo, "tok123")
        backgroundScope.launch { fresh.state.collect {} }
        runCurrent()
        val shown = fresh.state.value as SignupLiveState.Shown
        assertTrue(shown.url!!.endsWith("/a/ramesh/join?t=tok123"), shown.url)
        assertEquals(1, repo.refreshes)

        val reopened = live(repo, null)
        backgroundScope.launch { reopened.state.collect {} }
        runCurrent()
        assertNull((reopened.state.value as SignupLiveState.Shown).url)
    }

    @Test
    fun theLiveScreenFollowsTheDraftAsTheCustomerFillsItIn() = runTest {
        val repo = FakeCustomerDraftRepository(listOf(testDraft("d1", source = DraftSource.Customer)))
        val vm = live(repo, "tok")
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertFalse((vm.state.value as SignupLiveState.Shown).draft.consented)
        repo.rows.value = listOf(testDraft("d1", source = DraftSource.Customer, consented = true, fields = mapOf("name" to "Sun"), activeField = "name", seenAt = now))
        runCurrent()
        val draft = (vm.state.value as SignupLiveState.Shown).draft
        assertTrue(draft.consented)
        assertEquals("Sun", draft.fields["name"])
        assertEquals("name", draft.activeField)
        repo.rows.value = listOf(draft.copy(completedAt = now, customerId = "c9", fields = emptyMap()))
        runCurrent()
        val done = (vm.state.value as SignupLiveState.Shown).draft
        assertTrue(done.isCompleted)
        assertEquals("c9", done.customerId)
    }

    @Test
    fun aDeletedDraftIsGoneOnlyAfterTheFirstRefresh() = runTest {
        val repo = FakeCustomerDraftRepository()
        val vm = live(repo, "tok")
        backgroundScope.launch { vm.state.collect {} }
        assertEquals(SignupLiveState.Loading, vm.state.value, "nothing yet: still loading")
        runCurrent()
        assertEquals(SignupLiveState.Gone, vm.state.value)
    }

    @Test
    fun sendANewLinkRenewsThenSharesTheNewUrl() = runTest {
        val repo = FakeCustomerDraftRepository(listOf(testDraft("d1", source = DraftSource.Customer)))
        val vm = live(repo, null)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        vm.renew()
        runCurrent()
        assertEquals("renew:d1", repo.calls.single())
        assertEquals("https://agentpro.site/a/ramesh/join?t=tok456".substringAfter("/a/"), vm.share.value!!.substringAfter("/a/"))
        assertTrue((vm.state.value as SignupLiveState.Shown).url!!.endsWith("t=tok456"))
        vm.shareHandled()
        assertNull(vm.share.value)

        repo.renewResult = Result.Ok(SignupLinkResult.TooMany)
        vm.renew(); runCurrent()
        assertEquals(SignupNotice.TooMany, (vm.state.value as SignupLiveState.Shown).notice)
        repo.renewResult = Result.Err(AppError.Offline)
        vm.renew(); runCurrent()
        assertEquals(SignupNotice.Failed(AppError.Offline), (vm.state.value as SignupLiveState.Shown).notice)
        assertNull(vm.share.value)
    }
}
