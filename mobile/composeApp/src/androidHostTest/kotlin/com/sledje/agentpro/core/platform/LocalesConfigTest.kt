package com.sledje.agentpro.core.platform

import com.sledje.agentpro.core.catalog.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * androidApp's `res/xml/locales_config.xml` (Android 13+ per-app languages, DECISIONS #45) must list
 * exactly the catalog's locales (CLAUDE.md: definitions live only in shared/).
 */
class LocalesConfigTest {
    @Test
    fun localeConfigMatchesTheCatalog() {
        // Host tests run with the composeApp module as the working directory.
        val xml = File("../androidApp/src/main/res/xml/locales_config.xml").readText()
        val listed = Regex("""<locale\s+android:name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
        assertEquals(AppLocale.entries.map { it.key }.sorted(), listed.sorted())
    }
}
