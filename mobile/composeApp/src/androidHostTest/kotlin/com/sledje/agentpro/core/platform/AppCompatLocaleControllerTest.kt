package com.sledje.agentpro.core.platform

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sledje.agentpro.core.catalog.AppLocale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/**
 * [AppCompatLocaleController] over AppCompat's per-app locales (DECISIONS #45), on Robolectric SDK 35.
 * On Android 13+ AppCompat reaches the system LocaleManager through a live AppCompat activity (in the
 * app: MainActivity), so each test keeps one alive.
 */
@RunWith(RobolectricTestRunner::class)
class AppCompatLocaleControllerTest {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java)
        .also { it.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar) }
        .setup()

    @After fun reset() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        ShadowLooper.idleMainLooper()
        activity.pause().stop().destroy()
    }

    private fun set(controller: AppCompatLocaleController, locale: AppLocale?) {
        runBlocking { controller.set(locale) }
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun choiceIsNullUntilSynced() {
        val controller = AppCompatLocaleController()
        assertNull("the root waits instead of flashing the wrong language", controller.choice.value)
        controller.sync()
        assertEquals(LocaleChoice(null), controller.choice.value)
    }

    @Test
    fun settingHindiSetsTheAppLocales() {
        val controller = AppCompatLocaleController()
        set(controller, AppLocale.Hi)
        assertEquals(LocaleChoice(AppLocale.Hi), controller.choice.value)
        assertEquals("hi", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun followDeviceClearsTheAppLocales() {
        val controller = AppCompatLocaleController()
        set(controller, AppLocale.Hi)
        set(controller, null)
        assertEquals(LocaleChoice(null), controller.choice.value)
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
    }

    @Test
    fun syncReadsAChoiceMadeElsewhere() {
        // e.g. the user picked the language in Android 13+'s per-app language settings.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))
        ShadowLooper.idleMainLooper()
        val controller = AppCompatLocaleController()
        controller.sync()
        assertEquals(LocaleChoice(AppLocale.Hi), controller.choice.value)
    }

    @Test
    fun anUnsupportedLanguageFollowsTheDevice() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
        ShadowLooper.idleMainLooper()
        assertEquals(LocaleChoice(null), AppCompatLocaleController.current())
    }
}
