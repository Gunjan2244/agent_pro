package com.sledje.agentpro.feature.offers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sledje.agentpro.core.catalog.MembershipRejectReason
import com.sledje.agentpro.core.catalog.MembershipStatus
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.ConfirmDialog
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.SearchField
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.designsystem.components.SkeletonBlock
import com.sledje.agentpro.core.designsystem.components.StatusPill
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.designsystem.currentAppLocale
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.rememberMonthNames
import com.sledje.agentpro.core.forms.FormRenderer
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.offers.data.Company
import com.sledje.agentpro.feature.offers.data.Membership
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_back
import com.sledje.agentpro.resources.action_cancel
import com.sledje.agentpro.resources.action_remove
import com.sledje.agentpro.resources.action_retry
import com.sledje.agentpro.resources.company_add
import com.sledje.agentpro.resources.company_agency_code_line
import com.sledje.agentpro.resources.company_already_added
import com.sledje.agentpro.resources.company_block_hint
import com.sledje.agentpro.resources.company_block_title
import com.sledje.agentpro.resources.company_cancel_message
import com.sledje.agentpro.resources.company_cancel_request
import com.sledje.agentpro.resources.company_cancel_title
import com.sledje.agentpro.resources.company_edit_types
import com.sledje.agentpro.resources.company_empty
import com.sledje.agentpro.resources.company_error_duplicate
import com.sledje.agentpro.resources.company_error_types
import com.sledje.agentpro.resources.company_form_new
import com.sledje.agentpro.resources.company_line_pending
import com.sledje.agentpro.resources.company_line_rejected
import com.sledje.agentpro.resources.company_not_listed
import com.sledje.agentpro.resources.company_not_listed_hint
import com.sledje.agentpro.resources.company_picker_empty
import com.sledje.agentpro.resources.company_picker_search
import com.sledje.agentpro.resources.company_picker_title
import com.sledje.agentpro.resources.company_remove_message
import com.sledje.agentpro.resources.company_remove_title
import com.sledje.agentpro.resources.company_request_again
import com.sledje.agentpro.resources.company_required_hint
import com.sledje.agentpro.resources.company_save
import com.sledje.agentpro.resources.company_try_again
import com.sledje.agentpro.resources.needs_internet
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** The block's and sheets' callbacks (one set for onboarding and Settings → Profile). */
class CompanyActions(
    val onAdd: () -> Unit,
    val onEditTypes: (Membership) -> Unit,
    val onRemove: (Membership) -> Unit,
    val onTryAgain: (Membership) -> Unit,
    val onRetry: () -> Unit,
    val onPick: (Company?) -> Unit,
    val onLoadCompanies: () -> Unit,
    val onSave: () -> Unit,
    val onCloseSheet: () -> Unit,
    val onBackToPicker: () -> Unit,
    val onConfirmRemove: () -> Unit,
    val onCancelRemove: () -> Unit,
) {
    companion object {
        val None = CompanyActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

        fun of(vm: CompaniesViewModel) = CompanyActions(
            onAdd = vm::openPicker, onEditTypes = vm::editTypes, onRemove = vm::askRemove, onTryAgain = vm::tryAgain,
            onRetry = vm::refresh, onPick = vm::pick, onLoadCompanies = vm::loadCompanies, onSave = vm::submit,
            onCloseSheet = vm::closeSheet, onBackToPicker = vm::backToPicker,
            onConfirmRemove = vm::confirmRemove, onCancelRemove = vm::cancelRemove,
        )
    }
}

