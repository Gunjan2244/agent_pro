package com.sledje.agentpro.feature.customers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.size
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.NavRow
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.ScreenState
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.designsystem.components.itemMotion
import com.sledje.agentpro.core.designsystem.components.showUndo
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.formatDate
import com.sledje.agentpro.core.format.relativeTime
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.customers.data.CustomerDraft
import com.sledje.agentpro.feature.customers.data.DraftFields
import com.sledje.agentpro.feature.customers.data.DraftSource
import com.sledje.agentpro.core.format.Phone
import com.sledje.agentpro.core.data.parseDateOrNull
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_retry
import com.sledje.agentpro.resources.action_undo
import com.sledje.agentpro.resources.auth_email
import com.sledje.agentpro.resources.cat_field_city
import com.sledje.agentpro.resources.cat_field_name
import com.sledje.agentpro.resources.customer_address
import com.sledje.agentpro.resources.customer_dob
import com.sledje.agentpro.resources.customer_nominee
import com.sledje.agentpro.resources.customers_add_manually
import com.sledje.agentpro.resources.customers_pick_contact
import com.sledje.agentpro.resources.ic_contacts
import com.sledje.agentpro.resources.ic_delete
import com.sledje.agentpro.resources.ic_description
import com.sledje.agentpro.resources.ic_edit
import com.sledje.agentpro.resources.ic_person_add
import com.sledje.agentpro.resources.ic_public
import com.sledje.agentpro.resources.incomplete_by_you
import com.sledje.agentpro.resources.incomplete_delete
import com.sledje.agentpro.resources.incomplete_deleted
import com.sledje.agentpro.resources.incomplete_forms_empty
import com.sledje.agentpro.resources.incomplete_forms_row
import com.sledje.agentpro.resources.incomplete_forms_title
import com.sledje.agentpro.resources.incomplete_link_expired
import com.sledje.agentpro.resources.incomplete_link_not_opened
import com.sledje.agentpro.resources.incomplete_link_on_form
import com.sledje.agentpro.resources.incomplete_link_opened
import com.sledje.agentpro.resources.incomplete_no_name
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.profile_mobile
import com.sledje.agentpro.resources.signup_added
import com.sledje.agentpro.resources.signup_copied
import com.sledje.agentpro.resources.signup_copy
import com.sledje.agentpro.resources.signup_creating
import com.sledje.agentpro.resources.signup_err_no_site
import com.sledje.agentpro.resources.signup_err_too_many
import com.sledje.agentpro.resources.signup_err_unavailable
import com.sledje.agentpro.resources.signup_gone
import com.sledje.agentpro.resources.signup_link_until
import com.sledje.agentpro.resources.signup_live_title
import com.sledje.agentpro.resources.signup_open_customer
import com.sledje.agentpro.resources.signup_option_link
import com.sledje.agentpro.resources.signup_option_link_hint
import com.sledje.agentpro.resources.signup_option_manual_hint
import com.sledje.agentpro.resources.signup_presence_expired
import com.sledje.agentpro.resources.signup_presence_not_opened
import com.sledje.agentpro.resources.signup_presence_now
import com.sledje.agentpro.resources.signup_presence_seen
import com.sledje.agentpro.resources.signup_send_new
import com.sledje.agentpro.resources.signup_share
import com.sledje.agentpro.resources.signup_share_message
import com.sledje.agentpro.resources.signup_typing
import com.sledje.agentpro.resources.signup_waiting_consent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

fun incompleteRowTag(id: String) = "incomplete_$id"
fun incompleteDeleteTag(id: String) = "incomplete_delete_$id"

/** A clock that ticks every few seconds, so "On the form now" and relative times stay true while a screen is open. */
@Composable
fun rememberTickingNow(): Instant {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            now = Clock.System.now()
        }
    }
    return now
}

private const val TICK_MILLIS = 10_000L

/**
 * The Add customer sheet body (docs/UX.md, Customers tab): Fill it in myself · Pick from contacts · Send the
 * customer a link. The link row shows only when [onSendLink] is set (the `customerSignup` flag) and needs internet.
 */
