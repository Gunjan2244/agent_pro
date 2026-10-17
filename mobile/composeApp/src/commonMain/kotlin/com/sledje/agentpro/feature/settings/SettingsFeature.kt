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
import com.sledje.agentpro.feature.privacy.ui.NoticeScreen
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
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Serializable data object SettingsRoute

/** The in-app privacy notice (docs/PRIVACY.md, section 1), opened from Settings. */
@Serializable data object PrivacyNoticeRoute

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
            )
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
) {
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.action_settings), onBack = onBack) },
        offline = !LocalIsOnline.current,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
