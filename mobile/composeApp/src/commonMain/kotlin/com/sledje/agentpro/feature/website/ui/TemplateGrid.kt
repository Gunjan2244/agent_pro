package com.sledje.agentpro.feature.website.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.catalog.color
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Motion
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.rememberReduceMotion
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.allDrawableResources
import com.sledje.agentpro.resources.template_current
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Rows the grid's height holds: two whole rows and a tenth of the third, so the agent sees there is more. */
const val TEMPLATE_GRID_VISIBLE_ROWS = 2.1f

/** The wait after every template's picture has loaded before the first tile starts to scroll. */
const val TEMPLATE_GRID_START_DELAY_MS = 4_000L

/** Test tag of a template's tile in [TemplateGrid]. */
fun templateTileTag(key: String) = "template_tile_$key"

/**
 * The full-page pictures of each template that the grid's tiles scroll through: `template_page_<key>_<n>.webp` in
 * composeResources/drawable, n from 1, cut into strips so no bitmap exceeds a GPU texture. Generated from the web
 * templates by `scripts/gen-template-pages.py` (docs/WEB_RENDERER.md, Template page strips; DECISIONS #120).
 */
object TemplatePages {
    private const val PREFIX = "template_page_"

    /** [key]'s strips, top to bottom; empty when none are bundled. */
    fun strips(key: String): List<DrawableResource> = strips(key, Res.allDrawableResources)

    /** The values of [all] named `template_page_<key>_<n>`, by n. */
    internal fun <T> strips(key: String, all: Map<String, T>): List<T> =
        all.mapNotNull { (name, res) ->
            if (!name.startsWith("$PREFIX${key}_")) return@mapNotNull null
            name.removePrefix("$PREFIX${key}_").toIntOrNull()?.let { it to res }
        }.sortedBy { it.first }.map { it.second }
}

/** Loads a template's strips as bitmaps (screenshots pass one that returns none, so the tiles show their placeholder). */
typealias TemplatePageLoader = suspend (TemplateSpec) -> List<ImageBitmap>

/** Decodes the bundled strips off the main thread. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberTemplatePageLoader(): TemplatePageLoader {
    val environment = rememberResourceEnvironment()
    return remember(environment) {
        { template ->
            withContext(Dispatchers.Default) {
                TemplatePages.strips(template.key).map { getDrawableResourceBytes(environment, it).decodeToImageBitmap() }
            }
        }
    }
}

/**
 * One tile's scroll: the picture's offset from the top, in px, and how far it can go, from the tile's size and the
 * picture's height per unit of width (either may arrive first).
 */
private class TileScroll {
    val offset = Animatable(0f)
    var width by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(0f)
    var pageAspect by mutableFloatStateOf(0f)
    val max: Float get() = (width * pageAspect - height).coerceAtLeast(0f)
}

/**
 * The auto-scroll (docs/UX.md, Template grid): [startDelayMs] after it starts (every picture has loaded), the tiles
 * scroll one at a time, in order, each from the top to its end at [pxPerSecond]; at the end a tile jumps back to the
 * top and the next one starts, round and round until cancelled. A tile with nothing to scroll is skipped; when none
 * has, it waits [startDelayMs] before trying again (their sizes may still arrive).
 */
internal suspend fun runTemplateAutoScroll(
    count: Int,
    pxPerSecond: Float,
    maxOf: (Int) -> Float,
    scrollTo: suspend (index: Int, target: Float, durationMillis: Int) -> Unit,
    reset: suspend (index: Int) -> Unit,
    startDelayMs: Long = TEMPLATE_GRID_START_DELAY_MS,
) {
    delay(startDelayMs)
    while (true) {
        var moved = false
        for (i in 0 until count) {
            val max = maxOf(i)
            if (max <= 0f) continue
            scrollTo(i, max, (max / pxPerSecond * 1_000).roundToInt().coerceAtLeast(1))
            reset(i)
            moved = true
        }
        if (!moved) delay(startDelayMs)
    }
}

/**
 * The template grid shared by onboarding step 2 and Website → Appearance → Template (docs/UX.md, Template grid):
 * every template as a tile, two per row, sized so two rows show whole and a tenth of the third peeks out
 * ([TEMPLATE_GRID_VISIBLE_ROWS]). Each tile shows its template's full-page picture with the name below; once every
 * picture has loaded, they take turns scrolling ([runTemplateAutoScroll]); nothing moves under "remove animations".
 * A tap calls [onOpen] with the key. [currentKey] marks the site's current template (null: none, as in onboarding).
 */
