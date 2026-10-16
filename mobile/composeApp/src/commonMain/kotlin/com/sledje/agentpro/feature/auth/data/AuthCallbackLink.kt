package com.sledje.agentpro.feature.auth.data

/**
 * An `agentpro://auth-callback` link from a Supabase Auth email, parsed (pure, unit-tested).
 *
 * - Email confirmation: `#access_token=…&type=signup` (implicit flow, supabase-kt's default) or `?code=…` (PKCE).
 * - Password reset: the reset email redirects to [RECOVERY_REDIRECT], so the link keeps `?type=recovery`
 *   even when it carries an error, plus `#access_token=…&type=recovery` on success.
 * - Expired or invalid: `error`, `error_code` (e.g. `otp_expired`) and `error_description`, in the
 *   fragment or the query.
 */
data class AuthCallbackLink(
    /** PKCE authorization code, if any. */
    val code: String?,
    /** The fragment, when it holds an implicit-flow session (`access_token=`). */
    val sessionFragment: String?,
    /** A password-reset link (Set new password follows; docs/UX.md). */
    val recovery: Boolean,
    /** The Auth error code (`otp_expired`, `access_denied`, …) when the link is expired or invalid. */
    val error: String?,
) {
    val hasSession: Boolean get() = code != null || sessionFragment != null

    companion object {
        /** Where the reset email sends the agent back to. The Auth redirect allow-list must accept it (docs/TESTING.md). */
        const val RECOVERY_REDIRECT = "agentpro://auth-callback?type=recovery"

        fun parse(url: String): AuthCallbackLink {
            val query = url.substringAfter('?', "").substringBefore('#')
            val fragment = url.substringAfter('#', "")
            val q = params(query)
            val f = params(fragment)
            return AuthCallbackLink(
                code = q["code"]?.takeIf { it.isNotBlank() },
                sessionFragment = fragment.takeIf { f["access_token"]?.isNotBlank() == true },
                recovery = q["type"] == "recovery" || f["type"] == "recovery",
                error = listOf(f["error_code"], q["error_code"], f["error"], q["error"]).firstOrNull { !it.isNullOrBlank() },
            )
        }

        private fun params(part: String): Map<String, String> = part.split('&')
            .filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
    }
}
