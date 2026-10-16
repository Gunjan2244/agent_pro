package com.sledje.agentpro.feature.enquiries.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.DateField
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.NavRow
import com.sledje.agentpro.core.designsystem.components.OverflowItem
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.SectionHeader
import com.sledje.agentpro.core.designsystem.components.StatusPill
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.designsystem.components.showUndo
import com.sledje.agentpro.core.format.DateTimes
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.Phone
import com.sledje.agentpro.core.format.formatDate
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.customers.ui.ContactAction
import com.sledje.agentpro.feature.customers.ui.ContactActionRow
import com.sledje.agentpro.feature.customers.ui.DetailHeader
import com.sledje.agentpro.feature.customers.ui.LabelValue
import com.sledje.agentpro.feature.customers.ui.offlineHelper
import com.sledje.agentpro.feature.customers.ui.showError
import com.sledje.agentpro.feature.enquiries.data.Enquiry
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_undo
import com.sledje.agentpro.resources.crm_call
import com.sledje.agentpro.resources.crm_delete
import com.sledje.agentpro.resources.crm_email
import com.sledje.agentpro.resources.crm_no_app
import com.sledje.agentpro.resources.crm_notes
import com.sledje.agentpro.resources.crm_sms
import com.sledje.agentpro.resources.enquiry_convert
import com.sledje.agentpro.resources.enquiry_copied
import com.sledje.agentpro.resources.enquiry_copy
import com.sledje.agentpro.resources.enquiry_deleted
import com.sledje.agentpro.resources.enquiry_details
import com.sledje.agentpro.resources.enquiry_follow_up
import com.sledje.agentpro.resources.enquiry_follow_up_at
import com.sledje.agentpro.resources.enquiry_follow_up_clear
import com.sledje.agentpro.resources.enquiry_follow_up_date
import com.sledje.agentpro.resources.enquiry_follow_up_save
import com.sledje.agentpro.resources.enquiry_follow_up_time
import com.sledje.agentpro.resources.enquiry_follow_up_title
import com.sledje.agentpro.resources.enquiry_interest
import com.sledje.agentpro.resources.enquiry_message
import com.sledje.agentpro.resources.enquiry_meta
import com.sledje.agentpro.resources.enquiry_not_found
import com.sledje.agentpro.resources.enquiry_notes_empty
import com.sledje.agentpro.resources.enquiry_phone
import com.sledje.agentpro.resources.enquiry_status
import com.sledje.agentpro.resources.enquiry_status_changed
import com.sledje.agentpro.resources.enquiry_status_title
import com.sledje.agentpro.resources.enquiry_view_customer
import com.sledje.agentpro.resources.ic_call
import com.sledje.agentpro.resources.ic_check
import com.sledje.agentpro.resources.ic_mail
import com.sledje.agentpro.resources.ic_sms
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** The default follow-up time of day when none is set yet (10:00, Asia/Kolkata). */
private val DEFAULT_FOLLOW_UP_TIME = LocalTime(10, 0)

/** Enquiry detail (docs/UX.md), wired to its ViewModel. */
@Composable
fun EnquiryDetailScreen(
    vm: EnquiryDetailViewModel,
    onBack: () -> Unit,
    onConvert: (String) -> Unit,
    onViewCustomer: (String) -> Unit,
    onOpenNotes: (String) -> Unit,
    onCall: (String) -> Boolean,
    onSms: (String) -> Boolean,
    onEmail: (String) -> Boolean,
) {
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val undoLabel = stringResource(Res.string.action_undo)

    // One long-lived collector: consuming the event must not cancel the Undo snackbar it shows.
    LaunchedEffect(vm) {
        vm.event.filterNotNull().collect { e ->
            vm.eventShown()
            when (e) {
                is EnquiryDetailEvent.StatusChanged -> {
                    val message = getString(Res.string.enquiry_status_changed, getString(e.status.label))
                    if (snackbar.showUndo(message, undoLabel)) vm.undoStatus(e)
                }
                EnquiryDetailEvent.Deleted ->
                    if (snackbar.showUndo(getString(Res.string.enquiry_deleted), undoLabel)) vm.undoDelete() else { vm.undoExpired(); onBack() }
                is EnquiryDetailEvent.Failed -> snackbar.showError(e.error)
            }
        }
    }
    val noApp = stringResource(Res.string.crm_no_app)
    var noAppShown by remember { mutableStateOf(0) }
    LaunchedEffect(noAppShown) { if (noAppShown > 0) snackbar.showSnackbar(noApp) }
    val copiedText = stringResource(Res.string.enquiry_copied)
    var copied by remember { mutableStateOf(0) }
    LaunchedEffect(copied) { if (copied > 0) snackbar.showSnackbar(copiedText) }

    val interestLabel = (state as? EnquiryDetailState.Shown)?.enquiry?.interest?.let { stringResource(it.label) }
    EnquiryDetailContent(
        state = state,
        busy = busy,
        snackbar = snackbar,
        onBack = onBack,
        onSetStatus = vm::setStatus,
        onSetFollowUp = vm::setFollowUp,
        onOpenNotes = { onOpenNotes(vm.id) },
        onConvert = { onConvert(vm.id) },
        onViewCustomer = onViewCustomer,
        onCopy = { e ->
            clipboard.setText(AnnotatedString(EnquiryDetailViewModel.copyText(e, interestLabel)))
            copied++
        },
        onDelete = vm::delete,
        onCall = { if (!onCall(it)) noAppShown++ },
        onSms = { if (!onSms(it)) noAppShown++ },
        onEmail = { if (!onEmail(it)) noAppShown++ },
    )
}

