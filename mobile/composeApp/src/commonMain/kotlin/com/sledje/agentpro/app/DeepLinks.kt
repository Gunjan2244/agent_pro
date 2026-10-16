package com.sledje.agentpro.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's `agentpro://` links (docs/UX.md, Deep links; docs/BACKEND.md, Auth redirect). */
sealed interface DeepLink {
    data class Enquiry(val id: String) : DeepLink
    data class Customer(val id: String) : DeepLink
    data class Policy(val id: String) : DeepLink
    data class Dues(val bucket: String?) : DeepLink
    data object Website : DeepLink
    data object Domain : DeepLink

    /** `agentpro://auth-callback#…` or `?code=…`: email confirmation or password reset. */
    data class AuthCallback(val url: String) : DeepLink
}

/**
 * Parses incoming links and holds the latest one until the main shell consumes it. Auth callbacks are
 * handed to the auth repository by [App]. Phase 5 is the scaffold: the main shell opens the matching
 * tab; the detail screens (enquiry, customer, policy, domain) arrive with their features.
 */
class DeepLinks {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    /** Called by the platform entry point with the opened URL. Unknown links are ignored. */
    fun handle(url: String?) {
        parse(url)?.let { _pending.value = it }
    }

    fun consume(link: DeepLink) {
        if (_pending.value == link) _pending.value = null
    }

    companion object {
        const val SCHEME = "agentpro"

        fun parse(url: String?): DeepLink? {
            val raw = url?.trim() ?: return null
            val prefix = "$SCHEME://"
            if (!raw.startsWith(prefix, ignoreCase = true)) return null
            val rest = raw.substring(prefix.length)
            val host = rest.takeWhile { it != '/' && it != '?' && it != '#' }.lowercase()
            val path = rest.substring(host.length).substringBefore('?').substringBefore('#').trim('/')
            val query = rest.substringAfter('?', "").substringBefore('#')
            val id = path.substringBefore('/').takeIf { it.isNotBlank() }
            return when (host) {
                "enquiry" -> id?.let { DeepLink.Enquiry(it) }
                "customer" -> id?.let { DeepLink.Customer(it) }
                "policy" -> id?.let { DeepLink.Policy(it) }
                "dues" -> DeepLink.Dues(query.split('&').firstOrNull { it.startsWith("bucket=") }?.removePrefix("bucket=")?.takeIf { it.isNotBlank() })
                "website" -> DeepLink.Website
                "domain" -> DeepLink.Domain
                "auth-callback" -> DeepLink.AuthCallback(raw)
                else -> null
            }
        }
    }
}
