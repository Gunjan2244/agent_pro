package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.Tokens
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.ImageCrop
import com.sledje.agentpro.core.platform.ImageGeometry
import kotlin.math.max
import kotlin.math.roundToInt

/** How much the part of the photo outside the frame is dimmed. */
private const val OUTSIDE_SCRIM_ALPHA = 0.6f

/** The frame and circle guide: white on the dark surround in both themes. */
private val FRAME = Tokens.ColorLight.onPrimary

/**
 * The photo crop surface (docs/DESIGN_SYSTEM.md, Photo crop): [image] under a fixed frame of [aspect], the part
 * outside the frame dimmed, so the bright part is exactly what is uploaded. Pinch to zoom (around the fingers) and
 * drag to move; the frame always stays covered ([ImageGeometry.clampCrop]). [roundGuide] draws the circle the
 * website cuts from a 1:1 photo. [description] is read by screen readers (the zoom slider is the accessible control).
 */
@Composable
fun PhotoCropArea(
    image: ImageBitmap,
    aspect: ImageAspect,
    crop: ImageCrop,
    onCropChange: (ImageCrop) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    roundGuide: Boolean = false,
) {
    val current by rememberUpdatedState(crop)
    val change by rememberUpdatedState(onCropChange)
    val colors = MaterialTheme.colorScheme
    val inset = with(LocalDensity.current) { Spacing.l.toPx() }
    Canvas(
        modifier
            .clip(AppShapes.md)
            .semantics { contentDescription = description }
            .pointerInput(image, aspect) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val g = CropLayout(size.width.toFloat(), size.height.toFloat(), inset, image, aspect)
                    change(g.transform(current, centroid, pan, zoom))
                }
            },
    ) {
        val g = CropLayout(size.width, size.height, inset, image, aspect)
        // A dark surround and a white frame in both themes, like any photo editor (the frame sits on the photo).
        drawRect(colors.scrim)
        val dst = g.imageRect(crop)
        drawImage(
            image,
            dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
            dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
        )
        val frame = g.frame
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRect(frame)
        }
        drawPath(outside, colors.scrim.copy(alpha = OUTSIDE_SCRIM_ALPHA))
        val line = Dimens.hairline.toPx()
        drawRect(FRAME, frame.topLeft, frame.size, style = Stroke(Dimens.selectedBorder.toPx()))
        if (roundGuide) {
            clipRect(frame.left, frame.top, frame.right, frame.bottom) {
                drawCircle(FRAME, radius = minOf(frame.width, frame.height) / 2f, center = frame.center, style = Stroke(line))
            }
        }
    }
}

/**
 * A small preview of how the website shows the crop as [shape] ([round]: the circle), cut like CSS
 * `object-fit: cover; object-position: 50% [focusY]%` ([ImageGeometry.shownPart]), with its [label] under it.
 */
@Composable
fun PhotoShapePreview(
    image: ImageBitmap,
    aspect: ImageAspect,
    crop: ImageCrop,
    shape: ImageAspect,
    label: String,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    focusY: Int = 50,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .height(Dimens.cropPreview)
                .aspectRatio(shape.width.toFloat() / shape.height)
                .clip(if (round) AppShapes.full else AppShapes.md),
        ) {
            val rect = ImageGeometry.crop(image.width, image.height, aspect, crop)
            val part = ImageGeometry.shownPart(aspect, shape, focusY)
            drawImage(
                image,
                srcOffset = IntOffset((rect.left + part[0] * rect.width).roundToInt(), (rect.top + part[1] * rect.height).roundToInt()),
                srcSize = IntSize(max(1, (part[2] * rect.width).roundToInt()), max(1, (part[3] * rect.height).roundToInt())),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
        }
        Text(label, Modifier.padding(top = Spacing.s), style = AgentProTheme.text.bodyMuted, textAlign = TextAlign.Center)
    }
}

/** The crop surface's geometry in pixels: the [frame] inside the area, and where the image is drawn for a crop. */
private class CropLayout(width: Float, height: Float, inset: Float, private val image: ImageBitmap, private val aspect: ImageAspect) {
    val frame: Rect
    private val baseScale: Float

    init {
        val availW = (width - 2 * inset).coerceAtLeast(1f)
        val availH = (height - 2 * inset).coerceAtLeast(1f)
        val ratio = aspect.width.toFloat() / aspect.height
        val fw = if (availW / availH > ratio) availH * ratio else availW
        val fh = fw / ratio
        frame = Rect(Offset((width - fw) / 2f, (height - fh) / 2f), Size(fw, fh))
        // At zoom 1 the image just covers the frame: the frame is then the largest crop of the aspect.
        baseScale = max(fw / image.width, fh / image.height)
    }

    fun imageRect(crop: ImageCrop): Rect {
        val scale = baseScale * crop.zoom
        val w = image.width * scale
        val h = image.height * scale
        return Rect(Offset(frame.center.x - crop.centerX * w, frame.center.y - crop.centerY * h), Size(w, h))
    }

    /** The crop after a gesture: the image point under [centroid] stays under it while zooming, then the pan. */
    fun transform(crop: ImageCrop, centroid: Offset, pan: Offset, zoom: Float): ImageCrop {
        val before = imageRect(crop)
        val px = (centroid.x - before.left) / before.width
        val py = (centroid.y - before.top) / before.height
        val newZoom = (crop.zoom * zoom).coerceIn(1f, ImageCrop.MAX_ZOOM)
        val w = image.width * baseScale * newZoom
        val h = image.height * baseScale * newZoom
        val cx = (frame.center.x - centroid.x - pan.x) / w + px
        val cy = (frame.center.y - centroid.y - pan.y) / h + py
        return ImageGeometry.clampCrop(image.width, image.height, aspect, ImageCrop(newZoom, cx, cy))
    }
}
