package com.sledje.agentpro.testing

import com.sledje.agentpro.app.AppDependencies
import com.sledje.agentpro.app.AppEvents
import com.sledje.agentpro.app.DataSync
import com.sledje.agentpro.app.DeepLinks
import com.sledje.agentpro.app.OnboardingSession
import com.sledje.agentpro.app.SyncStatus
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.NoticeConsent
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.feature.privacy.data.ConsentRepository
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.InMemoryKeyValueStore
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.features.FeatureFlags
import com.sledje.agentpro.core.platform.LocaleChoice
import com.sledje.agentpro.core.platform.LocaleController
import com.sledje.agentpro.core.platform.NetworkMonitor
import com.sledje.agentpro.core.platform.UrlLauncher
import com.sledje.agentpro.feature.auth.data.AuthRepository
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.auth.data.NewAccount
import com.sledje.agentpro.feature.auth.data.SignUpOutcome
import com.sledje.agentpro.feature.settings.data.Profile
import com.sledje.agentpro.feature.settings.data.ProfileInput
import com.sledje.agentpro.feature.settings.data.ProfileRepository
import com.sledje.agentpro.feature.website.data.NewSite
import com.sledje.agentpro.feature.website.data.PublishOutcome
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteRepository
import com.sledje.agentpro.feature.website.data.SiteAppearance
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.feature.website.data.ContentTooLarge
import com.sledje.agentpro.core.catalog.AccentSwatch
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Fakes of every repository and platform seam the app foundation uses. No backend is ever called. */

class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    override val isOnline = MutableStateFlow(online)
}

class FakeUrlLauncher(var emailAppAvailable: Boolean = true) : UrlLauncher {
    var emailAppOpened = 0
    override fun openEmailApp(): Boolean { emailAppOpened++; return emailAppAvailable }
    val shared = mutableListOf<String>()
    val opened = mutableListOf<String>()
    override fun share(text: String): Boolean { shared += text; return true }
    override fun openUrl(url: String): Boolean { opened += url; return true }
    /** Call / SMS / email actions (Phases 6–7), e.g. "call:+919812345678". */
    val actions = mutableListOf<String>()
    override fun call(phone: String): Boolean { actions += "call:$phone"; return true }
    override fun sms(phone: String): Boolean { actions += "sms:$phone"; return true }
    override fun email(address: String): Boolean { actions += "email:$address"; return true }
}

class FakeLocaleController(initial: LocaleChoice? = LocaleChoice(null)) : LocaleController {
    override val choice = MutableStateFlow(initial)
    override suspend fun set(locale: AppLocale?) { choice.value = LocaleChoice(locale) }
}

const val TEST_USER = "00000000-0000-0000-0000-00000000000a"

fun testProfile(
    fullName: String? = "Ramesh Kumar",
    phone: String? = "+919876543210",
    designation: String? = "LIC Agent",
    city: String? = "Pune",
) = Profile(
    id = TEST_USER, fullName = fullName, phone = phone, email = "ramesh@example.com", designation = designation,
    agencyCode = null, branch = null, city = city, photoUrl = null, preferredLanguage = null,
)

fun testSite(slug: String = "ramesh-kumar", published: Boolean = false) = Site(
    id = "site-1", slug = slug, templateKey = "classic", accent = null, locale = null,
    draftContent = kotlinx.serialization.json.JsonObject(emptyMap()), isPublished = published, publishedAt = null,
)

/**
 * Auth fake. [signInResult] etc. decide the outcome; a successful sign-in switches [state] to
 * signed in, so the gate moves on as it would with Supabase.
 */
class FakeAuthRepository(initial: AuthState = AuthState.SignedOut) : AuthRepository {
    override val state = MutableStateFlow(initial)
    var signInResult: AppResult<Unit> = Result.Ok(Unit)
    var signUpResult: AppResult<SignUpOutcome> = Result.Ok(SignUpOutcome.ConfirmEmail)
    var resetResult: AppResult<Unit> = Result.Ok(Unit)
    var resendResult: AppResult<Unit> = Result.Ok(Unit)
    var updatePasswordResult: AppResult<Unit> = Result.Ok(Unit)
    var callbackResult: AppResult<Unit> = Result.Ok(Unit)
    val calls = mutableListOf<String>()

    override suspend fun signIn(email: String, password: String): AppResult<Unit> {
        calls += "signIn:$email"
        if (signInResult is Result.Ok) state.value = AuthState.SignedIn(TEST_USER, email)
        return signInResult
    }

    /** The consent each sign-up sent (`data.consent` in the real call). */
    val signUpConsents = mutableListOf<NoticeConsent>()
    val signUpAccounts = mutableListOf<NewAccount>()

