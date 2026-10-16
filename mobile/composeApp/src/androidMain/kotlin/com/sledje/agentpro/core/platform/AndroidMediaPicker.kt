package com.sledje.agentpro.core.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [MediaPicker] on Android: the system photo picker (`PickVisualMedia`, images only). It needs no
 * storage permission; on devices without the picker, AndroidX falls back to the document picker.
 */
class AndroidMediaPicker : MediaPicker {
    @Composable
    override fun rememberImagePicker(onResult: (PickedImage?) -> Unit): () -> Unit {
        val resolver = LocalContext.current.applicationContext.contentResolver
        val callback by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            callback(
                uri?.let { picked ->
                    PickedImage {
                        withContext(Dispatchers.IO) {
                            resolver.openInputStream(picked)?.use { it.readBytes() } ?: throw IOException("unreadable image")
                        }
                    }
                },
            )
        }
        return remember(launcher) {
            { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
    }
}
