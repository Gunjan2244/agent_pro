package com.sledje.agentpro.core.data

import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.error_conflict
import com.sledje.agentpro.resources.error_email_limited
import com.sledje.agentpro.resources.error_generic
import com.sledje.agentpro.resources.error_offline
import com.sledje.agentpro.resources.error_rate_limited
import com.sledje.agentpro.resources.error_read_only
import com.sledje.agentpro.resources.error_unauthorized
import com.sledje.agentpro.resources.error_validation
import org.jetbrains.compose.resources.StringResource

/**
 * Why a repository call failed (docs/ARCHITECTURE.md, Patterns). Each case maps to one translated
 * string resource ([message]); screens may show a more specific message for a case they expect.
 */
sealed interface AppError {
    /** No connection, a timeout, or the server couldn't be reached. */
    data object Offline : AppError

    /** No valid session (expired or signed out on the server). */
    data object Unauthorized : AppError

    /** The plan has lapsed: writes are refused by `can_write()` (docs/BILLING.md, read-only mode). */
    data object ReadOnly : AppError

    /**
     * A value was refused. [field] names it (e.g. `email`, `password`, `credentials`, `slug`, or
     * `section.field` for publish errors); [rule] says why when known (e.g. `not_confirmed`, `weak`, `required`).
     */
    data class Validation(val field: String, val rule: String? = null) : AppError

    /** Already exists or changed meanwhile (unique key, an existing account, a newer payment). */
    data object Conflict : AppError

    /** Too many requests; try again later. */
    data object RateLimited : AppError

    /**
     * Supabase Auth refused to send an email (`over_email_send_rate_limit`). [retryAfterSeconds] is set for
     * the per-address wait ("you can only request this after 42 seconds"); null means the project's
     * hourly email quota is used up (docs/BACKEND.md, Auth emails).
     */
    data class EmailLimited(val retryAfterSeconds: Int? = null) : AppError

    /** Anything else. [detail] is for logs only and never shown. */
    data class Unknown(val detail: String? = null) : AppError
}

/** The translated one-line message for this error. */
val AppError.message: StringResource
    get() = when (this) {
        AppError.Offline -> Res.string.error_offline
        AppError.Unauthorized -> Res.string.error_unauthorized
        AppError.ReadOnly -> Res.string.error_read_only
        is AppError.Validation -> Res.string.error_validation
        AppError.Conflict -> Res.string.error_conflict
        AppError.RateLimited -> Res.string.error_rate_limited
        is AppError.EmailLimited -> if (retryAfterSeconds != null) Res.string.error_rate_limited else Res.string.error_email_limited
        is AppError.Unknown -> Res.string.error_generic
    }
