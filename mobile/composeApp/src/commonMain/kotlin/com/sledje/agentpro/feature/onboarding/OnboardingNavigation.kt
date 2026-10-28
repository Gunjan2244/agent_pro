package com.sledje.agentpro.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import com.sledje.agentpro.core.designsystem.currentAppLocale
import androidx.compose.runtime.collectAsState
import com.sledje.agentpro.core.catalog.FeatureId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.feature.onboarding.ui.LaunchStep
import com.sledje.agentpro.feature.onboarding.ui.SiteLaunchScreen
import com.sledje.agentpro.feature.website.data.SiteLinks
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.sledje.agentpro.core.ui.SharedAxisNavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.sledje.agentpro.app.AppDependencies
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.feature.onboarding.ui.OnboardingLinkScreen
import com.sledje.agentpro.feature.offers.ui.CompaniesViewModel
import com.sledje.agentpro.feature.offers.ui.CompanyActions
import com.sledje.agentpro.feature.onboarding.ui.OnboardingDetailsScreen
import com.sledje.agentpro.feature.onboarding.ui.OnboardingPhotoScreen
import com.sledje.agentpro.feature.onboarding.ui.OnboardingPreviewScreen
import com.sledje.agentpro.core.catalog.MembershipStatus
import com.sledje.agentpro.feature.offers.data.Membership
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.sledje.agentpro.feature.onboarding.ui.OnboardingMobileScreen
import com.sledje.agentpro.feature.onboarding.ui.OnboardingTemplateGridScreen
import com.sledje.agentpro.feature.onboarding.ui.OnboardingStep
import com.sledje.agentpro.feature.onboarding.ui.OnboardingTemplateScreen
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import kotlinx.serialization.Serializable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.feature.settings.data.Profile
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SitePreviewPayload
import com.sledje.agentpro.feature.website.data.rememberPreviewOffers
import com.sledje.agentpro.feature.website.data.SiteServices
import com.sledje.agentpro.feature.website.data.StarterContent

/**
 * Onboarding routes (docs/UX.md: 1 Your mobile → 2 Choose your template (the grid) → 3 That template's preview →
 * 4 Your work → 5 Your photo → 6 Your website → 7 Your link → Done; DECISIONS #113, #116, #120).
 */
object OnboardingRoutes {
    @Serializable data object Graph
    @Serializable data object Mobile
    @Serializable data object Intro
    @Serializable data object Template
    @Serializable data object Details
    @Serializable data object Photo
    @Serializable data object Preview
    @Serializable data object Link

    /** The two screens once the site is live: Add to phone, Share (docs/UX.md, Site is live). */
    @Serializable data object Launch
}

/**
 * The onboarding graph, shown by the gate while the profile or site is missing. One
 * [OnboardingViewModel] is shared by the seven steps (scoped to [OnboardingRoutes.Graph]). It always
 * starts at step 1, prefilled from the profile (Google's or Create account's name, a mobile saved earlier).
 */