    /** Whether [signInAfterConfirmation] finds the email confirmed. */
    var emailConfirmed = false

    override suspend fun signUp(account: NewAccount, consent: NoticeConsent): AppResult<SignUpOutcome> {
        calls += "signUp:${account.email}"
        signUpAccounts += account
        signUpConsents += consent
        val result = signUpResult
        if (result is Result.Ok && result.value == SignUpOutcome.SignedIn) state.value = AuthState.SignedIn(TEST_USER, account.email)
        return result
    }

    override suspend fun signInAfterConfirmation(): Boolean {
        calls += "signInAfterConfirmation"
        if (emailConfirmed) state.value = AuthState.SignedIn(TEST_USER, signUpAccounts.lastOrNull()?.email)
        return emailConfirmed
    }
    override suspend fun resendConfirmation(email: String): AppResult<Unit> { calls += "resend:$email"; return resendResult }
    override suspend fun sendPasswordReset(email: String): AppResult<Unit> { calls += "reset:$email"; return resetResult }
    override suspend fun updatePassword(password: String): AppResult<Unit> { calls += "updatePassword"; return updatePasswordResult }

    override suspend fun signOut(): AppResult<Unit> {
        calls += "signOut"
        state.value = AuthState.SignedOut
        return Result.Ok(Unit)
    }

    /** A successful callback starts a session, like importing it in supabase-kt. */
    override suspend fun handleAuthCallback(url: String): AppResult<Unit> {
        calls += "callback:$url"
        if (callbackResult is Result.Ok) state.value = AuthState.SignedIn(TEST_USER, "ramesh@example.com")
        return callbackResult
    }
}

/**
 * Consent fake. [dutyAccepted] starts true in whole-app fakes so existing flows aren't gated; privacy tests set
 * it false. [error] fails the next records (e.g. Offline). [calls] holds "duty:<locale key>:<hash>" and
 * "guardian:<customerId>:<locale key>:<hash>".
 */
class FakeConsentRepository(accepted: Boolean = false) : ConsentRepository {
    val accepted = MutableStateFlow(accepted)
    var error: AppError? = null
    val calls = mutableListOf<String>()

    override fun customerDutyAccepted(): Flow<Boolean> = accepted

    override suspend fun acceptCustomerDuty(locale: AppLocale): AppResult<Unit> {
        val c = PrivacyNotice.AgentCustomerDuty.consent(locale)
        calls += "duty:${c.noticeKey}:${c.version}:${c.hash}"
        error?.let { return Result.Err(it) }
        accepted.value = true
        return Result.Ok(Unit)
    }

    override suspend fun recordGuardianConsent(customerId: String, locale: AppLocale): AppResult<Unit> {
        val c = PrivacyNotice.GuardianConsent.consent(locale)
        calls += "guardian:$customerId:${c.noticeKey}:${c.version}:${c.hash}"
        error?.let { return Result.Err(it) }
        return Result.Ok(Unit)
    }
}

class FakeProfileRepository(initial: Profile? = null) : ProfileRepository {
    val profile = MutableStateFlow(initial)
    var updateError: AppError? = null
    val updates = mutableListOf<ProfileInput>()

    override fun observeMine(): Flow<Profile?> = profile

    override suspend fun update(input: ProfileInput): AppResult<Profile> {
        updates += input
        updateError?.let { return Result.Err(it) }
        val updated = (profile.value ?: testProfile()).copy(
            fullName = input.fullName, phone = input.phone, designation = input.designation, city = input.city,
            agencyCode = input.agencyCode, branch = input.branch, photoUrl = input.photoUrl,
        )
        profile.value = updated
        return Result.Ok(updated)
    }
}

class FakeSiteRepository(initial: Site? = null) : SiteRepository {
    val site = MutableStateFlow(initial)
    /** Slugs other agents already use. */
    val taken = mutableSetOf("taken-slug")
    var availabilityError: AppError? = null
    var createError: AppError? = null
    var publishResult: AppResult<PublishOutcome> = Result.Ok(PublishOutcome.Published(null))
    val availabilityChecks = mutableListOf<String>()
    val created = mutableListOf<NewSite>()
    var publishCalls = 0

    override fun observeMine(): Flow<Site?> = site

    override suspend fun isSlugAvailable(slug: String): AppResult<Boolean> {
        availabilityChecks += slug
        availabilityError?.let { return Result.Err(it) }
        return Result.Ok(slug !in taken)
    }

    override suspend fun create(site: NewSite): AppResult<Site> {
        createError?.let { return Result.Err(it) }
        if (site.slug in taken) return Result.Err(AppError.Conflict)
        created += site
        val s = testSite(site.slug).copy(templateKey = site.templateKey, draftContent = site.draftContent)
        this.site.value = s
        return Result.Ok(s)
    }

