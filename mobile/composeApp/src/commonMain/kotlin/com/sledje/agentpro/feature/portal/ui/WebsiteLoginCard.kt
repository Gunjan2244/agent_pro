package com.sledje.agentpro.feature.portal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sledje.agentpro.core.catalog.Portal
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.CardSkeleton
import com.sledje.agentpro.core.designsystem.components.ConfirmDialog
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.formatDate
import com.sledje.agentpro.core.format.relativeTime
import com.sledje.agentpro.feature.customers.CustomerFormRoute
import com.sledje.agentpro.feature.portal.data.PortalState
import com.sledje.agentpro.feature.website.WebsiteContentRoute
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_retry
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.portal_add_phone
import com.sledje.agentpro.resources.portal_invalid_phone
import com.sledje.agentpro.resources.portal_invited_until
import com.sledje.agentpro.resources.portal_locked
import com.sledje.agentpro.resources.portal_login_off
import com.sledje.agentpro.resources.portal_open_content
import com.sledje.agentpro.resources.portal_needs_phone
import com.sledje.agentpro.resources.portal_needs_site
import com.sledje.agentpro.resources.portal_none_message
import com.sledje.agentpro.resources.portal_not_signed_in
import com.sledje.agentpro.resources.portal_send_again
import com.sledje.agentpro.resources.portal_send_link
import com.sledje.agentpro.resources.portal_send_new
import com.sledje.agentpro.resources.portal_send_new_short
import com.sledje.agentpro.resources.portal_share_message
import com.sledje.agentpro.resources.portal_shared_with
import com.sledje.agentpro.resources.portal_signed_in
import com.sledje.agentpro.resources.portal_title
import com.sledje.agentpro.resources.portal_turn_off
import com.sledje.agentpro.resources.portal_turn_off_message
import com.sledje.agentpro.resources.portal_turn_off_title
import com.sledje.agentpro.resources.portal_unavailable
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

/** The Details tab's "Website login" card, wired to [PortalViewModel] (docs/UX.md, Customer detail). */
@Composable
fun WebsiteLoginCard(host: FeatureHost, customerId: String, modifier: Modifier = Modifier) {
    val deps = host.deps
    val vm = viewModel(key = "portal_$customerId") {
        PortalViewModel(
            customerId = customerId,
            portal = deps.portal,
            customer = deps.customers.observe(customerId),
            site = deps.sites.observeMine(),
            online = deps.network.isOnline,
            agentName = deps.profiles.observeMine().map { it?.fullName },
            store = deps.store,
        )
    }
    val state by vm.state.collectAsState()
    val share by vm.share.collectAsState()
    val message = share?.let { stringResource(Res.string.portal_share_message, it.customerName, it.url, Portal.pinLength, it.agentName) }
    LaunchedEffect(share) {
        if (message != null) {
            deps.urlLauncher.share(message)
            vm.shareHandled()
        }
    }
    WebsiteLoginCardContent(
        state = state,
        onSend = vm::send,
        onTurnOff = vm::turnOff,
        onEditCustomer = { host.nav.navigate(CustomerFormRoute(id = customerId)) },
        onOpenWebsiteContent = { host.nav.navigate(WebsiteContentRoute) { launchSingleTop = true } },
        onRetry = vm::load,
        modifier = modifier,
    )
}

