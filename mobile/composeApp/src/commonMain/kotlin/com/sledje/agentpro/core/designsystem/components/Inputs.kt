package com.sledje.agentpro.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Motion
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.TABULAR_FIGURES
import com.sledje.agentpro.core.designsystem.Tokens
import com.sledje.agentpro.core.format.Money
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_clear
import com.sledje.agentpro.resources.action_remove
import com.sledje.agentpro.resources.filter_all
import com.sledje.agentpro.resources.filter_chip_count
import com.sledje.agentpro.resources.fewer_details
import com.sledje.agentpro.resources.form_add_photo
import com.sledje.agentpro.resources.form_change_photo
import com.sledje.agentpro.resources.form_choose
import com.sledje.agentpro.resources.form_photo_added
import com.sledje.agentpro.resources.ic_add_photo_alternate
import com.sledje.agentpro.resources.ic_check
import com.sledje.agentpro.resources.ic_close
import com.sledje.agentpro.resources.ic_keyboard_arrow_down
import com.sledje.agentpro.resources.ic_keyboard_arrow_up
import com.sledje.agentpro.resources.ic_search
import com.sledje.agentpro.resources.more_details
import com.sledje.agentpro.resources.search_hint
import com.sledje.agentpro.resources.selected
import com.sledje.agentpro.core.catalog.CatalogEntry
import com.sledje.agentpro.core.catalog.label
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Search input debounce (docs/UX.md, Global UX rule 5). */
const val SEARCH_DEBOUNCE_MILLIS: Long = 300L

/**
 * Search: filled with surfaceContainerLow, no border. [onQueryChange] gets every keystroke (to show
 * the text); [onSearch] gets the query once typing has paused for 300ms.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(Res.string.search_hint),
) {
    val latestOnSearch by rememberUpdatedState(onSearch)
    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MILLIS)
        latestOnSearch(query)
    }
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = AgentProTheme.text.body,
        placeholder = { Text(placeholder, style = AgentProTheme.text.body, color = AgentProTheme.colors.muted) },
        leadingIcon = { Icon(painterResource(Res.drawable.ic_search), null, Modifier.size(Dimens.icon)) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.action_clear), Modifier.size(Dimens.icon))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = AppShapes.sm,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    )
}

@Composable
private fun AppFilterChip(label: String, selected: Boolean, onClick: () -> Unit, icon: DrawableResource? = null) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = AgentProTheme.text.label) },
        leadingIcon = icon?.let { { Icon(painterResource(it), null, Modifier.size(Dimens.iconSmall)) } },
        shape = AppShapes.sm,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/**
 * Filter chips built from a catalog list, with counts; scrolls sideways. Single choice: the first
 * chip is "All" (selected when [selected] is null).
 */
@Composable
fun <T : CatalogEntry> FilterChipRow(
    entries: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    counts: Map<T, Int> = emptyMap(),
    allCount: Int? = null,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        val all = stringResource(Res.string.filter_all)
        AppFilterChip(
            label = if (allCount != null) stringResource(Res.string.filter_chip_count, all, allCount) else all,
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        entries.forEach { entry ->
            val label = entry.label()
            val count = counts[entry]
            AppFilterChip(
                label = if (count != null) stringResource(Res.string.filter_chip_count, label, count) else label,
                selected = entry == selected,
                onClick = { onSelect(if (entry == selected) null else entry) },
            )
        }
    }
}

/** One option of [ChoiceChips] / [SelectField]. */
data class ChoiceOption(val key: String, val label: String, val icon: DrawableResource? = null)

/** Multi-select as wrapping chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(
    options: List<ChoiceOption>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        options.forEach { option ->
            AppFilterChip(option.label, option.key in selected, { onToggle(option.key) }, option.icon)
        }
    }
}

/**
 * The field frame used by every form field: label above (`label` style), the field [Spacing.s] below,
 * then one line of error or helper text.
 */
@Composable
fun FieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    trailingHelper: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = AgentProTheme.text.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.s))
        content()
        if (error != null || helper != null || trailingHelper != null) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                Text(
                    error ?: helper.orEmpty(),
                    style = AgentProTheme.text.bodyMuted,
                    color = if (error != null) MaterialTheme.colorScheme.error else AgentProTheme.colors.muted,
                    modifier = Modifier.weight(1f),
                )
                if (trailingHelper != null) Text(trailingHelper, style = AgentProTheme.text.bodyMuted)
            }
        }
    }
}

/** An outlined text field (radius 8) inside a [FieldFrame]. [onFocusLost] drives validate-on-blur. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    counter: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    prefix: String? = null,
    tabular: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    onFocusLost: () -> Unit = {},
    /** Applied to the input itself (e.g. a test tag), not the frame. */
    inputModifier: Modifier = Modifier,
) {
    var wasFocused by remember { mutableStateOf(false) }
    val style = if (tabular) AgentProTheme.text.body.copy(fontFeatureSettings = TABULAR_FIGURES) else AgentProTheme.text.body
    FieldFrame(label, modifier, error, helper, counter) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = inputModifier
                .fillMaxWidth()
                .semantics { contentDescription = label }
                .onFocusChanged {
                    if (wasFocused && !it.isFocused) onFocusLost()
                    wasFocused = it.isFocused
                },
            textStyle = style,
            placeholder = placeholder?.let { { Text(it, style = AgentProTheme.text.body, color = AgentProTheme.colors.muted) } },
            prefix = prefix?.let { { Text(it, style = style) } },
            trailingIcon = trailingIcon,
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            readOnly = readOnly,
            shape = AppShapes.sm,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
        )
    }
}