/** Stateless detail screen (also rendered by the screenshot tests). */
@Composable
fun EnquiryDetailContent(
    state: EnquiryDetailState,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSetStatus: (EnquiryStatus, Instant?) -> Unit,
    onSetFollowUp: (Instant?) -> Unit,
    onOpenNotes: () -> Unit,
    onConvert: () -> Unit,
    onViewCustomer: (String) -> Unit,
    onCopy: (Enquiry) -> Unit,
    onDelete: () -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onEmail: (String) -> Unit,
) {
    val online = LocalIsOnline.current
    val shown = state as? EnquiryDetailState.Shown
    var statusSheet by remember { mutableStateOf(false) }
    var followUpSheet by remember { mutableStateOf<FollowUpRequest?>(null) }
    AppScaffold(
        topBar = {
            AppTopBar(
                title = "",
                onBack = onBack,
                overflow = if (shown != null) {
                    listOf(
                        OverflowItem(stringResource(Res.string.enquiry_copy)) { onCopy(shown.enquiry) },
                        OverflowItem(stringResource(Res.string.crm_delete)) { if (online) onDelete() },
                    )
                } else {
                    emptyList()
                },
            )
        },
        offline = !online,
        snackbarHostState = snackbar,
        bottomBar = {
            if (shown != null) {
                val customer = shown.customer
                if (customer != null) {
                    BottomActionBar(stringResource(Res.string.enquiry_view_customer), { onViewCustomer(customer.id) })
                } else {
                    BottomActionBar(
                        stringResource(Res.string.enquiry_convert),
                        onConvert,
                        enabled = online && !busy,
                        helperText = offlineHelper(),
                    )
                }
            }
        },
    ) { padding ->
        when (state) {
            EnquiryDetailState.Loading -> ListSkeleton(Modifier.padding(padding))
            is EnquiryDetailState.Gone -> EmptyState(FeatureId.Enquiries.icon, stringResource(Res.string.enquiry_not_found), Modifier.padding(padding))
            is EnquiryDetailState.Shown -> EnquiryDetailBody(
                state.enquiry,
                Modifier.padding(padding),
                writable = online && !busy,
                onStatus = { statusSheet = true },
                onFollowUp = { followUpSheet = FollowUpRequest(asStatus = false, current = state.enquiry.followUpAt) },
                onOpenNotes = onOpenNotes,
                onCall = onCall,
                onSms = onSms,
                onEmail = onEmail,
            )
        }
    }
    if (statusSheet && shown != null) {
        StatusSheet(
            current = shown.enquiry.status,
            onDismiss = { statusSheet = false },
            onPick = { status ->
                statusSheet = false
                if (status == EnquiryStatus.FollowUp) {
                    followUpSheet = FollowUpRequest(asStatus = true, current = shown.enquiry.followUpAt)
                } else {
                    onSetStatus(status, null)
                }
            },
        )
    }
    followUpSheet?.let { request ->
        FollowUpSheet(
            current = request.current,
            canClear = !request.asStatus && request.current != null,
            onDismiss = { followUpSheet = null },
            onSave = { at ->
                followUpSheet = null
                if (request.asStatus) onSetStatus(EnquiryStatus.FollowUp, at) else onSetFollowUp(at)
            },
            onClear = { followUpSheet = null; onSetFollowUp(null) },
        )
    }
}

/** Whether the follow-up sheet was opened by choosing the Follow-up status or from the Follow-up row. */
private data class FollowUpRequest(val asStatus: Boolean, val current: Instant?)

