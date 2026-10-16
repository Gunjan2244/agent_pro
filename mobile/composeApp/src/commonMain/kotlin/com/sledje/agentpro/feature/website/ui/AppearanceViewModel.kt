package com.sledje.agentpro.feature.website.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.AccentSwatch
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteAppearance
import com.sledje.agentpro.feature.website.data.SiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppearanceUiState(
    val site: Site? = null,
    val saving: Boolean = false,
    val error: AppError? = null,
    /** One-shot: the template picker's choice was saved (close the picker). */
    val templateSaved: Boolean = false,
) {
    val template: TemplateSpec? get() = site?.let { Templates.fromKey(it.templateKey) }

    /** The swatch shown as selected: the site's, else its template's default (what the site renders). */
    val accent: AccentSwatch? get() = site?.accent ?: template?.defaultAccent

    /** The site language shown as selected: the site's, else the default (first) locale. */
    val locale: AppLocale get() = site?.locale?.let { AppLocale.fromKey(it) } ?: AppLocale.active.first()
}

/**
 * Appearance (docs/UX.md): template (through the picker), one of the 8 catalog accent swatches and the
 * site language. Each choice is saved at once to `sites.template_key / accent / locale`, which the live
 * site reads directly (no publish needed). Choices come only from the generated catalog.
 */
class AppearanceViewModel(private val sites: SiteRepository) : ViewModel() {
    private val _state = MutableStateFlow(AppearanceUiState())
    val state: StateFlow<AppearanceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { sites.observeMine().collect { s -> _state.update { it.copy(site = s) } } }
    }

    fun selectAccent(key: String) {
        val swatch = AccentSwatch.fromKey(key) ?: return
        if (swatch == _state.value.accent) return
        save { it.copy(accent = swatch.key) }
    }

    fun selectLocale(key: String) {
        val locale = AppLocale.fromKey(key) ?: return
        if (locale == _state.value.locale && _state.value.site?.locale != null) return
        save { it.copy(locale = locale.key) }
    }

    fun selectTemplate(key: String) {
        val template = Templates.fromKey(key) ?: return
        if (template.key == _state.value.site?.templateKey) {
            _state.update { it.copy(templateSaved = true) }
            return
        }
        save(onDone = { _state.update { it.copy(templateSaved = true) } }) { it.copy(templateKey = template.key) }
    }

    fun consumeTemplateSaved() = _state.update { it.copy(templateSaved = false) }

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun save(onDone: () -> Unit = {}, change: (SiteAppearance) -> SiteAppearance) {
        val current = _state.value
        val site = current.site ?: return
        if (current.saving) return
        // The accent saved is the one shown (an unset accent becomes the template default explicitly).
        val base = SiteAppearance(site.templateKey, current.accent?.key, site.locale)
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = sites.updateAppearance(change(base))) {
                is Result.Ok -> {
                    _state.update { it.copy(saving = false, site = result.value) }
                    onDone()
                }
                is Result.Err -> _state.update { it.copy(saving = false, error = result.error) }
            }
        }
    }
}
