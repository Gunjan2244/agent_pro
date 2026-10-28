package com.sledje.agentpro.testing

import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.feature.home.WhatsNew
import kotlinx.coroutines.runBlocking

/**
 * Marks the current "What's new" as seen, for flows that aren't about it: an agent with a site and nothing seen gets the
 * full-screen walkthrough over Home first (DECISIONS #121; `WhatsNewWalkthroughUiTest` covers it).
 */
fun FakeAppDependencies.whatsNewSeen(): FakeAppDependencies = apply {
    runBlocking { store.put(StoreKeys.WHATS_NEW_SEEN, WhatsNew.VERSION.toString()) }
}
