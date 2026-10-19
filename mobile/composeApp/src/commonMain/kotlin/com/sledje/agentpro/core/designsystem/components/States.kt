package com.sledje.agentpro.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Motion
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.rememberReduceMotion
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.core.ui.screenState
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_retry
import com.sledje.agentpro.resources.error_generic
import com.sledje.agentpro.resources.ic_error
import com.sledje.agentpro.resources.loading
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Switches between the four states of a data screen (docs/UX.md, Global UX rule 1): loading
 * (skeleton), empty (one sentence, one button), error (message and Retry) and content (with
 * pull-to-refresh when [onRefresh] is set). Moving between them crossfades; new data in the same
 * state (a refresh) doesn't.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ScreenState(
    state: UiState<T>,
    empty: @Composable () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    skeleton: @Composable () -> Unit = { ListSkeleton() },
    content: @Composable (T) -> Unit,
) {
    StateCrossfade(state, modifier.fillMaxSize(), key = { it.screenState }) { shown ->
        when (shown) {
            UiState.Loading -> skeleton()
            UiState.Empty -> empty()
            is UiState.Error -> ErrorState(
                message = shown.message?.let { stringResource(it) } ?: stringResource(Res.string.error_generic),
                onRetry = onRetry,
            )
            is UiState.Content -> if (onRefresh != null) {
                PullToRefreshBox(isRefreshing = shown.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    content(shown.data)
                }
            } else {
                content(shown.data)
            }
        }
    }
}

/**
 * Crossfades between a screen's states (docs/DESIGN_SYSTEM.md, Motion), e.g. skeleton → content.
 * Only a change of [key] animates; a new value with the same key just recomposes [content], so pass
 * the state's kind, not its data.
 */
@Composable
fun <S> StateCrossfade(state: S, modifier: Modifier = Modifier, key: (S) -> Any?, content: @Composable (S) -> Unit) {
    AnimatedContent(state, modifier, transitionSpec = { Motion.crossfade() }, contentKey = key, label = "state") { shown ->
        content(shown)
    }
}

/** Empty states teach the next step: a line icon, one sentence and one button. */
@Composable
fun EmptyState(
    icon: DrawableResource,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CenteredMessage(icon, message, modifier) {
        if (actionLabel != null && onAction != null) PrimaryButton(actionLabel, onAction)
    }
}

/** A message and Retry. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CenteredMessage(Res.drawable.ic_error, message, modifier) {
        SecondaryButton(stringResource(Res.string.action_retry), onRetry)
    }
}

@Composable
private fun CenteredMessage(icon: DrawableResource, message: String, modifier: Modifier, button: @Composable () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.icon),
        )
        Text(message, style = AgentProTheme.text.body, textAlign = TextAlign.Center)
        button()
    }
}

/** A grey placeholder block with a subtle shimmer (static when animations are removed). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = AppShapes.sm) {
    val reduceMotion = rememberReduceMotion()
    val alpha = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition()
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(Motion.SHIMMER_MILLIS, easing = Motion.StandardEasing), RepeatMode.Reverse),
        )
        value
    }
    Box(modifier.alpha(alpha).background(MaterialTheme.colorScheme.surfaceContainer, shape))
}

/** A skeleton list row (72dp): avatar, title and supporting lines. */
@Composable
fun ListRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Dimens.rowTwoLine).padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(Dimens.avatar), AppShapes.full)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(Dimens.skeletonLine))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(Dimens.skeletonLine))
        }
    }
}

/** The default loading state of a list screen. */
@Composable
fun ListSkeleton(modifier: Modifier = Modifier, rows: Int = 6) {
    val label = stringResource(Res.string.loading)
    Column(modifier.fillMaxWidth().semantics { contentDescription = label }) {
        repeat(rows) { ListRowSkeleton() }
    }
}

/** A skeleton card (KPI tile or chart). */
@Composable
fun CardSkeleton(modifier: Modifier = Modifier) {
    SkeletonBlock(modifier.fillMaxWidth().height(Dimens.skeletonBlock), AppShapes.md)
}
