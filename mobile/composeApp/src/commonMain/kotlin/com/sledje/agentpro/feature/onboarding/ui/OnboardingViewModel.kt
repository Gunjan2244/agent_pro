package com.sledje.agentpro.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.app.AppEvents
import com.sledje.agentpro.app.AppMessage
import com.sledje.agentpro.app.OnboardingSession
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.core.forms.Validators
import com.sledje.agentpro.feature.settings.data.Profile
import com.sledje.agentpro.feature.settings.data.ProfileInput
import com.sledje.agentpro.feature.settings.data.ProfileRepository
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.feature.website.data.ImageUploader
import com.sledje.agentpro.feature.website.data.MediaPaths
import com.sledje.agentpro.feature.website.data.NewSite
import com.sledje.agentpro.feature.website.data.PublishOutcome
import com.sledje.agentpro.feature.website.data.SiteRepository
import com.sledje.agentpro.feature.website.data.StarterContent
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.cat_field_city
import com.sledje.agentpro.resources.cat_field_designation
import com.sledje.agentpro.resources.cat_field_name
import com.sledje.agentpro.resources.cat_field_photo
import com.sledje.agentpro.resources.profile_agency_code
import com.sledje.agentpro.resources.profile_branch
import com.sledje.agentpro.resources.profile_mobile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** The onboarding steps (docs/UX.md, Onboarding: 1 Profile → 2 Template → 3 Your link). */
enum class OnboardingStep(val number: Int) { Profile(1), Template(2), Link(3) }

/** Live state of the slug field on step 3. */
sealed interface SlugStatus {
    data object Empty : SlugStatus
    data class Invalid(val error: ValidationError) : SlugStatus
    data object Checking : SlugStatus
    data object Available : SlugStatus
    data object Taken : SlugStatus

    /** The availability check failed (e.g. offline); the agent can still try to save. */
    data class Unchecked(val error: AppError) : SlugStatus
}

data class OnboardingUiState(
    val saving: Boolean = false,
    /** The step-1 photo is being prepared and uploaded (Continue waits for it). */
    val uploadingPhoto: Boolean = false,
    /**
     * Step 1 shows the name and mobile (from sign-up) as a summary with Edit instead of asking again;
     * false once Edit is tapped, or when either is missing or invalid.
     */
    val contactSummary: Boolean = false,
    val error: AppError? = null,
    val templateKey: String = Templates.all.first().key,
    val slug: String = "",
    val slugStatus: SlugStatus = SlugStatus.Empty,
    /** One-shot: the step to open next. */
    val navigateTo: OnboardingStep? = null,
    val finished: Boolean = false,
) {
    val template: TemplateSpec get() = Templates.fromKey(templateKey) ?: Templates.all.first()
    val canSubmitLink: Boolean get() = !saving && (slugStatus == SlugStatus.Available || slugStatus is SlugStatus.Unchecked)
}

/**
 * Drives all three onboarding steps (one instance, scoped to the onboarding nav graph):
 * 1. saves the profile (name and mobile required, usually already given at sign-up and shown as a
 *    summary with Edit; photo, agency code and branch under More details);
 * 2. picks a template from the generated `Templates` (shared/templates/index.json);
 * 3. suggests a slug from the name, validates it live (format, `ReservedSlugs`) and checks
 *    `is_slug_available` after a 300ms pause; then creates the site and either publishes it
 *    (`publish_site`) or leaves it as a draft ("Later"). Finishing posts "Your 14-day free trial has
 *    started" and releases the [OnboardingSession] latch so the gate opens Main.
 */