/** Stateless card (also rendered by the screenshot tests). */
@Composable
fun WebsiteLoginCardContent(
    state: WebsiteLoginUiState,
    onSend: () -> Unit,
    onTurnOff: () -> Unit,
    onEditCustomer: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onOpenWebsiteContent: () -> Unit = {},
) {
    var confirmOff by remember { mutableStateOf(false) }
    if (state is WebsiteLoginUiState.Loading) {
        CardSkeleton(modifier.fillMaxWidth())
        return
    }
    AppCard(modifier.fillMaxWidth().testTag("portal_card")) {
        Text(stringResource(Res.string.portal_title), style = AgentProTheme.text.strong)
        Spacer(Modifier.height(Spacing.s))
        when (state) {
            WebsiteLoginUiState.Loading -> Unit
            is WebsiteLoginUiState.Error -> {
                Line(stringResource(state.error.message))
                TertiaryButton(stringResource(Res.string.action_retry), onRetry, Modifier.testTag("portal_retry"))
            }
            is WebsiteLoginUiState.NeedsPhone -> {
                Line(stringResource(Res.string.portal_needs_phone))
                TertiaryButton(stringResource(Res.string.portal_add_phone), onEditCustomer, Modifier.testTag("portal_edit_customer"))
            }
            is WebsiteLoginUiState.NeedsSite -> Line(stringResource(Res.string.portal_needs_site))
            is WebsiteLoginUiState.Ready -> {
                val enabled = state.online && !state.busy
                val status = state.status
                if (!status.portalAvailable) {
                    Text(
                        stringResource(Res.string.portal_login_off),
                        style = AgentProTheme.text.body,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("portal_login_off"),
                    )
                    TertiaryButton(stringResource(Res.string.portal_open_content), onOpenWebsiteContent, Modifier.testTag("portal_open_content"))
                    Spacer(Modifier.height(Spacing.s))
                }
                when (status.state) {
                    PortalState.None -> {
                        Line(stringResource(Res.string.portal_none_message, state.customerName))
                        Buttons(state.online) {
                            PrimaryButton(stringResource(Res.string.portal_send_link), onSend, Modifier.fillMaxWidth().testTag("portal_send"), enabled = enabled)
                        }
                    }
                    PortalState.Invited -> {
                        status.inviteExpiresAt?.let { Line(stringResource(Res.string.portal_invited_until, formatDate(Dates.dateOf(it)))) }
                        Buttons(state.online) {
                            SecondaryButton(stringResource(Res.string.portal_send_again), onSend, Modifier.fillMaxWidth().testTag("portal_send"), enabled = enabled)
                            TurnOff(enabled) { confirmOff = true }
                        }
                    }
                    PortalState.Active -> {
                        val last = status.lastLoginAt
                        Line(
                            if (last != null) {
                                stringResource(Res.string.portal_signed_in, relativeTime(last, state.now))
                            } else {
                                stringResource(Res.string.portal_not_signed_in)
                            },
                        )
                        if (status.sharedWith.isNotEmpty()) Line(stringResource(Res.string.portal_shared_with, status.sharedWith.joinToString(", ")))
                        Buttons(state.online) {
                            SecondaryButton(stringResource(Res.string.portal_send_new), onSend, Modifier.fillMaxWidth().testTag("portal_send"), enabled = enabled)
                            TurnOff(enabled) { confirmOff = true }
                        }
                    }
                    PortalState.Locked -> {
                        Line(stringResource(Res.string.portal_locked))
                        Buttons(state.online) {
                            SecondaryButton(stringResource(Res.string.portal_send_new_short), onSend, Modifier.fillMaxWidth().testTag("portal_send"), enabled = enabled)
                            TurnOff(enabled) { confirmOff = true }
                        }
                    }
                }
                state.notice?.let { Notice(it) }
            }
        }
    }
    if (confirmOff && state is WebsiteLoginUiState.Ready) {
        ConfirmDialog(
            title = stringResource(Res.string.portal_turn_off_title),
            message = stringResource(Res.string.portal_turn_off_message, state.customerName),
            confirmLabel = stringResource(Res.string.portal_turn_off),
            onConfirm = { confirmOff = false; onTurnOff() },
            onDismiss = { confirmOff = false },
        )
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.s))
}

@Composable
private fun Buttons(online: Boolean, content: @Composable () -> Unit) {
    content()
    if (!online) {
        Spacer(Modifier.height(Spacing.s))
        Text(stringResource(Res.string.needs_internet), style = AgentProTheme.text.bodyMuted, modifier = Modifier.testTag("portal_offline"))
    }
}

@Composable
private fun TurnOff(enabled: Boolean, onClick: () -> Unit) {
    TertiaryButton(stringResource(Res.string.portal_turn_off), onClick, Modifier.fillMaxWidth().testTag("portal_turn_off"), enabled = enabled)
}

@Composable
private fun Notice(notice: PortalNotice) {
    val text = when (notice) {
        is PortalNotice.Failed -> stringResource(notice.error.message)
        PortalNotice.InvalidPhone -> stringResource(Res.string.portal_invalid_phone)
        PortalNotice.NoSite -> stringResource(Res.string.portal_needs_site)
        PortalNotice.Unavailable -> stringResource(Res.string.portal_unavailable)
    }
    Spacer(Modifier.height(Spacing.s))
    Text(text, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("portal_notice"))
}
