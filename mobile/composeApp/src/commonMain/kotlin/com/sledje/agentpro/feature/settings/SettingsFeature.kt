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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.components.Avatar
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.ImageCrop
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.feature.home.heroPhoto
import com.sledje.agentpro.feature.settings.data.ProfileInput
import com.sledje.agentpro.feature.settings.data.ProfileRepository
import com.sledje.agentpro.feature.website.data.ImageUnreadable
import com.sledje.agentpro.feature.website.data.ImageUploader
import com.sledje.agentpro.feature.website.data.MediaPaths
import com.sledje.agentpro.feature.website.data.SiteRepository
import com.sledje.agentpro.feature.website.data.StarterContent
import com.sledje.agentpro.feature.website.ui.rememberPhotoEditor
import com.sledje.agentpro.resources.form_add_photo
import com.sledje.agentpro.resources.form_change_photo
import com.sledje.agentpro.resources.profile_photo_saved
import com.sledje.agentpro.resources.website_photo_unreadable
import com.sledje.agentpro.resources.website_photo_uploading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            val photoVm = viewModel { ProfilePhotoViewModel(host.deps.profiles, host.deps.sites, host.deps.website.imageUploader) }
            val photo by photoVm.state.collectAsState()
            val pickPhoto = rememberPhotoEditor(
                host.deps.website.mediaPicker, host.deps.website.imageUploader,
                fieldFor = { ProfilePhotoViewModel.PHOTO_SPEC }, onResult = { _, image, crop -> photoVm.onPicked(image, crop) },
            )
            LaunchedEffect(photo.saved) {
                if (photo.saved) { photoVm.consumeSaved(); snackbar.showSnackbar(getString(Res.string.profile_photo_saved)) }
            }
            ProfileScreen(state, CompanyActions.of(vm), onBack = { host.nav.popBackStack() }, snackbar = snackbar, photo = photo, onChangePhoto = { pickPhoto(ProfilePhotoViewModel.PHOTO) })
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

/** Settings → Profile → the photo: shown as the Home avatar, and as the website's hero photo once published. */
data class ProfilePhotoState(
    val name: String = "",
    val photoUrl: String? = null,
    val uploading: Boolean = false,
    val error: AppError? = null,
    val saved: Boolean = false,
)

/**
 * Changes the agent's photo from Profile: uploads the cropped photo, saves it on the profile and, when the agent has a
 * site, in the draft's `hero.photo` too, so the website and the profile never show two different people. The live
 * site changes at the next publish (the same as editing the Hero section).
 */
class ProfilePhotoViewModel(
    private val profiles: ProfileRepository,
    private val sites: SiteRepository,
    private val uploader: ImageUploader,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfilePhotoState())
    val state: StateFlow<ProfilePhotoState> = _state

    init {
        viewModelScope.launch {
            combine(profiles.observeMine(), sites.observeMine()) { profile, site ->
                ProfilePhotoState().copy(name = profile?.fullName.orEmpty(), photoUrl = site?.heroPhoto() ?: profile?.photoUrl?.takeIf { it.isNotBlank() })
            }.collect { latest -> _state.update { it.copy(name = latest.name, photoUrl = latest.photoUrl) } }
        }
    }

    fun onPicked(image: PickedImage, crop: ImageCrop?) {
        if (_state.value.uploading) return
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val url = when (val up = uploader.upload(image, ImageAspect.parse(PHOTO_SPEC.aspect), MediaPaths.PROFILE_PHOTO, crop)) {
                is Result.Ok -> up.value
                is Result.Err -> return@launch fail(up.error)
            }
            val profile = profiles.observeMine().first() ?: return@launch fail(AppError.Validation(PHOTO))
            val input = ProfileInput(
                fullName = profile.fullName.orEmpty(), phone = profile.phone.orEmpty(), city = profile.city,
                agencyCode = profile.agencyCode, branch = profile.branch, photoUrl = url,
            )
            (profiles.update(input) as? Result.Err)?.let { return@launch fail(it.error) }
            sites.observeMine().first()?.let { site ->
                val hero = (site.draftContent[StarterContent.HERO] as? JsonObject) ?: JsonObject(emptyMap())
                val content = JsonObject(site.draftContent + (StarterContent.HERO to JsonObject(hero + (StarterContent.PHOTO to JsonPrimitive(url)))))
                (sites.saveDraft(content) as? Result.Err)?.let { return@launch fail(it.error) }
            }
            _state.update { it.copy(uploading = false, saved = true) }
        }
    }

    private fun fail(error: AppError) = _state.update { it.copy(uploading = false, error = error) }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    companion object {
        const val PHOTO = "photo_url"

        /** The website shows this photo as the hero photo, so it takes that field's crop and shapes. */
        val PHOTO_SPEC: FieldSpec = SiteSchema.section("hero")!!.fields.first { it.key == "photo" }
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
fun ProfileScreen(
    state: CompaniesUiState,
    actions: CompanyActions,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    photo: ProfilePhotoState = ProfilePhotoState(),
    onChangePhoto: () -> Unit = {},
) {
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.profile_title), onBack = onBack) },
        offline = !LocalIsOnline.current,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            val online = LocalIsOnline.current
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Avatar(photo.name, photoUrl = photo.photoUrl)
                SecondaryButton(
                    text = stringResource(if (photo.photoUrl.isNullOrBlank()) Res.string.form_add_photo else Res.string.form_change_photo),
                    onClick = onChangePhoto,
                    enabled = online && !photo.uploading,
                )
            }
            if (photo.uploading) Text(stringResource(Res.string.website_photo_uploading), style = AgentProTheme.text.bodyMuted)
            photo.error?.let { Text(stringResource(if (it == ImageUnreadable) Res.string.website_photo_unreadable else it.message), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.error) }
            CompaniesBlock(state, actions)
        }
    }
    CompanySheets(state, actions)
}
