package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitionsException
import com.sledje.agentpro.buildlogic.codegen.SiteSeedKotlinGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Interests per insurance type, seed interests, copy mixes and type-derived services (DECISIONS #93). Uses the real shared/. */
class InsuranceTypesSeedTest {

    private fun assertViolation(violations: List<String>, vararg fragments: String) {
        assertTrue(violations.any { v -> fragments.all { it in v } }, "expected a violation containing ${fragments.toList()}, got:\n${violations.joinToString("\n")}")
    }

    /** A fixture holding a copy of the real shared/; null (test skipped) outside the repo. */
    private fun real(): Fixture? {
        val dir = realSharedDir() ?: return null
        try { SharedDefinitions.load(dir) } catch (e: SharedDefinitionsException) { return null }
        return Fixture().also { dir.copyRecursively(it.shared, overwrite = true) }
    }

    private fun checks(fx: Fixture) = fx.verifier().let { it.checkSeed() + it.checkOffers() }

    @Test fun `the real catalog and seed pass and generate the type helpers`() {
        val fx = real() ?: return
        assertEquals(emptyList(), checks(fx))
        val code = SiteSeedKotlinGenerator(fx.defs()).generate()
        listOf(
            "val INSURANCE_TYPES: List<String> = listOf(\"life\", \"health\", \"motor\", \"home\", \"travel\", \"business\", \"accident\")",
            "val WHEN_NONE: List<String> = listOf(\"life\")",
            "SeedMix(\"life\", listOf(\"life\")),",
            "SeedMix(\"general\", null),",
            "\"offered\" to SeedValue.FromInsuranceTypes(\"interestKeys\"),",
            "\"items\" to SeedValue.FromInsuranceTypes(\"serviceItems\"),",
            "\"subheadline\" to SeedValue.ByMix(mapOf(",
            "\"motor\" to SeedValue.Variants(listOf(SeedVariant(texts.getValue(\"seed.services.motor\")))),",
            "fun servicesFor(types: Collection<String>, max: Int): SeedServices {",
        ).forEach { assertTrue(it in code, it) }
    }

    @Test fun `an interest needs a known insurance type or null`() {
        val fx = real() ?: return
        fx.replace("shared/catalog.json", "\"insuranceType\": \"motor\"", "\"insuranceType\": \"car\"")
        assertViolation(checks(fx), "catalog.interests.motor.insuranceType", "\"car\" is not an insuranceTypes key")
    }

    @Test fun `every insurance type needs a seed interest of that type`() {
        val fx = real() ?: return
        fx.replace("shared/site-seed.json", "      \"motor\": [\n        \"motor\"\n      ],", "      \"motor\": [\n        \"term\"\n      ],")
        assertViolation(checks(fx), "seedInterests.motor", "interest \"term\" belongs to insurance type \"life\"")
        val gone = real() ?: return
        gone.replace("shared/site-seed.json", "      \"motor\": [\n        \"motor\"\n      ],\n", "")
        assertViolation(checks(gone), "seedInterests", "insurance type \"motor\" needs at least one seed interest")
    }

    @Test fun `every byMix covers every mix and the last mix matches everything`() {
        val fx = real() ?: return
        fx.replace("shared/site-seed.json", "        \"key\": \"general\"", "        \"key\": \"broad\"")
        val v = checks(fx)
        assertViolation(v, "hero.subheadline", "byMix has no value for mix \"broad\"")
        assertViolation(v, "hero.subheadline", "byMix names unknown mix \"general\"")
        val only = real() ?: return
        only.replace("shared/site-seed.json", "        \"key\": \"general\"", "        \"key\": \"general\", \"onlyTypes\": [\"health\"]")
        assertViolation(checks(only), "mixes.general", "the last mix must leave out \"onlyTypes\"")
    }

    @Test fun `fromInsuranceTypes must fit its field`() {
        val fx = real() ?: return
        fx.replace("shared/site-seed.json", "\"fromInsuranceTypes\": \"serviceItems\"", "\"fromInsuranceTypes\": \"interestKeys\"")
        assertViolation(checks(fx), "services.items", "fromInsuranceTypes \"interestKeys\" needs a multiSelect")
    }
}
