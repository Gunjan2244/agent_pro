package com.sledje.agentpro.core.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the device can reach the internet (docs/ARCHITECTURE.md, Platform seams). Drives the
 * OfflineBanner, the "Back online" snackbar, the disabled Save/Publish buttons ("Needs internet") and
 * skips sync while offline. Android: ConnectivityManager default-network callback (androidMain);
 * iOS: NWPathMonitor (iOS phase).
 */
interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}
