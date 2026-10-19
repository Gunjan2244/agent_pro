package com.sledje.agentpro.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sledje.agentpro.app.AppDependencies
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.AvatarButton
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.NavRow
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.BottomNavItem
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.StarterContent
import kotlinx.serialization.json.JsonObject
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_settings
import com.sledje.agentpro.resources.home_greeting
import com.sledje.agentpro.resources.home_greeting_plain
import com.sledje.agentpro.resources.home_nothing_due
import com.sledje.agentpro.resources.home_setup_row
import com.sledje.agentpro.resources.ic_check_circle
import com.sledje.agentpro.resources.ic_home
import com.sledje.agentpro.resources.ic_radio_button_unchecked
import com.sledje.agentpro.resources.setup_about
import com.sledje.agentpro.resources.setup_customer
import com.sledje.agentpro.resources.setup_done
import com.sledje.agentpro.resources.setup_profile
import com.sledje.agentpro.resources.setup_publish
import com.sledje.agentpro.resources.setup_share
import com.sledje.agentpro.resources.setup_title
import com.sledje.agentpro.resources.setup_todo
import com.sledje.agentpro.resources.tab_home
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Serializable data object HomeRoute

/** Home: always on (no catalog flag). Phase 5 has the greeting, the setup checklist and "Nothing due today"; Phase 11 adds Today, KPIs and the chart. */
object HomeFeature : AppFeature {
    override val flag = null

    override val bottomNavItem = BottomNavItem("home", HomeRoute, Res.string.tab_home, Res.drawable.ic_home, order = 1)

    override fun NavGraphBuilder.routes(host: FeatureHost) {
        composable<HomeRoute> {
            val vm = viewModel { HomeViewModel(host.deps) }
            val state by vm.state.collectAsState()
            HomeScreen(state, onOpenSettings = host::openSettings)
        }
    }
}

/** One item of the setup checklist (docs/UX.md, Home: profile, publish, own About text, share the link, first customer). */
enum class SetupItem(val label: StringResource) {
    Profile(Res.string.setup_profile),
    Publish(Res.string.setup_publish),
    About(Res.string.setup_about),
    Share(Res.string.setup_share),
    FirstCustomer(Res.string.setup_customer),
}

data class HomeUiState(val firstName: String? = null, val done: Set<SetupItem> = emptySet()) {
    val setupComplete: Boolean get() = done.size == SetupItem.entries.size
}

/**
 * Home's state. The checklist is a stub until its features exist: "Share your link" is a local mark
 * (set by the Website share action in Phase 8) and "Add your first customer" is never done until Phase 7.
 */
class HomeViewModel(deps: AppDependencies) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        deps.profiles.observeMine(),
        deps.sites.observeMine(),
        deps.store.observe(StoreKeys.SITE_SHARED),
    ) { profile, site, shared ->
        HomeUiState(
            firstName = profile?.firstName,
            done = buildSet {
                if (profile?.isComplete == true) add(SetupItem.Profile)
                if (site?.isPublished == true) add(SetupItem.Publish)
                // The seeded bio is shared text; the agent's own words rank better (docs/UX.md, Home).
                if (StarterContent.isOwnBio(site?.aboutBio())) add(SetupItem.About)
                if (shared != null) add(SetupItem.Share)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())
}

/** The draft's `about.bio` text, null when unset. */
internal fun Site.aboutBio(): String? =
    ((draftContent[StarterContent.ABOUT] as? JsonObject)?.get(StarterContent.BIO) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

@Composable
fun HomeScreen(state: HomeUiState, onOpenSettings: () -> Unit) {
    var sheet by remember { mutableStateOf(false) }
    val title = state.firstName?.let { stringResource(Res.string.home_greeting, it) } ?: stringResource(Res.string.home_greeting_plain)
    TabScreen(
        Res.string.tab_home,
        titleText = title,
        trailing = { AvatarButton(state.firstName.orEmpty(), stringResource(Res.string.action_settings), onOpenSettings) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (!state.setupComplete) {
                NavRow(
                    title = stringResource(Res.string.home_setup_row, state.done.size, SetupItem.entries.size),
                    onClick = { sheet = true },
                )
                HairlineDivider()
            }
            Text(
                stringResource(Res.string.home_nothing_due),
                style = AgentProTheme.text.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.m),
            )
        }
    }
    if (sheet) {
        AppBottomSheet(stringResource(Res.string.setup_title), onDismiss = { sheet = false }) {
            SetupItem.entries.forEach { item ->
                val done = item in state.done
                ListRow(
                    title = stringResource(item.label),
                    supporting = stringResource(if (done) Res.string.setup_done else Res.string.setup_todo),
                    leading = ListLeading.Icon(if (done) Res.drawable.ic_check_circle else Res.drawable.ic_radio_button_unchecked),
                )
            }
        }
    }
}
