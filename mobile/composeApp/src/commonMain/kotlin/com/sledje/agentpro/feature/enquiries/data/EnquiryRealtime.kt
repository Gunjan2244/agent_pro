package com.sledje.agentpro.feature.enquiries.data

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
 * Live enquiries while the app is in the foreground (docs/ARCHITECTURE.md, Realtime). The main shell
 * calls [start] on `ON_START` and [stop] on `ON_STOP`; nothing runs in the background (polling is the
 * Phase 12 worker).
 */
interface EnquiryLive {
    fun start()

    fun stop()
}

/**
 * A `postgres_changes` channel on `enquiries` filtered to `agent_id = auth.uid()` (RLS applies to
 * realtime too, docs/BACKEND.md). Each insert, update or delete event triggers an incremental
 * `SyncEngine.refresh("enquiries")`, which upserts the changed rows into Room (and removes tombstones)
 * with exactly the same row format and cursor as a normal sync. Failures are ignored: the next
 * foreground refresh catches up.
 */
class SupabaseEnquiryRealtime(
    private val supabase: SupabaseProvider,
    private val sync: SyncEngine,
    private val scope: CoroutineScope,
) : EnquiryLive {
    private var job: Job? = null
    private var channel: RealtimeChannel? = null

    override fun start() {
        if (job != null) return
        val client = supabase.client ?: return
        val uid = client.auth.currentUserOrNull()?.id ?: return
        val ch = client.channel("enquiries-$uid")
        val changes = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = ENQUIRIES_TABLE
            filter(AGENT_ID, FilterOperator.EQ, uid)
        }
        channel = ch
        job = scope.launch {
            try {
                changes.onEach { sync.refresh(ENQUIRIES_TABLE) }.launchIn(this)
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

    private companion object {
        const val AGENT_ID = "agent_id"
    }
}