/** The message for a company RPC failure: the specific ones first, then the generic error text. */
@Composable
private fun companyErrorText(error: AppError): String = when {
    error is AppError.Validation && error.field == "company" && error.rule == "duplicate" -> stringResource(Res.string.company_error_duplicate)
    error is AppError.Validation && error.field == "insurance_types" -> stringResource(Res.string.company_error_types)
    else -> stringResource(error.message)
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text,
        style = AgentProTheme.text.body,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * "Your companies": one card per company with its status, then **Add company** (docs/UX.md, Your companies). Used by
 * onboarding step 1 ([requiredHint] = true shows "Add at least one company to continue" while there is none) and by
 * Settings → Profile.
 */
@Composable
fun CompaniesBlock(
    state: CompaniesUiState,
    actions: CompanyActions,
    modifier: Modifier = Modifier,
    requiredHint: Boolean = false,
) {
    val online = LocalIsOnline.current
    val locale = currentAppLocale().key
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(Res.string.company_block_title), style = AgentProTheme.text.title, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.company_block_hint), style = AgentProTheme.text.bodyMuted)
        when {
            state.loading && state.memberships.isEmpty() -> SkeletonBlock(Modifier.fillMaxWidth())
            state.loadError != null && state.memberships.isEmpty() -> {
                ErrorText(stringResource(state.loadError.message))
                TertiaryButton(stringResource(Res.string.action_retry), actions.onRetry)
            }
            else -> {
                if (state.memberships.isEmpty()) Text(stringResource(Res.string.company_empty), style = AgentProTheme.text.bodyMuted)
                state.memberships.forEach { m ->
                    CompanyCard(m, locale, enabled = online && !state.saving, actions)
                }
            }
        }
        state.error?.let { ErrorText(companyErrorText(it)) }
        if (requiredHint && !state.satisfiesOnboarding && !state.loading) {
            Text(stringResource(Res.string.company_required_hint), style = AgentProTheme.text.bodyMuted)
        }
        SecondaryButton(
            stringResource(Res.string.company_add),
            actions.onAdd,
            Modifier.fillMaxWidth(),
            enabled = online && !state.saving,
        )
        if (!online) Text(stringResource(Res.string.needs_internet), style = AgentProTheme.text.bodyMuted)
    }
}

