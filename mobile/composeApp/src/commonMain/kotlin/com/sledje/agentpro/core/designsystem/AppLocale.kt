package com.sledje.agentpro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * Overrides the app language inside a composition (the Compose Multiplatform "change the locale in
 * app" pattern). Used by the debug component gallery's EN/HI switch now, and by the iOS
 * `LocaleController` later (docs/ARCHITECTURE.md, Platform seams). Wrap the overridden content in
 * `key(language) { … }` so it recomposes with the new strings, font and line heights.
 * A null value restores the language the app started with.
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}
