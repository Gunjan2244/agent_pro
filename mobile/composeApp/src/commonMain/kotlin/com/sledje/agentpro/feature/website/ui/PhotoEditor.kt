package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.PhotoCropArea
import com.sledje.agentpro.core.designsystem.components.PhotoShapePreview
import com.sledje.agentpro.core.designsystem.components.SkeletonBlock
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.ImageShape
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.ImageCrop
import com.sledje.agentpro.core.platform.ImageGeometry
import com.sledje.agentpro.core.platform.MediaPicker
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.feature.website.data.ImageUploader
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.photo_edit_another
import com.sledje.agentpro.resources.photo_edit_area
import com.sledje.agentpro.resources.photo_edit_hint
import com.sledje.agentpro.resources.photo_edit_hint_round
import com.sledje.agentpro.resources.photo_edit_reset
import com.sledje.agentpro.resources.photo_edit_shown
import com.sledje.agentpro.resources.photo_edit_title
import com.sledje.agentpro.resources.photo_edit_use
import com.sledje.agentpro.resources.photo_edit_zoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Pick, then adjust, a photo (docs/UX.md, Photo editor): the returned function opens the photo picker for an image
 * field key; the picked photo opens [PhotoEditorScreen] on the field's `aspect`, and **Use this photo** hands
 * [onResult] the photo with the agent's crop. A field without an aspect, or a file that can't be decoded, skips the
 * editor (crop null), so the upload reports an unusable file as before. [fieldFor] resolves the key, which survives
 * the picker even if Android recreates the screen.
 */
@Composable
fun rememberPhotoEditor(
    picker: MediaPicker,
    uploader: ImageUploader,
    fieldFor: (String) -> FieldSpec?,
    onResult: (key: String, image: PickedImage, crop: ImageCrop?) -> Unit,
): (key: String) -> Unit {
    var pickingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EditingPhoto?>(null) }
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    val field by rememberUpdatedState(fieldFor)
    val pick = picker.rememberImagePicker { picked ->
        val key = pickingKey
        val aspect = key?.let { field(it) }?.let { ImageAspect.parse(it.aspect) }
        when {
            picked == null || key == null -> pickingKey = null
            aspect == null -> { pickingKey = null; result(key, picked, null) }
            else -> {
                val opening = EditingPhoto(key, picked, aspect, field(key)?.shownAs.orEmpty())
                editing = opening
                scope.launch {
                    // Read the file once: the editor and the upload then use the same bytes.
                    val bytes = try { picked.read() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                    val image = bytes?.let { b -> PickedImage { b } }
                    val preview = image?.let { uploader.preview(it) }
                    if (editing !== opening) return@launch
                    if (image == null || preview == null) {
                        editing = null
                        pickingKey = null
                        result(key, image ?: picked, null)
                    } else {
                        editing = opening.copy(image = image, preview = preview)
                    }
                }
            }
        }
    }
    editing?.let { photo ->
        PhotoEditorDialog(
            photo = photo,
            onUse = { crop ->
                editing = null
                pickingKey = null
                result(photo.key, photo.image, crop)
            },
            onAnother = { editing = null; pick() },
            onCancel = { editing = null; pickingKey = null },
        )
    }
    return remember(pick) { { key -> pickingKey = key; pick() } }
}

/** The crop surface's width ÷ height. */
private const val CROP_AREA_RATIO = 4f / 3f

/** The photo being adjusted; [preview] is null while it is decoded. */
private data class EditingPhoto(
    val key: String,
    val image: PickedImage,
    val aspect: ImageAspect,
    val shownAs: List<ImageShape>,
    val preview: ImageBitmap? = null,
)

@Composable
private fun PhotoEditorDialog(photo: EditingPhoto, onUse: (ImageCrop) -> Unit, onAnother: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            PhotoEditorScreen(photo.preview, photo.aspect, photo.shownAs, onUse, onAnother, onCancel)
        }
    }
}

/**
 * Adjust the photo before it is uploaded (docs/UX.md, Photo editor): the crop surface with the circle guide when the
 * website shows the photo round, a zoom slider and Reset, and "How customers see it", one preview per schema
 * `shownAs` shape that follows every move. [image] is null while the photo is decoded (a skeleton; Use waits).
 */
@Composable
fun PhotoEditorScreen(
    image: ImageBitmap?,
    aspect: ImageAspect,
    shownAs: List<ImageShape>,
    onUse: (ImageCrop) -> Unit,
    onAnother: () -> Unit,
    onCancel: () -> Unit,
) {
    var crop by remember(image) { mutableStateOf(ImageCrop.Centered) }
    val round = shownAs.any { it.round }
    AppScaffold(
        topBar = { AppTopBar(stringResource(Res.string.photo_edit_title), onBack = onCancel) },
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.photo_edit_use),
                onClick = { onUse(crop) },
                enabled = image != null,
                secondaryText = stringResource(Res.string.photo_edit_another),
                onSecondary = onAnother,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(Res.string.photo_edit_hint), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (round) Text(stringResource(Res.string.photo_edit_hint_round), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Wider than tall, so the previews below fit on a phone's first screen; the frame keeps the field's aspect.
            val area = Modifier.fillMaxWidth().aspectRatio(CROP_AREA_RATIO)
            if (image == null) {
                SkeletonBlock(area, shape = AppShapes.md)
                return@Column
            }
            PhotoCropArea(
                image, aspect, crop, onCropChange = { crop = it },
                description = stringResource(Res.string.photo_edit_area), modifier = area, roundGuide = round,
            )
            val zoomLabel = stringResource(Res.string.photo_edit_zoom)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(zoomLabel, style = AgentProTheme.text.label)
                Slider(
                    value = crop.zoom,
                    onValueChange = { crop = ImageGeometry.clampCrop(image.width, image.height, aspect, crop.copy(zoom = it)) },
                    valueRange = 1f..ImageCrop.MAX_ZOOM,
                    modifier = Modifier.weight(1f).semantics { contentDescription = zoomLabel },
                )
                TertiaryButton(stringResource(Res.string.photo_edit_reset), onClick = { crop = ImageCrop.Centered }, enabled = crop != ImageCrop.Centered)
            }
            if (shownAs.isNotEmpty()) {
                Text(stringResource(Res.string.photo_edit_shown), style = AgentProTheme.text.strong, modifier = Modifier.semantics { heading() })
                // Wraps to a new row when the labels are long (Hindi, large text) rather than squeezing them.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    shownAs.forEach { shape ->
                        val shapeAspect = ImageAspect.parse(shape.aspect) ?: return@forEach
                        PhotoShapePreview(image, aspect, crop, shapeAspect, stringResource(shape.label), round = shape.round, focusY = shape.focusY)
                    }
                }
            }
        }
    }
}
