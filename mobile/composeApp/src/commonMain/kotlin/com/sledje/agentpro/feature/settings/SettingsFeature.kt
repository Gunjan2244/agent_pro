package com.sledje.agentpro.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sledje.agentpro.app.AppDependencies
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.ChoiceChips
import com.sledje.agentpro.core.designsystem.components.ChoiceOption
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.NavRow
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.feature.offers.ui.CompaniesBlock
import com.sledje.agentpro.feature.offers.ui.CompaniesUiState
import com.sledje.agentpro.feature.offers.ui.CompaniesViewModel
import com.sledje.agentpro.feature.offers.ui.CompanyActions
import com.sledje.agentpro.feature.offers.ui.CompanySheets
import com.sledje.agentpro.feature.privacy.ui.NoticeScreen
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.sledje.agentpro.resources.profile_title
import com.sledje.agentpro.resources.settings_profile_supporting
import com.sledje.agentpro.resources.cat_privacy_agent_signup_title
import com.sledje.agentpro.core.designsystem.components.SectionHeader
import com.sledje.agentpro.core.designsystem.currentAppLocale
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_settings
import com.sledje.agentpro.resources.settings_language
import com.sledje.agentpro.resources.settings_sign_out
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.sledje.agentpro.feature.website.ui.ServicesMatchSheet
import com.sledje.agentpro.feature.website.ui.ServicesMatchViewModel
import com.sledje.agentpro.resources.website_match_done
import org.jetbrains.compose.resources.getString
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Serializable data object SettingsRoute

/** The in-app privacy notice (docs/PRIVACY.md, section 1), opened from Settings. */
@Serializable data object PrivacyNoticeRoute

/** Settings → Profile: the agent's companies and their insurance types (docs/UX.md; DECISIONS #89). */
@Serializable data object ProfileRoute

/**
 * Settings: always on, opened from the Home avatar. Phase 5 has Language (EN/हिं) and Sign out;
 * Phase 13 adds the rest (docs/UX.md, Settings).
 */
object SettingsFeature : AppFeature {
    override val flag = null

    override fun NavGraphBuilder.routes(host: FeatureHost) {
        composable<SettingsRoute> {
            val vm = viewModel { SettingsViewModel(host.deps) }
            SettingsScreen(
                currentLocale = currentAppLocale(),
                onLocale = vm::setLocale,
                onSignOut = vm::signOut,
                onBack = { host.nav.popBackStack() },
                onPrivacyNotice = { host.nav.navigate(PrivacyNoticeRoute) },
                onProfile = { host.nav.navigate(ProfileRoute) },
            )
        }
        composable<ProfileRoute> {
            val match = viewModel { ServicesMatchViewModel(host.deps.sites, host.deps.companies, host.deps.profiles) }
            val vm = viewModel { CompaniesViewModel(host.deps.companies, onChanged = match::typesChanged) }
            val state by vm.state.collectAsState()
            val matchState by match.state.collectAsState()
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(matchState.applied) {
                if (matchState.applied) { match.consumeApplied(); snackbar.showSnackbar(getString(Res.string.website_match_done)) }
            }
            ProfileScreen(state, CompanyActions.of(vm), onBack = { host.nav.popBackStack() }, snackbar = snackbar)
            ServicesMatchSheet(matchState, onApply = match::apply, onDismiss = match::dismiss)
        }
        composable<PrivacyNoticeRoute> { NoticeScreen(PrivacyNotice.AgentSignup, onBack = { host.nav.popBackStack() }) }
    }
}

class SettingsViewModel(private val deps: AppDependencies) : ViewModel() {
    fun setLocale(locale: AppLocale) {
        viewModelScope.launch { deps.locale.set(locale) }
    }

    /** Signs out; the gate then shows Welcome and the cache is cleared. */
    fun signOut() {
        viewModelScope.launch { deps.auth.signOut() }
    }
}

@Composable
fun SettingsScreen(
    currentLocale: AppLocale,
    onLocale: (AppLocale) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onPrivacyNotice: () -> Unit = {},
    onProfile: () -> Unit = {},
) {
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.action_settings), onBack = onBack) },
        offline = !LocalIsOnline.current,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NavRow(title = stringResource(Res.string.profile_title), supporting = stringResource(Res.string.settings_profile_supporting), onClick = onProfile)
            HairlineDivider()
            SectionHeader(stringResource(Res.string.settings_language))
            ChoiceChips(
                options = AppLocale.active.map { ChoiceOption(it.key, it.label()) },
                selected = setOf(currentLocale.key),
                onToggle = { key -> AppLocale.fromKey(key)?.let(onLocale) },
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
            HairlineDivider()
            NavRow(title = stringResource(Res.string.cat_privacy_agent_signup_title), onClick = onPrivacyNotice)
            HairlineDivider()
            ListRow(title = stringResource(Res.string.settings_sign_out), onClick = onSignOut)
        }
    }
}

/**
 * Settings → Profile: the agent's companies, one card each with its status, plus Add company (docs/UX.md, Settings →
 * Profile). The profile's own fields are not editable here yet.
 */
@Composable
fun ProfileScreen(state: CompaniesUiState, actions: CompanyActions, onBack: () -> Unit, snackbar: SnackbarHostState? = null) {
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.profile_title), onBack = onBack) },
        offline = !LocalIsOnline.current,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m)) {
            CompaniesBlock(state, actions)
        }
    }
    CompanySheets(state, actions)
}