    override suspend fun publish(): AppResult<PublishOutcome> {
        publishCalls++
        val result = publishResult
        if (result is Result.Ok && result.value is PublishOutcome.Published) {
            site.value = site.value?.let { it.copy(isPublished = true, publishedContent = it.draftContent) }
        }
        return result
    }

    /** Set to fail the next writes (saveDraft, updateAppearance, changeSlug). */
    var writeError: AppError? = null
    val drafts = mutableListOf<JsonObject>()
    val appearances = mutableListOf<SiteAppearance>()
    val slugChanges = mutableListOf<String>()

    override suspend fun saveDraft(content: JsonObject): AppResult<Site> {
        writeError?.let { return Result.Err(it) }
        if (!SiteContent.fitsSizeCap(content)) return Result.Err(ContentTooLarge)
        drafts += content
        return write { it.copy(draftContent = content) }
    }

    override suspend fun updateAppearance(appearance: SiteAppearance): AppResult<Site> {
        writeError?.let { return Result.Err(it) }
        appearances += appearance
        return write {
            it.copy(templateKey = appearance.templateKey, accent = appearance.accent?.let(AccentSwatch::fromKey), locale = appearance.locale)
        }
    }

    override suspend fun changeSlug(slug: String): AppResult<Site> {
        writeError?.let { return Result.Err(it) }
        if (slug in taken) return Result.Err(AppError.Conflict)
        slugChanges += slug
        return write { it.copy(slug = slug) }
    }

    private fun write(change: (Site) -> Site): AppResult<Site> {
        val updated = change(site.value ?: return Result.Err(AppError.Unknown("no site")))
        site.value = updated
        return Result.Ok(updated)
    }
}

class FakeDataSync(var result: AppResult<Unit> = Result.Ok(Unit)) : DataSync {
    override val status = MutableStateFlow(SyncStatus.Idle)
    var refreshes = 0
    var cleared = 0
    /** Runs on every refresh, e.g. to deliver the rows a real sync would bring. */
    var onRefresh: () -> Unit = {}
    val signedIn = mutableListOf<String>()

    override suspend fun refreshAll(): AppResult<Unit> {
        refreshes++
        if (result is Result.Ok) onRefresh()
        status.value = if (result is Result.Ok) SyncStatus.Done else SyncStatus.Failed
        return result
    }

    override suspend fun onSignedIn(userId: String) { signedIn += userId }
    override suspend fun clearAll() { cleared++; status.value = SyncStatus.Idle }
}

/** The whole app wired with fakes (nav smoke and screenshot tests). */
class FakeAppDependencies(
    override val auth: FakeAuthRepository = FakeAuthRepository(),
    override val profiles: FakeProfileRepository = FakeProfileRepository(),
    override val sites: FakeSiteRepository = FakeSiteRepository(),
    override val network: FakeNetworkMonitor = FakeNetworkMonitor(),
    override val locale: FakeLocaleController = FakeLocaleController(),
    override val urlLauncher: FakeUrlLauncher = FakeUrlLauncher(),
    override val dataSync: FakeDataSync = FakeDataSync(),
    override val enquiries: FakeEnquiryRepository = FakeEnquiryRepository(),
    override val policies: FakePolicyRepository = FakePolicyRepository(),
    override val customers: FakeCustomerRepository = FakeCustomerRepository(enquiries = enquiries, policies = policies),
    override val consents: FakeConsentRepository = FakeConsentRepository(accepted = true),
    override val enquiryLive: FakeEnquiryLive = FakeEnquiryLive(),
    override val contactPicker: FakeContactPicker = FakeContactPicker(),
    val fakeWebsite: FakeWebsite = FakeWebsite(),
    flags: FeatureFlags = FeatureFlags.Defaults,
    override val isConfigured: Boolean = true,
) : AppDependencies {
    override val featureFlags: StateFlow<FeatureFlags> = MutableStateFlow(flags)
    override val website = fakeWebsite.services
    override val onboarding = OnboardingSession()
    override val events = AppEvents()
    override val deepLinks = DeepLinks()
    override val store: KeyValueStore = InMemoryKeyValueStore()

    companion object {
        /** Signed in with a complete profile and a site: the gate opens Main. */
        fun ready(flags: FeatureFlags = FeatureFlags.Defaults) = FakeAppDependencies(
            auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, "ramesh@example.com")),
            profiles = FakeProfileRepository(testProfile()),
            sites = FakeSiteRepository(testSite()),
            flags = flags,
        )
    }
}