@Composable
fun OnboardingNavHost(deps: AppDependencies) {
    val nav = rememberNavController()
    SharedAxisNavHost(nav, startDestination = OnboardingRoutes.Graph) {
        navigation<OnboardingRoutes.Graph>(startDestination = OnboardingRoutes.Mobile) {
            composable<OnboardingRoutes.Mobile> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                Navigate(vm, state.navigateTo, nav)
                OnboardingMobileScreen(vm.profileForm, state, onContinue = vm::saveMobile, onEditContact = vm::editContact)
            }
            composable<OnboardingRoutes.Intro> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                Navigate(vm, state.navigateTo, nav)
                OnboardingTemplateGridScreen(templates = Templates.all, onOpen = vm::previewTemplate, onBack = { nav.popBackStack() })
            }
            composable<OnboardingRoutes.Template> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                Navigate(vm, state.navigateTo, nav)
                val form = vm.profileForm
                OnboardingTemplateScreen(
                    template = state.template,
                    agentName = form.text(OnboardingViewModel.NAME),
                    agentLine = form.text(OnboardingViewModel.CITY),
                    onUse = vm::selectTemplate,
                    onBack = { nav.popBackStack() },
                    // The agent's own starter content rendered in each template (docs/UX.md, Onboarding step 3).
                    preview = { template ->
                        val profile by deps.profiles.observeMine().collectAsState(null)
                        val memberships by deps.companies.memberships.collectAsState()
                        val types = remember(memberships) { SiteServices.agentTypes(memberships) }
                        val content = remember(profile, template.key, state.locale, types) { StarterPreview.content(profile, template, state.locale, types) }
                        val offers = rememberPreviewOffers(deps.sites, content, template.key)
                        val portal = deps.featureFlags.collectAsState().value.isEnabled(FeatureId.CustomerPortal)
                        val json = remember(profile, template.key, state.locale, offers, types, portal) { StarterPreview.json(profile, template, state.locale, offers, types, portal) }
                        deps.website.sitePreview.Render(json, Modifier.fillMaxSize())
                    },
                )
            }
            composable<OnboardingRoutes.Details> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                Navigate(vm, state.navigateTo, nav)
                val companiesVm = viewModel { CompaniesViewModel(deps.companies) }
                val companies by companiesVm.state.collectAsState()
                OnboardingDetailsScreen(
                    vm.profileForm, state, onContinue = vm::saveDetails, onBack = { nav.popBackStack() },
                    companies = companies, companyActions = CompanyActions.of(companiesVm),
                )
            }
            composable<OnboardingRoutes.Photo> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                Navigate(vm, state.navigateTo, nav)
                val pickPhoto = deps.website.mediaPicker.rememberImagePicker { picked -> picked?.let(vm::uploadPhoto) }
                OnboardingPhotoScreen(
                    vm.profileForm, state, onPickPhoto = pickPhoto, onContinue = vm::savePhoto, onSkip = vm::skipPhoto,
                    onBack = { nav.popBackStack() },
                )
            }
            composable<OnboardingRoutes.Preview> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                Navigate(vm, state.navigateTo, nav)
                val memberships by deps.companies.memberships.collectAsState()
                OnboardingPreviewScreen(
                    pendingCompanies = memberships.any { it.status == MembershipStatus.Pending.key },
                    onContinue = vm::openLink,
                    onBack = { nav.popBackStack() },
                    // The finished site: the photo and companies saved in steps 4 and 5 (docs/UX.md, Onboarding step 6).
                    preview = { modifier ->
                        val profile by deps.profiles.observeMine().collectAsState(null)
                        val template = state.template
                        val types = remember(memberships) { SiteServices.agentTypes(memberships) }
                        val companies = remember(memberships, state.locale) { StarterPreview.companies(memberships, state.locale) }
                        val content = remember(profile, template.key, state.locale, types) { StarterPreview.content(profile, template, state.locale, types) }
                        val offers = rememberPreviewOffers(deps.sites, content, template.key)
                        val portal = deps.featureFlags.collectAsState().value.isEnabled(FeatureId.CustomerPortal)
                        val json = remember(profile, template.key, state.locale, offers, types, portal, companies) {
                            StarterPreview.json(profile, template, state.locale, offers, types, portal, companies)
                        }
                        deps.website.sitePreview.Render(json, modifier)
                    },
                )
            }
            composable<OnboardingRoutes.Link> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                // Live: the launch screens take over. Their back stack starts fresh, so Back can't return to a site that exists.
                LaunchedEffect(state.launch != null) {
                    if (state.launch != null) nav.navigate(OnboardingRoutes.Launch) { popUpTo(OnboardingRoutes.Mobile) { inclusive = true } }
                }
                OnboardingLinkScreen(
                    state = state,
                    onSlugChange = vm::onSlugChange,
                    onPublishNow = vm::publishNow,
                    onLater = vm::later,
                    onBack = { nav.popBackStack() },
                )
            }
            composable<OnboardingRoutes.Launch> { entry -> LaunchRoute(entry.onboardingViewModel(nav, deps), deps) }
        }
    }
}