/** One company link: name and status pill, the detail line (request date or reject reason), the types, and the actions its status allows. */
@Composable
fun CompanyCard(m: Membership, locale: String, enabled: Boolean, actions: CompanyActions, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(m.displayName(locale), style = AgentProTheme.text.strong, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            m.statusEntry?.let { StatusPill(it) }
        }
        statusLine(m)?.let { Text(it, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        m.reviewNote?.takeIf { it.isNotBlank() && !m.isActive }?.let {
            Text(it, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        m.agencyCode?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(Res.string.company_agency_code_line, it), style = AgentProTheme.text.bodyMuted)
        }
        if (m.types.isNotEmpty()) {
            Text(m.types.map { it.label() }.joinToString(", "), style = AgentProTheme.text.body, modifier = Modifier.padding(top = Spacing.s))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            when (m.statusEntry) {
                MembershipStatus.Pending -> {
                    TertiaryButton(stringResource(Res.string.company_edit_types), { actions.onEditTypes(m) }, enabled = enabled)
                    TertiaryButton(stringResource(Res.string.company_cancel_request), { actions.onRemove(m) }, enabled = enabled)
                }
                MembershipStatus.Approved -> {
                    TertiaryButton(stringResource(Res.string.company_edit_types), { actions.onEditTypes(m) }, enabled = enabled)
                    TertiaryButton(stringResource(Res.string.action_remove), { actions.onRemove(m) }, enabled = enabled)
                }
                MembershipStatus.Rejected -> TertiaryButton(stringResource(Res.string.company_try_again), { actions.onTryAgain(m) }, enabled = enabled)
                MembershipStatus.Revoked -> TertiaryButton(stringResource(Res.string.company_request_again), { actions.onTryAgain(m) }, enabled = enabled)
                else -> Unit
            }
        }
    }
}

/**
 * The extra detail under the status pill (the pill already says Waiting for approval / Approved / Not approved /
 * Removed): "Requested 16 Oct" while pending, "Reason: Agent not found" when not approved; nothing otherwise.
 */
@Composable
private fun statusLine(m: Membership): String? = when (m.statusEntry) {
    MembershipStatus.Pending ->
        requestedDate(m.requestedAt)?.let { stringResource(Res.string.company_line_pending, Dates.formatDayMonth(it, rememberMonthNames())) }
    MembershipStatus.Rejected ->
        m.rejectReason?.let { MembershipRejectReason.fromKey(it) }?.label()?.takeIf { it.isNotBlank() }
            ?.let { stringResource(Res.string.company_line_rejected, it) }
    else -> null
}

/** The Kolkata calendar date of an ISO timestamp from the server; null when absent or unreadable. */
internal fun requestedDate(iso: String?): LocalDate? =
    iso?.let { runCatching { Dates.dateOf(Instant.parse(it)) }.getOrNull() }

// ---- Sheets --------------------------------------------------------------------------------------

/** The picker sheet, the add / edit sheet and the remove confirmation for [state]; nothing when none is open. */
@Composable
fun CompanySheets(state: CompaniesUiState, actions: CompanyActions) {
    val locale = currentAppLocale().key
    when (val sheet = state.sheet) {
        CompanySheet.Picker -> AppBottomSheet(stringResource(Res.string.company_picker_title), actions.onCloseSheet) {
            CompanyPickerContent(state.companies, state.companiesError, state.linkedCompanyIds, locale, actions.onPick, actions.onLoadCompanies)
        }
        is CompanySheet.Form -> AppBottomSheet(formTitle(sheet.target, locale), actions.onCloseSheet) {
            CompanyFormContent(
                sheet = sheet, saving = state.saving, onSave = actions.onSave,
                onBack = if (sheet.target is CompanyTarget.New) actions.onBackToPicker else actions.onCloseSheet,
            )
        }
        null -> Unit
    }
    state.confirmRemove?.let { m ->
        val name = m.displayName(locale)
        val pending = m.statusEntry == MembershipStatus.Pending
        ConfirmDialog(
            title = stringResource(if (pending) Res.string.company_cancel_title else Res.string.company_remove_title, name),
            message = stringResource(if (pending) Res.string.company_cancel_message else Res.string.company_remove_message),
            confirmLabel = stringResource(if (pending) Res.string.company_cancel_request else Res.string.action_remove),
            onConfirm = actions.onConfirmRemove,
            onDismiss = actions.onCancelRemove,
        )
    }
}

@Composable
private fun formTitle(target: CompanyTarget, locale: String): String = when (target) {
    is CompanyTarget.New -> target.company?.name(locale) ?: stringResource(Res.string.company_form_new)
    is CompanyTarget.Types -> target.membership.displayName(locale)
    is CompanyTarget.Again -> target.membership.displayName(locale).ifBlank { stringResource(Res.string.company_form_new) }
}

/**
 * The picker's content: a search field (more than [SEARCH_FROM] companies), one row per company (initials avatar, the name in
 * the app language; companies already added are marked and can't be picked) and **My company isn't listed**. While the
 * list is loading ([companies] null) a skeleton shows; a failure shows the error with Retry.
 */
@Composable
fun CompanyPickerContent(
    companies: List<Company>?,
    error: AppError?,
    linkedIds: Set<String>,
    locale: String,
    onPick: (Company?) -> Unit,
    onRetry: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        if ((companies?.size ?: 0) > SEARCH_FROM) {
            SearchField(
                query, { query = it }, {}, Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s),
                placeholder = stringResource(Res.string.company_picker_search),
            )
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when {
                companies != null -> {
                    val shown = companies.filter { c -> query.isBlank() || c.names.values.any { it.contains(query.trim(), ignoreCase = true) } }
                    if (shown.isEmpty()) {
                        Text(stringResource(Res.string.company_picker_empty), style = AgentProTheme.text.bodyMuted, modifier = Modifier.padding(Spacing.m))
                    }
                    shown.forEach { c ->
                        val added = c.id in linkedIds
                        ListRow(
                            title = c.name(locale),
                            supporting = if (added) stringResource(Res.string.company_already_added) else null,
                            leading = ListLeading.Avatar(c.name(locale)),
                            onClick = if (added) null else ({ onPick(c) }),
                        )
                        HairlineDivider()
                    }
                }
                error != null -> Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ErrorText(stringResource(error.message))
                    TertiaryButton(stringResource(Res.string.action_retry), onRetry)
                }
                else -> Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SkeletonBlock(Modifier.fillMaxWidth())
                }
            }
            // Always offered, even offline or while loading: the agent is never stuck on the list (OFFERS.md).
            ListRow(
                title = stringResource(Res.string.company_not_listed),
                supporting = stringResource(Res.string.company_not_listed_hint),
                onClick = { onPick(null) },
            )
        }
    }
}

/** The add / edit form: the fields from FormRenderer, the last error, **Save** and Back (a new company) or Cancel. */
@Composable
fun CompanyFormContent(sheet: CompanySheet.Form, saving: Boolean, onSave: () -> Unit, onBack: () -> Unit) {
    val backLabel = stringResource(if (sheet.target is CompanyTarget.New) Res.string.action_back else Res.string.action_cancel)
    val online = LocalIsOnline.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        FormRenderer(sheet.form, enabled = !saving, onDone = onSave)
        sheet.error?.let { ErrorText(companyErrorText(it)) }
        PrimaryButton(
            stringResource(Res.string.company_save), onSave, Modifier.fillMaxWidth(),
            enabled = online && !saving && sheet.form.isValid,
        )
        if (!online) Text(stringResource(Res.string.needs_internet), style = AgentProTheme.text.bodyMuted)
        TertiaryButton(backLabel, onBack, Modifier.fillMaxWidth())
    }
}

/** The list is searchable once it has more than this many companies (docs/OFFERS.md). */
const val SEARCH_FROM = 8
