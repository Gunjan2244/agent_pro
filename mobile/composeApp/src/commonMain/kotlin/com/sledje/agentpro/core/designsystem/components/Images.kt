package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.sledje.agentpro.core.designsystem.AppShapes

/**
 * A remote image (Coil 3), e.g. an uploaded photo's public URL inside [ImagePickerSlot]. It fills its
 * slot, centre-cropped, on the muted surface while it loads. Decorative: the slot's label says what it is.
 */
@Composable
fun NetworkImage(url: String, modifier: Modifier = Modifier.fillMaxSize(), contentDescription: String? = null) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(AppShapes.md).background(MaterialTheme.colorScheme.surfaceContainerLow),
    )
}
