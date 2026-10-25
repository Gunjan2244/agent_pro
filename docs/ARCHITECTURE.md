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
│ (Cloudflare)       │  Worker SSR: /, /a/{slug}/, /privacy → rpc/get_public_site* → HTML (DECISIONS #75)
└────────────────────┘  also /manifest.webmanifest, /robots.txt, /sitemap.xml (list_public_sites); assets for the rest
                        /a/{slug}/my/* (customer login): GET → portal_home; POST login|start|ask|request|logout → portal_* (anon RPCs, cookie ap_portal, secret header x-portal-key)
App ─JWT→ Edge Functions (billing, domains) ─→ Razorpay · Registrar API · Cloudflare API
Razorpay ─webhook→ razorpay-webhook ; pg_cron → cron   (details: BILLING.md, DOMAINS.md)
```

## Public-site data flow (Worker SSR, DECISIONS #75)
`GET /a/<slug>/` → `worker.js` (`run_worker_first`) → `resolveTarget` → anon RPC `get_public_site` (or `get_public_site_by_host` on a custom domain) → `ssr.js renderDocument()` calls the same pure template `render()` the browser uses (from the static map in `templates/index.js`) → full HTML with head tags and JSON-LD, security headers from `headers.js`, `Last-Modified`. The browser then loads `app.js` → `hydrate.js` to wire the enquiry form, install button and view count; it never re-renders. Static data (config, schema, catalog, dictionaries, notices) is read through `env.ASSETS` and cached per isolate. `?preview=1` (in-app preview) and a backend failure use the static `index.html` shell and the client render. Details: WEB_RENDERER.md.

**Customer-login data flow (DECISIONS #96–#102).** `/a/<slug>/my…` is a separate branch of the same Worker (`handlePortal`): GET renders from `portal_home` with the `ap_portal` session cookie (a first-login link's token is moved into a 15-minute `ap_invite` cookie), POST forms call the six anon `portal_*` RPCs and redirect. **Every one of those calls carries the secret header `x-portal-key`** (the Worker secret `PORTAL_RPC_KEY`; the SQL side compares its SHA-256 with the one row in `private.portal_gate`), so the public anon key alone cannot reach the login logic; without the secret the Worker fails closed with a 503 portal page and makes no call. The site pages only link to the portal: the payload's `portal` boolean (from `get_public_site*`: flag on, owner not lapsed, `showCustomerLogin` not off) decides whether the hero link, the "For my customers" section and the footer link are drawn, and the portal base path is derived from the privacy URL (`ctx.portalBase`). The in-app preview has no Worker, so the app adds the same boolean itself (`SitePreviewPayload.build(…, portalEnabled)`, below).

## Repo layout
```
mobile/                 Kotlin Multiplatform Gradle project (use ./gradlew from here)
  build-logic/          included build: convention plugins agentpro.shared + agentpro.site-preview,
                        generateShared + verifyShared + verifyReleaseReady (codegen/, verify/, tasks/),
                        unit tests, catalog-keys.lock.json (committed; catalog keys and privacy
                        notice hashes; see SHARED_DEFINITIONS.md)
  composeApp/           KMP library module — almost all code
    src/commonMain/kotlin/        UI, ViewModels, data, domain, design system (shared)
    src/commonMain/composeResources/  strings (values, values-hi), fonts (font/), OFL licences (files/licenses/), icons (vector XML)
    src/androidMain/kotlin/       Android implementations of platform seams
    src/iosMain/kotlin/           iOS implementations of platform seams (stubs until iOS phase)
    src/commonTest/ androidHostTest/ iosTest/   (host tests run with :composeApp:testAndroidHostTest;
                                  androidHostTest holds the Robolectric UI tests and the Roborazzi
                                  baselines in androidHostTest/screenshots/)
    build/generated/agentpro/     generated Kotlin + Compose resources (never edit; see SHARED_DEFINITIONS.md)
  androidApp/           Android application: MainActivity, Application, manifest, WorkManager
                        workers, notification channels, Razorpay Android SDK wiring
                        (res/values{,-hi}/strings.xml holds only the launcher label app_name;
                        res/xml holds the backup exclusion rules; res/drawable + res/mipmap-anydpi
                        hold the adaptive launcher icon, generated by design/app-icon/build_icon.py;
                        src/debug holds the debug-only
                        GalleryActivity, the component gallery, with no launcher entry)
  iosApp/               Xcode project (added in the iOS phase)
  gradle/libs.versions.toml, gradle/wrapper/, gradlew
  gradle.properties     memory limits (see Build setup)
  local.properties      (git-ignored) sdk.dir, SUPABASE_URL, SUPABASE_ANON_KEY, SITE_BASE_URL
  local.properties.example
shared/                 single source of truth JSON (see SHARED_DEFINITIONS.md) — NOT a Gradle module;
                        shared/generated/privacy-notices.json is written by generateShared (never edit)
web/                    renderer source; web/dist is build output (git-ignored)
supabase/migrations/    SQL migrations (some generated: *_catalog_sync.sql)
supabase/functions/     Edge Functions: billing, domains, razorpay-webhook, cron, _shared/
scripts/build-web.sh
design/app-icon/        app icon source script, master SVGs and store PNGs (see DESIGN_SYSTEM.md, App icon)
docs/
```

## `composeApp/commonMain` packages (`com.sledje.agentpro`)
These packages are feature-first, so they can be split into Gradle modules later without restructuring.
```
app/            App() root composable + AppNavHost (the gate), AppDependencies (interface) + AppContainer
                (manual DI; androidAppContainer()/iosAppContainer() per platform), AppGate + RootViewModel,
                DataSync (AppDataSync), MainShell (bottom bar + main NavHost), DeepLinks, OnboardingSession,
                AppEvents (shell snackbars), GateScreens (Splash, Not configured, Startup error)
core/
  designsystem/ AgentProTheme, Tokens (generated), Color, Type, Shape, Spacing (+ Dimens), Motion, ChartStyle,
                LocalAppLocale (expect/actual), components/, gallery/ (debug component gallery)
  catalog/      Catalog.kt + SiteSchema.kt + Templates.kt (generated enums, schema and template specs)
                + ext: label(), tone(), icon()
  forms/        FieldSpec, Validators (+ ValidationError messages), FormState, FormRenderer
  data/         SupabaseProvider (+ Throwable.toAppError(), remoteCall), AppDatabase (Room KMP), SyncEngine
                (+ SyncTable, SyncRemote, SyncCursorStore, RoomSyncTable, SupabaseSyncRemote), AppError
                (+ ErrorMapper), Result/AppResult, KeyValueStore (DataStore Preferences; StoreKeys)
  format/       Money (₹ Indian grouping, compact L/Cr), Dates (kotlinx-datetime, Asia/Kolkata;
                translated month names via DateText), Phone (+91, same rule as the backend)
  features/     AppFeature, FeatureRegistry, FeatureFlags
  platform/     interfaces for platform seams (below)
  ui/           ScreenState, UiState<T>, LocalIsOnline (connectivity composition local), SharedAxisNavHost (the NavHost for linear flows)
feature/<name>/ data/ (Dto, Entity, Dao, Repository) · ui/ (Screen, ViewModel, components) · <Name>Feature.kt
  names: auth, onboarding, home, insights, enquiries, customers, policies, dues,
         website, domains, billing, settings, export, portal
domain/         pure Kotlin, unit-tested in commonTest: DueDateCalculator, StatsAggregator, CsvWriter
```

**What exists after Phase 5 (app foundation):**
- `feature/privacy` (P1-4): `data/ConsentRepository` (`record_consent` RPC; the local once-per-account mark in `KeyValueStore` under `privacy.customer_duty.<userId>` = notice version; `PrivacyLinks`), `ui/NoticeScreen` (lays out a generated `PrivacyNotice` from its resource keys), `ui/CustomerDuty` (`CustomerDutyViewModel`, the attestation sheet, `CustomerDutyGateHost` used by the customer and enquiry form routes). `AppDependencies.consents`. Sign-up consent travels in `AuthRepository.signUp(email, password, consent)` as `data.consent`. New seams in `core`: `FieldType.Consent` + `FieldSpec.links` + `FormRenderer(onLink)` (app-only checkbox with tappable substrings, component `ConsentCheckbox`), `domain/Age`.
- `feature/auth`: `data/AuthRepository` (`SupabaseAuthRepository`, `AuthState`, `AuthRules`), `data/AuthCallbackLink`
  (pure parser of `agentpro://auth-callback` links), `ui/` (Welcome, the shared Sign in / Create account / Forgot password
  form with `AuthFormViewModel`, Check your email and Reset sent with `ResendEmailViewModel` (Resend with the per-address
  countdown, `AuthRules.EMAIL_RESEND_SECONDS`), Set new password with
  `SetNewPasswordViewModel`, This link has expired), `AuthNavigation.kt` (`AuthRoutes`, `AuthNavHost`, `PasswordRecoveryNavHost`).
- `feature/onboarding`: `ui/OnboardingViewModel` (one instance shared by the 3 steps, scoped to the onboarding graph;
  `SlugSuggestion`), `ui/OnboardingScreens.kt`, `OnboardingNavigation.kt` (`OnboardingRoutes`, `OnboardingNavHost`).
- `feature/offers` (built: the companies part of DECISIONS #89 and the offers picker, DECISIONS #104 step 1: `OffersFeature` registers `OfferPickerRoute(section, field)`, `OfferPickerViewModel`/`OfferPickerUi`, `OfferDraft.kt` with the pure draft edits, and `CompanyRepository.liveOffers()` over `my_company_offers`; it saves through `sites.saveDraft`. `FormRenderer(onOfferField)` renders `offerRef`/`offerList` as `NavRow`s and `SectionRoute` navigates to the picker; with a null callback or a disabled form the rows are muted. Slot mode is still to do, 8b-4b): `data/CompanyRepository` (`SupabaseCompanyRepository`, `Company`, `Membership`, `CompanyJson`; remote only, an in-memory `StateFlow` of the memberships), `ui/CompaniesViewModel` + `CompaniesUi` (the "Your companies" block, the picker and form sheets). `AppDependencies.companies`; used by `feature/onboarding` (step 1) and `feature/settings` (Profile route).
- `feature/portal` (customer login, DECISIONS #96–#100): `PortalFeature` (flag `customerPortal`, no routes and no tab; it contributes one `CustomerDetailItem`, key `website_login`, order 1), `data/PortalRepository` (reached as `AppDependencies.portal`, default `NoPortalRepository` so tests that don't care get an offline stub; `AppContainer` supplies `SupabasePortalRepository`, which calls `portal_status`, `portal_invite`, `portal_revoke`; `PortalState`, `PortalStatus`, `PortalJson`; **online only, nothing in Room**), `ui/PortalViewModel`, `ui/WebsiteLoginCard` (and the public `WebsiteLoginCardContent`, the stateless card used by the screenshot tests). Strings in `strings_portal.xml` (EN + HI). The invite link is shared through the share sheet (`/a/<slug>/my/start?t=<token>`; a custom domain isn't used yet because the app's `Site` model has no domain field).
- `feature/settings/data`: `Profile`, `ProfileDto`/`ProfileEntity`/`ProfileDao`, `ProfileRepository` (`SupabaseProfileRepository`).
  `feature/settings/SettingsFeature.kt`: Profile (the companies list), Language, the privacy notice and Sign out (the rest is Phase 13).
- `feature/website` (Phase 8, the Website hub):
  - `data/`: `Site` (+ `publishedContent`, `hasUnpublishedChanges`), `SiteDto`/`SiteEntity`/`SiteDao`, `SiteRepository`
    (`SupabaseSiteRepository`: `is_slug_available`, insert, `publish_site`, `saveDraft` (only `draft_content`; the 64 KB cap is
    checked first, `ContentTooLarge`), `updateAppearance` (`template_key`, `accent`, `locale`), `changeSlug`; `NewSite` carries the site `locale`), `StarterContent` (`from(profile, template, locale, agentId)`: the generated `SiteSeed` resolved into the first draft, with `sectionOrder`, `hidden`, `maxLength` cuts and publish-safe list items; `isOwnBio` for Home's checklist; services seeded from the agent's insurance types), `SiteServices` (`agentTypes`: the union of `insurance_types` over pending and approved company links; the services to add or remove when the types change; DECISIONS #93), `ServicesMatchViewModel` (the "Update your website to match?" sheet from Settings → Profile and the Website → Content hint),
    `SiteLinks`; `SiteContent` (pure: section order, `withOffersAfterHero` for the Appearance switch, `hidden`, pinned sections, schema values ⇄ `FormState`, summaries, size
    cap) and `PublishIssues` (`publish_site` errors → schema field + `ValidationError`); `SitePreviewPayload` (the renderSite input, which **always** carries `portal`: `FeatureId.CustomerPortal` enabled and `contact.showCustomerLogin` not `false`, passed as `portalEnabled` by the Preview screen, the template-switch preview, the onboarding template picker and the launch preview; optionally with the `preview_site_offers` answer as top-level `offers`/`company`; `SiteRepository.previewOffers` caches it in memory per (draft, template) in `PreviewOffersCache`, and `rememberPreviewOffers` loads it after the preview is shown and re-renders on arrival, any failure meaning no offers; DECISIONS #84, #86) (the
    `renderSite` JSON); `SiteMedia` (`SiteMediaRepository` / `SupabaseSiteMediaRepository`: Storage upload to
    `site-media/<uid>/<name>-<millis>-<nonce>.webp` and its public URL; `ImageUploader` = `ImageProcessor` + upload;
    `MediaPaths`); `SiteStats` (`SiteDailyViewDto`/`Entity`/`Dao` + its sync table, `SiteStatsAggregator`, `StatsPeriod`
    7/30 days, `SiteStatsRepository`, and the `WebsiteEnquirySource` hook for website-enquiry counts, which `AppContainer`
    fills from the Enquiries cache: the Asia/Kolkata day of each `source = website` enquiry).
  - `ui/`: `WebsiteHubViewModel` (scoped to the `WebsiteRoute` back-stack entry and shared by every Website screen: site,
    profile, this week's views, Publish and the per-section publish issues), the hub, `ContentScreen` (+ `ContentViewModel`:
    show/hide saves at once; reorder is local until Done), `SectionEditorScreen` (+ `SectionEditorViewModel`: FormRenderer
    from the generated `SiteSchema` section), `AppearanceScreen` + `TemplatePickerScreen` (+ `AppearanceViewModel`),
    `LinkScreen` + `ChangeLinkDialog` (+ `LinkViewModel`), `StatsScreen` (+ `StatsViewModel`), `PreviewScreen`, and
    `WebsiteUi.kt` (status pill, Share / QR sheet, `SiteQrCode`, error copy).
  - `WebsiteFeature.kt`: routes `WebsiteRoute` (tab), `WebsiteContentRoute`, `WebsiteSectionRoute(key)`,
    `WebsiteAppearanceRoute`, `WebsiteTemplatesRoute`, `WebsiteLinkRoute`, `WebsiteStatsRoute`, `WebsitePreviewRoute`, and
    its 4 `websiteHubEntries` (Content, Appearance, Link & domain, Stats). `WebsiteServices` groups what the hub needs
    beyond `sites` (MediaPicker, `ImageUploader`, SitePreview, stats) as one `AppDependencies.website` entry.
- `feature/home` (greeting, `TabScreen` frame, the setup checklist and What's new):
  - `HomeViewModel` builds `HomeUiState` with the ordered checklist (Profile, Companies, Publish, About, Share, FirstCustomer, CustomerLogin; items whose flag is off are hidden and not counted) and `next`/`isTappable`: only the first undone item and done items open anything (`openSetupItem`). Companies is done on a pending or approved link or a pending "not listed" (one `companies.refresh()`; undone offline); CustomerLogin on `StoreKeys.PORTAL_INVITED` (set by `PortalViewModel` on invite, cleared by `DataSync.clearAll`).
  - `WhatsNew.kt` (no route, no flag): `VERSION = 1`, the rows Companies, CustomerLogin and ForMyCustomers (each gated on its flag), and the sheet. It shows when `whats_new_seen < VERSION`, the agent has a site, at least one row is enabled and the checklist sheet is closed; closing, a row or Got it marks it seen, and `OnboardingViewModel.finish()` marks it seen (`NonCancellable`) so new sign-ups skip it. Sign-out doesn't reset it.
  - `FeatureHost.openTab(tabKey): Boolean` switches the bottom-bar tab (false when that tab is off); the checklist and What's new use it before navigating to a screen inside the tab.
- **Phases 6–7 (enquiries, customers, policies, dues):**
  - `domain/DueDateCalculator.kt`: `DueDateCalculator` (calendar months from `start_date`, clamped to the month end;
    `nextAfterPayment` is exactly the `mark_premium_paid` rule; `initialNextDue` for new policies) and `DueBuckets`
    (classification by catalog `dueBuckets`; null beyond the last bucket).
  - `feature/enquiries`: `data/` `Enquiry` (+ `EnquiryDto`/`EnquiryEntity`/`EnquiryDao`, `NewEnquiry`), `EnquiryRepository`
    (`SupabaseEnquiryRepository`: add, status (+ `follow_up_at`), follow-up, notes, `seen_at`, soft delete and restore; pure
    `statusPatch`), `EnquiryRealtime` (`EnquiryLive` seam + `SupabaseEnquiryRealtime`); `ui/` list, detail (status sheet,
    follow-up sheet), Add enquiry and notes editor with their ViewModels; `EnquiriesFeature.kt` (routes `EnquiriesRoute`,
    `EnquiryDetailRoute(id)`, `AddEnquiryRoute`, `EnquiryNotesRoute(id)`; tab badge = unseen enquiries).
  - `feature/customers` also holds the **customer sign-up link and Incomplete forms** (DECISIONS #108): `data/CustomerDraft.kt`
    (`CustomerDraft`, `CustomerDraftDto/Entity/Dao`, `DraftJson`), `CustomerDraftRepository.kt` (`SupabaseCustomerDraftRepository`,
    `customerDraftsSyncTable`, `CustomerSignupJson` for the `customer_signup_link` / `customer_signup_renew` answers, `SignupLinks.joinUrl`
    = `<site url>/join?t=<token>`, `NoCustomerDraftRepository`), `CustomerDraftRealtime.kt` (`CustomerDraftLive` seam +
    `SupabaseCustomerDraftRealtime`); `ui/CustomerSignupViewModels.kt` (`IncompleteForms` presence/line logic, `IncompleteFormsViewModel`,
    `SignupStartViewModel`, `SignupLiveViewModel`) and `ui/CustomerSignupScreens.kt` (Add customer sheet content, Incomplete forms,
    start and live screens). `CustomerFormViewModel` autosaves Add mode into the draft (debounced, insert then update, completed after Save).
    Routes in `CustomersFeature`: `IncompleteFormsRoute`, `CustomerSignupStartRoute`, `CustomerSignupLiveRoute(draftId, token?)` and
    `CustomerFormRoute(draftId)`. They are registered with the Customers feature (not behind the `customerSignup` flag, so an existing
    link still opens if the flag is turned off); the flag only gates the "Send the customer a link" option and the realtime channel.
  - `feature/customers`: `data/` `Customer` (+ DTO/entity/DAO, `CustomerInput`), `CustomerRepository` (create, update,
    `convertEnquiry` (insert with `source_enquiry_id`, then the enquiry's `customer_id` + status `converted`; a retry reuses
    the customer), soft delete of the customer and its policies); `ui/` list (`CustomerFilter`), detail (Policies · Details
    · Activity), the customer form, and `CrmParts.kt` (the action row, label/value rows, `CrmFormScreen`: the shared form
    frame with the sticky Save bar and "Discard changes?"); `CustomersFeature.kt` (routes `CustomersRoute(segment, bucket)`,
    `CustomerDetailRoute(id)`, `CustomerFormRoute(id | fromEnquiryId | name + phone)`; tab badge = customers with an overdue
    premium; `LocalSegmentArgument` passes the deep-link bucket to the Dues segment).
  - `feature/policies`: `data/` `Policy`, `PremiumPayment` (+ DTOs/entities/DAOs), `PolicyRepository` (create, update,
    `markPaid` → `mark_premium_paid`, `undoPayment` → `undo_premium_payment`, each followed by a refresh of `policies` and
    `premium_payments`); `ui/` policy form (`PolicyFormViewModel`, live summary) and policy detail with the record-payment
    sheet (`PolicyDetailViewModel`); `PoliciesFeature.kt` (flag `policies`; routes `PolicyFormRoute(customerId, policyId?)`,
    `PolicyDetailRoute(id)`; no tab).
  - `feature/dues`: `ui/Dues.kt` (`DuesViewModel`, the segment content) and `DuesFeature.kt` (the Dues `TabSegment`).

### Platform seams
Platform-specific behaviour sits behind **interfaces** in `core/platform`, rather than scattered `expect`/`actual` declarations. Each platform provides its implementations to `AppContainer`, so they're easy to fake in tests.

The one hand-written `expect`/`actual` declaration is `LocalAppLocale` in `core/designsystem` (a composition-local language override, needed inside composition rather than through `AppContainer`). Room KMP also requires `expect object AppDatabaseConstructor` in `core/data`, whose `actual` is generated by Room's KSP processor per target. "Remove animations" needs no seam: Compose scales animations by the platform's `MotionDurationScale`.

**Defined so far (Phase 5, Phase 8):** `NetworkMonitor`, `UrlLauncher` (`openEmailApp()`; Phase 8 adds `share(text)` (`ACTION_SEND` chooser) and `openUrl(url)` (http(s) only, `ACTION_VIEW`); Phases 6–7 add `call(phone)` (`ACTION_DIAL`, so no `CALL_PHONE` permission), `sms(phone)` (`ACTION_SENDTO smsto:`) and `email(address)` (`ACTION_SENDTO mailto:`), with default bodies returning false so other platforms and fakes compile), `ContactPicker` (Phase 7: composable `rememberContactPicker(onResult)`; Android `AndroidContactPicker` = `ACTION_PICK` on `CommonDataKinds.Phone.CONTENT_URI`, which returns one name and number with a temporary read grant and needs no `READ_CONTACTS`; iOS `IosContactPicker` stub; `NoContactPicker` is the `PlatformServices` default), `MediaPicker`, `ImageProcessor` and `SitePreview` (Phase 8, below), `LocaleController` and `SecureSettings` (the Phase 5 follow-up, DECISIONS #44 and #45). Each platform passes them to `AppContainer` through `PlatformServices` (with the Room builder and the DataStore path). Android: `AndroidNetworkMonitor` (default-network callback; online = `INTERNET` + `VALIDATED`) and `AndroidUrlLauncher` in `composeApp/src/androidMain/.../core/platform`, wired by `androidAppContainer(context)`. iOS: stubs in `iosMain` that throw `NotImplementedError`. Fakes for tests are in `commonTest/.../testing/Fakes.kt`. The other rows below come with their phases.

| Interface | Android (`androidMain` / `androidApp`) | iOS (`iosMain`, iOS phase) |
|---|---|---|
| `NetworkMonitor` | ConnectivityManager callback | NWPathMonitor |
| `BackgroundScheduler` (poll, follow-up, digest) | WorkManager | BGTaskScheduler (best effort). iOS will need push for timely enquiry alerts |
| `Notifier` (channels, post, deep link) | NotificationManager + PendingIntent | UNUserNotificationCenter |
| `PaymentLauncher` | Razorpay Android Checkout SDK | Razorpay iOS SDK, or StoreKit for the plan (see BILLING.md, Compliance) |
| `UrlLauncher` (call, SMS, email, open link, share) | Intents | UIApplication.open / UIActivityViewController |
| `ContactPicker` | `ACTION_PICK` contact picker (no permission) | CNContactPickerViewController |
| `MediaPicker` + `ImageProcessor` (crop, WebP at 1024px or less) | `AndroidMediaPicker`: a composable `rememberImagePicker` over AndroidX `PickVisualMedia` (images only, no permission; the document picker on devices without the photo picker); returns a `PickedImage` whose bytes are read on IO. `AndroidImageProcessor`: bounded decode (power-of-two `inSampleSize`), EXIF orientation (`android.media.ExifInterface`), a **centred crop** to the schema `aspect` (1:1 for the hero photo and the profile photo), scaled so the longer side is ≤ 1024 px, WebP lossy (`WEBP` before API 30) from quality 82 down to 40 until it fits the 2 MB bucket limit. The geometry (`ImageGeometry`) is common and unit-tested | `IosMediaPicker` / `IosImageProcessor` stubs; PHPicker + UIImage (JPEG fallback if no WebP encoder) in the iOS phase |
| `FileExporter` (zip, save or share) | SAF `CreateDocument` + share sheet | UIDocumentPicker / share sheet |
| `ReceiptPdfRenderer` | `PdfDocument` | UIGraphicsPDFRenderer |
| `SitePreview` (composable, the bundled `web/` + `renderSite(json)`) | `AndroidSitePreview`: a WebView (JavaScript and DOM storage on, file and content access off) that loads `https://appassets.androidplatform.net/index.html?preview=1`. Its `WebViewClient` (`AssetSiteClient`) answers every request on that reserved host from the APK assets (`/shared/…` → `site/shared/…`, everything else → `site/web/…`, a 404 for anything missing such as `config.js`), because the renderer's root-relative URLs and ES modules don't work from `file://`; only the Google Fonts CSS goes to the network. After `onPageFinished` it evaluates `SitePreviewScript.renderCall(json)` (`window.renderSite("<escaped JSON>")`, retried until the renderer has defined it) and again whenever the JSON changes; link taps never navigate. `tokens.css` is generated into the assets by `copySitePreviewAssets` | `IosSitePreview` stub; WKWebView (UIKitView) in the iOS phase |
| `LocaleController` (EN/हिं in-app switch) | `AppCompatLocaleController` (`androidMain`, DECISIONS #45): `set()` calls `AppCompatDelegate.setApplicationLocales` (empty list = follow the device), so the choice shows in Android 13+'s per-app language settings; `choice` is read back with `getApplicationLocales()` by `sync()`, which `MainActivity.onCreate` calls after `super.onCreate` (on Android 12 and lower AppCompat loads its stored choice only when the first AppCompat activity is created; until then `choice` is null and `App()` shows a blank frame). `MainActivity` is an `AppCompatActivity` with `Theme.AgentPro` (parent `Theme.AppCompat.DayNight.NoActionBar`, `res/values/themes.xml`; edge to edge as before). The manifest sets `android:localeConfig="@xml/locales_config"` (en, hi; `LocalesConfigTest` checks it against `catalog.locales`) and declares AppCompat's `AppLocalesMetadataHolderService` with `autoStoreLocales=true` for Android 12 and lower. `App()` still applies the choice through the common `LocalAppLocale` override, so Compose resources follow it at once on every version | `StoredLocaleController` (common; the default when a platform passes no controller): the choice is stored in DataStore and applied through `LocalAppLocale`. Also what previews and tests use |
| `SecureSettings` (encrypted small secrets: the login session) | `KeystoreSecureSettings` (`androidMain`, DECISIONS #44): AES-256-GCM (`AES/GCM/NoPadding`, 128-bit tag, a fresh random IV per write) with a non-exportable key generated in the `AndroidKeyStore` (alias `agentpro_secure_settings`, `AndroidKeystoreCipher`); Base64 IV + ciphertext in the app-private SharedPreferences file `agentpro_secure`, excluded from backup and device transfer like all app data. The cipher sits behind `SecretCipher` so the store logic is host-tested. An unreadable value (lost key, tampered or truncated data) is deleted with the key and `get()` throws `UnreadableSecretException`. supabase-kt stores its session through `SecureSessionManager` (`core/data`, the Auth plugin's `sessionManager`): an unreadable session is cleared, `onUnreadable` makes `AppContainer` clear the cache, and supabase-kt sees "no session" (`NoSessionFoundException`), so the app opens on Welcome instead of crashing. (supabase-kt's PKCE code-verifier cache still uses its default settings storage; the app uses the implicit flow) | `IosSecureSettings` stub; Keychain in the iOS phase |

**iOS phase:** each seam gets a real `iosMain` implementation. Until then, the stubs throw `NotImplementedError` and aren't compiled into Android builds.

**Planned (designed, not built):**
- **`SitePreview` slot mode** (OFFERS.md, Phase 8b): `slotsMode` and `onSlotTap(SlotRef)`; the renderer posts `{v:1, type:"slot", section, field, index?}` from `preview-bridge.js`, Android receives it through `androidx.webkit`'s `WebViewCompat.addWebMessageListener` restricted to `https://appassets.androidplatform.net`, and the pure `SlotMessage.parse()` accepts only schema `offerRef`/`offerList` fields. iOS uses a `WKScriptMessageHandler`.
- **New packages:** `feature/offers/` (companies and the admin-verified membership with its status states, the offers snapshot in Room with no sync cursor, Place offers, the picker sheet, the custom offer form; flag `offers`) and later `feature/privacy/` (privacy requests, Your data, erase and export one person; PRIVACY.md).
- **New top-level app:** `admin/`, the web admin panel (Vite + TypeScript + React + supabase-js, a separate Cloudflare Worker/Pages project at `admin.<primary domain>`, invite-only with mandatory MFA). It's a separate app, so the renderer's no-SDK rule (DECISIONS #26) doesn't apply to it (OFFERS.md, Admin panel). New Edge Function `admin` (invite, roles, revoke, reset MFA). The panel also has a membership-requests queue where admins approve, reject or revoke agents' company links.

### Patterns
- **MVVM.** ViewModels come from the multiplatform `androidx.lifecycle.ViewModel` and expose a single `StateFlow<UiState>`. There's no business logic in composables.
- **Manual DI.** `AppContainer` is built by each platform's entry point (`AgentProApp` on Android, and `MainViewController` on iOS later) with that platform's seam implementations. There's no Hilt or Koin; see DECISIONS #6.
- **Navigation.** The multiplatform Navigation Compose library uses type-safe `@Serializable` routes. Each feature registers its own routes.
  - `AppNavHost` is the gate (`AppGate`, resolved by the pure `resolveGate()` in `RootViewModel`): `Loading` → Splash, `NotConfigured`, `Error` (empty cache and the first sync failed → Retry), `SignedOut` → `AuthNavHost`, `Onboarding` → `OnboardingNavHost` (always from step 1, prefilled), `Ready` → `MainShell`. Each has its own NavHost, so signing out drops every back stack.
  - Ready = signed in, profile complete (name and mobile) and a site exists. While the profile exists but the site hasn't synced yet, the gate waits for the first refresh. `OnboardingSession` latches the gate on onboarding until step 3 finishes, so creating the site doesn't jump to Main before "Publish now" completes. A signed-in user whose stored session can't refresh (offline) still counts as signed in (the last user id is kept in DataStore), so a cached session opens Main offline.
  - Routes: `AuthRoutes.Welcome / SignIn / SignUp / CheckEmail(email) / ForgotPassword / ResetSent(email)`, plus `SetNewPassword` and `LinkExpired` in `PasswordRecoveryNavHost`; `OnboardingRoutes.Graph { Profile, Template, Link }`; main `HomeRoute`, `EnquiriesRoute`, `CustomersRoute`, `WebsiteRoute` (tabs) and `SettingsRoute` (from the Home avatar).
  - `DeepLinks` parses `agentpro://enquiry|customer|policy/{id}`, `dues?bucket=`, `website`, `domain` and `auth-callback`. `MainActivity` (`launchMode="singleTop"`, a `VIEW` intent filter for the `agentpro` scheme) passes every opened URL to it. Auth callbacks go to `RootViewModel.handleAuthCallback` → `AuthRepository.handleAuthCallback` (PKCE `code` or the implicit `#access_token` fragment, parsed by the pure `AuthCallbackLink`; an `error`/`error_code` in the link or no session in it is `Validation("link", "expired")` without a server call); the others open their tab until the detail screens exist.
  - **Password reset (DECISIONS #46):** the reset email redirects to `agentpro://auth-callback?type=recovery` (`AuthCallbackLink.RECOVERY_REDIRECT`), so even an expired link is recognised as a reset link. For such a link `RootViewModel.recovery` goes `Verifying` (Splash) → `SetPassword` or `Expired`, and `AppNavHost` shows `PasswordRecoveryNavHost` above the gate (Set new password, or This link has expired → Forgot password → Reset sent). Saving calls `updatePassword` (`auth.updateUser { password }`), posts `AppMessage.PasswordUpdated` and closes the flow, so the freshly composed main shell opens on Home with the snackbar. Back closes the flow; the recovery session stays signed in.
- **Errors.** Repositories return `Result<T, AppError>` (`core/data/Result.kt`, alias `AppResult<T>`; importing it shadows `kotlin.Result`). The `AppError` cases are `Offline`, `Unauthorized`, `ReadOnly` (plan lapsed), `Validation(field, rule?)`, `Conflict`, `RateLimited` and `Unknown`. Each maps to a string resource (`AppError.message`).
  - `remoteCall { }` wraps every Supabase call; `Throwable.toAppError()` turns supabase-kt/Ktor exceptions into a `RemoteFailure(status, code, message, network)`, and the pure `ErrorMapper` maps it in this order: network → `Offline`; our RPC reasons (`plan_read_only` → `ReadOnly`, `not_authenticated` → `Unauthorized`, `not_latest_payment`/`orders_in_progress`/`media_not_deleted` → `Conflict`, `invalid_amount`… → `Validation`); Auth codes (`invalid_credentials` → `Validation("credentials")`, `email_not_confirmed` → `Validation("email","not_confirmed")`, `weak_password` → `Validation("password","weak")`, `user_already_exists` → `Conflict`, `over_*_rate_limit` → `RateLimited`, session codes → `Unauthorized`); SQLSTATEs (`42501` RLS refusal → `ReadOnly`, `23505`/`P0001` → `Conflict`, `23514`/`22023`/`22P02` → `Validation("value")`, `PGRST301` → `Unauthorized`); then the HTTP status (401/403 → `Unauthorized`, 409 → `Conflict`, 429 → `RateLimited`, 400/422 → `Validation`, else `Unknown`).
- **Not configured.** `SupabaseProvider` builds no client when `SUPABASE_URL` or `SUPABASE_ANON_KEY` is empty (or the client can't be built); the gate then shows "This app isn't connected to a server yet" instead of crashing.

### Feature registry
```kotlin
interface AppFeature {
    val flag: FeatureId?                      // catalog.features flag; null = core feature, always on
    fun NavGraphBuilder.routes(host: FeatureHost)   // host: nav, deps, segmentsFor(tabKey), openSettings(), openTab(tabKey): Boolean
    val bottomNavItem: BottomNavItem? get() = null
    val segments: List<TabSegment> get() = emptyList()   // e.g. Dues inside the Customers tab
    val homeCards: List<HomeCard> get() = emptyList()
    val settingsEntries: List<SettingsEntry> get() = emptyList()
    val customerDetailItems: List<CustomerDetailItem> get() = emptyList()   // cards on Customer detail's Details tab, drawn in `order`; content(host, customerId)
    val websiteHubEntries: List<HubEntry> get() = emptyList()
    fun openDeepLink(link: DeepLink, host: FeatureHost): Boolean = false   // after the shell opened the link's tab
}
object FeatureRegistry {
    val all = listOf(HomeFeature, EnquiriesFeature, CustomersFeature, DuesFeature, PoliciesFeature, WebsiteFeature, PortalFeature, SettingsFeature)
    fun enabled(flags: FeatureFlags) = all.filter { f -> f.flag?.let(flags::isEnabled) ?: true }
}
```
- Phase 5 registers Home (core), Enquiries (`enquiries`), Customers (`customers`), Dues (`dues`, a `TabSegment` of the Customers tab, no tab of its own; DECISIONS #18), Website (`siteBuilder`) and Settings (core). Phase 7 adds Policies (`policies`, routes only). Insights, Domains, Billing and Export are added by their phases.
- `BottomNavItem.badge` (a composable `(FeatureHost) -> Int?`) gives a tab its count badge. `openDeepLink` lets the owning feature open `agentpro://enquiry|customer|policy/{id}` and `dues?bucket=` on its detail screen or segment.
- **Home and Settings have no catalog flag**, so `flag` is nullable and null means "always on" (pending the user's confirmation; the alternative is adding `home`/`settings` flags to `catalog.features`).
- `MainShell` builds the bottom bar (tabs sorted by `BottomNavItem.order`: Home · Enquiries · Customers · Website, labels `tab_*`, icons from `FeatureId.icon` and `ic_home`) and the NavHost from the enabled features, and hides the bar on non-tab screens (Settings).
- `customerDetailItems` is the extension point that lets a feature add a card to another feature's screen without that feature knowing it: `CustomersFeature`'s `CustomerDetailRoute` collects the enabled features' items and passes them to `CustomerDetailScreen(detailExtras = …)`, which draws them on the **Details** tab under the fields. `PortalFeature` is the first user.
`FeatureFlags` takes its defaults from `catalog.features`. The server's values come from the RPC `effective_flags()`, cached in DataStore by `FeatureFlagsRepository` (refreshed with every `DataSync.refreshAll()`; a failed refresh keeps the cache). That RPC merges two `app_config` rows (DECISIONS #35): `flags`, the catalog defaults that every `catalog_sync` migration rewrites, and `flag_overrides`, manual overrides that migrations never touch. An override wins, and only boolean override values count. `FeatureFlags` applies the cached result over the catalog defaults, and the SQL (`private.flag_enabled`) and the Edge Functions use the same rule.

## Data flow and offline cache
- **Reads.** Repositories expose Room `Flow`s, so screens render from the cache immediately.
  - **One exception, the site check (DECISIONS #83):** before onboarding offers to create a site, `SiteRepository.fetchMine()` reads the agent's `sites` row straight from Supabase (owner-only RLS) and caches it, because an empty cache can't prove the agent has no site. `RootViewModel.confirmOnboarding` (the gate) and `OnboardingViewModel` (after step 1) call it.
- **Refresh.** `SyncEngine.refresh(table)` pulls rows where `updated_at > lastSyncAt`, upserts them, and removes rows with `deleted_at` set. The cursor for each table is stored in DataStore.
  - It runs at app start, on pull-to-refresh, when the app returns to the foreground, and after each write.
  - **Details (Phase 5):** pages of 500 ordered by `updated_at, id`, continued with a keyset `(updated_at, id)` so rows sharing a timestamp are never skipped at a page boundary; the cursor is stored only after every page applied; offline it returns `Offline` without a request; one mutex per table. `applyLocal(table, rows)` writes a returned row without moving the cursor.
  - Synced tables so far: `profiles`, `sites`, `site_daily_views` (Phase 8; read-only for the client), `enquiries`, `customers`, `customer_drafts` (DECISIONS #108; the agent writes only `source = agent` drafts, customer links are made by `customer_signup_link()`), `policies` and `premium_payments` (Phases 6–7; `premium_payments` is written only by the RPCs; `AppDatabase.VERSION` 3) (`SyncTable` adapters over the Room DAOs, `RoomSyncTable`). Room filters soft-deleted rows in its queries too (DECISIONS #27).
  - `AppDataSync` (`DataSync`) runs `refreshAll()` (flags + every table) at sign-in (`RootViewModel`), on `ON_START` and from Retry. It clears the cache and cursors on sign-out and when a different user signs in (`last_user_id`), and resets the cursors once when `AppDatabase.VERSION` changes, because the cache uses a destructive migration (it can always be re-synced).
- **Writes** go to Supabase first, and Room is updated from the returned row.
  - While offline, save buttons are disabled and the `OfflineBanner` shows. There's no write queue (DECISIONS #8).
- **Realtime.** While the app is in the foreground, channels on `enquiries`, `orders`, `subscriptions` and `site_domains` (filtered to `agent_id`) upsert into Room. These drive in-app notifications, payment results and domain setup progress.
  - **Enquiries (Phase 6):** `SupabaseEnquiryRealtime` opens a `postgres_changes` channel on `enquiries` filtered to `agent_id = auth.uid()`; `MainShell` starts it on `ON_START` and stops it on `ON_STOP` (`LifecycleStartEffect`). Each insert, update or delete event runs `SyncEngine.refresh("enquiries")`, which upserts the changed rows (and removes tombstones) with the same row format and cursor as a normal sync. Failures are ignored; the next foreground refresh catches up.
  - **Customer sign-up links (DECISIONS #108):** `SupabaseCustomerDraftRealtime` does the same for `customer_drafts` (channel `customer-drafts-<uid>`, `agent_id` filter); `MainShell` starts it on `ON_START` only while the `customerSignup` flag is on. Each event refreshes `customer_drafts` **and** `customers` (a customer's Submit creates one), which is how the live screen shows typing, presence and consent.
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
| QR | qrose 1.3.0 (`io.github.alexzhirkevich:qrose`, `rememberQrCodePainter`): verified in Phase 8. It resolves and renders with Compose MP 1.12.1 on Android (the Link + QR screenshots), publishes `iosArm64`/`iosSimulatorArm64`, and the code is drawn dark on a white quiet zone in both themes |
| Background | WorkManager (Android only, `androidApp`) |
| Payments | Razorpay Checkout: Android SDK in `androidApp` (an iOS SDK is added in the iOS phase) |
| Testing | kotlin-test, kotlinx-coroutines-test, Turbine, Compose MP UI test (`runComposeUiTest`), Roborazzi (Android screenshots), with Robolectric, JUnit 4 and androidx `ui-test-junit4`/`ui-test-manifest` for the Android host tests (test-only, JVM) |

- Exact versions are pinned in `mobile/gradle/libs.versions.toml` (summary under Build setup). Record any version bump in CHANGELOG.md.
- Every library above was confirmed on Maven Central / Google Maven to publish Android, `iosArm64` and `iosSimulatorArm64` variants (qrose 1.3.0 included, and verified on Android in Phase 8). JetBrains `material3` is pinned to 1.9.0 because every later version is alpha.
- `composeApp` depends on Compose (runtime, foundation, ui, material3, components-resources), kotlinx-coroutines, kotlinx-datetime and Vico (Phase 2), plus (Phase 5) kotlinx-serialization-json, lifecycle-viewmodel-compose and lifecycle-runtime-compose, navigation-compose, supabase-kt (BOM; auth, postgrest, realtime, storage), ktor-client-core (OkHttp engine in `androidMain`, Darwin in `iosMain`), Room runtime, sqlite-bundled and DataStore Preferences; `commonTest` adds Turbine. Its plugins add `kotlin.serialization`, `ksp` and `androidx.room` (schemas exported to `composeApp/schemas/`, committed). `androidApp` depends on `activity-compose` and `appcompat`; `composeApp`'s `androidMain` also depends on `appcompat` (Android only, for `AppCompatLocaleController`). Phase 8 adds Coil 3 (`coil-compose` + `coil-network-ktor3`, the `NetworkImage` component), qrose and `ui-backhandler` (`org.jetbrains.compose.ui`, the same version as Compose MP; system Back for "Discard changes?", `@ExperimentalComposeUiApi`) to `commonMain`, and `activity-compose` to `androidMain` (the photo picker's result launcher). The rest are catalog entries for later phases.
- `androidApp`'s manifest declares `INTERNET` and `ACCESS_NETWORK_STATE`, `android:localeConfig` and AppCompat's `AppLocalesMetadataHolderService` (`autoStoreLocales`), and `MainActivity` (an `AppCompatActivity`, theme `Theme.AgentPro`) handles the `agentpro://` scheme.
- **Before adding a dependency, check that it supports KMP.** If it's platform-only, wrap it behind a `core/platform` interface.

## Build setup (`mobile/`)
**Toolchain.** Each version was the newest stable release at least two weeks old on 2026-10-15, checked against Maven Central, Google Maven and services.gradle.org, and the versions are compatible with each other.

| Item | Version | Note |
|---|---|---|
| Gradle (wrapper) | 9.7.1 | The wrapper pins `distributionSha256Sum` |
| Kotlin (KGP, Compose compiler plugin) | 2.4.20 | Officially supports Gradle ≤ 9.7 and AGP ≤ 9.3.x |
| Android Gradle Plugin | 9.3.3 | The newest patch inside KGP 2.4.20's supported range. AGP 9.4.1 exists but is outside it |
| Compose Multiplatform | 1.12.1 | JetBrains `material3` is 1.9.0, the newest stable |
| KSP | 2.3.12 | Room's processor: `kspAndroid`, plus `kspIosArm64`/`kspIosSimulatorArm64` (configured, built only on macOS) |
| JDK | 21 | build-logic uses `jvmToolchain(21)`; Android bytecode targets 17 |

The library versions are in `gradle/libs.versions.toml`: coroutines 1.11.0, serialization 1.11.0, datetime 0.8.0, lifecycle 2.11.0, navigation 2.9.2, supabase-kt 3.8.0, Ktor 3.6.0, Room 2.8.5, sqlite-bundled 2.7.1, DataStore 1.2.1, Vico 2.5.2, Coil 3.6.3, qrose 1.3.0, WorkManager 2.12.0, Razorpay checkout 1.6.41, activity-compose 1.13.0, AppCompat 1.8.0 (Android only; the newest stable on Google Maven, published 2026-08-12, checked 2026-10-16), Turbine 1.2.1, Roborazzi 1.76.0, Robolectric 4.17, JUnit 4.13.2 and androidx Compose ui-test 1.12.1 (matching Compose MP 1.12.1).

**Modules and plugins**
- **`settings.gradle.kts`:**
  - `pluginManagement { includeBuild("build-logic") }`, with repositories `google()` (androidx, com.android and com.google only), `mavenCentral()` and `gradlePluginPortal()`.
  - `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.
  - Modules `:composeApp` and `:androidApp`.
- **Root `build.gradle.kts`** declares every plugin `apply false`, so they load once. build-logic compiles against KGP, the Compose plugin and AGP's `gradle-api` as `compileOnly`.
- **`:composeApp`** uses `org.jetbrains.kotlin.multiplatform`, `com.android.kotlin.multiplatform.library` (AGP's KMP library plugin; the old `com.android.library` and KMP pairing isn't supported by AGP 9), `org.jetbrains.compose`, `org.jetbrains.kotlin.plugin.compose` and `agentpro.shared`.
  - **Android target:** `kotlin { android { … } }`, with namespace `com.sledje.agentpro`, `androidResources.enable = true` (needed for Compose resources) and `withHostTest {}`.
  - **iOS targets:** `iosArm64` and `iosSimulatorArm64`, with a static `ComposeApp` framework. They're declared only, because they can't be built on Linux. `iosX64` is left out because several required libraries (Coil, Vico, qrose) don't publish it.
- **`:androidApp`** uses `com.android.application`, `org.jetbrains.kotlin.plugin.compose` and `agentpro.site-preview`.
  - AGP 9 compiles Kotlin itself, so there's no `org.jetbrains.kotlin.android`.
  - Namespace `com.sledje.agentpro.android`, `applicationId` `com.sledje.agentpro`.
  - `AgentProApp` builds `AppContainer`, and `MainActivity` calls `setContent { App() }`.
- **Generated code and resources:**
  - Kotlin goes to `composeApp/build/generated/agentpro/kotlin/commonMain` and Compose resources to `composeApp/build/generated/agentpro/composeResources/commonMain`. The `Res` accessors are public, in `com.sledje.agentpro.resources`.
  - The site preview assets go to `androidApp/build/generated/agentpro/sitePreviewAssets`. Details are in SHARED_DEFINITIONS.md, Codegen.

**Memory (`gradle.properties`).** The dev machine has about 7 GB of RAM with little free, so:
- There's one daemon, `-Xmx2g -XX:MaxMetaspaceSize=768m`.
- `kotlin.compiler.execution.strategy=in-process` means no separate Kotlin daemon (`kotlin.daemon.jvmargs=-Xmx1536m` applies only if that's switched back).
- `org.gradle.workers.max=2` and `org.gradle.parallel=false`.
- Build cache and configuration cache are on.
- `kotlin.native.ignoreDisabledTargets=true` silences the iOS warning on Linux.
- `android.builder.sdkDownload=false` stops AGP from installing SDK components by itself. Install them deliberately and record them in STATUS.md.
- Run one Gradle invocation at a time, and `./gradlew --stop` when done. build-logic has its own `gradle.properties` (`-Xmx1g`), used only by `./gradlew -p build-logic …`.

## Configuration
- **`mobile/local.properties`** (git-ignored; `local.properties.example` is committed) holds `sdk.dir`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL`.
  - `build-logic` turns them into a generated `AppConfig` object in `commonMain`, because `BuildConfig` is Android-only.
- **`web/config.js`** is generated by `build-web.sh` from environment variables. Only `config.example.js` is committed. The Cloudflare Worker (`web/worker.js`: server-side rendering, manifest, robots.txt, sitemap.xml) reads the same values from the deployed `config.js` asset, so `wrangler.jsonc` holds no keys or `vars`.
- **Android:** minSdk 26, compileSdk 37, targetSdk 36 (the `android-*` entries in `libs.versions.toml`; build-tools 36.0.0, AGP's default).
  - compileSdk 37 is required by Compose 1.12.1 and current AndroidX.
  - The app's data is excluded from backup and device transfer (`allowBackup="false"`, `res/xml/data_extraction_rules.xml` for Android 12+, `res/xml/backup_rules.xml` for older versions). A session is re-created by signing in.
- **iOS:** deployment target 16 or later (iOS phase).
