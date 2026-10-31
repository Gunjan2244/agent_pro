package com.sledje.agentpro.core.data

/**
 * A failed remote call, described without any Supabase or Ktor types so the mapping is unit-testable.
 *
 * @param status the HTTP status, when a response arrived.
 * @param code the Supabase Auth error code (e.g. `invalid_credentials`) or the Postgres SQLSTATE
 *   (e.g. `42501`, `23505`) or PostgREST code (e.g. `PGRST301`).
 * @param message the error message; for our RPCs this is the snake_case reason (e.g. `plan_read_only`).
 * @param network true when no response arrived (no connection, DNS failure, timeout).
 */
data class RemoteFailure(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
    val network: Boolean = false,
)

/**
 * Maps Supabase/HTTP failures to [AppError] (docs/BACKEND.md, "Errors from authenticated RPCs", and
 * the Supabase Auth error codes). Checked in this order: network, our RPC messages, Auth codes,
 * SQLSTATEs, then the HTTP status.
 */
object ErrorMapper {
    private val authCredentials = setOf("invalid_credentials", "invalid_grant")
    private val authUnauthorized = setOf(
        "session_not_found", "session_expired", "refresh_token_not_found", "refresh_token_already_used",
        "bad_jwt", "no_authorization", "user_not_found",
    )
    private val authConflict = setOf("user_already_exists", "email_exists", "identity_already_exists")
    private val authRateLimited = setOf("over_request_rate_limit", "over_sms_send_rate_limit")

    /** "For security purposes, you can only request this after 42 seconds." (the per-address email wait). */
    private val retryAfter = Regex("""after (\d+) seconds?""")
    private val validationReasons = setOf("invalid_amount", "invalid_paid_on", "no_due_date", "unknown_premium_mode")

    fun map(f: RemoteFailure): AppError {
        if (f.network) return AppError.Offline
        val code = f.code?.trim().orEmpty()
        val message = f.message?.trim().orEmpty()

        // Our RPCs raise a snake_case reason as the message (docs/BACKEND.md).
        when (message) {
            "plan_read_only" -> return AppError.ReadOnly
            "not_authenticated" -> return AppError.Unauthorized
            "not_latest_payment", "orders_in_progress", "media_not_deleted" -> return AppError.Conflict
            // Company links (docs/BACKEND.md, Companies and offers): the row is gone or has already moved on.
            "not_found", "invalid_transition" -> return AppError.Conflict
            "duplicate_membership" -> return AppError.Validation("company", "duplicate")
            "invalid_types" -> return AppError.Validation("insurance_types")
        }
        // The public text check (DECISIONS #77) on an agent's own words, e.g. a plan's key features (DECISIONS #130):
        // blocked_term:<point>:<term>. [field] is the point (1-based), so the screen can mark it.
        if (message.startsWith("blocked_term:")) return AppError.Validation(message.split(":").getOrNull(1).orEmpty(), "blocked_term")
        if (message in validationReasons) return AppError.Validation(message)

        // Supabase Auth error codes.
        when {
            code in authCredentials -> return AppError.Validation("credentials")
            code == "email_not_confirmed" -> return AppError.Validation("email", "not_confirmed")
            code == "weak_password" -> return AppError.Validation("password", "weak")
            code == "email_address_invalid" || code == "email_address_not_authorized" -> return AppError.Validation("email")
            // A wrong, used or expired email code: Auth answers all three the same way.
            code == "otp_expired" -> return AppError.Validation("code")
            code == "same_password" -> return AppError.Validation("password", "same")
            code in authUnauthorized -> return AppError.Unauthorized
            code in authConflict -> return AppError.Conflict
            code == "over_email_send_rate_limit" ->
                return AppError.EmailLimited(retryAfter.find(message)?.groupValues?.get(1)?.toIntOrNull())
            code in authRateLimited -> return AppError.RateLimited
        }

        // Postgres SQLSTATEs and PostgREST codes.
        when (code) {
            // Row-level security refused a write: for the owner that only happens when can_write() is false.
            "42501" -> return AppError.ReadOnly
            "23505", "P0001" -> return AppError.Conflict
            "23514", "23502", "22P02", "22023", "22001" -> return AppError.Validation("value")
            "PGRST301", "PGRST302" -> return AppError.Unauthorized
        }

        return when (f.status) {
            401 -> AppError.Unauthorized
            403 -> AppError.Unauthorized
            409 -> AppError.Conflict
            429 -> AppError.RateLimited
            400, 422 -> AppError.Validation("value")
            else -> AppError.Unknown(listOfNotNull(f.status?.toString(), code.ifEmpty { null }, message.ifEmpty { null }).joinToString(" "))
        }
    }
}
