package com.sledje.agentpro.app

import androidx.room.RoomDatabase
import com.sledje.agentpro.core.data.AppDatabase
import com.sledje.agentpro.core.data.DataStoreKeyValueStore
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.SecureSessionManager
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.SupabaseSyncRemote
import com.sledje.agentpro.core.data.SyncCursorStore
import com.sledje.agentpro.core.data.SyncEngine
import com.sledje.agentpro.core.data.buildAppDatabase
import com.sledje.agentpro.core.data.createPreferencesDataStore
import com.sledje.agentpro.core.data.remoteCall
import com.sledje.agentpro.core.features.FeatureFlags
import com.sledje.agentpro.core.features.FeatureFlagsRepository
import com.sledje.agentpro.core.platform.ContactPicker
import com.sledje.agentpro.core.platform.GoogleSignIn
import com.sledje.agentpro.core.platform.NoGoogleSignIn
import com.sledje.agentpro.core.platform.LocaleController
import com.sledje.agentpro.core.platform.NoContactPicker
import com.sledje.agentpro.core.platform.NetworkMonitor
import com.sledje.agentpro.core.platform.SecureSettings
import com.sledje.agentpro.core.platform.StoredLocaleController
import com.sledje.agentpro.core.platform.UrlLauncher
import com.sledje.agentpro.feature.auth.data.AuthRepository
import com.sledje.agentpro.feature.auth.data.SupabaseAuthRepository
import com.sledje.agentpro.feature.customers.data.CustomerDraftLive
import com.sledje.agentpro.feature.customers.data.CustomerDraftRepository
import com.sledje.agentpro.feature.customers.data.CustomerRepository
import com.sledje.agentpro.feature.customers.data.SupabaseCustomerDraftRealtime
import com.sledje.agentpro.feature.customers.data.SupabaseCustomerDraftRepository
import com.sledje.agentpro.feature.customers.data.customerDraftsSyncTable
import com.sledje.agentpro.feature.customers.data.SupabaseCustomerRepository
import com.sledje.agentpro.feature.customers.data.customersSyncTable
import com.sledje.agentpro.feature.enquiries.data.EnquiryLive
import com.sledje.agentpro.feature.enquiries.data.EnquiryRepository
import com.sledje.agentpro.feature.enquiries.data.SupabaseEnquiryRealtime
import com.sledje.agentpro.feature.enquiries.data.SupabaseEnquiryRepository
import com.sledje.agentpro.feature.enquiries.data.enquiriesSyncTable
import com.sledje.agentpro.feature.policies.data.PolicyRepository
import com.sledje.agentpro.feature.privacy.data.ConsentRepository
import com.sledje.agentpro.feature.privacy.data.SupabaseConsentRepository
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.policies.data.SupabasePolicyRepository
import com.sledje.agentpro.feature.policies.data.policiesSyncTable
import com.sledje.agentpro.feature.policies.data.premiumPaymentsSyncTable
import com.sledje.agentpro.feature.settings.data.ProfileRepository
import com.sledje.agentpro.feature.settings.data.SupabaseProfileRepository
import com.sledje.agentpro.feature.settings.data.profilesSyncTable
import com.sledje.agentpro.core.platform.ImageProcessor
import com.sledje.agentpro.core.platform.MediaPicker
import com.sledje.agentpro.core.platform.SitePreview
import com.sledje.agentpro.feature.website.WebsiteServices
import com.sledje.agentpro.feature.website.data.ImageUploader
import com.sledje.agentpro.feature.offers.data.CompanyRepository
import com.sledje.agentpro.feature.offers.data.PlansRepository
import com.sledje.agentpro.feature.offers.data.SupabasePlansRepository
import com.sledje.agentpro.feature.portal.data.PortalRepository
import com.sledje.agentpro.feature.portal.data.SupabasePortalRepository
import com.sledje.agentpro.feature.offers.data.SupabaseCompanyRepository
import com.sledje.agentpro.feature.website.data.SiteRepository
import com.sledje.agentpro.feature.website.data.SiteStatsRepository
import com.sledje.agentpro.feature.website.data.WebsiteEnquirySource
import com.sledje.agentpro.feature.website.data.SupabaseSiteMediaRepository
import com.sledje.agentpro.feature.website.data.siteDailyViewsSyncTable
import com.sledje.agentpro.feature.website.data.SupabaseSiteRepository
import com.sledje.agentpro.feature.website.data.sitesSyncTable
import io.github.jan.supabase.postgrest.postgrest
import com.sledje.agentpro.core.catalog.EnquirySource
import com.sledje.agentpro.core.format.Dates
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** What each platform supplies to [AppContainer] (docs/ARCHITECTURE.md, Platform seams). */
class PlatformServices(
    /** Room builder for [AppDatabase.FILE_NAME] in the platform's database directory. */
    val databaseBuilder: RoomDatabase.Builder<AppDatabase>,
    /** Absolute path of the DataStore Preferences file (`*.preferences_pb`). */
    val dataStorePath: String,
    val network: NetworkMonitor,
    val urlLauncher: UrlLauncher,
    /** Website hub seams (Phase 8): photo picker, crop + WebP, and the WebView site preview. */
    val mediaPicker: MediaPicker,
    val imageProcessor: ImageProcessor,
    val sitePreview: SitePreview,
    /** Encrypted storage for the login session (DECISIONS #44). */
    val secureSettings: SecureSettings,
    /** The platform's language switch (Android: AppCompat per-app locales); null = [StoredLocaleController]. */
    val locale: LocaleController? = null,
    /** The system contact picker (Customers: "Pick from contacts"; no permission). */
    val contactPicker: ContactPicker = NoContactPicker,
    /** "Continue with Google" (Welcome; DECISIONS #112). */
    val googleSignIn: GoogleSignIn = NoGoogleSignIn,
)