@Composable
fun AddCustomerSheetContent(
    onFillMyself: () -> Unit,
    onPickContact: () -> Unit,
    onSendLink: (() -> Unit)?,
    online: Boolean = LocalIsOnline.current,
) {
    Column(Modifier.testTag("add_customer_sheet")) {
        ListRow(
            title = stringResource(Res.string.customers_add_manually),
            supporting = stringResource(Res.string.signup_option_manual_hint),
            leading = ListLeading.Icon(Res.drawable.ic_edit),
            onClick = onFillMyself,
            modifier = Modifier.testTag("add_option_manual"),
        )
        ListRow(
            title = stringResource(Res.string.customers_pick_contact),
            leading = ListLeading.Icon(Res.drawable.ic_contacts),
            onClick = onPickContact,
            modifier = Modifier.testTag("add_option_contacts"),
        )
        if (onSendLink != null) {
            ListRow(
                title = stringResource(Res.string.signup_option_link),
                supporting = stringResource(if (online) Res.string.signup_option_link_hint else Res.string.needs_internet),
                leading = ListLeading.Icon(Res.drawable.ic_person_add),
                onClick = if (online) onSendLink else null,
                muted = !online,
                modifier = Modifier.testTag("add_option_link"),
            )
        }
    }
}

/** The "Incomplete forms · N" row above the Customers search. */
@Composable
fun IncompleteFormsRow(count: Int, onClick: () -> Unit) {
    NavRow(
        title = stringResource(Res.string.incomplete_forms_row, count),
        onClick = onClick,
        icon = Res.drawable.ic_description,
        modifier = Modifier.testTag("incomplete_forms_row"),
    )
    HairlineDivider()
}

/** The Incomplete forms screen, wired to its ViewModel. */
@Composable
fun IncompleteFormsScreen(vm: IncompleteFormsViewModel, onBack: () -> Unit, onOpen: (CustomerDraft) -> Unit) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.action_undo)
    // One long-lived collector: consuming the event must not cancel the Undo snackbar it shows.
    LaunchedEffect(vm) {
        vm.event.filterNotNull().collect { e ->
            vm.eventShown()
            when (e) {
                is IncompleteFormsEvent.Deleted -> if (snackbar.showUndo(getString(Res.string.incomplete_deleted), undoLabel)) vm.undoDelete(e.id)
                is IncompleteFormsEvent.Failed -> snackbar.showError(e.error)
            }
        }
    }
    IncompleteFormsContent(state, rememberTickingNow(), snackbar, onBack, onOpen, vm::delete)
}

