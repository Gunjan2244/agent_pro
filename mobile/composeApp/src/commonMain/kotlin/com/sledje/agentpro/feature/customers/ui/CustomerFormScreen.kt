package com.sledje.agentpro.feature.customers.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.customer_form_add
import com.sledje.agentpro.resources.customer_form_edit
import org.jetbrains.compose.resources.stringResource

/** The customer form (add, edit, or convert an enquiry). [onSaved] gets the customer's id. */
@Composable
fun CustomerFormScreen(vm: CustomerFormViewModel, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val form by vm.form.collectAsState()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
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
        onSave = vm::save,
        onBack = onBack,
        snackbar = snackbar,
        visibleKeys = CustomerFormViewModel.VISIBLE,
    )
}
