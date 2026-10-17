package com.sledje.agentpro.feature.enquiries.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTextField
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.customers.ui.CrmFormScreen
import com.sledje.agentpro.feature.customers.ui.offlineHelper
import com.sledje.agentpro.feature.customers.ui.showError
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.crm_notes
import com.sledje.agentpro.resources.enquiry_form_title
import com.sledje.agentpro.resources.enquiry_notes_hint
import com.sledje.agentpro.resources.enquiry_notes_saved
import com.sledje.agentpro.resources.enquiry_notes_saving
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Test tag of the notes editor's text field. */
const val NOTES_FIELD_TAG = "enquiry_notes"

/** "Add enquiry" (FormRenderer; docs/UX.md). Closes once saved. */
@Composable
fun AddEnquiryScreen(
    vm: AddEnquiryViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    /** The one-time customer-data attestation for a manual enquiry (docs/PRIVACY.md, section 3). */
    dutyGate: (@Composable (SnackbarHostState) -> Unit)? = null,
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showError(it) } }
    CrmFormScreen(
        title = stringResource(Res.string.enquiry_form_title),
        form = vm.form,
        saving = state.saving,
        onSave = vm::save,
        onBack = onBack,
        snackbar = snackbar,
        visibleKeys = AddEnquiryViewModel.VISIBLE,
    )
    dutyGate?.invoke(snackbar)
}

/** The notes editor with autosave (docs/UX.md, Enquiry detail → Notes). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NotesScreen(vm: NotesViewModel, onBack: () -> Unit) {
    val text by vm.text.collectAsState()
    val status by vm.status.collectAsState()
    val error by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val online = LocalIsOnline.current
    LaunchedEffect(error) { error?.let { snackbar.showError(it) } }
    val close: () -> Unit = { scope.launch { vm.flush(); onBack() } }
    BackHandler { close() }
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.crm_notes), onBack = close) },
        offline = !online,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.m)) {
            AppTextField(
                value = text,
                onValueChange = { if (it.length <= NotesViewModel.MAX_LENGTH) vm.onChange(it) },
                label = stringResource(Res.string.crm_notes),
                placeholder = stringResource(Res.string.enquiry_notes_hint),
                helper = offlineHelper() ?: when (status) {
                    NotesStatus.Saving -> stringResource(Res.string.enquiry_notes_saving)
                    NotesStatus.Saved -> stringResource(Res.string.enquiry_notes_saved)
                    else -> null
                },
                singleLine = false,
                minLines = 8,
                enabled = online,
                modifier = Modifier.fillMaxWidth(),
                inputModifier = Modifier.testTag(NOTES_FIELD_TAG),
            )
        }
    }
}
