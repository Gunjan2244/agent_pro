package com.sledje.agentpro.core.designsystem

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null

    actual val current: String
        @Composable get() = Locale.getDefault().language

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        val initial = default ?: Locale.getDefault().also { default = it }
        val locale = if (value == null) initial else Locale.forLanguageTag(value)
        // Compose resources and Compose's Locale.current read the JVM default locale.
        Locale.setDefault(locale)
        val overridden = Configuration(configuration).apply { setLocale(locale) }
        return LocalConfiguration provides overridden
    }
}
