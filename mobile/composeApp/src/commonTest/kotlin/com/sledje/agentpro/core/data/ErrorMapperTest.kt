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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Supabase/HTTP failures → AppError (docs/BACKEND.md, "Errors from authenticated RPCs"). */
class ErrorMapperTest {
    private fun map(status: Int? = null, code: String? = null, message: String? = null, network: Boolean = false) =
        ErrorMapper.map(RemoteFailure(status, code, message, network))

    @Test
    fun networkFailuresAreOffline() {
        assertEquals(AppError.Offline, map(network = true))
        assertEquals(AppError.Offline, map(status = 500, network = true))
    }

    @Test
    fun ourRpcReasons() {
        assertEquals(AppError.ReadOnly, map(403, "42501", "plan_read_only"))
        assertEquals(AppError.Unauthorized, map(401, "42501", "not_authenticated"))
        assertEquals(AppError.Conflict, map(400, "P0001", "not_latest_payment"))
        assertEquals(AppError.Conflict, map(400, "P0001", "orders_in_progress"))
        assertEquals(AppError.Validation("invalid_amount"), map(400, "22023", "invalid_amount"))
    }

    @Test
    fun companyLinkReasons() {
        assertEquals(AppError.Validation("insurance_types"), map(400, "22023", "invalid_types"))
        assertEquals(AppError.Conflict, map(404, "P0002", "not_found"))
        assertEquals(AppError.Conflict, map(400, "P0001", "invalid_transition"))
        assertEquals(AppError.Validation("company", "duplicate"), map(409, "P0001", "duplicate_membership"))
        assertEquals(AppError.ReadOnly, map(403, "42501", "plan_read_only"))
    }

    @Test
    fun rowLevelSecurityRefusalMeansReadOnly() {
        assertEquals(AppError.ReadOnly, map(403, "42501", "new row violates row-level security policy for table \"sites\""))
    }

    @Test
    fun authCodes() {
        assertEquals(AppError.Validation("credentials"), map(400, "invalid_credentials", "Invalid login credentials"))
        assertEquals(AppError.Validation("email", "not_confirmed"), map(400, "email_not_confirmed"))
        assertEquals(AppError.Validation("code"), map(403, "otp_expired"))
        assertEquals(AppError.Validation("password", "weak"), map(422, "weak_password"))
        assertEquals(AppError.Validation("email"), map(400, "email_address_invalid"))
        assertEquals(AppError.Conflict, map(422, "user_already_exists"))
        assertEquals(AppError.RateLimited, map(429, "over_request_rate_limit"))
        assertEquals(AppError.Unauthorized, map(403, "session_not_found"))
    }

    /** The two email refusals Supabase sends with the same code (seen live on AGENT_PRO, 2026-10-16). */
    @Test
    fun emailSendLimits() {
        assertEquals(AppError.EmailLimited(null), map(429, "over_email_send_rate_limit", "email rate limit exceeded"))
        assertEquals(
            AppError.EmailLimited(42),
            map(429, "over_email_send_rate_limit", "For security purposes, you can only request this after 42 seconds."),
        )
        assertEquals(AppError.EmailLimited(1), map(429, "over_email_send_rate_limit", "you can only request this after 1 second."))
    }

    @Test
    fun sqlStatesAndPostgrestCodes() {
        assertEquals(AppError.Conflict, map(409, "23505", "duplicate key value violates unique constraint \"sites_slug_key\""))
        assertEquals(AppError.Validation("value"), map(400, "23514", "new row violates check constraint"))
        assertEquals(AppError.Unauthorized, map(401, "PGRST301", "JWT expired"))
    }

    @Test
    fun httpStatusFallbacks() {
        assertEquals(AppError.Unauthorized, map(401))
        assertEquals(AppError.Conflict, map(409))
        assertEquals(AppError.RateLimited, map(429))
        assertEquals(AppError.Validation("value"), map(422))
        assertTrue(map(500, message = "oops") is AppError.Unknown)
        assertTrue(map() is AppError.Unknown)
    }

    @Test
    fun unknownExceptionsAreUnknownAndCancellationIsNeverSwallowed() {
        assertTrue(IllegalStateException("x").toAppError() is AppError.Unknown)
        assertTrue(NotConfiguredException().toAppError() is AppError.Unknown)
    }

    @Test
    fun everyCaseHasAStringResource() {
        assertEquals(Res.string.error_offline, AppError.Offline.message)
        assertEquals(Res.string.error_unauthorized, AppError.Unauthorized.message)
        assertEquals(Res.string.error_read_only, AppError.ReadOnly.message)
        assertEquals(Res.string.error_validation, AppError.Validation("x").message)
        assertEquals(Res.string.error_conflict, AppError.Conflict.message)
        assertEquals(Res.string.error_rate_limited, AppError.RateLimited.message)
        assertEquals(Res.string.error_rate_limited, AppError.EmailLimited(42).message)
        assertEquals(Res.string.error_email_limited, AppError.EmailLimited(null).message)
        assertEquals(Res.string.error_generic, AppError.Unknown().message)
    }
}
