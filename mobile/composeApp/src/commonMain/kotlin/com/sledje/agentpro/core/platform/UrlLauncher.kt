package com.sledje.agentpro.core.platform

/**
 * Opens other apps (docs/ARCHITECTURE.md, Platform seams: call, SMS, email, open link, share).
 * Phase 5 needs only [openEmailApp] ("Check your email" → Open email app); later phases add the
 * other actions here. Android: Intents (androidMain); iOS: UIApplication.open (iOS phase).
 */
interface UrlLauncher {
    /** Opens the device's email app. Returns false when there is none. */
    fun openEmailApp(): Boolean

    /** Opens the system share sheet with [text] (Website: share the site link). Returns false when nothing can share. */
    fun share(text: String): Boolean

    /** Opens an http(s) link in the browser (Website: Open). Returns false when nothing can open it. */
    fun openUrl(url: String): Boolean

    /** Opens the dialer with [phone] filled in (Enquiries, Customers: Call). Never places the call itself. */
    fun call(phone: String): Boolean = false

    /** Opens the SMS app addressed to [phone] (Enquiries, Customers: SMS). */
    fun sms(phone: String): Boolean = false

    /** Opens a new email to [address] (Enquiry detail: Email). */
    fun email(address: String): Boolean = false
}
