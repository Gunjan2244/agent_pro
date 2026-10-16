package com.sledje.agentpro.core.platform

import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.StoreKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The in-app language choice: [locale] null follows the device language. */
data class LocaleChoice(val locale: AppLocale?)

/**
 * The EN/हिं in-app switch (Welcome and Settings; docs/UX.md, Global rule 9).
 * [choice] is null until the stored preference has loaded, so the first frame never flashes the
 * wrong language. The app root applies it through `LocalAppLocale`.
 */
interface LocaleController {
    val choice: StateFlow<LocaleChoice?>
    suspend fun set(locale: AppLocale?)
}

/**
 * Stores the choice in the app's [KeyValueStore]; the root composable applies it with the
 * `LocalAppLocale` override. The default when a platform supplies no controller (iOS for now).
 * Android uses `AppCompatLocaleController` (AppCompat per-app locales, DECISIONS #45).
 */
class StoredLocaleController(private val store: KeyValueStore, scope: CoroutineScope) : LocaleController {
    override val choice: StateFlow<LocaleChoice?> = store.observe(StoreKeys.APP_LOCALE)
        .map { key -> LocaleChoice(key?.let { AppLocale.fromKey(it) }) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun set(locale: AppLocale?) = store.put(StoreKeys.APP_LOCALE, locale?.key)
}
