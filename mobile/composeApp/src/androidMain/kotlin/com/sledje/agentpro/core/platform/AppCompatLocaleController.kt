package com.sledje.agentpro.core.platform

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sledje.agentpro.core.catalog.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [LocaleController] on Android (DECISIONS #45): the in-app EN/हिं switch is AppCompat's per-app
 * language, so it also shows in Android 13+'s per-app language settings. On Android 12 and lower,
 * AppCompat stores it itself (`autoStoreLocales` metadata in the app manifest).
 *
 * [choice] stays null until [sync] runs in `MainActivity.onCreate` (after `super.onCreate`): on
 * Android 12 and lower AppCompat only loads the stored language when the first AppCompat activity is
 * created. The app root still applies the choice through the common `LocalAppLocale` override, so
 * Compose resources follow it on every Android version without waiting for a recreate.
 */
class AppCompatLocaleController : LocaleController {
    private val _choice = MutableStateFlow<LocaleChoice?>(null)
    override val choice: StateFlow<LocaleChoice?> = _choice.asStateFlow()

    /** Reads AppCompat's current app language (also after the user changed it in system settings). */
    fun sync() {
        _choice.value = current()
    }

    override suspend fun set(locale: AppLocale?) {
        withContext(Dispatchers.Main.immediate) {
            val tags = locale?.key?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
            _choice.value = LocaleChoice(locale)
            // Recreates the activities when the language really changes; ViewModels survive.
            AppCompatDelegate.setApplicationLocales(tags)
        }
    }

    companion object {
        /** Empty app locales = follow the device; an unknown language also follows the device. */
        fun current(): LocaleChoice {
            val locales = AppCompatDelegate.getApplicationLocales()
            val language = if (locales.isEmpty) null else locales[0]?.language
            return LocaleChoice(language?.let { AppLocale.fromKey(it) })
        }
    }
}