/**
 * Manual DI container (DECISIONS #6). Built once per process by the platform entry point
 * (`androidAppContainer(context)` from AgentProApp on Android; `iosAppContainer()` in the iOS phase).
 */
class AppContainer(platform: PlatformServices) : AppDependencies {
    /** Lives as long as the process. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fires when the stored session couldn't be decrypted: the cache is cleared too (clean sign-out). */
    private val sessionUnreadable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val supabase = SupabaseProvider(
        sessionManager = SecureSessionManager(platform.secureSettings, onUnreadable = { sessionUnreadable.emit(Unit) }),
    )
    val database: AppDatabase = buildAppDatabase(platform.databaseBuilder)
    override val store: KeyValueStore = DataStoreKeyValueStore(createPreferencesDataStore(platform.dataStorePath))

    override val network: NetworkMonitor = platform.network
    override val urlLauncher: UrlLauncher = platform.urlLauncher
    override val contactPicker: ContactPicker = platform.contactPicker
    override val googleSignIn: GoogleSignIn = platform.googleSignIn
    override val locale: LocaleController = platform.locale ?: StoredLocaleController(store, scope)

    val syncEngine = SyncEngine(
        remote = SupabaseSyncRemote(supabase),
        cursors = SyncCursorStore(store),
        network = network,
        tables = listOf(
            profilesSyncTable(database.profileDao()),
            sitesSyncTable(database.siteDao()),
            siteDailyViewsSyncTable(database.siteDailyViewDao()),
            enquiriesSyncTable(database.enquiryDao()),
            customersSyncTable(database.customerDao()),
            customerDraftsSyncTable(database.customerDraftDao()),
            policiesSyncTable(database.policyDao()),
            premiumPaymentsSyncTable(database.premiumPaymentDao()),
        ),
    )

    private val flagsRepository = FeatureFlagsRepository(store, scope) {
        remoteCall { supabase.require().postgrest.rpc("effective_flags").decodeAs<JsonObject>() }
    }
    override val featureFlags: StateFlow<FeatureFlags> = flagsRepository.flags

    private val appDataSync = AppDataSync(syncEngine, flagsRepository, store, AppDatabase.VERSION)
    override val dataSync: DataSync = appDataSync

    override val isConfigured: Boolean get() = supabase.isConfigured
    override val auth: AuthRepository = SupabaseAuthRepository(supabase, store, scope, onSignedOut = { appDataSync.clearAll(); companies.clear() })
    override val profiles: ProfileRepository = SupabaseProfileRepository(supabase, database.profileDao(), syncEngine)
    override val sites: SiteRepository = SupabaseSiteRepository(supabase, database.siteDao(), syncEngine)
    override val companies: CompanyRepository = SupabaseCompanyRepository(supabase)
    override val portal: PortalRepository = SupabasePortalRepository(supabase)
    override val plans: PlansRepository = SupabasePlansRepository(supabase)
    override val enquiries: EnquiryRepository = SupabaseEnquiryRepository(supabase, database.enquiryDao(), syncEngine)
    override val customers: CustomerRepository = SupabaseCustomerRepository(supabase, database.customerDao(), syncEngine)
    override val customerDrafts: CustomerDraftRepository = SupabaseCustomerDraftRepository(supabase, database.customerDraftDao(), syncEngine)
    override val policies: PolicyRepository =
        SupabasePolicyRepository(supabase, database.policyDao(), database.premiumPaymentDao(), syncEngine)
    override val consents: ConsentRepository = SupabaseConsentRepository(
        supabase, store,
        userId = { (auth.state.value as? AuthState.SignedIn)?.userId },
        userIds = auth.state.map { (it as? AuthState.SignedIn)?.userId },
    )
    override val enquiryLive: EnquiryLive = SupabaseEnquiryRealtime(supabase, syncEngine, scope)
    override val customerDraftLive: CustomerDraftLive = SupabaseCustomerDraftRealtime(supabase, syncEngine, scope)
    override val website = WebsiteServices(
        mediaPicker = platform.mediaPicker,
        imageUploader = ImageUploader(platform.imageProcessor, SupabaseSiteMediaRepository(supabase)),
        sitePreview = platform.sitePreview,
        // Website enquiry counts come from the Enquiries cache (Phase 6): the IST day of each `website` enquiry.
        stats = SiteStatsRepository(
            database.siteDailyViewDao(),
            enquiries = WebsiteEnquirySource.from(enquiries),
        ),
    )

    override val onboarding = OnboardingSession()
    override val events = AppEvents()
    override val deepLinks = DeepLinks()

    init {
        // An unreadable stored session (lost Keystore key, corrupt data) means signed out: drop the cache too.
        scope.launch { sessionUnreadable.collect { appDataSync.clearAll() } }
    }
}
