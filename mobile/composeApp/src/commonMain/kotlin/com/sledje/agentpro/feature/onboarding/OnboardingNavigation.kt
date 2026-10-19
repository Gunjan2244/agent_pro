package com.sledje.agentpro.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import com.sledje.agentpro.core.designsystem.currentAppLocale
import androidx.compose.runtime.collectAsState
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
import com.sledje.agentpro.feature.onboarding.ui.OnboardingProfileScreen
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
import com.sledje.agentpro.feature.website.data.StarterContent

/** Onboarding routes (docs/UX.md: 1 Profile → 2 Template → 3 Your link → Done). */
object OnboardingRoutes {
    @Serializable data object Graph
    @Serializable data object Profile
    @Serializable data object Template
    @Serializable data object Link

    /** The three screens once the site is live: Add to phone, Preview, Share (docs/UX.md, Site is live). */
    @Serializable data object Launch
}

/**
 * The onboarding graph, shown by the gate while the profile or site is missing. One
 * [OnboardingViewModel] is shared by the three steps (scoped to [OnboardingRoutes.Graph]). It always
 * starts at step 1, prefilled from the profile (sign-up's name and mobile, or a step 1 saved earlier).
 */
@Composable
fun OnboardingNavHost(deps: AppDependencies) {
    val nav = rememberNavController()
    SharedAxisNavHost(nav, startDestination = OnboardingRoutes.Graph) {
        navigation<OnboardingRoutes.Graph>(startDestination = OnboardingRoutes.Profile) {
            composable<OnboardingRoutes.Profile> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                Navigate(vm, state.navigateTo, nav)
                val pickPhoto = deps.website.mediaPicker.rememberImagePicker { picked -> picked?.let(vm::uploadPhoto) }
                OnboardingProfileScreen(vm.profileForm, state, onContinue = vm::saveProfile, onEditContact = vm::editContact, onPickPhoto = pickPhoto)
            }
            composable<OnboardingRoutes.Template> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                Navigate(vm, state.navigateTo, nav)
                val form = vm.profileForm
                OnboardingTemplateScreen(
                    templates = Templates.all,
                    selectedKey = state.templateKey,
                    agentName = form.text(OnboardingViewModel.NAME),
                    agentLine = listOf(form.text(OnboardingViewModel.DESIGNATION), form.text(OnboardingViewModel.CITY))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    onUse = vm::selectTemplate,
                    onBack = { nav.popBackStack() },
                    // The agent's own starter content rendered in each template (docs/UX.md, Onboarding step 2).
                    preview = { template ->
                        val profile by deps.profiles.observeMine().collectAsState(null)
                        val json = remember(profile, template.key, state.locale) { StarterPreview.json(profile, template, state.locale) }
                        deps.website.sitePreview.Render(json, Modifier.fillMaxSize())
                    },
                )
            }
            composable<OnboardingRoutes.Link> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                // Live: the launch screens take over. Their back stack starts fresh, so Back can't return to a site that exists.
                LaunchedEffect(state.launch != null) {
                    if (state.launch != null) nav.navigate(OnboardingRoutes.Launch) { popUpTo(OnboardingRoutes.Profile) { inclusive = true } }
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

/** The launch screens: Add to phone → Preview → Share, then Home. Back goes one screen back; on the first it skips ahead. */
@OptIn(ExperimentalComposeUiApi::class) // BackHandler (ui-backhandler)
@Composable
private fun LaunchRoute(vm: OnboardingViewModel, deps: AppDependencies) {
    val state by vm.state.collectAsState()
    val launch = state.launch ?: return
    val scope = rememberCoroutineScope()
    val profile by deps.profiles.observeMine().collectAsState(null)
    val site by deps.sites.observeMine().collectAsState(null)
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
        preview = { modifier ->
            val json = remember(site, profile) { site?.let { SitePreviewPayload.json(it, profile) } }
            if (json != null) deps.website.sitePreview.Render(json, modifier)
        },
    )
}

@Composable
private fun NavBackStackEntry.onboardingViewModel(nav: NavController, deps: AppDependencies): OnboardingViewModel {
    val graphEntry = remember(this) { nav.getBackStackEntry<OnboardingRoutes.Graph>() }
    val vm: OnboardingViewModel = viewModel(viewModelStoreOwner = graphEntry) {
        OnboardingViewModel(deps.profiles, deps.sites, deps.onboarding, deps.events, photoUploader = deps.website.imageUploader)
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
            OnboardingStep.Template -> nav.navigate(OnboardingRoutes.Template)
            OnboardingStep.Link -> nav.navigate(OnboardingRoutes.Link)
            OnboardingStep.Profile, null -> Unit
        }
        if (step != null) vm.onNavigated()
    }
}

/** The renderSite input for step 2: the seeded starter content (from the saved profile) in [template], in [locale]. */
internal object StarterPreview {
    fun json(profile: Profile?, template: TemplateSpec, locale: String): String {
        val content = profile?.let { StarterContent.from(it, template, locale, it.id) } ?: kotlinx.serialization.json.JsonObject(emptyMap())
        val site = Site(
            id = "", slug = "", templateKey = template.key, accent = template.defaultAccent, locale = locale,
            draftContent = content, isPublished = false, publishedAt = null,
        )
        return SitePreviewPayload.json(site, profile)
    }
}
