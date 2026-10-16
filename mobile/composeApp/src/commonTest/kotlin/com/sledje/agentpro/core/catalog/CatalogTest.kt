package com.sledje.agentpro.core.catalog

import com.sledje.agentpro.core.forms.FieldType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Smoke tests of the generated catalog (the generator itself is tested in mobile/build-logic). */
class CatalogTest {
    @Test
    fun keysRoundTrip() {
        EnquiryStatus.entries.forEach { assertEquals(it, EnquiryStatus.fromKey(it.key)) }
        PremiumMode.entries.forEach { assertTrue(it.months > 0, "${it.key} has months") }
        assertEquals(EnquiryStatus.entries.size, EnquiryStatus.entries.map { it.key }.toSet().size)
    }

    @Test
    fun siteSchemaIsGenerated() {
        val hero = assertNotNull(SiteSchema.section("hero"))
        assertTrue(hero.pinned)
        assertTrue(SiteSchema.sections.flatMap { it.fields }.any { it.type == FieldType.ItemList })
    }
}