@Composable
private fun EnquiryDetailBody(
    e: Enquiry,
    modifier: Modifier,
    writable: Boolean,
    onStatus: () -> Unit,
    onFollowUp: () -> Unit,
    onOpenNotes: () -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onEmail: (String) -> Unit,
) {
    val created = formatDate(Dates.dateOf(e.createdAt))
    val meta = e.source?.let { stringResource(Res.string.enquiry_meta, created, it.label()) } ?: created
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DetailHeader(e.name, meta, trailing = { StatusPill(e.status) })
        Spacer(Modifier.height(Spacing.m))
        ContactActionRow(
            listOf(
                ContactAction(stringResource(Res.string.crm_call), Res.drawable.ic_call, enabled = e.phone != null) { e.phone?.let(onCall) },
                ContactAction(stringResource(Res.string.crm_sms), Res.drawable.ic_sms, enabled = e.phone != null) { e.phone?.let(onSms) },
                ContactAction(stringResource(Res.string.crm_email), Res.drawable.ic_mail, enabled = e.email != null) { e.email?.let(onEmail) },
            ),
        )
        Spacer(Modifier.height(Spacing.l))
        NavRow(
            title = stringResource(Res.string.enquiry_status),
            supporting = e.status.label(),
            onClick = { if (writable) onStatus() },
            icon = e.status.icon,
        )
        e.followUpAt?.let { at ->
            NavRow(
                title = stringResource(Res.string.enquiry_follow_up),
                supporting = followUpText(at),
                onClick = { if (writable) onFollowUp() },
                icon = EnquiryStatus.FollowUp.icon,
            )
        }
        NavRow(
            title = stringResource(Res.string.crm_notes),
            supporting = e.notes?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.enquiry_notes_empty),
            onClick = onOpenNotes,
        )
        SectionHeader(stringResource(Res.string.enquiry_details))
        LabelValue(stringResource(Res.string.enquiry_phone), e.phone?.let { Phone.display(it) })
        LabelValue(stringResource(Res.string.crm_email), e.email)
        LabelValue(stringResource(Res.string.enquiry_interest), e.interest?.label())
        LabelValue(stringResource(Res.string.enquiry_message), e.message)
        Spacer(Modifier.height(Spacing.l))
    }
}

/** "16 Oct 2026, 10:00" (absolute in detail screens). */
@Composable
fun followUpText(at: Instant): String {
    val local = DateTimes.localOf(at)
    return stringResource(Res.string.enquiry_follow_up_at, formatDate(local.date), DateTimes.formatTime(local.time))
}

/** The status options (catalog `enquiryStatuses`), the current one marked. */
@Composable
private fun StatusSheet(current: EnquiryStatus, onDismiss: () -> Unit, onPick: (EnquiryStatus) -> Unit) {
    AppBottomSheet(title = stringResource(Res.string.enquiry_status_title), onDismiss = onDismiss) {
        EnquiryStatus.active.forEach { status ->
            ListRow(
                title = status.label(),
                leading = ListLeading.Icon(status.icon),
                selected = status == current,
                onClick = { onPick(status) },
                trailing = if (status == current) {
                    { Icon(painterResource(Res.drawable.ic_check), null) }
                } else {
                    null
                },
            )
        }
    }
}

/** The follow-up date and time (docs/UX.md: "Choosing Follow-up asks for a date and time"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpSheet(
    current: Instant?,
    canClear: Boolean,
    onDismiss: () -> Unit,
    onSave: (Instant) -> Unit,
    onClear: () -> Unit,
    today: LocalDate = Dates.today(),
) {
    val start = current?.let { DateTimes.localOf(it) }
    var date by remember { mutableStateOf(start?.date ?: today) }
    val time = rememberTimePickerState(
        initialHour = start?.hour ?: DEFAULT_FOLLOW_UP_TIME.hour,
        initialMinute = start?.minute ?: DEFAULT_FOLLOW_UP_TIME.minute,
        is24Hour = true,
    )
    AppBottomSheet(title = stringResource(Res.string.enquiry_follow_up_title), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            DateField(value = date, onValueChange = { picked -> picked?.let { date = it } }, label = stringResource(Res.string.enquiry_follow_up_date))
            androidx.compose.material3.Text(stringResource(Res.string.enquiry_follow_up_time), style = AgentProTheme.text.label)
            TimeInput(state = time, colors = TimePickerDefaults.colors())
            PrimaryButton(
                stringResource(Res.string.enquiry_follow_up_save),
                { onSave(DateTimes.instantOf(date, LocalTime(time.hour, time.minute))) },
                Modifier.fillMaxWidth(),
                enabled = LocalIsOnline.current,
            )
            if (canClear) TertiaryButton(stringResource(Res.string.enquiry_follow_up_clear), onClear, Modifier.fillMaxWidth())
        }
    }
}
