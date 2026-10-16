package com.sledje.agentpro.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** iOS phase: PHPickerViewController. Until then the seam isn't implemented (docs/ARCHITECTURE.md, Platform seams). */
class IosMediaPicker : MediaPicker {
    @Composable
    override fun rememberImagePicker(onResult: (PickedImage?) -> Unit): () -> Unit =
        throw NotImplementedError("iOS phase: PHPickerViewController")
}

/** iOS phase: UIImage crop + scale, WebP or a JPEG fallback. */
class IosImageProcessor : ImageProcessor {
    override suspend fun prepare(image: PickedImage, aspect: ImageAspect?, maxSide: Int): ProcessedImage? =
        throw NotImplementedError("iOS phase: UIImage")
}

/** iOS phase: WKWebView (UIKitView) on the bundled web/ + shared/ files. */
class IosSitePreview : SitePreview {
    @Composable
    override fun Render(siteJson: String, modifier: Modifier): Unit = throw NotImplementedError("iOS phase: WKWebView")
}
