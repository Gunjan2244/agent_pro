package com.sledje.agentpro.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
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
}

/**
 * The onboarding graph, shown by the gate while the profile or site is missing. One
 * [OnboardingViewModel] is shared by the three steps (scoped to [OnboardingRoutes.Graph]).
 * [profileDone] starts at step 2 when the profile was already saved.
 */
@Composable
fun OnboardingNavHost(deps: AppDependencies, profileDone: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = OnboardingRoutes.Graph) {
        navigation<OnboardingRoutes.Graph>(startDestination = if (profileDone) OnboardingRoutes.Template else OnboardingRoutes.Profile) {
            composable<OnboardingRoutes.Profile> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                Navigate(vm, state.navigateTo, nav)
                val pickPhoto = deps.website.mediaPicker.rememberImagePicker { picked -> picked?.let(vm::uploadPhoto) }
                OnboardingProfileScreen(vm.profileForm, state, onContinue = vm::saveProfile, onPickPhoto = pickPhoto)
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
                    onBack = if (profileDone) null else fun() { nav.popBackStack() },
                    // The agent's own starter content rendered in each template (docs/UX.md, Onboarding step 2).
                    preview = { template ->
                        val profile by deps.profiles.observeMine().collectAsState(null)
                        val json = remember(profile, template.key) { StarterPreview.json(profile, template) }
                        deps.website.sitePreview.Render(json, Modifier.fillMaxSize())
                    },
                )
            }
            composable<OnboardingRoutes.Link> { entry ->
                val vm = entry.onboardingViewModel(nav, deps)
                val state by vm.state.collectAsState()
                OnboardingLinkScreen(
                    state = state,
                    onSlugChange = vm::onSlugChange,
                    onPublishNow = vm::publishNow,
                    onLater = vm::later,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun NavBackStackEntry.onboardingViewModel(nav: NavController, deps: AppDependencies): OnboardingViewModel {
    val graphEntry = remember(this) { nav.getBackStackEntry<OnboardingRoutes.Graph>() }
    return viewModel(viewModelStoreOwner = graphEntry) {
        OnboardingViewModel(deps.profiles, deps.sites, deps.onboarding, deps.events, photoUploader = deps.website.imageUploader)
    }
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

/** The renderSite input for step 2: the starter content (from the saved profile) in [template]. */
internal object StarterPreview {
    fun json(profile: Profile?, template: TemplateSpec): String {
        val content = profile?.let { StarterContent.from(it) } ?: kotlinx.serialization.json.JsonObject(emptyMap())
        val site = Site(
            id = "", slug = "", templateKey = template.key, accent = template.defaultAccent, locale = null,
            draftContent = content, isPublished = false, publishedAt = null,
        )
        return SitePreviewPayload.json(site, profile)
    }
}
