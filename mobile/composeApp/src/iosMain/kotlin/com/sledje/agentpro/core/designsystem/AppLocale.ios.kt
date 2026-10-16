package com.sledje.agentpro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

// iOS phase: not compiled on Linux. Follows the Compose Multiplatform "change the locale in app" sample.
actual object LocalAppLocale {
    private const val LANGUAGES_KEY = "AppleLanguages"
    private val default: String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    private val LocalLanguage = staticCompositionLocalOf { default }

    actual val current: String
        @Composable get() = LocalLanguage.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val language = value ?: default
        if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LANGUAGES_KEY)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(listOf(language), LANGUAGES_KEY)
        }
        return LocalLanguage provides language
    }
}
