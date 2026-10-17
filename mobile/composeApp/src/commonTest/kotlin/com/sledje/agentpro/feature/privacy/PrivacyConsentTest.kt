package com.sledje.agentpro.feature.privacy

import app.cash.turbine.test
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.domain.Age
import com.sledje.agentpro.feature.auth.data.NewAccount
import com.sledje.agentpro.feature.auth.data.signUpMetadata
import com.sledje.agentpro.feature.auth.ui.AuthFormMode
import com.sledje.agentpro.feature.auth.ui.AuthFormViewModel
import com.sledje.agentpro.feature.customers.ui.CustomerFormMode
import com.sledje.agentpro.feature.customers.ui.CustomerFormViewModel
import com.sledje.agentpro.feature.privacy.data.PrivacyLinks
import com.sledje.agentpro.feature.privacy.data.customerDutyKey
import com.sledje.agentpro.feature.privacy.data.parseRecordConsentResult
import com.sledje.agentpro.feature.privacy.data.recordConsentArgs
import com.sledje.agentpro.feature.privacy.ui.CustomerDutyViewModel
import com.sledje.agentpro.feature.privacy.ui.NoticeBlock
import com.sledje.agentpro.feature.privacy.ui.layoutNotice
import com.sledje.agentpro.testing.FakeAuthRepository
import com.sledje.agentpro.testing.FakeConsentRepository
import com.sledje.agentpro.testing.FakeCustomerRepository
import com.sledje.agentpro.testing.FakeEnquiryRepository
import com.sledje.agentpro.testing.TEST_TODAY
import com.sledje.agentpro.testing.testCustomer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** P1-4 (docs/PRIVACY.md): sign-up consent, the customer-data attestation, and the guardian-consent rule. */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyConsentTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // ---- Sign-up consent ----

    private fun signUpVm(auth: FakeAuthRepository): AuthFormViewModel = AuthFormViewModel(AuthFormMode.SignUp, auth).also {
        it.form.set(AuthFormViewModel.NAME, "Ramesh Kumar")
        it.form.set(AuthFormViewModel.MOBILE, "9876543210")
        it.form.set(AuthFormViewModel.EMAIL, "new@example.com")
        it.form.set(AuthFormViewModel.PASSWORD, "long enough")
        it.form.set(AuthFormViewModel.CONFIRM_PASSWORD, "long enough")
    }

    @Test
    fun signUpHasAnUntickedRequiredConsentAndSignInHasNone() {
        val vm = signUpVm(FakeAuthRepository())
        assertEquals(false, vm.form.value(AuthFormViewModel.CONSENT), "never pre-ticked")
        assertFalse(vm.form.isValid, "Create account stays disabled until the box is ticked")
        assertEquals(ValidationError.Required, vm.form.error(AuthFormViewModel.CONSENT))
        vm.form.set(AuthFormViewModel.CONSENT, true)
        assertTrue(vm.form.isValid)
        assertTrue(AuthFormViewModel.fieldsFor(AuthFormMode.SignIn).none { it.key == AuthFormViewModel.CONSENT })
        assertTrue(AuthFormViewModel.fieldsFor(AuthFormMode.ResetPassword).none { it.key == AuthFormViewModel.CONSENT })
    }

    @Test
    fun signUpIsNotSentWithoutConsent() = runTest {
        val auth = FakeAuthRepository()
        val vm = signUpVm(auth)
        vm.submit(AppLocale.En)
        advanceUntilIdle()
        assertTrue(auth.calls.isEmpty())
        assertTrue(auth.signUpConsents.isEmpty())
    }

    @Test
    fun signUpSendsTheNoticeVersionAndHashOfTheLanguageShown() = runTest {
        for (locale in AppLocale.active) {
            val auth = FakeAuthRepository()
            val vm = signUpVm(auth)
            vm.form.set(AuthFormViewModel.CONSENT, true)
            vm.state.test {
                awaitItem()
                vm.submit(locale)
                advanceUntilIdle()
                assertEquals("new@example.com", expectMostRecentItem().sentTo)
            }
            val sent = auth.signUpConsents.single()
            assertEquals("agent_signup", sent.noticeKey)
            assertEquals(PrivacyNotice.AgentSignup.version, sent.version)
            assertEquals(PrivacyNotice.AgentSignup.hash(locale), sent.hash)
            assertTrue(Regex("[0-9a-f]{64}").matches(sent.hash))
        }
        assertNotEquals(PrivacyNotice.AgentSignup.hash(AppLocale.En), PrivacyNotice.AgentSignup.hash(AppLocale.Hi))
    }

    @Test
    fun signUpMetadataIsTheConsentObject() {
        val consent = PrivacyNotice.AgentSignup.consent(AppLocale.Hi)
        val metadata = signUpMetadata(NewAccount(" Ramesh Kumar ", "+919876543210", "new@example.com", "long enough"), consent)
        assertEquals(setOf("full_name", "phone", "consent"), metadata.keys, "the password never goes in the metadata")
        assertEquals("Ramesh Kumar", metadata.getValue("full_name").jsonPrimitive.content)
        assertEquals("+919876543210", metadata.getValue("phone").jsonPrimitive.content)
        val consentJson = metadata.getValue("consent").jsonObject
        assertEquals(setOf("notice_key", "version", "hash"), consentJson.keys)
        assertEquals("agent_signup", consentJson.getValue("notice_key").jsonPrimitive.content)
        assertEquals(consent.version, consentJson.getValue("version").jsonPrimitive.int)
        assertEquals(consent.hash, consentJson.getValue("hash").jsonPrimitive.content)
    }

    // ---- record_consent plumbing ----

    @Test
    fun recordConsentArgsAndResult() {
        val c = PrivacyNotice.GuardianConsent.consent(AppLocale.En)
        assertEquals(setOf("notice_key", "version", "hash"), recordConsentArgs(c, null).keys)
        val withCustomer = recordConsentArgs(c, "cus-1")
        assertEquals("cus-1", withCustomer.getValue("customer_id").jsonPrimitive.content)
        assertEquals("guardian_consent", withCustomer.getValue("notice_key").jsonPrimitive.content)

        assertEquals(Result.Ok(Unit), parseRecordConsentResult(buildJsonObject { put("ok", JsonPrimitive(true)) }))
        val invalid = parseRecordConsentResult(
            buildJsonObject {
                put("ok", JsonPrimitive(false))
                put("error", JsonPrimitive("invalid"))
                put("field", JsonPrimitive("consent"))
            },
        )
        assertEquals(Result.Err(AppError.Validation("consent", "invalid")), invalid)
        assertIs<Result.Err<AppError>>(parseRecordConsentResult(JsonObject(emptyMap())))
    }

    @Test
    fun privacyLinks() {
        assertEquals("https://agentpro.site/a/ramesh/privacy", PrivacyLinks.sitePrivacy("ramesh", "https://agentpro.site/"))
        assertEquals("https://agentpro.site/terms", PrivacyLinks.terms("https://agentpro.site"))
        assertNull(PrivacyLinks.terms(""))
        assertEquals("privacy.customer_duty.u1", customerDutyKey("u1"))
    }

    // ---- Customer-data attestation ----

    @Test
    fun attestationIsNeededOnlyUntilAccepted() = runTest {
        val consents = FakeConsentRepository(accepted = false)
        val vm = CustomerDutyViewModel(consents)
        advanceUntilIdle()
        assertTrue(vm.state.value.needed)
        vm.accept(AppLocale.Hi)
        advanceUntilIdle()
        val duty = PrivacyNotice.AgentCustomerDuty.consent(AppLocale.Hi)
        assertEquals(listOf("duty:agent_customer_duty:${duty.version}:${duty.hash}"), consents.calls)
        assertFalse(vm.state.value.needed)

        // Once per account: the next form (a new view model over the same repository/store) doesn't ask, and
        // accepting again is ignored.
        val next = CustomerDutyViewModel(consents)
        advanceUntilIdle()
        assertFalse(next.state.value.needed)
        next.accept(AppLocale.En)
        advanceUntilIdle()
        assertEquals(1, consents.calls.size)
    }

    @Test
    fun attestationCantBeAcceptedOffline() = runTest {
        val consents = FakeConsentRepository(accepted = false).apply { error = AppError.Offline }
        val vm = CustomerDutyViewModel(consents)
        advanceUntilIdle()
        vm.accept(AppLocale.En)
        advanceUntilIdle()
        assertTrue(vm.state.value.needed, "still blocked")
        assertEquals(AppError.Offline, vm.state.value.error)
        assertFalse(vm.state.value.recording)
        vm.errorShown()
        assertNull(vm.state.value.error)
        consents.error = null
        vm.accept(AppLocale.En)
        advanceUntilIdle()
        assertFalse(vm.state.value.needed)
    }

    // ---- Guardian consent ----

    private val minor = LocalDate(2015, 3, 1)
    private val adult = LocalDate(1990, 3, 1)

    private fun form(repo: FakeCustomerRepository, consents: FakeConsentRepository, mode: CustomerFormMode = CustomerFormMode.Add()) =
        CustomerFormViewModel(mode, repo, FakeEnquiryRepository(), consents, today = { TEST_TODAY })

    @Test
    fun ageRule() {
        assertTrue(Age.isUnder18(LocalDate(2008, 10, 17), TEST_TODAY), "turns 18 tomorrow")
        assertFalse(Age.isUnder18(LocalDate(2008, 10, 16), TEST_TODAY), "18 today")
        assertFalse(Age.isUnder18(adult, TEST_TODAY))
    }

    @Test
    fun guardianToggleIsRequiredForAMinorAndRecordedAfterTheCustomerIsCreated() = runTest {
        val repo = FakeCustomerRepository()
        val consents = FakeConsentRepository(accepted = true)
        val vm = form(repo, consents)
        advanceUntilIdle()
        val f = vm.form.value!!
        f.set(CustomerFormViewModel.NAME, "Aarav")
        f.set(CustomerFormViewModel.PHONE, "9876543210")
        assertFalse(vm.requiresGuardian(), "no date of birth")
        f.set(CustomerFormViewModel.DOB, minor)
        assertTrue(vm.requiresGuardian())

        vm.save(AppLocale.En)
        advanceUntilIdle()
        assertTrue(repo.calls.isEmpty(), "can't save without the toggle")
        assertTrue(vm.guardianMissing.value)

        vm.setGuardianAgreed(true)
        assertFalse(vm.guardianMissing.value)
        vm.save(AppLocale.Hi)
        advanceUntilIdle()
        val id = repo.rows.value.single().id
        val g = PrivacyNotice.GuardianConsent.consent(AppLocale.Hi)
        assertEquals(listOf("guardian:$id:guardian_consent:${g.version}:${g.hash}"), consents.calls)
        assertEquals(id, vm.state.value.savedId)
    }

    @Test
    fun anAdultNeedsNoGuardianAndRecordsNothing() = runTest {
        val repo = FakeCustomerRepository()
        val consents = FakeConsentRepository(accepted = true)
        val vm = form(repo, consents)
        advanceUntilIdle()
        val f = vm.form.value!!
        f.set(CustomerFormViewModel.NAME, "Sunita")
        f.set(CustomerFormViewModel.PHONE, "9876543210")
        f.set(CustomerFormViewModel.DOB, adult)
        assertFalse(vm.requiresGuardian())
        vm.save(AppLocale.En)
        advanceUntilIdle()
        assertEquals(1, repo.rows.value.size)
        assertTrue(consents.calls.isEmpty())
    }

    @Test
    fun aFailedGuardianRecordKeepsTheFormAndTheRetryDoesNotAddTwice() = runTest {
        val repo = FakeCustomerRepository()
        val consents = FakeConsentRepository(accepted = true).apply { error = AppError.Offline }
        val vm = form(repo, consents)
        advanceUntilIdle()
        val f = vm.form.value!!
        f.set(CustomerFormViewModel.NAME, "Aarav")
        f.set(CustomerFormViewModel.PHONE, "9876543210")
        f.set(CustomerFormViewModel.DOB, minor)
        vm.setGuardianAgreed(true)
        vm.save(AppLocale.En)
        advanceUntilIdle()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertNull(vm.state.value.savedId, "stays on the form")
        consents.error = null
        vm.save(AppLocale.En)
        advanceUntilIdle()
        assertEquals(1, repo.rows.value.size, "no duplicate customer")
        assertEquals(2, consents.calls.size)
        assertEquals(repo.rows.value.single().id, vm.state.value.savedId)
    }

    @Test
    fun editingAnExistingMinorDoesNotAskAgainButMakingSomeoneAMinorDoes() = runTest {
        val existingMinor = testCustomer("m1").copy(dob = minor)
        val existingAdult = testCustomer("a1").copy(dob = adult)
        val repo = FakeCustomerRepository(listOf(existingMinor, existingAdult))
        val consents = FakeConsentRepository(accepted = true)

        val editMinor = form(repo, consents, CustomerFormMode.Edit("m1"))
        advanceUntilIdle()
        assertFalse(editMinor.requiresGuardian())

        val editAdult = form(repo, consents, CustomerFormMode.Edit("a1"))
        advanceUntilIdle()
        editAdult.form.value!!.set(CustomerFormViewModel.DOB, minor)
        assertTrue(editAdult.requiresGuardian())
    }

    // ---- Notice layout ----

    @Test
    fun theSignUpNoticeIsLaidOutFromItsGeneratedTexts() {
        val layout = layoutNotice(PrivacyNotice.AgentSignup)
        assertIs<NoticeBlock.Title>(layout.blocks.first())
        assertEquals(7, layout.blocks.count { it is NoticeBlock.Item }, "account, profile, website, billing, registrant, session, customers")
        assertTrue(layout.columns != null)
        val shown = layout.blocks.flatMap {
            when (it) {
                is NoticeBlock.Title -> listOf(it.text.key)
                is NoticeBlock.Paragraph -> listOf(it.text.key)
                is NoticeBlock.Item -> listOf(it.data.key, it.purpose.key, it.kept.key)
            }
        }
        assertFalse("cat_privacy_agent_signup_consent" in shown, "the checkbox line isn't body text")
        // Every other text of the notice is shown exactly once, in order.
        val expected = PrivacyNotice.AgentSignup.texts.map { it.key }.filterNot {
            it == "cat_privacy_agent_signup_consent" || it.startsWith("cat_privacy_notice_col_")
        }
        assertEquals(expected.sorted(), shown.sorted())
    }
}