@Composable
fun TemplateGrid(
    templates: List<TemplateSpec>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentKey: String? = null,
    loader: TemplatePageLoader = rememberTemplatePageLoader(),
) {
    val scrolls = remember(templates) { templates.associate { it.key to TileScroll() } }
    var pages by remember(templates) { mutableStateOf<Map<String, List<ImageBitmap>>>(emptyMap()) }
    val currentLoader by rememberUpdatedState(loader)
    LaunchedEffect(templates) {
        pages = templates.associate { template ->
            // A picture that can't be read leaves its tile on the placeholder; the others still scroll.
            template.key to try {
                currentLoader(template)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    val reduceMotion = rememberReduceMotion()
    val pxPerSecond = with(LocalDensity.current) { Motion.TEMPLATE_SCROLL_DP_PER_SECOND * density }
    val loaded = pages.size == templates.size
    LaunchedEffect(loaded, reduceMotion, scrolls) {
        // Under "remove animations" every animateTo ends at once, so the loop would spin: nothing scrolls instead.
        if (!loaded || reduceMotion) return@LaunchedEffect
        val ordered = templates.map { scrolls.getValue(it.key) }
        val run: suspend () -> Unit = {
            runTemplateAutoScroll(
                count = ordered.size,
                pxPerSecond = pxPerSecond,
                maxOf = { ordered[it].max },
                scrollTo = { i, target, millis -> ordered[i].offset.animateTo(target, tween(millis, easing = LinearEasing)) },
                reset = { ordered[it].offset.snapTo(0f) },
            )
        }
        // An endless animation, like rememberInfiniteTransition's: UI tests' policy stops it so they can go idle.
        val policy = coroutineContext[InfiniteAnimationPolicy]
        if (policy != null) policy.onInfiniteOperation(run) else run()
    }
    BoxWithConstraints(modifier) {
        val rowHeight = ((maxHeight - Spacing.m - Spacing.s * 2) / TEMPLATE_GRID_VISIBLE_ROWS).coerceAtLeast(Dimens.imageSlot)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            items(templates, key = { it.key }) { template ->
                TemplateTile(
                    template = template,
                    pages = pages[template.key],
                    scroll = scrolls.getValue(template.key),
                    current = template.key == currentKey,
                    onClick = { onOpen(template.key) },
                    modifier = Modifier.height(rowHeight),
                )
            }
        }
    }
}

/** A tile: the picture (or, until it loads, the template's accent) in a card, the name below; one tap target. */
@Composable
private fun TemplateTile(
    template: TemplateSpec,
    pages: List<ImageBitmap>?,
    scroll: TileScroll,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(pages) {
        val strips = pages ?: return@LaunchedEffect
        val width = strips.firstOrNull()?.width ?: return@LaunchedEffect
        scroll.pageAspect = strips.sumOf { it.height }.toFloat() / width
    }
    val colors = AgentProTheme.colors
    val border = if (current) {
        BorderStroke(Dimens.selectedBorder, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(Dimens.hairline, colors.cardBorder)
    }
    Column(
        modifier
            .testTag(templateTileTag(template.key))
            .clip(AppShapes.md)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .clip(AppShapes.md)
                .border(border, AppShapes.md)
                .background(colors.card)
                .clipToBounds()
                .onSizeChanged { size ->
                    scroll.width = size.width.toFloat()
                    scroll.height = size.height.toFloat()
                },
        ) {
            if (pages.isNullOrEmpty()) {
                Box(Modifier.fillMaxSize().background(template.defaultAccent.color().copy(alpha = PLACEHOLDER_ALPHA)))
            } else {
                Column(
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .graphicsLayer { translationY = -scroll.offset.value },
                ) {
                    pages.forEach { strip ->
                        Image(
                            bitmap = strip,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().aspectRatio(strip.width.toFloat() / strip.height),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                stringResource(template.name),
                style = AgentProTheme.text.strong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (current) {
                Text(stringResource(Res.string.template_current), style = AgentProTheme.text.label, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** The accent wash a tile shows while its picture loads. */
private const val PLACEHOLDER_ALPHA = 0.16f
