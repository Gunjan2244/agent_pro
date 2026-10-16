package com.sledje.agentpro.core.platform

import kotlinx.coroutines.flow.StateFlow

/** iOS phase: NWPathMonitor. Until then the seam isn't implemented (docs/ARCHITECTURE.md, Platform seams). */
class IosNetworkMonitor : NetworkMonitor {
    override val isOnline: StateFlow<Boolean> get() = throw NotImplementedError("iOS phase: NWPathMonitor")
}

/** iOS phase: UIApplication.open. */
class IosUrlLauncher : UrlLauncher {
    override fun openEmailApp(): Boolean = throw NotImplementedError("iOS phase: UIApplication.open(message://)")
    override fun share(text: String): Boolean = throw NotImplementedError("iOS phase: UIActivityViewController")
    override fun openUrl(url: String): Boolean = throw NotImplementedError("iOS phase: UIApplication.open")
}
