package com.sledje.agentpro.feature.offers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.sledje.agentpro.core.catalog.Tone
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.ConfirmDialog
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.SectionHeader
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.designsystem.components.StatusPill
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.rememberMonthNames
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.offers.data.OfferRef
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_more_options
import com.sledje.agentpro.resources.action_remove
import com.sledje.agentpro.resources.action_retry
import com.sledje.agentpro.resources.ic_block
import com.sledje.agentpro.resources.ic_event_busy
import com.sledje.agentpro.resources.ic_more_vert
import com.sledje.agentpro.resources.ic_star
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.offer_picker_add
import com.sledje.agentpro.resources.offer_picker_add_own
import com.sledje.agentpro.resources.offer_picker_cap_hint
import com.sledje.agentpro.resources.offer_picker_copy_confirm
import com.sledje.agentpro.resources.offer_picker_copy_full
import com.sledje.agentpro.resources.offer_picker_copy_message
import com.sledje.agentpro.resources.offer_picker_copy_title
import com.sledje.agentpro.resources.offer_picker_edit
import com.sledje.agentpro.resources.offer_picker_empty
import com.sledje.agentpro.resources.offer_picker_ended
import com.sledje.agentpro.resources.offer_picker_ends
import com.sledje.agentpro.resources.offer_picker_featured_chip
import com.sledje.agentpro.resources.offer_picker_from_companies
import com.sledje.agentpro.resources.offer_picker_move_down
import com.sledje.agentpro.resources.offer_picker_move_up
import com.sledje.agentpro.resources.offer_picker_no_company
import com.sledje.agentpro.resources.offer_picker_no_live
import com.sledje.agentpro.resources.offer_picker_none
import com.sledje.agentpro.resources.offer_picker_on_site
import com.sledje.agentpro.resources.offer_picker_open_profile
import com.sledje.agentpro.resources.offer_picker_set_featured
import com.sledje.agentpro.resources.offer_picker_title_featured
import com.sledje.agentpro.resources.offer_picker_title_items
import com.sledje.agentpro.resources.offer_picker_unavailable
import com.sledje.agentpro.resources.offer_picker_unavailable_title
import com.sledje.agentpro.resources.offer_picker_unfeature
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The picker's callbacks (one set for the screen and its screenshots). */
class OfferPickerActions(
    val onBack: () -> Unit,
    val onAdd: (OfferRef) -> Unit,
    val onRemove: (OfferRef) -> Unit,
    val onMove: (index: Int, delta: Int) -> Unit,
    val onSetFeatured: (OfferRef?) -> Unit,
    val onEdit: (OfferRef) -> Unit,
    val onAddOwn: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onRetry: () -> Unit,
    val onConfirmCopy: () -> Unit,
    val onCancelCopy: () -> Unit,
) {
    companion object {
        val None = OfferPickerActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})

        fun of(vm: OfferPickerViewModel, onBack: () -> Unit, onAddOwn: () -> Unit, onOpenProfile: () -> Unit) = OfferPickerActions(
            onBack = onBack,
            onAdd = vm::add,
            onRemove = vm::remove,
            onMove = vm::move,
            onSetFeatured = vm::setFeatured,
            onEdit = vm::edit,
            onAddOwn = onAddOwn,
            onOpenProfile = onOpenProfile,
            onRetry = vm::load,
            onConfirmCopy = vm::confirmCopy,
            onCancelCopy = vm::cancelCopy,
        )
    }
}

/**
 * The offer picker (docs/UX.md, Offers on your site; DECISIONS #104). [PickerMode.Items]: the offers on the site (move, remove,
 * feature, edit) and the company offers not yet on it, each with **Add**; [PickerMode.Featured]: one choice. No Save button:
 * every change is already saved.
 */
@Composable
fun OfferPickerScreen(state: OfferPickerState, actions: OfferPickerActions, snackbar: SnackbarHostState? = null) {
    val online = LocalIsOnline.current
    AppScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(if (state.mode == PickerMode.Items) Res.string.offer_picker_title_items else Res.string.offer_picker_title_featured),
                onBack = actions.onBack,
            )
        },
        offline = !online,
        snackbarHostState = snackbar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading && state.onSite.isEmpty()) {
                ListSkeleton(rows = 4)
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.l)) {
                    when (state.mode) {
                        PickerMode.Items -> ItemsBody(state, actions, online)
                        PickerMode.Featured -> FeaturedBody(state, actions, online)
                    }
                    state.error?.let {
                        val text = if (it is AppError.Validation && it.field == "offers.custom") stringResource(Res.string.offer_picker_copy_full) else stringResource(it.message)
                        Text(text, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.m))
                    }
                }
            }
        }
    }
    state.confirmCopy?.let {
        ConfirmDialog(
            title = stringResource(Res.string.offer_picker_copy_title),
            message = stringResource(Res.string.offer_picker_copy_message),
            confirmLabel = stringResource(Res.string.offer_picker_copy_confirm),
            onConfirm = actions.onConfirmCopy,
            onDismiss = actions.onCancelCopy,
            destructive = false,
        )
    }
}

