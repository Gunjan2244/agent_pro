package com.sledje.agentpro.feature.privacy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sledje.agentpro.app.AppDependencies
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.designsystem.currentAppLocale
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.customers.ui.showError
import com.sledje.agentpro.feature.privacy.data.ConsentRepository
import com.sledje.agentpro.feature.privacy.data.PrivacyLinks
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.cat_privacy_agent_customer_duty_action
import com.sledje.agentpro.resources.cat_privacy_agent_customer_duty_text
import com.sledje.agentpro.resources.cat_privacy_agent_customer_duty_title
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.privacy_share_note
import com.sledje.agentpro.resources.privacy_share_note_message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** [accepted] is null until the local mark has been read. */
data class CustomerDutyUiState(val accepted: Boolean? = null, val recording: Boolean = false, val error: AppError? = null) {
    /** The sheet shows when the attestation is known to be missing. */
    val needed: Boolean get() = accepted == false
}

/**
 * The one-time customer-data attestation (docs/PRIVACY.md, section 3): shown the first time the agent adds a
 * customer or a manual enquiry; **I understand** records `agent_customer_duty` (needs internet) and remembers it.
 */
class CustomerDutyViewModel(private val consents: ConsentRepository) : ViewModel() {
    private val _state = MutableStateFlow(CustomerDutyUiState())
    val state: StateFlow<CustomerDutyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { consents.customerDutyAccepted().collect { ok -> _state.update { it.copy(accepted = ok) } } }
    }

    /** [locale] is the language the sheet is shown in (the hash identifies that text). */
    fun accept(locale: AppLocale) {
        val s = _state.value
        if (s.recording || s.accepted != false) return
        _state.update { it.copy(recording = true, error = null) }
        viewModelScope.launch {
            when (val r = consents.acceptCustomerDuty(locale)) {
                is Result.Ok -> _state.update { it.copy(recording = false, accepted = true) }
                is Result.Err -> _state.update { it.copy(recording = false, error = r.error) }
            }
        }
    }

    fun errorShown() = _state.update { it.copy(error = null) }
}

/** The sheet's body: the notice text, **I understand**, and **Share privacy note** when there is a link to share. */
@Composable
fun CustomerDutySheetContent(
    recording: Boolean,
    canShare: Boolean,
    onAccept: () -> Unit,
    onShare: () -> Unit,
    online: Boolean = LocalIsOnline.current,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(Res.string.cat_privacy_agent_customer_duty_text), style = AgentProTheme.text.body)
        PrimaryButton(
            stringResource(Res.string.cat_privacy_agent_customer_duty_action),
            onAccept,
            Modifier.fillMaxWidth(),
            enabled = online && !recording,
        )
        if (canShare) SecondaryButton(stringResource(Res.string.privacy_share_note), onShare, Modifier.fillMaxWidth())
        if (!online) {
            Text(
                stringResource(Res.string.needs_internet),
                style = AgentProTheme.text.bodyMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Shows the attestation sheet over a customer or manual-enquiry form until it's accepted. Dismissing the sheet
 * leaves the form ([onDeclined]): nothing can be added without it. [privacyUrl] is the agent's site privacy
 * page (null when there is no site yet: no share button); [onShare] shares text and returns false when nothing can.
 */
@Composable
fun CustomerDutyGate(
    vm: CustomerDutyViewModel,
    privacyUrl: String?,
    onShare: (String) -> Boolean,
    onDeclined: () -> Unit,
    snackbar: SnackbarHostState? = null,
) {
    val state by vm.state.collectAsState()
    val locale = currentAppLocale()
    val shareText = privacyUrl?.let { stringResource(Res.string.privacy_share_note_message, it) }
    val host = snackbar
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        host?.showError(error)
        vm.errorShown()
    }
    if (state.needed) {
        AppBottomSheet(stringResource(Res.string.cat_privacy_agent_customer_duty_title), onDismiss = onDeclined) {
            CustomerDutySheetContent(
                recording = state.recording,
                canShare = shareText != null,
                onAccept = { vm.accept(locale) },
                onShare = { shareText?.let(onShare) },
            )
        }
    }
}

/** [CustomerDutyGate] wired to the app: the signed-in agent's attestation and their site's privacy page. */
@Composable
fun CustomerDutyGateHost(deps: AppDependencies, snackbar: SnackbarHostState, onDeclined: () -> Unit) {
    val vm = viewModel { CustomerDutyViewModel(deps.consents) }
    val site by deps.sites.observeMine().collectAsState(null)
    CustomerDutyGate(
        vm = vm,
        privacyUrl = site?.slug?.let { PrivacyLinks.sitePrivacy(it) },
        onShare = deps.urlLauncher::share,
        onDeclined = onDeclined,
        snackbar = snackbar,
    )
}
