package com.sledje.agentpro.core.features

import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.InMemoryKeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.feature.customers.CustomersFeature
import com.sledje.agentpro.feature.dues.DuesFeature
import com.sledje.agentpro.feature.home.HomeFeature
import com.sledje.agentpro.feature.settings.SettingsFeature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FeatureFlags: catalog defaults + effective_flags() (DECISIONS #35). */
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagsTest {
    @Test
    fun defaultsComeFromTheCatalog() {
        FeatureId.entries.forEach { assertEquals(it.default, FeatureFlags.Defaults.isEnabled(it), it.key) }
    }

    @Test
    fun aRemoteBooleanWinsOverTheDefault() {
        val flags = FeatureFlags.fromJson(buildJsonObject { put("customDomains", false); put("enquiries", true) })
        assertFalse(flags.isEnabled(FeatureId.CustomDomains))
        assertTrue(flags.isEnabled(FeatureId.Enquiries))
        assertEquals(FeatureId.Dues.default, flags.isEnabled(FeatureId.Dues), "missing keys keep the default")
    }

    @Test
    fun nonBooleanRemoteValuesAreIgnored() {
        val flags = FeatureFlags.fromJson(
            buildJsonObject {
                put("customDomains", JsonPrimitive("false"))
                put("payments", 0)
                put("dues", JsonNull)
                put("insights", buildJsonObject { put("on", false) })
            },
        )
        assertEquals(emptyMap(), flags.remote)
        listOf(FeatureId.CustomDomains, FeatureId.Payments, FeatureId.Dues, FeatureId.Insights)
            .forEach { assertEquals(it.default, flags.isEnabled(it), it.key) }
    }

    @Test
    fun repositoryCachesTheServerValuesAndKeepsThemWhenARefreshFails() = runTest {
        val store = InMemoryKeyValueStore()
        var remote: com.sledje.agentpro.core.data.AppResult<kotlinx.serialization.json.JsonObject> =
            Result.Ok(buildJsonObject { put("customDomains", false) })
        val repo = FeatureFlagsRepository(store, backgroundScope + UnconfinedTestDispatcher(testScheduler)) { remote }
        assertTrue(repo.flags.value.isEnabled(FeatureId.CustomDomains), "defaults before any refresh")

        repo.refresh()
        assertFalse(repo.flags.value.isEnabled(FeatureId.CustomDomains))
        assertTrue(store.snapshot.getValue(StoreKeys.FEATURE_FLAGS).contains("customDomains"))

        remote = Result.Err(AppError.Offline)
        assertEquals(Result.Err(AppError.Offline), repo.refresh())
        assertFalse(repo.flags.value.isEnabled(FeatureId.CustomDomains), "the cache survives a failed refresh")

        val reopened = FeatureFlagsRepository(store, backgroundScope + UnconfinedTestDispatcher(testScheduler)) { remote }
        assertFalse(reopened.flags.value.isEnabled(FeatureId.CustomDomains), "read back from DataStore on start")
    }

    @Test
    fun registryKeepsCoreFeaturesAndFiltersFlaggedOnes() {
        val all = FeatureRegistry.enabled(FeatureFlags.Defaults)
        assertTrue(HomeFeature in all && SettingsFeature in all && DuesFeature in all)
        assertEquals(listOf("home", "enquiries", "customers", "website"), all.mapNotNull { it.bottomNavItem }.sortedBy { it.order }.map { it.key })

        val noDues = FeatureRegistry.enabled(FeatureFlags(mapOf("dues" to false, "siteBuilder" to false)))
        assertFalse(DuesFeature in noDues)
        assertTrue(HomeFeature in noDues && CustomersFeature in noDues)
        assertEquals(listOf("home", "enquiries", "customers"), noDues.mapNotNull { it.bottomNavItem }.sortedBy { it.order }.map { it.key })
    }

    @Test
    fun duesIsASegmentOfCustomersNotATab() {
        assertEquals(null, DuesFeature.bottomNavItem)
        val segments = FeatureRegistry.all.flatMap { it.segments }.filter { it.tabKey == CustomersFeature.TAB_KEY }.sortedBy { it.order }
        assertEquals(listOf("customers", "dues"), segments.map { it.key })
    }
}