@Composable
private fun ItemsBody(state: OfferPickerState, actions: OfferPickerActions, online: Boolean) {
    SectionHeader(stringResource(Res.string.offer_picker_on_site))
    if (state.onSite.isEmpty()) {
        Text(stringResource(Res.string.offer_picker_empty), style = AgentProTheme.text.bodyMuted, modifier = Modifier.padding(Spacing.m))
    }
    state.onSite.forEachIndexed { i, row ->
        OfferRowView(row, trailing = {
            val feature = if (row.featured) stringResource(Res.string.offer_picker_unfeature) else stringResource(Res.string.offer_picker_set_featured)
            RowMenu(
                online,
                buildList {
                    if (row.editable) add(stringResource(Res.string.offer_picker_edit) to { actions.onEdit(row.ref) })
                    if (row.flag != OfferFlag.Unavailable) add(feature to { actions.onSetFeatured(if (row.featured) null else row.ref) })
                    if (i > 0) add(stringResource(Res.string.offer_picker_move_up) to { actions.onMove(i, -1) })
                    if (i < state.onSite.lastIndex) add(stringResource(Res.string.offer_picker_move_down) to { actions.onMove(i, 1) })
                    add(stringResource(Res.string.action_remove) to { actions.onRemove(row.ref) })
                },
            )
        })
        HairlineDivider()
    }
    CompanyOffers(state, actions) { row ->
        OfferRowView(row, trailing = {
            SecondaryButton(stringResource(Res.string.offer_picker_add), { actions.onAdd(row.ref) }, enabled = online && !state.full && !state.saving)
        })
        HairlineDivider()
    }
    if (state.full && state.available.isNotEmpty()) {
        Text(stringResource(Res.string.offer_picker_cap_hint, state.cap), style = AgentProTheme.text.bodyMuted, modifier = Modifier.padding(horizontal = Spacing.m))
    }
    SecondaryButton(
        stringResource(Res.string.offer_picker_add_own),
        actions.onAddOwn,
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(top = Spacing.m),
    )
}

@Composable
private fun FeaturedBody(state: OfferPickerState, actions: OfferPickerActions, online: Boolean) {
    val select = { ref: OfferRef? -> if (online && !state.saving) actions.onSetFeatured(ref) }
    OfferChoice(stringResource(Res.string.offer_picker_none), null, state.featured == null) { select(null) }
    HairlineDivider()
    if (state.onSite.isNotEmpty()) SectionHeader(stringResource(Res.string.offer_picker_on_site))
    state.onSite.forEach { row ->
        OfferRowView(row.copy(featured = false), selected = state.featured == row.ref, onClick = { select(row.ref) }, trailing = {
            RadioButton(selected = state.featured == row.ref, onClick = null)
        })
        HairlineDivider()
    }
    CompanyOffers(state, actions) { row ->
        OfferRowView(row, selected = state.featured == row.ref, onClick = { select(row.ref) }, trailing = {
            RadioButton(selected = state.featured == row.ref, onClick = null)
        })
        HairlineDivider()
    }
}

/** The "From your companies" section: loading, the error (needs internet or retry), the no-company note, or the rows. */
@Composable
private fun CompanyOffers(state: OfferPickerState, actions: OfferPickerActions, row: @Composable (OfferRow) -> Unit) {
    SectionHeader(stringResource(Res.string.offer_picker_from_companies))
    when {
        state.loadError != null -> Column(Modifier.padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                stringResource(if (state.loadError is AppError.Offline) Res.string.needs_internet else state.loadError.message),
                style = AgentProTheme.text.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TertiaryButton(stringResource(Res.string.action_retry), actions.onRetry)
        }
        state.hasApprovedCompany == null -> ListSkeleton(rows = 2)
        state.noCompany -> AppCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.m)) {
            Text(stringResource(Res.string.offer_picker_no_company), style = AgentProTheme.text.body)
            TertiaryButton(stringResource(Res.string.offer_picker_open_profile), actions.onOpenProfile)
        }
        state.available.isEmpty() -> Text(
            stringResource(Res.string.offer_picker_no_live), style = AgentProTheme.text.bodyMuted, modifier = Modifier.padding(horizontal = Spacing.m),
        )
        else -> state.available.forEach { row(it) }
    }
}

@Composable
private fun OfferChoice(title: String, supporting: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.rowOneLine).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AgentProTheme.text.strong)
            supporting?.let { Text(it, style = AgentProTheme.text.bodyMuted) }
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * One offer: the public title, a one-line summary, "Ends {date}" and the company name (supporting text, only inside the app),
 * then the Featured / Ended / No longer available pills.
 */
@Composable
private fun OfferRowView(
    row: OfferRow,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    Column(
        modifier.fillMaxWidth()
            .background(background)
            .then(if (onClick != null) Modifier.clickable(role = Role.RadioButton, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.title.ifBlank { stringResource(Res.string.offer_picker_unavailable_title) },
                    style = AgentProTheme.text.strong, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (row.summary.isNotBlank()) {
                    Text(row.summary, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                row.endsOn?.let {
                    Text(stringResource(Res.string.offer_picker_ends, Dates.formatDayMonth(it, rememberMonthNames())), style = AgentProTheme.text.bodyMuted)
                }
                row.companyName?.let { Text(it, style = AgentProTheme.text.bodyMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            trailing?.invoke()
        }
        if (row.featured || row.flag != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (row.featured) StatusPill(Tone.Info, Res.drawable.ic_star, stringResource(Res.string.offer_picker_featured_chip))
                when (row.flag) {
                    OfferFlag.Ended -> StatusPill(Tone.Danger, Res.drawable.ic_event_busy, stringResource(Res.string.offer_picker_ended))
                    OfferFlag.Unavailable -> StatusPill(Tone.Warning, Res.drawable.ic_block, stringResource(Res.string.offer_picker_unavailable))
                    null -> Unit
                }
            }
        }
    }
}

/** The "more" menu of an offer on the site; entries are (label, action). */
@Composable
private fun RowMenu(enabled: Boolean, items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), stringResource(Res.string.action_more_options), Modifier.size(Dimens.icon))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = AgentProTheme.colors.elevatedSurface) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, style = AgentProTheme.text.body) },
                    enabled = enabled,
                    onClick = { open = false; action() },
                )
            }
        }
    }
}
