package com.sledje.agentpro.core.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the device is online, provided once at the app root from `NetworkMonitor` (docs/UX.md,
 * Global rule 7). Screens pass `!LocalIsOnline.current` to `AppScaffold(offline = …)` and disable
 * Save / Publish / Pay with "Needs internet" while it's false.
 */
val LocalIsOnline = compositionLocalOf { true }
