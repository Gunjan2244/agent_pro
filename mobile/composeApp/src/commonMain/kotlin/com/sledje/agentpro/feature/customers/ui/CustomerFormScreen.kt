package com.sledje.agentpro.feature.customers.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.components.ToggleRow
import com.sledje.agentpro.core.designsystem.currentAppLocale
import com.sledje.agentpro.resources.validation_required
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.customer_form_add
import com.sledje.agentpro.resources.customer_form_edit
import org.jetbrains.compose.resources.stringResource

/** The customer form (add, edit, or convert an enquiry). [onSaved] gets the customer's id. */
@Composable
fun CustomerFormScreen(
    vm: CustomerFormViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    /** The one-time customer-data attestation for a new customer (docs/PRIVACY.md, section 3); null when not needed. */
    dutyGate: (@Composable (SnackbarHostState) -> Unit)? = null,
) {
    val form by vm.form.collectAsState()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val guardianAgreed by vm.guardianAgreed.collectAsState()
    val guardianMissing by vm.guardianMissing.collectAsState()
    val locale = currentAppLocale()
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showError(it) } }
    val title = stringResource(if (vm.mode is CustomerFormMode.Edit) Res.string.customer_form_edit else Res.string.customer_form_add)
    val f = form
    if (f == null) {
        AppScaffold(topBar = { AppTopBar(title = title, onBack = onBack) }) { ListSkeleton() }
        return
    }
    CrmFormScreen(
        title = title,
        form = f,
        saving = state.saving,
        onSave = { vm.save(locale) },
        onBack = onBack,
        snackbar = snackbar,
        visibleKeys = CustomerFormViewModel.VISIBLE,
        aboveSave = if (vm.requiresGuardian()) {
            { GuardianConsentToggle(guardianAgreed, guardianMissing, vm::setGuardianAgreed, enabled = !state.saving) }
        } else {
            null
        },
    )
    dutyGate?.invoke(snackbar)
}

/**
 * The under-18 line and the required toggle above Save (docs/PRIVACY.md, "Customers under 18"). The texts are
 * the generated `guardian_consent` notice; [missing] shows the toggle as required after Save was tapped without it.
 */
@Composable
fun GuardianConsentToggle(agreed: Boolean, missing: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(PrivacyNotice.GuardianConsent.texts[1]), style = AgentProTheme.text.body)
        ToggleRow(
            label = stringResource(PrivacyNotice.GuardianConsent.texts[2]),
            checked = agreed,
            onCheckedChange = onChange,
            enabled = enabled,
        )
        if (missing) {
            Text(stringResource(Res.string.validation_required), style = AgentProTheme.text.bodyMuted, color = MaterialTheme.colorScheme.error)
        }
    }
}
