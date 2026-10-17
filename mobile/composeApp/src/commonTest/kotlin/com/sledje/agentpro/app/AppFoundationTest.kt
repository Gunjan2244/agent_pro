package com.sledje.agentpro.app

import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.data.InMemoryKeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.core.data.SyncCursorStore
import com.sledje.agentpro.core.data.SyncEngine
import com.sledje.agentpro.core.data.SyncRemote
import com.sledje.agentpro.core.data.SyncTable
import com.sledje.agentpro.core.features.FeatureFlagsRepository
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.website.data.PublishError
import com.sledje.agentpro.feature.website.data.PublishOutcome
import com.sledje.agentpro.feature.website.data.SiteLinks
import com.sledje.agentpro.feature.website.data.StarterContent
import com.sledje.agentpro.feature.website.data.parsePublishResult
import com.sledje.agentpro.testing.FakeNetworkMonitor
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The gate, deep links, starter content, publish parsing and the sign-out / user-change cleanup. */
class AppFoundationTest {
    private val signedIn = AuthState.SignedIn(TEST_USER, null)

    @Test
    fun gate() {
        val p = testProfile()
        val s = testSite()
        assertEquals(AppGate.NotConfigured, resolveGate(false, signedIn, p, s, SyncStatus.Done, false))
        assertEquals(AppGate.Loading, resolveGate(true, AuthState.Loading, null, null, SyncStatus.Idle, false))
        assertEquals(AppGate.SignedOut, resolveGate(true, AuthState.SignedOut, p, s, SyncStatus.Idle, false))
        assertEquals(AppGate.Ready, resolveGate(true, signedIn, p, s, SyncStatus.Idle, false), "a cached session opens Main at once, even offline")
        assertEquals(AppGate.Loading, resolveGate(true, signedIn, null, null, SyncStatus.Running, false), "empty cache: wait for the first sync")
        assertEquals(AppGate.Error, resolveGate(true, signedIn, null, null, SyncStatus.Failed, false))
        assertEquals(AppGate.Onboarding(false), resolveGate(true, signedIn, testProfile(fullName = null), null, SyncStatus.Done, false))
        assertEquals(AppGate.Loading, resolveGate(true, signedIn, p, null, SyncStatus.Running, false), "the site may not have synced yet")
        assertEquals(AppGate.Onboarding(true), resolveGate(true, signedIn, p, null, SyncStatus.Done, false))
        assertEquals(AppGate.Onboarding(true), resolveGate(true, signedIn, p, s, SyncStatus.Done, true), "latched while onboarding runs")
    }

    @Test
    fun deepLinks() {
        assertEquals(DeepLink.Enquiry("e1"), DeepLinks.parse("agentpro://enquiry/e1"))
        assertEquals(DeepLink.Customer("c1"), DeepLinks.parse("agentpro://customer/c1"))
        assertEquals(DeepLink.Policy("p1"), DeepLinks.parse("agentpro://policy/p1/"))
        assertEquals(DeepLink.Dues("overdue"), DeepLinks.parse("agentpro://dues?bucket=overdue"))
        assertEquals(DeepLink.Dues(null), DeepLinks.parse("agentpro://dues"))
        assertEquals(DeepLink.Website, DeepLinks.parse("agentpro://website"))
        assertEquals(DeepLink.Domain, DeepLinks.parse("AGENTPRO://domain"))
        val callback = "agentpro://auth-callback#access_token=a&refresh_token=b&type=signup"
        assertEquals(DeepLink.AuthCallback(callback), DeepLinks.parse(callback))
        assertNull(DeepLinks.parse("agentpro://enquiry"))
        assertNull(DeepLinks.parse("https://agentpro.site/a/x"))
        assertNull(DeepLinks.parse("agentpro://unknown/1"))
        assertNull(DeepLinks.parse(null))

        val links = DeepLinks()
        links.handle("agentpro://website")
        assertEquals(DeepLink.Website, links.pending.value)
        links.consume(DeepLink.Website)
        assertNull(links.pending.value)
    }

