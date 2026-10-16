package com.sledje.agentpro.android.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sledje.agentpro.core.designsystem.gallery.ComponentGalleryScreen

/**
 * Debug builds only (src/debug): the component gallery (docs/DESIGN_SYSTEM.md, Components).
 * It has no launcher entry; open it with
 * `adb shell am start -n com.sledje.agentpro/com.sledje.agentpro.android.debug.GalleryActivity`.
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ComponentGalleryScreen(onBack = ::finish) }
    }
}