/** Shows a digits[.dd] string with Indian grouping (1,25,000.50) while editing. */
class IndianGroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val intPart = raw.substringBefore('.')
        if (intPart.isEmpty() || !intPart.all { it.isDigit() }) return TransformedText(text, OffsetMapping.Identity)
        val grouped = Money.groupIndian(intPart)
        val out = grouped + raw.substring(intPart.length)
        val toTransformed = IntArray(raw.length + 1)
        var o = 0
        grouped.forEachIndexed { t, c -> if (c != ',') toTransformed[o++] = t }
        for (k in intPart.length..raw.length) toTransformed[k] = grouped.length + (k - intPart.length)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toTransformed[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int = out.take(offset.coerceIn(0, out.length)).count { it != ',' }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/** ₹ amount input: digits with up to 2 decimals, shown with Indian grouping and tabular figures. */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusLost: () -> Unit = {},
    inputModifier: Modifier = Modifier,
) {
    val transformation = remember { IndianGroupingTransformation() }
    AppTextField(
        value = value,
        onValueChange = { input ->
            val cleaned = input.filter { it.isDigit() || it == '.' }
            if (Regex("""\d*(\.\d{0,2})?""").matches(cleaned)) onValueChange(cleaned)
        },
        label = label,
        modifier = modifier,
        error = error,
        helper = helper,
        enabled = enabled,
        prefix = Money.SYMBOL,
        tabular = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = transformation,
        onFocusLost = onFocusLost,
        inputModifier = inputModifier,
    )
}

/** A read-only field that opens a choice ([onClick]); shared by select, date and similar pickers. */
@Composable
fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    placeholder: String = stringResource(Res.string.form_choose),
    icon: DrawableResource = Res.drawable.ic_keyboard_arrow_down,
    enabled: Boolean = true,
) {
    Box(modifier) {
        AppTextField(
            value = value,
            onValueChange = {},
            label = label,
            error = error,
            helper = helper,
            placeholder = placeholder,
            readOnly = true,
            enabled = enabled,
            trailingIcon = { Icon(painterResource(icon), null, Modifier.size(Dimens.icon)) },
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        )
    }
}

/** Single choice from a short list, as a dropdown. */
@Composable
fun SelectField(
    options: List<ChoiceOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        PickerField(
            value = options.firstOrNull { it.key == selectedKey }?.label.orEmpty(),
            label = label,
            onClick = { open = true },
            error = error,
            enabled = enabled,
            icon = if (open) Res.drawable.ic_keyboard_arrow_up else Res.drawable.ic_keyboard_arrow_down,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = AgentProTheme.colors.elevatedSurface) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = AgentProTheme.text.body) },
                    leadingIcon = option.icon?.let { { Icon(painterResource(it), null, Modifier.size(Dimens.icon)) } },
                    onClick = { open = false; onSelect(option.key) },
                )
            }
        }
    }
}

/** A labelled switch row (48dp touch target). */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AgentProTheme.text.body)
            if (supporting != null) Text(supporting, style = AgentProTheme.text.bodyMuted)
        }
        Spacer(Modifier.width(Spacing.m))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** One accent swatch. */
data class SwatchOption(val key: String, val label: String, val color: Color)

/** Accent swatches as round buttons; the selected one shows a check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwatchPicker(
    swatches: List<SwatchOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedText = stringResource(Res.string.selected)
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        swatches.forEach { swatch ->
            val isSelected = swatch.key == selectedKey
            Box(
                Modifier
                    .size(Dimens.touchTarget)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(swatch.key) })
                    .semantics {
                        contentDescription = swatch.label
                        if (isSelected) stateDescription = selectedText
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(Dimens.swatch)
                        .background(swatch.color, AppShapes.full)
                        .then(
                            if (isSelected) Modifier.border(Dimens.stepperConnector, MaterialTheme.colorScheme.outline, AppShapes.full) else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Swatches keep ≥ 4.5:1 with white text (catalog accentSwatches), so the check is white.
                    if (isSelected) Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(Dimens.iconSmall), tint = Tokens.ColorLight.onPrimary)
                }
            }
        }
    }
}

/**
 * The image field's slot. The real picker (MediaPicker + ImageProcessor, docs/ARCHITECTURE.md) is
 * wired by the caller through [onPick]; [preview] renders the image once an image loader exists.
 */
@Composable
fun ImagePickerSlot(
    label: String,
    hasImage: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    preview: (@Composable () -> Unit)? = null,
    helper: String? = null,
) {
    FieldFrame(label, modifier, error, helper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(Dimens.imageSlot)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, AppShapes.md)
                    .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant, AppShapes.md),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    hasImage && preview != null -> preview()
                    hasImage -> Icon(painterResource(Res.drawable.ic_check), stringResource(Res.string.form_photo_added), Modifier.size(Dimens.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Icon(painterResource(Res.drawable.ic_add_photo_alternate), null, Modifier.size(Dimens.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(Spacing.m))
            Column {
                TertiaryButton(
                    stringResource(if (hasImage) Res.string.form_change_photo else Res.string.form_add_photo),
                    onClick = onPick,
                    enabled = enabled,
                )
                if (hasImage) TertiaryButton(stringResource(Res.string.action_remove), onClick = onRemove, enabled = enabled)
            }
        }
    }
}

/** "More details": an expandable group for optional form fields. */
@Composable
fun MoreDetails(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        TertiaryButton(
            text = stringResource(if (expanded) Res.string.fewer_details else Res.string.more_details),
            onClick = onToggle,
            icon = if (expanded) Res.drawable.ic_keyboard_arrow_up else Res.drawable.ic_keyboard_arrow_down,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.stateChange()) + fadeIn(Motion.stateChange()),
            exit = shrinkVertically(Motion.stateChange()) + fadeOut(Motion.stateChange()),
        ) {
            Column(Modifier.padding(top = Spacing.s)) { content() }
        }
    }
}