/** Stateless Incomplete forms (also rendered by the screenshot tests). */
@Composable
fun IncompleteFormsContent(
    state: UiState<List<CustomerDraft>>,
    now: Instant,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (CustomerDraft) -> Unit,
    onDelete: (String) -> Unit,
) {
    val online = LocalIsOnline.current
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.incomplete_forms_title), onBack = onBack) },
        offline = !online,
        snackbarHostState = snackbar,
    ) { padding ->
        ScreenState(
            state = state,
            empty = { EmptyState(Res.drawable.ic_description, stringResource(Res.string.incomplete_forms_empty), Modifier.padding(padding)) },
            onRetry = {},
            modifier = Modifier.padding(padding),
        ) { drafts ->
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(drafts, key = { _, d -> d.id }) { index, draft ->
                    Column(itemMotion()) {
                        if (index > 0) HairlineDivider()
                        IncompleteFormRow(draft, now, online, onOpen, onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun IncompleteFormRow(draft: CustomerDraft, now: Instant, online: Boolean, onOpen: (CustomerDraft) -> Unit, onDelete: (String) -> Unit) {
    val delete = stringResource(Res.string.incomplete_delete)
    ListRow(
        title = draft.name ?: stringResource(Res.string.incomplete_no_name),
        supporting = draftLineText(IncompleteForms.line(draft, now), now),
        leading = ListLeading.Icon(if (draft.source == DraftSource.Agent) Res.drawable.ic_edit else Res.drawable.ic_public),
        onClick = { onOpen(draft) },
        modifier = Modifier.testTag(incompleteRowTag(draft.id)),
        trailing = {
            IconButton(onClick = { if (online) onDelete(draft.id) }, modifier = Modifier.testTag(incompleteDeleteTag(draft.id))) {
                Icon(
                    painterResource(Res.drawable.ic_delete),
                    contentDescription = delete,
                    modifier = Modifier.size(Dimens.icon),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun draftLineText(line: DraftLine, now: Instant): String = when (line) {
    is DraftLine.ByYou -> stringResource(Res.string.incomplete_by_you, relativeTime(line.updatedAt, now))
    is DraftLine.CustomerLink -> when (val p = line.presence) {
        Presence.OnFormNow -> stringResource(Res.string.incomplete_link_on_form)
        is Presence.LastSeen -> stringResource(Res.string.incomplete_link_opened, relativeTime(p.openedAt ?: p.at, now))
        Presence.NotOpened -> stringResource(Res.string.incomplete_link_not_opened)
        is Presence.Expired -> stringResource(Res.string.incomplete_link_expired)
    }
}

/** One line under a link or renewal problem. */
@Composable
fun signupNoticeText(notice: SignupNotice): String = when (notice) {
    is SignupNotice.Failed -> stringResource(notice.error.message)
    SignupNotice.NoSite -> stringResource(Res.string.signup_err_no_site)
    SignupNotice.TooMany -> stringResource(Res.string.signup_err_too_many)
    SignupNotice.Unavailable -> stringResource(Res.string.signup_err_unavailable)
}

/** "Making the link…" (or why it could not be made, with Retry) between the Add sheet and the live screen. */
@Composable
fun SignupStartContent(state: SignupStartState, onBack: () -> Unit, onRetry: () -> Unit) {
    AppScaffold(topBar = { AppTopBar(title = stringResource(Res.string.signup_live_title), onBack = onBack) }) { padding ->
        when (state) {
            is SignupStartState.Failed -> EmptyState(
                Res.drawable.ic_person_add,
                signupNoticeText(state.notice),
                Modifier.padding(padding),
                actionLabel = stringResource(Res.string.action_retry),
                onAction = onRetry,
            )
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                Text(stringResource(Res.string.signup_creating), style = AgentProTheme.text.body, modifier = Modifier.padding(Spacing.m))
                ListSkeleton(rows = 2)
            }
        }
    }
}

/** The live sign-up screen, wired to its ViewModel. */
@Composable
fun SignupLiveScreen(
    vm: SignupLiveViewModel,
    onBack: () -> Unit,
    onShare: (String) -> Boolean,
    onOpenCustomer: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val shareUrl by vm.share.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val message = shareUrl?.let { stringResource(Res.string.signup_share_message, it) }
    LaunchedEffect(shareUrl) {
        if (message != null) {
            onShare(message)
            vm.shareHandled()
        }
    }
    var copied by remember { mutableStateOf(0) }
    val copiedText = stringResource(Res.string.signup_copied)
    LaunchedEffect(copied) { if (copied > 0) snackbar.showSnackbar(copiedText) }
    val shown = state as? SignupLiveState.Shown
    val shareText = shown?.url?.let { stringResource(Res.string.signup_share_message, it) }
    SignupLiveContent(
        state = state,
        now = rememberTickingNow(),
        snackbar = snackbar,
        onBack = onBack,
        onShare = { shareText?.let(onShare) },
        onCopy = {
            shown?.url?.let {
                @Suppress("DEPRECATION")
                clipboard.setText(AnnotatedString(it))
                copied++
            }
        },
        onRenew = vm::renew,
        onOpenCustomer = onOpenCustomer,
    )
}

/** Stateless live sign-up screen (also rendered by the screenshot tests). */
@Composable
fun SignupLiveContent(
    state: SignupLiveState,
    now: Instant,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onRenew: () -> Unit,
    onOpenCustomer: (String) -> Unit,
) {
    val online = LocalIsOnline.current
    val shown = state as? SignupLiveState.Shown
    val title = shown?.draft?.name ?: stringResource(Res.string.signup_live_title)
    AppScaffold(
        topBar = { AppTopBar(title = title, onBack = onBack) },
        offline = !online,
        snackbarHostState = snackbar,
    ) { padding ->
        when (state) {
            SignupLiveState.Loading -> ListSkeleton(Modifier.padding(padding))
            SignupLiveState.Gone -> EmptyState(FeatureId.Customers.icon, stringResource(Res.string.signup_gone), Modifier.padding(padding))
            is SignupLiveState.Shown -> SignupLiveBody(state, now, online, onShare, onCopy, onRenew, onOpenCustomer, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SignupLiveBody(
    state: SignupLiveState.Shown,
    now: Instant,
    online: Boolean,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onRenew: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    modifier: Modifier,
) {
    val draft = state.draft
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val customerId = draft.customerId
        if (draft.isCompleted) {
            Text(stringResource(Res.string.signup_added), style = AgentProTheme.text.title, modifier = Modifier.testTag("signup_added"))
            if (customerId != null) {
                PrimaryButton(stringResource(Res.string.signup_open_customer), { onOpenCustomer(customerId) }, Modifier.fillMaxWidth().testTag("signup_open_customer"))
            }
            return@Column
        }
        if (state.url != null) {
            AppCard(Modifier.fillMaxWidth().testTag("signup_link_card")) {
                Text(state.url, style = AgentProTheme.text.body)
                draft.linkExpiresAt?.let {
                    Spacer(Modifier.height(Spacing.s))
                    Text(stringResource(Res.string.signup_link_until, formatDate(Dates.dateOf(it))), style = AgentProTheme.text.bodyMuted)
                }
                Spacer(Modifier.height(Spacing.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PrimaryButton(stringResource(Res.string.signup_share), onShare, Modifier.weight(1f).testTag("signup_share"))
                    SecondaryButton(stringResource(Res.string.signup_copy), onCopy, Modifier.weight(1f).testTag("signup_copy"))
                }
            }
        } else {
            SecondaryButton(
                stringResource(Res.string.signup_send_new),
                onRenew,
                Modifier.fillMaxWidth().testTag("signup_renew"),
                enabled = online && !state.busy,
            )
            if (!online) Text(stringResource(Res.string.needs_internet), style = AgentProTheme.text.bodyMuted, modifier = Modifier.testTag("signup_offline"))
        }
        state.notice?.let {
            Text(signupNoticeText(it), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("signup_notice"))
        }
        Text(presenceText(IncompleteForms.presence(draft, now), now), style = AgentProTheme.text.strong, modifier = Modifier.testTag("signup_presence"))
        if (!draft.consented) {
            Text(stringResource(Res.string.signup_waiting_consent), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("signup_waiting_consent"))
        } else {
            val onForm = IncompleteForms.isOnForm(draft, now)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                DraftFields.CUSTOMER_KEYS.forEach { key ->
                    LiveField(
                        label = fieldLabel(key),
                        value = fieldValue(key, draft.fields[key]),
                        active = onForm && draft.activeField == key,
                        tag = key,
                    )
                }
            }
        }
    }
}

@Composable
private fun presenceText(presence: Presence, now: Instant): String = when (presence) {
    Presence.OnFormNow -> stringResource(Res.string.signup_presence_now)
    is Presence.LastSeen -> stringResource(Res.string.signup_presence_seen, relativeTime(presence.at, now))
    Presence.NotOpened -> stringResource(Res.string.signup_presence_not_opened)
    is Presence.Expired -> stringResource(Res.string.signup_presence_expired, formatDate(Dates.dateOf(presence.at)))
}

@Composable
private fun fieldLabel(key: String): String = stringResource(
    when (key) {
        DraftFields.NAME -> Res.string.cat_field_name
        DraftFields.PHONE -> Res.string.profile_mobile
        DraftFields.EMAIL -> Res.string.auth_email
        DraftFields.DOB -> Res.string.customer_dob
        DraftFields.ADDRESS -> Res.string.customer_address
        DraftFields.CITY -> Res.string.cat_field_city
        else -> Res.string.customer_nominee
    },
)

@Composable
private fun fieldValue(key: String, raw: String?): String? = when {
    raw.isNullOrBlank() -> null
    key == DraftFields.DOB -> parseDateOrNull(raw)?.let { formatDate(it) } ?: raw
    key == DraftFields.PHONE -> Phone.display(raw)
    else -> raw
}

/** A read-only field of the live form; the one the customer is in is highlighted and says "Typing…". */
@Composable
private fun LiveField(label: String, value: String?, active: Boolean, tag: String) {
    val background = if (active) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    Column(
        Modifier.fillMaxWidth().background(background, AppShapes.sm).padding(Spacing.s).testTag("signup_field_$tag"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(label, style = AgentProTheme.text.bodyMuted)
            if (active) {
                Text(stringResource(Res.string.signup_typing), style = AgentProTheme.text.label, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("signup_typing"))
            }
        }
        Text(value.orEmpty().ifEmpty { " " }, style = AgentProTheme.text.body)
    }
}