class OnboardingViewModel(
    private val profiles: ProfileRepository,
    private val sites: SiteRepository,
    private val session: OnboardingSession,
    private val events: AppEvents,
    private val slugDebounceMillis: Long = SLUG_DEBOUNCE_MILLIS,
    /** Step 1's photo pipeline (ImageProcessor 1:1 WebP → Storage); null in tests that never pick one. */
    private val photoUploader: ImageUploader? = null,
) : ViewModel() {
    val profileForm = FormState(PROFILE_FIELDS)

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var profile: Profile? = null
    private var prefilled = false
    private var siteCreated = false
    private var slugJob: Job? = null

    init {
        viewModelScope.launch {
            profiles.observeMine().collect { p ->
                profile = p
                if (p != null && !prefilled) {
                    prefilled = true
                    prefill(p)
                }
            }
        }
    }

    private fun prefill(p: Profile) {
        mapOf(
            NAME to p.fullName, MOBILE to p.phone, DESIGNATION to p.designation, CITY to p.city,
            AGENCY_CODE to p.agencyCode, BRANCH to p.branch, PHOTO to p.photoUrl,
        ).forEach { (key, value) -> if (!value.isNullOrBlank() && profileForm.text(key).isBlank()) profileForm.set(key, value) }
        if (profileForm.error(NAME) == null && profileForm.error(MOBILE) == null) _state.update { it.copy(contactSummary = true) }
        if (_state.value.slug.isEmpty()) suggestSlug(p.fullName)
    }

    /** Edit on step 1's summary: shows the name and mobile fields. */
    fun editContact() = _state.update { it.copy(contactSummary = false) }

    // ---- Step 1 -------------------------------------------------------------------------------

    /** Prepares the picked photo (square crop, WebP ≤ 1024 px) and uploads it; the form keeps its URL. */
    fun uploadPhoto(image: PickedImage) {
        val uploader = photoUploader ?: return
        if (_state.value.uploadingPhoto) return
        _state.update { it.copy(uploadingPhoto = true, error = null) }
        viewModelScope.launch {
            val aspect = ImageAspect.parse(profileForm.spec(PHOTO).aspect)
            when (val result = uploader.upload(image, aspect, MediaPaths.PROFILE_PHOTO)) {
                is Result.Ok -> {
                    profileForm.set(PHOTO, result.value)
                    _state.update { it.copy(uploadingPhoto = false) }
                }
                is Result.Err -> _state.update { it.copy(uploadingPhoto = false, error = result.error) }
            }
        }
    }

    fun saveProfile() {
        if (_state.value.saving || _state.value.uploadingPhoto || !profileForm.validateAll()) return
        val v = profileForm.values()
        val input = ProfileInput(
            fullName = v[NAME] as String,
            phone = v[MOBILE] as String,
            designation = v[DESIGNATION] as? String,
            city = v[CITY] as? String,
            agencyCode = v[AGENCY_CODE] as? String,
            branch = v[BRANCH] as? String,
            photoUrl = v[PHOTO] as? String,
        )
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = profiles.update(input)) {
                is Result.Ok -> {
                    profile = result.value
                    if (_state.value.slug.isEmpty()) suggestSlug(result.value.fullName)
                    _state.update { it.copy(saving = false, navigateTo = OnboardingStep.Template) }
                }
                is Result.Err -> _state.update { it.copy(saving = false, error = result.error) }
            }
        }
    }

    // ---- Step 2 -------------------------------------------------------------------------------

    fun selectTemplate(key: String) {
        if (Templates.fromKey(key) == null) return
        _state.update { it.copy(templateKey = key, navigateTo = OnboardingStep.Link) }
    }

    // ---- Step 3 -------------------------------------------------------------------------------

    fun onSlugChange(input: String) {
        val slug = input.trim().lowercase()
        _state.update { it.copy(slug = slug, error = null) }
        slugJob?.cancel()
        val local = if (slug.isEmpty()) null else Validators.slug(slug)
        when {
            slug.isEmpty() -> _state.update { it.copy(slugStatus = SlugStatus.Empty) }
            local != null -> _state.update { it.copy(slugStatus = SlugStatus.Invalid(local)) }
            else -> {
                _state.update { it.copy(slugStatus = SlugStatus.Checking) }
                slugJob = viewModelScope.launch {
                    delay(slugDebounceMillis)
                    val status = when (val result = sites.isSlugAvailable(slug)) {
                        is Result.Ok -> if (result.value) SlugStatus.Available else SlugStatus.Taken
                        is Result.Err -> SlugStatus.Unchecked(result.error)
                    }
                    _state.update { if (it.slug == slug) it.copy(slugStatus = status) else it }
                }
            }
        }
    }

    fun publishNow() = finishLink(publish = true)

    fun later() = finishLink(publish = false)

    private fun finishLink(publish: Boolean) {
        val current = _state.value
        if (!current.canSubmitLink && !siteCreated) return
        if (current.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            if (!siteCreated) {
                val p = profile
                val created = sites.create(
                    NewSite(
                        slug = current.slug,
                        templateKey = current.template.key,
                        accent = current.template.defaultAccent.key,
                        draftContent = if (p != null) StarterContent.from(p) else JsonObject(emptyMap()),
                    ),
                )
                when (created) {
                    is Result.Ok -> siteCreated = true
                    is Result.Err -> {
                        _state.update {
                            if (created.error == AppError.Conflict) it.copy(saving = false, slugStatus = SlugStatus.Taken)
                            else it.copy(saving = false, error = created.error)
                        }
                        return@launch
                    }
                }
            }
            if (publish) {
                when (val published = sites.publish()) {
                    is Result.Ok -> if (published.value is PublishOutcome.Invalid) {
                        _state.update { it.copy(saving = false, error = AppError.Validation("site")) }
                        return@launch
                    }
                    is Result.Err -> {
                        _state.update { it.copy(saving = false, error = published.error) }
                        return@launch
                    }
                }
            }
            events.post(AppMessage.TrialStarted())
            _state.update { it.copy(saving = false, finished = true) }
            session.finish()
        }
    }

    fun onNavigated() = _state.update { it.copy(navigateTo = null) }

    private fun suggestSlug(name: String?) {
        val suggestion = SlugSuggestion.fromName(name)
        if (suggestion.isNotEmpty()) onSlugChange(suggestion)
    }

    companion object {
        const val SLUG_DEBOUNCE_MILLIS = 300L
        const val NAME = "full_name"
        const val MOBILE = "phone"
        const val DESIGNATION = "designation"
        const val CITY = "city"
        const val AGENCY_CODE = "agency_code"
        const val BRANCH = "branch"
        const val PHOTO = "photo_url"

        /** Step 1's visible fields; agency code and branch go under More details (docs/UX.md). */
        val VISIBLE_PROFILE_KEYS = setOf(NAME, MOBILE, DESIGNATION, CITY)

        /** The fields step 1's contact summary stands in for. */
        val CONTACT_KEYS = setOf(NAME, MOBILE)

        /**
         * The profile form. `profiles` has no length limits in SQL; these follow the matching site
         * fields (enquiry name 80, about.designation 60) and are provisional.
         */
        val PROFILE_FIELDS: List<FieldSpec> = listOf(
            FieldSpec(NAME, FieldType.Text, Res.string.cat_field_name, required = true, maxLength = 80),
            FieldSpec(MOBILE, FieldType.Phone, Res.string.profile_mobile, required = true),
            FieldSpec(DESIGNATION, FieldType.Text, Res.string.cat_field_designation, maxLength = 60),
            FieldSpec(CITY, FieldType.Text, Res.string.cat_field_city, maxLength = 60),
            // The same square crop as the hero photo (site-schema hero.photo, aspect 1:1).
            FieldSpec(PHOTO, FieldType.Image, Res.string.cat_field_photo, aspect = "1:1"),
            FieldSpec(AGENCY_CODE, FieldType.Text, Res.string.profile_agency_code, maxLength = 30),
            FieldSpec(BRANCH, FieldType.Text, Res.string.profile_branch, maxLength = 60),
        )
    }
}

/** A slug suggested from the agent's name (docs/UX.md, step 3): "Ramesh Kumar" → `ramesh-kumar`. */
object SlugSuggestion {
    private const val MAX = 30

    /** Lowercase ASCII letters and digits joined by `-`, at most 30; "" when nothing valid remains. */
    fun fromName(name: String?): String {
        val base = name.orEmpty().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX)
            .trimEnd('-')
        return if (Validators.slug(base) == null) base else ""
    }
}
