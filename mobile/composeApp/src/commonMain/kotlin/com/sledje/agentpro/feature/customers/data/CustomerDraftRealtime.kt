package com.sledje.agentpro.feature.customers.data

import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.SyncEngine
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Live customer sign-up forms while the app is in the foreground (docs/ARCHITECTURE.md, Realtime). The main
 * shell calls [start] on `ON_START` and [stop] on `ON_STOP` while the `customerSignup` flag is on.
 */
interface CustomerDraftLive {
    fun start()

    fun stop()
}

/** Nothing live (tests, and the default of `AppDependencies`). */
object NoCustomerDraftLive : CustomerDraftLive {
    override fun start() = Unit
    override fun stop() = Unit
}

/**
 * A `postgres_changes` channel on `customer_drafts` filtered to `agent_id = auth.uid()`. Each event refreshes
 * `customer_drafts` (the customer's typing, presence and consent) and `customers` (a submit creates one).
 * Failures are ignored: the next foreground refresh catches up.
 */
class SupabaseCustomerDraftRealtime(
    private val supabase: SupabaseProvider,
    private val sync: SyncEngine,
    private val scope: CoroutineScope,
) : CustomerDraftLive {
    private var job: Job? = null
    private var channel: RealtimeChannel? = null

    override fun start() {
        if (job != null) return
        val client = supabase.client ?: return
        val uid = client.auth.currentUserOrNull()?.id ?: return
        val ch = client.channel("customer-drafts-$uid")
        val changes = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = CUSTOMER_DRAFTS_TABLE
            filter("agent_id", FilterOperator.EQ, uid)
        }
        channel = ch
        job = scope.launch {
            try {
                changes.onEach {
                    sync.refresh(CUSTOMER_DRAFTS_TABLE)
                    sync.refresh(CUSTOMERS_TABLE)
                }.launchIn(this)
                ch.subscribe()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Realtime is best effort; ON_START refreshes and pull-to-refresh still work.
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        val ch = channel ?: return
        channel = null
        scope.launch {
            runCatching {
                ch.unsubscribe()
                supabase.client?.realtime?.removeChannel(ch)
            }
        }
    }
}
