package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.ic_drag_handle
import com.sledje.agentpro.resources.ic_error
import com.sledje.agentpro.resources.website_content_fix_banner
import com.sledje.agentpro.resources.website_done
import com.sledje.agentpro.resources.website_drag_handle
import com.sledje.agentpro.resources.website_hub_content
import com.sledje.agentpro.resources.website_move_down
import com.sledje.agentpro.resources.website_move_up
import com.sledje.agentpro.resources.website_reorder
import com.sledje.agentpro.resources.website_section_always_on
import com.sledje.agentpro.resources.website_section_companies_note
import com.sledje.agentpro.resources.website_section_portal_note
import com.sledje.agentpro.resources.website_section_empty
import com.sledje.agentpro.resources.website_section_hidden
import com.sledje.agentpro.resources.website_section_items
import com.sledje.agentpro.resources.website_section_needs_fix
import com.sledje.agentpro.resources.website_section_show
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Test tag of a section row ("section_hero"), its switch ("section_switch_hero") and handle ("section_handle_hero"). */
fun sectionRowTag(key: String) = "section_$key"
fun sectionSwitchTag(key: String) = "section_switch_$key"
fun sectionHandleTag(key: String) = "section_handle_$key"

/**
 * Content (docs/UX.md): one row per schema section (icon, title, summary, visibility switch; Hero and
 * Contact pinned on). **Reorder** in the top bar turns on drag handles for the movable rows (TalkBack:
 * Move up / Move down actions); **Done** saves the order. Sections that `publish_site` refused are
 * marked with an error icon and "Needs a fix before publishing".
 */
@Composable
fun ContentScreen(
    state: ContentUiState,
    issueSections: Set<String>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit,
    onToggleVisible: (SectionRow, Boolean) -> Unit,
    onStartReorder: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDone: () -> Unit,
    /** The services don't match the agent's insurance types (DECISIONS #93): the hint with "Update services" shows. */
    servicesMismatch: Boolean = false,
    onUpdateServices: () -> Unit = {},
) {
    val online = isOnline()
    AppScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.website_hub_content),
                onBack = onBack,
                trailing = {
                    if (state.reordering) {
                        TertiaryButton(stringResource(Res.string.website_done), onDone)
                    } else {
                        TertiaryButton(stringResource(Res.string.website_reorder), onStartReorder, enabled = online && !state.saving && !state.loading)
                    }
                },
            )
        },
        offline = !online,
        snackbarHostState = snackbar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                ListSkeleton(rows = 8)
                return@Box
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (issueSections.isNotEmpty()) {
                    ErrorText(stringResource(Res.string.website_content_fix_banner), Modifier.padding(Spacing.m))
                }
                if (servicesMismatch && !state.reordering) {
                    ServicesMatchHint(onUpdateServices, enabled = online && !state.saving, modifier = Modifier.padding(Spacing.m))
                }
                val movable = state.rows.filterNot { it.pinned }
                state.rows.forEachIndexed { i, row ->
                    key(row.section.key) {
                        if (i > 0) HairlineDivider()
                        val movableIndex = movable.indexOf(row)
                        SectionListRow(
                            row = row,
                            hasIssue = row.section.key in issueSections,
                            reordering = state.reordering,
                            movableIndex = movableIndex,
                            movableCount = movable.size,
                            toggleEnabled = online && !state.saving,
                            onOpen = { onOpenSection(row.section.key) },
                            onToggle = { visible -> onToggleVisible(row, visible) },
                            onMove = onMove,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionListRow(
    row: SectionRow,
    hasIssue: Boolean,
    reordering: Boolean,
    movableIndex: Int,
    movableCount: Int,
    toggleEnabled: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val title = stringResource(row.section.label)
    val supporting = when {
        hasIssue -> stringResource(Res.string.website_section_needs_fix)
        // No fields to edit (`companies`, `portal`): say where the content comes from instead of opening an empty form.
        row.section.fields.isEmpty() && !row.hidden -> when (row.section.key) {
            "companies" -> stringResource(Res.string.website_section_companies_note)
            "portal" -> stringResource(Res.string.website_section_portal_note)
            else -> null
        }
        row.hidden -> stringResource(Res.string.website_section_hidden)
        else -> when (val s = row.summary) {
            is SiteContent.Summary.Text -> s.value
            is SiteContent.Summary.Items -> stringResource(Res.string.website_section_items, s.count)
            SiteContent.Summary.Empty -> if (row.pinned) stringResource(Res.string.website_section_always_on) else stringResource(Res.string.website_section_empty)
        }
    }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableStateOf(1) }
    val dragging = offset != 0f
    val canMove = reordering && movableIndex >= 0
    ListRow(
        title = title,
        supporting = supporting,
        leading = ListLeading.Icon(if (hasIssue) Res.drawable.ic_error else row.section.icon),
        onClick = if (reordering || row.section.fields.isEmpty()) null else onOpen,
        modifier = Modifier
            .testTag(sectionRowTag(row.section.key))
            .onSizeChanged { rowHeight = it.height.coerceAtLeast(1) }
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, offset.roundToInt()) }
            .alpha(if (reordering && !canMove) DISABLED_ALPHA else 1f),
        trailing = when {
            canMove -> {
                {
                    DragHandle(
                        label = title,
                        index = movableIndex,
                        count = movableCount,
                        rowHeight = rowHeight,
                        onDrag = { offset = it },
                        onMove = onMove,
                        tag = sectionHandleTag(row.section.key),
                    )
                }
            }
            reordering -> null
            else -> {
                {
                    val description = stringResource(Res.string.website_section_show, title)
                    Switch(
                        checked = !row.hidden,
                        onCheckedChange = onToggle,
                        enabled = toggleEnabled && !row.pinned,
                        modifier = Modifier.testTag(sectionSwitchTag(row.section.key)).semantics { contentDescription = description },
                    )
                }
            }
        },
    )
}

/**
 * The drag handle of a movable row. Dragging past half a row swaps it with its neighbour ([onMove]);
 * [onDrag] reports the remaining offset so the row follows the finger.
 */
@Composable
private fun DragHandle(
    label: String,
    index: Int,
    count: Int,
    rowHeight: Int,
    onDrag: (Float) -> Unit,
    onMove: (Int, Int) -> Unit,
    tag: String,
) {
    val currentIndex by rememberUpdatedState(index)
    val height by rememberUpdatedState(rowHeight)
    val move by rememberUpdatedState(onMove)
    val moveUp = stringResource(Res.string.website_move_up)
    val moveDown = stringResource(Res.string.website_move_down)
    val description = stringResource(Res.string.website_drag_handle, label)
    Icon(
        painterResource(Res.drawable.ic_drag_handle),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(Dimens.touchTarget)
            .padding(Spacing.s)
            .testTag(tag)
            .semantics {
                contentDescription = description
                customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(moveUp) { move(index, index - 1); true })
                    if (index < count - 1) add(CustomAccessibilityAction(moveDown) { move(index, index + 1); true })
                }
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragEnd = { total = 0f; onDrag(0f) },
                    onDragCancel = { total = 0f; onDrag(0f) },
                ) { change, amount ->
                    change.consume()
                    total += amount
                    val half = height / 2f
                    if (total > half && currentIndex < count - 1) {
                        move(currentIndex, currentIndex + 1)
                        total -= height
                    } else if (total < -half && currentIndex > 0) {
                        move(currentIndex, currentIndex - 1)
                        total += height
                    }
                    onDrag(total)
                }
            },
    )
}

private const val DISABLED_ALPHA = 0.6f
