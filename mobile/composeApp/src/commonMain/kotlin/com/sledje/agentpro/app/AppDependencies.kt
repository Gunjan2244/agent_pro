package com.sledje.agentpro.app

import com.sledje.agentpro.core.catalog.Pricing
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.features.FeatureFlags
import com.sledje.agentpro.core.platform.ContactPicker
import com.sledje.agentpro.core.platform.LocaleController
import com.sledje.agentpro.core.platform.NetworkMonitor
import com.sledje.agentpro.core.platform.UrlLauncher
import com.sledje.agentpro.feature.auth.data.AuthRepository
import com.sledje.agentpro.feature.customers.data.CustomerDraftLive
import com.sledje.agentpro.feature.customers.data.CustomerDraftRepository
import com.sledje.agentpro.feature.customers.data.CustomerRepository
import com.sledje.agentpro.feature.customers.data.NoCustomerDraftLive
import com.sledje.agentpro.feature.customers.data.NoCustomerDraftRepository
import com.sledje.agentpro.feature.enquiries.data.EnquiryLive
import com.sledje.agentpro.feature.enquiries.data.EnquiryRepository
import com.sledje.agentpro.feature.offers.data.CompanyRepository
import com.sledje.agentpro.feature.policies.data.PolicyRepository
import com.sledje.agentpro.feature.portal.data.NoPortalRepository
import com.sledje.agentpro.feature.portal.data.PortalRepository
import com.sledje.agentpro.feature.privacy.data.ConsentRepository
import com.sledje.agentpro.feature.settings.data.ProfileRepository
import com.sledje.agentpro.feature.website.WebsiteServices
import com.sledje.agentpro.feature.website.data.SiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the UI needs (manual DI, DECISIONS #6). [AppContainer] is the real implementation built
 * by each platform; tests pass fakes (commonTest `FakeAppDependencies`).
 */
interface AppDependencies {
    /** False when SUPABASE_URL / SUPABASE_ANON_KEY are missing: the app shows "not configured". */
    val isConfigured: Boolean
    val auth: AuthRepository
    val profiles: ProfileRepository
    val sites: SiteRepository

    /** The agent's insurance companies and their insurance types (DECISIONS #89; onboarding step 1, Settings → Profile). */
    val companies: CompanyRepository

    /** Phases 6–7: enquiries, customers, policies and payments (Room-backed, written to Supabase first). */
    val enquiries: EnquiryRepository
    val customers: CustomerRepository
    val policies: PolicyRepository

    /** Unfinished customer forms and customer sign-up links (DECISIONS #108). Tests that don't care get the offline stub. */
    val customerDrafts: CustomerDraftRepository get() = NoCustomerDraftRepository

    /** Realtime on `customer_drafts` while the app is in the foreground and `customerSignup` is on (started and stopped by MainShell). */
    val customerDraftLive: CustomerDraftLive get() = NoCustomerDraftLive

    /** Customer portal invites (online only; DECISIONS: customer portal). Tests that don't care get the offline stub. */
    val portal: PortalRepository get() = NoPortalRepository

    /** In-app consents recorded through `record_consent` (docs/PRIVACY.md; P1-4). */
    val consents: ConsentRepository

    /** Realtime on `enquiries` while the app is in the foreground (started and stopped by MainShell). */
    val enquiryLive: EnquiryLive
    /** Website hub: media picker, image upload, site preview and stats (Phase 8). */
    val website: WebsiteServices
    val featureFlags: StateFlow<FeatureFlags>
    val dataSync: DataSync
    val network: NetworkMonitor
    val locale: LocaleController
    val urlLauncher: UrlLauncher
    val contactPicker: ContactPicker
    val onboarding: OnboardingSession
    val events: AppEvents
    val deepLinks: DeepLinks

    /** Small local marks (e.g. the setup checklist's "shared the link"). */
    val store: KeyValueStore
}

/**
 * Keeps the gate on onboarding while it runs: creating the site in step 3 must not switch to Main
 * before "Publish now" has finished (docs/UX.md, Onboarding).
 */
class OnboardingSession {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun start() { _active.value = true }

    fun finish() { _active.value = false }
}

/** One-shot messages shown by the main shell's snackbar. */
sealed interface AppMessage {
    /** "Your 14-day free trial has started" after onboarding (docs/UX.md, Onboarding). */
    data class TrialStarted(val days: Int = Pricing.Service.trialDays) : AppMessage

    /** "Password updated" after Set new password (docs/UX.md). */
    data object PasswordUpdated : AppMessage
}

class AppEvents {
    private val _message = MutableStateFlow<AppMessage?>(null)
    val message: StateFlow<AppMessage?> = _message.asStateFlow()

    fun post(message: AppMessage) { _message.value = message }

    fun consume(message: AppMessage) { if (_message.value == message) _message.value = null }
}
