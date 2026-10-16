package com.sledje.agentpro.core.platform

import androidx.compose.runtime.Composable

/** An image the agent picked. [read] loads its bytes (off the main thread); it may throw if the file is gone. */
class PickedImage(val read: suspend () -> ByteArray)

/**
 * Picks one image (docs/ARCHITECTURE.md, Platform seams). Android: the system photo picker
 * (`PickVisualMedia`, no storage permission); iOS: PHPicker (iOS phase).
 *
 * It is composable because the platform picker is bound to the screen's lifecycle (an Activity result
 * launcher on Android). Call it once per screen; the returned function opens the picker.
 */
interface MediaPicker {
    /** [onResult] gets the picked image, or null when the agent cancelled. */
    @Composable
    fun rememberImagePicker(onResult: (PickedImage?) -> Unit): () -> Unit
}