    @Test
    fun starterContentUsesRealSchemaKeysAndLimits() {
        val hero = SiteSchema.section(StarterContent.HERO)!!
        val headline = hero.fields.single { it.key == StarterContent.HEADLINE }
        assertTrue(headline.required)
        assertTrue(SiteSchema.section(StarterContent.CONTACT)!!.fields.any { it.key == StarterContent.PHONE && it.required })
        assertTrue(SiteSchema.section(StarterContent.ABOUT)!!.fields.any { it.key == StarterContent.DESIGNATION })

        val long = "R".repeat(200)
        val content = StarterContent.from(testProfile(fullName = long, designation = null))
        assertEquals(headline.maxLength, content["hero"]!!.jsonObject["headline"]!!.jsonPrimitive.content.length)
        assertNull(content["about"], "no designation → no about block")
        assertEquals("+919876543210", content["contact"]!!.jsonObject["phone"]!!.jsonPrimitive.content)
    }

    @Test
    fun publishResultParsing() {
        assertEquals(
            PublishOutcome.Published(Instant.parse("2026-10-15T10:00:00Z")),
            parsePublishResult(Json.parseToJsonElement("""{"ok":true,"published_at":"2026-10-15T10:00:00+00:00"}""").jsonObject),
        )
        assertEquals(
            PublishOutcome.Invalid(listOf(PublishError("hero", "headline", null, "required"), PublishError("faq", "question", 1, "maxLength"))),
            parsePublishResult(
                Json.parseToJsonElement(
                    """{"ok":false,"errors":[{"section":"hero","field":"headline","item":null,"rule":"required"},
                       {"section":"faq","field":"question","item":1,"rule":"maxLength"}]}""",
                ).jsonObject,
            ),
        )
    }

    @Test
    fun siteLinks() {
        assertEquals("https://agentpro.site/a/ramesh", SiteLinks.url("ramesh", "https://agentpro.site/"))
        assertEquals("agentpro.site/a/ramesh", SiteLinks.display("ramesh", "https://agentpro.site"))
        assertEquals("/a/ramesh", SiteLinks.display("ramesh", ""))
        // Add to home screen: the app scope's start page (/a/<slug>/) with the install sheet (DECISIONS #65)
        assertEquals("https://agentpro.site/a/ramesh/?install=1", SiteLinks.installUrl("ramesh", "https://agentpro.site/"))
    }

    private class CountingTable : SyncTable {
        override val name = "profiles"
        var cleared = 0
        override suspend fun upsert(rows: List<JsonObject>) = Unit
        override suspend fun delete(ids: List<String>) = Unit
        override suspend fun clear() { cleared++ }
    }

    @Test
    fun dataSyncClearsOnUserChangeSchemaChangeAndSignOut() = runTest {
        val store = InMemoryKeyValueStore(mapOf(StoreKeys.SYNC_CURSOR_PREFIX + "profiles" to "2026-10-15T10:00:00Z"))
        val table = CountingTable()
        val remote = object : SyncRemote {
            override suspend fun fetchChanges(table: String, since: Instant?, after: com.sledje.agentpro.core.data.SyncKey?, limit: Int) = emptyList<JsonObject>()
        }
        val engine = SyncEngine(remote, SyncCursorStore(store), FakeNetworkMonitor(), listOf(table))
        val flags = FeatureFlagsRepository(store, backgroundScope) { Result.Ok(JsonObject(emptyMap())) }
        val sync = AppDataSync(engine, flags, store, cacheSchemaVersion = 1)

        sync.onSignedIn("user-a")
        assertEquals(1, table.cleared, "first run with a new cache schema version resets the cache")
        assertEquals("1", store.get(StoreKeys.CACHE_SCHEMA_VERSION))
        assertEquals("user-a", store.get(StoreKeys.LAST_USER_ID))

        sync.onSignedIn("user-a")
        assertEquals(1, table.cleared, "same user: cache kept")

        sync.onSignedIn("user-b")
        assertEquals(2, table.cleared, "another user: cache cleared")

        assertEquals(Result.Ok(Unit), sync.refreshAll())
        assertEquals(SyncStatus.Done, sync.status.value)

        sync.clearAll()
        assertEquals(3, table.cleared)
        assertNull(store.get(StoreKeys.LAST_USER_ID))
        assertNull(SyncCursorStore(store).get("profiles"))
    }
}
