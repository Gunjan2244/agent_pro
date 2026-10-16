package com.sledje.agentpro.core.data

import com.sledje.agentpro.app.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/**
 * The one Supabase client (auth, postgrest, realtime, storage), configured from the generated
 * [AppConfig] (mobile/local.properties). When the URL or key is empty, or the client can't be built,
 * [client] is null and [isConfigured] is false: the app then shows a clear "not configured" screen
 * instead of crashing.
 *
 * Auth redirects (email confirmation, password reset) come back as `agentpro://auth-callback`
 * (docs/BACKEND.md, Auth), handled by `DeepLinks`.
 */
class SupabaseProvider(
    url: String = AppConfig.SUPABASE_URL,
    anonKey: String = AppConfig.SUPABASE_ANON_KEY,
    /** Where the session is stored: [SecureSessionManager] in the app (encrypted at rest, DECISIONS #44). */
    sessionManager: SessionManager? = null,
) {
    val client: SupabaseClient? = if (url.isBlank() || anonKey.isBlank()) {
        null
    } else {
        try {
            createSupabaseClient(supabaseUrl = url.trim(), supabaseKey = anonKey.trim()) {
                install(Auth) {
                    scheme = AUTH_SCHEME
                    host = AUTH_HOST
                    sessionManager?.let { this.sessionManager = it }
                }
                install(Postgrest)
                install(Realtime)
                install(Storage)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    val isConfigured: Boolean get() = client != null

    /** The client, or throws [NotConfiguredException] (mapped to [AppError.Unknown]). */
    fun require(): SupabaseClient = client ?: throw NotConfiguredException()

    companion object {
        const val AUTH_SCHEME = "agentpro"
        const val AUTH_HOST = "auth-callback"
    }
}

class NotConfiguredException : IllegalStateException("Supabase is not configured (SUPABASE_URL / SUPABASE_ANON_KEY)")

/** Describes a supabase-kt / Ktor failure as a [RemoteFailure]. */
fun Throwable.toRemoteFailure(): RemoteFailure = when (this) {
    is PostgrestRestException -> RemoteFailure(status = statusCode, code = code, message = error)
    is AuthRestException -> RemoteFailure(status = statusCode, code = error, message = errorDescription)
    is RestException -> RemoteFailure(status = statusCode, code = error, message = description)
    is HttpRequestException, is HttpRequestTimeoutException, is IOException -> RemoteFailure(network = true)
    else -> RemoteFailure(message = this::class.simpleName)
}

/** Maps any failure from a Supabase call to an [AppError]. */
fun Throwable.toAppError(): AppError = ErrorMapper.map(toRemoteFailure())

/**
 * Runs a remote call and wraps the outcome. Cancellation is rethrown; everything else becomes an
 * [AppError] through [toAppError].
 */
suspend inline fun <T> remoteCall(crossinline block: suspend () -> T): AppResult<T> = try {
    Result.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.Err(e.toAppError())
}