/** The launch screens: Add to phone → Share, then Home. Back goes one screen back; on the first it skips ahead. */
@OptIn(ExperimentalComposeUiApi::class) // BackHandler (ui-backhandler)
@Composable
private fun LaunchRoute(vm: OnboardingViewModel, deps: AppDependencies) {
    val state by vm.state.collectAsState()
    val launch = state.launch ?: return
    val scope = rememberCoroutineScope()
    val profile by deps.profiles.observeMine().collectAsState(null)
    val url = SiteLinks.url(launch.slug)
    BackHandler { if (launch.step == LaunchStep.AddToPhone) vm.launchNext() else vm.launchBack() }
    SiteLaunchScreen(
        step = launch.step,
        agentName = profile?.fullName.orEmpty(),
        url = url,
        display = SiteLinks.display(launch.slug),
        // The install sheet is in the browser; moving on first means the agent returns to the next screen.
        onAddToHome = { deps.urlLauncher.openUrl(SiteLinks.installUrl(launch.slug)); vm.launchNext() },
        onNext = vm::launchNext,
        onOpen = { deps.urlLauncher.openUrl(url) },
        onShare = {
            deps.urlLauncher.share(url)
            scope.launch { deps.store.put(StoreKeys.SITE_SHARED, "true") } // Home setup checklist: "Share your link"
        },
        onDone = vm::finishLaunch,
    )
}

@Composable
private fun NavBackStackEntry.onboardingViewModel(nav: NavController, deps: AppDependencies): OnboardingViewModel {
    val graphEntry = remember(this) { nav.getBackStackEntry<OnboardingRoutes.Graph>() }
    val vm: OnboardingViewModel = viewModel(viewModelStoreOwner = graphEntry) {
        OnboardingViewModel(
            deps.profiles, deps.sites, deps.onboarding, deps.events, deps.companies,
            photoUploader = deps.website.imageUploader, store = deps.store, consents = deps.consents,
        )
    }
    // The site is created in the app's language at that moment (docs/UX.md, Onboarding).
    val locale = currentAppLocale().key
    SideEffect { vm.setLocale(locale) }
    return vm
}

@Composable
private fun Navigate(vm: OnboardingViewModel, step: OnboardingStep?, nav: NavController) {
    LaunchedEffect(step) {
        when (step) {
            OnboardingStep.Intro -> nav.navigate(OnboardingRoutes.Intro)
            OnboardingStep.Template -> nav.navigate(OnboardingRoutes.Template)
            OnboardingStep.Details -> nav.navigate(OnboardingRoutes.Details)
            OnboardingStep.Photo -> nav.navigate(OnboardingRoutes.Photo)
            OnboardingStep.Preview -> nav.navigate(OnboardingRoutes.Preview)
            OnboardingStep.Link -> nav.navigate(OnboardingRoutes.Link)
            OnboardingStep.Mobile, null -> Unit
        }
        if (step != null) vm.onNavigated()
    }
}

/**
 * The renderSite input for steps 3 and 6: the seeded starter content (from the saved profile and the [types] of step
 * 4's companies, DECISIONS #93; none yet on the first pass, so the seed's default types) in [template], in [locale].
 */
internal object StarterPreview {
    /**
     * Step 6's "Companies I represent": every pending or approved link as `[{name, insurance_types}]`. The public
     * payload lists only approved ones (DECISIONS #90); the screen says the rest show once confirmed.
     */
    fun companies(memberships: List<Membership>, locale: String): JsonArray = buildJsonArray {
        memberships.filter { it.isActive }.forEach { m ->
            val name = m.displayName(locale).trim()
            if (name.isNotEmpty()) add(buildJsonObject {
                put("name", name)
                put("insurance_types", JsonArray(m.insuranceTypes.map(::JsonPrimitive)))
            })
        }
    }

    fun content(profile: Profile?, template: TemplateSpec, locale: String, types: List<String> = emptyList()): kotlinx.serialization.json.JsonObject =
        profile?.let { StarterContent.from(it, template, locale, it.id, types) } ?: kotlinx.serialization.json.JsonObject(emptyMap())

    fun json(
        profile: Profile?,
        template: TemplateSpec,
        locale: String,
        previewOffers: kotlinx.serialization.json.JsonObject? = null,
        types: List<String> = emptyList(),
        portalEnabled: Boolean = false,
        companies: JsonArray? = null,
    ): String {
        val content = content(profile, template, locale, types)
        val site = Site(
            id = "", slug = "", templateKey = template.key, accent = template.defaultAccent, locale = locale,
            draftContent = content, isPublished = false, publishedAt = null,
        )
        return SitePreviewPayload.json(site, profile, previewOffers = previewOffers, portalEnabled = portalEnabled, companies = companies)
    }
}
