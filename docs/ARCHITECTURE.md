# Architecture

**Platform:** **Kotlin Multiplatform (KMP) with Compose Multiplatform.**
- UI, ViewModels, data, domain logic and the design system are written once in `commonMain`.
- **Android ships first.** iOS is added later without a rewrite (DECISIONS #22).

## System overview
```
┌────────────────────┐  supabase-kt (auth, postgrest, realtime, storage)  ┌──────────────────┐
│ Mobile app (KMP)   │ ─────────────────────────────────────────────────▶ │ Supabase         │
│ Compose MP UI      │ ◀───────── realtime: enquiries, orders, … ──────── │ Postgres + RLS   │
│ Room KMP cache     │                                                    │ Auth, Storage    │
│ Android now, iOS   │                                                    │ RPCs, Edge Fns   │
│ later              │                                                    └────────▲─────────┘
└────────────────────┘                                                             │
        ▲ bundled copy of web/ for preview                                         │
┌────────────────────┐  fetch → rpc/get_public_site*, submit_enquiry, record_site_view (anon)
│ Web renderer       │ ────────────────────────────────────────────────────────────┘
│ (Vercel)           │  serves /a/{slug} and custom hostnames
└────────────────────┘
App ─JWT→ Edge Functions (billing, domains) ─→ Razorpay · Registrar API · Vercel API
Razorpay ─webhook→ razorpay-webhook ; pg_cron → cron   (details: BILLING.md, DOMAINS.md)
```

## Repo layout
```
mobile/                 Kotlin Multiplatform Gradle project (use ./gradlew from here)
  build-logic/          included build: generateShared + verifyShared tasks, convention plugins
  composeApp/           KMP library module — almost all code
    src/commonMain/kotlin/        UI, ViewModels, data, domain, design system (shared)
    src/commonMain/composeResources/  strings (values, values-hi), fonts, icons (vector XML)
    src/androidMain/kotlin/       Android implementations of platform seams
    src/iosMain/kotlin/           iOS implementations of platform seams (stubs until iOS phase)
    src/commonTest/ androidUnitTest/ iosTest/
  androidApp/           Android application: MainActivity, Application, manifest, WorkManager
                        workers, notification channels, Razorpay Android SDK wiring
  iosApp/               Xcode project (added in the iOS phase)
  gradle/libs.versions.toml
  local.properties      (git-ignored) sdk.dir, SUPABASE_URL, SUPABASE_ANON_KEY, SITE_BASE_URL
shared/                 single source of truth JSON (see SHARED_DEFINITIONS.md) — NOT a Gradle module
web/                    renderer source; web/dist is build output (git-ignored)
supabase/migrations/    SQL migrations (some generated: *_catalog_sync.sql)
supabase/functions/     Edge Functions: billing, domains, razorpay-webhook, cron, _shared/
scripts/build-web.sh
docs/
```

## `composeApp/commonMain` packages (`com.sledje.agentpro`)
These packages are feature-first, so they can be split into Gradle modules later without restructuring.
```
app/            App() root composable, AppContainer (manual DI), AppNavHost, DeepLinks
core/
  designsystem/ AgentProTheme, Tokens (generated), Type, Shape, Spacing, Motion, ChartStyle, components/
  catalog/      Catalog.kt (generated enums) + ext: label(), tone(), icon()
  forms/        FieldSpec, Validators, FormState, FormRenderer
  data/         SupabaseProvider, AppDatabase (Room KMP), SyncEngine, AppError, Result
  format/       Money (₹ Indian grouping, compact L/Cr), Dates (kotlinx-datetime), Phone (+91)
  features/     AppFeature, FeatureRegistry, FeatureFlags
  platform/     interfaces for platform seams (below)
  ui/           ScreenState, UiState<T>
feature/<name>/ data/ (Dto, Entity, Dao, Repository) · ui/ (Screen, ViewModel, components) · <Name>Feature.kt
  names: auth, onboarding, home, insights, enquiries, customers, policies, dues,
         website, domains, billing, settings, export
domain/         pure Kotlin, unit-tested in commonTest: DueDateCalculator, StatsAggregator, CsvWriter
```

### Platform seams
Platform-specific behaviour sits behind **interfaces** in `core/platform`, rather than scattered `expect`/`actual` declarations. Each platform provides its implementations to `AppContainer`, so they're easy to fake in tests.

| Interface | Android (`androidMain` / `androidApp`) | iOS (`iosMain`, iOS phase) |
|---|---|---|
| `NetworkMonitor` | ConnectivityManager callback | NWPathMonitor |
| `BackgroundScheduler` (poll, follow-up, digest) | WorkManager | BGTaskScheduler (best effort). iOS will need push for timely enquiry alerts |
| `Notifier` (channels, post, deep link) | NotificationManager + PendingIntent | UNUserNotificationCenter |
| `PaymentLauncher` | Razorpay Android Checkout SDK | Razorpay iOS SDK, or StoreKit for the plan (see BILLING.md, Compliance) |
| `UrlLauncher` (call, SMS, email, open link, share) | Intents | UIApplication.open / UIActivityViewController |
| `ContactPicker` | `ACTION_PICK` contact picker (no permission) | CNContactPickerViewController |
| `MediaPicker` + `ImageProcessor` (crop, WebP at 1024px or less) | Photo picker + Bitmap/WebP | PHPicker + UIImage (JPEG fallback if no WebP encoder) |
| `FileExporter` (zip, save or share) | SAF `CreateDocument` + share sheet | UIDocumentPicker / share sheet |
| `ReceiptPdfRenderer` | `PdfDocument` | UIGraphicsPDFRenderer |
| `SitePreview` (composable, the bundled `web/` + `renderSite(json)`) | Android WebView | WKWebView (UIKitView) |
| `LocaleController` (EN/हिं in-app switch) | `AppCompatDelegate.setApplicationLocales` | Stored preference, applied via the `LocalAppLocale` override |
| `SecureSettings` (session storage) | supabase-kt default settings (EncryptedSharedPreferences) | Keychain |

**iOS phase:** each seam gets a real `iosMain` implementation. Until then, the stubs throw `NotImplementedError` and aren't compiled into Android builds.

### Patterns
- **MVVM.** ViewModels come from the multiplatform `androidx.lifecycle.ViewModel` and expose a single `StateFlow<UiState>`. There's no business logic in composables.
- **Manual DI.** `AppContainer` is built by each platform's entry point (`AgentProApp` on Android, and `MainViewController` on iOS later) with that platform's seam implementations. There's no Hilt or Koin; see DECISIONS #6.
- **Navigation.** The multiplatform Navigation Compose library uses type-safe `@Serializable` routes. Each feature registers its own routes.
- **Errors.** Repositories return `Result<T, AppError>`. The `AppError` cases are `Offline`, `Unauthorized`, `ReadOnly` (plan lapsed), `Validation(field)`, `Conflict`, `RateLimited` and `Unknown`. Each maps to a string resource.

### Feature registry
```kotlin
interface AppFeature {
    val id: FeatureId                         // generated from catalog.features
    fun NavGraphBuilder.routes(nav: NavController)
    val bottomNavItem: BottomNavItem? get() = null
    val homeCards: List<HomeCard> get() = emptyList()
    val settingsEntries: List<SettingsEntry> get() = emptyList()
    val websiteHubEntries: List<HubEntry> get() = emptyList()
}
object FeatureRegistry {
    val all = listOf(HomeFeature, EnquiriesFeature, CustomersFeature, PoliciesFeature, DuesFeature,
                     WebsiteFeature, DomainsFeature, BillingFeature, InsightsFeature, SettingsFeature,
                     ExportFeature)
    fun enabled(flags: FeatureFlags) = all.filter { flags.isEnabled(it.id) }
}
```
`FeatureFlags` takes its defaults from `catalog.features`. Remote overrides come from `app_config`, cached in DataStore.

## Data flow and offline cache
- **Reads.** Repositories expose Room `Flow`s, so screens render from the cache immediately.
- **Refresh.** `SyncEngine.refresh(table)` pulls rows where `updated_at > lastSyncAt`, upserts them, and removes rows with `deleted_at` set. The cursor for each table is stored in DataStore.
  - It runs at app start, on pull-to-refresh, when the app returns to the foreground, and after each write.
- **Writes** go to Supabase first, and Room is updated from the returned row.
  - While offline, save buttons are disabled and the `OfflineBanner` shows. There's no write queue (DECISIONS #8).
- **Realtime.** While the app is in the foreground, channels on `enquiries`, `orders`, `subscriptions` and `site_domains` (filtered to `agent_id`) upsert into Room. These drive in-app notifications, payment results and domain setup progress.
- **Payments.** `PaymentLauncher` calls `billing/create-order` with a `kind` (and a `quote_id` for domains). It then opens checkout and sends the result to `billing/verify`. The app never decides a price or writes payment, plan or domain status (BILLING.md, DOMAINS.md).
- **Entitlement.** `EntitlementRepository` calls `my_entitlement()` and caches the result. `FeatureGate` disables edit actions when the plan is `lapsed`. The server enforces the same rule through RLS `can_write()`.
- **Stats** are computed locally from Room by `StatsAggregator`, so charts work offline.
- **Navigation:** the bottom bar has 4 tabs (Home · Enquiries · Customers · Website), with Dues as a segment of Customers.

## Background work (through `BackgroundScheduler`; Android uses WorkManager)
| Job | Schedule | Does |
|---|---|---|
| `EnquiryPoll` | Every 15 min, network required | Notifies about enquiries created since the last check |
| `FollowUp` | One-time, at `follow_up_at` | "Follow up with {name}" |
| `DailyReminder` | Daily at the digest time (default 09:00) | Digest of policy dues and follow-ups, plus plan reminders (trial, renewal, grace) and domain renewals |

The job logic is in `commonMain`. The Android `CoroutineWorker`s in `androidApp` just call it.

## Libraries (all multiplatform unless marked)
| Purpose | Library |
|---|---|
| UI | Compose Multiplatform (Material 3), Compose MP resources, multiplatform Navigation Compose and Lifecycle ViewModel |
| Backend | supabase-kt (auth, postgrest, realtime, storage); Ktor client, OkHttp engine (Android) and Darwin engine (iOS) |
| Serialization, time | kotlinx-serialization-json, kotlinx-datetime |
| Cache and settings | Room KMP (KSP for every target), DataStore Preferences KMP |
| Charts | Vico (multiplatform artifact) |
| Images | Coil 3 (KMP) |
| QR | A Compose Multiplatform QR library (for example qrose), to be verified when Phase 8 is built |
| Background | WorkManager (Android only, `androidApp`) |
| Payments | Razorpay Checkout: Android SDK in `androidApp` (an iOS SDK is added in the iOS phase) |
| Testing | kotlin-test, Turbine, Compose MP UI test (`runComposeUiTest`), Roborazzi (Android screenshots) |

- Exact versions are pinned in `mobile/gradle/libs.versions.toml`. Record any version bump in CHANGELOG.md.
- **Before adding a dependency, check that it supports KMP.** If it's platform-only, wrap it behind a `core/platform` interface.

## Configuration
- **`mobile/local.properties`** (git-ignored; `local.properties.example` is committed) holds `sdk.dir`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL`.
  - `build-logic` turns them into a generated `AppConfig` object in `commonMain`, because `BuildConfig` is Android-only.
- **`web/config.js`** is generated by `build-web.sh` from environment variables. Only `config.example.js` is committed.
- **Android:** minSdk 26, compile and target SDK 35.
- **iOS:** deployment target 16 or later (iOS phase).
