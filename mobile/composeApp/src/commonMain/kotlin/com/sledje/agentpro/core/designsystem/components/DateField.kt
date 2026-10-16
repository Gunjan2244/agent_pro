package com.sledje.agentpro.core.designsystem.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.formatDate
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_cancel
import com.sledje.agentpro.resources.action_ok
import com.sledje.agentpro.resources.form_pick_date
import com.sledje.agentpro.resources.ic_calendar_month
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** A date field ("15 Oct 2026") that opens the Material date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        value = value?.let { formatDate(it) }.orEmpty(),
        label = label,
        onClick = { open = true },
        modifier = modifier,
        error = error,
        helper = helper,
        placeholder = stringResource(Res.string.form_pick_date),
        icon = Res.drawable.ic_calendar_month,
        enabled = enabled,
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value?.let(Dates::toPickerMillis))
        val colors = DatePickerDefaults.colors(containerColor = AgentProTheme.colors.elevatedSurface)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TertiaryButton(stringResource(Res.string.action_ok), onClick = {
                    state.selectedDateMillis?.let { onValueChange(Dates.fromPickerMillis(it)) }
                    open = false
                })
            },
            dismissButton = { TertiaryButton(stringResource(Res.string.action_cancel), onClick = { open = false }) },
            colors = colors,
        ) {
            DatePicker(state = state, colors = colors)
        }
    }
}
