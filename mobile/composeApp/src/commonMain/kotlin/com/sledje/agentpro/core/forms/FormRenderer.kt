package com.sledje.agentpro.core.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import com.sledje.agentpro.core.designsystem.components.MoneyField
import com.sledje.agentpro.core.designsystem.components.SegmentOption
import com.sledje.agentpro.core.designsystem.components.SegmentedControl
import androidx.compose.material3.IconButton
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.resources.form_hide_password
import com.sledje.agentpro.resources.form_show_password
import com.sledje.agentpro.resources.ic_visibility
import com.sledje.agentpro.resources.ic_visibility_off
import org.jetbrains.compose.resources.painterResource
import com.sledje.agentpro.core.catalog.AccentSwatch
import com.sledje.agentpro.core.catalog.color
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppTextField
import com.sledje.agentpro.core.designsystem.components.ChoiceChips
import com.sledje.agentpro.core.designsystem.components.ConsentCheckbox
import com.sledje.agentpro.core.designsystem.components.ConsentLink
import com.sledje.agentpro.core.designsystem.components.ChoiceOption
import com.sledje.agentpro.core.designsystem.components.DateField
import com.sledje.agentpro.core.designsystem.components.FieldFrame
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ImagePickerSlot
import com.sledje.agentpro.core.designsystem.components.MoreDetails
import com.sledje.agentpro.core.designsystem.components.SelectField
import com.sledje.agentpro.core.designsystem.components.SwatchOption
import com.sledje.agentpro.core.designsystem.components.SwatchPicker
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.designsystem.components.ToggleRow
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_add
import com.sledje.agentpro.resources.action_remove
import com.sledje.agentpro.resources.form_character_count
import com.sledje.agentpro.resources.form_item_title
import com.sledje.agentpro.resources.ic_add
import kotlinx.datetime.LocalDate
import androidx.compose.material3.Text
import org.jetbrains.compose.resources.stringResource

/** Calm-screen rule 8: at most 6 fields show at once; the rest go under "More details". */
const val MAX_VISIBLE_FIELDS: Int = 6

/** The test tag of a field's input, for UI tests. */
fun fieldTestTag(key: String): String = "field_$key"

/**
 * Splits [fields] into the visible ones and those under "More details", keeping schema order.
 * With [visibleKeys], exactly those fields (plus every required one) are visible. Otherwise all fields
 * show when there are ≤ 6; else every required field plus the first optional ones up to 6.
 * Required fields are never hidden.
 */
fun splitVisibleFields(fields: List<FieldSpec>, visibleKeys: Set<String>? = null): Pair<List<FieldSpec>, List<FieldSpec>> {
    if (visibleKeys != null) return fields.partition { it.required || it.key in visibleKeys }
    if (fields.size <= MAX_VISIBLE_FIELDS) return fields to emptyList()
    var optionalSlots = (MAX_VISIBLE_FIELDS - fields.count { it.required }).coerceAtLeast(0)
    return fields.partition { spec ->
        when {
            spec.required -> true
            optionalSlots > 0 -> { optionalSlots--; true }
            else -> false
        }
    }
}

/**
 * The only way to build forms (docs/DESIGN_SYSTEM.md, Components). Renders [state]'s [FieldSpec]s:
 * text, multiline, number, phone, email, url, image, select, multiSelect, toggle, swatch, date and
 * item lists. Validation shows when a field loses focus (or after [FormState.validateAll]); each
 * field gets the right keyboard; IME Next moves on and Done calls [onDone]. Labels come from the
 * generated (translated) string resources.
 *
 * @param onPickImage called with the field key when the agent taps an image slot. The MediaPicker seam
 *   (core/platform) is wired by the screen, which then calls `state.set(key, url)`.
 * @param imagePreview renders a picked image (URL) once an image loader is added.
 */
@Composable
fun FormRenderer(
    state: FormState,
    modifier: Modifier = Modifier,
    visibleKeys: Set<String>? = null,
    enabled: Boolean = true,
    onPickImage: (fieldKey: String) -> Unit = {},
    imagePreview: (@Composable (url: String) -> Unit)? = null,
    onDone: () -> Unit = {},
    onLink: (fieldKey: String, linkKey: String) -> Unit = { _, _ -> },
) {
    val (visible, more) = splitVisibleFields(state.fields, visibleKeys)
    var expanded by rememberSaveable { mutableStateOf(false) }
    // A hidden field with a showing error opens "More details" so the error is never out of sight.
    val forced = more.any { state.visibleError(it.key) != null }
    val showMore = expanded || forced
    val textOrder = (visible + if (showMore) more else emptyList()).filter { it.type.isTextInput }.map { it.key }
    val lastTextKey = textOrder.lastOrNull()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        visible.forEach { Field(state, it, it.key == lastTextKey, enabled, onPickImage, imagePreview, onDone, onLink) }
        if (more.isNotEmpty()) {
            MoreDetails(expanded = showMore, onToggle = { expanded = !showMore }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    more.forEach { Field(state, it, it.key == lastTextKey, enabled, onPickImage, imagePreview, onDone, onLink) }
                }
            }
        }
    }
}

private val FieldType.isTextInput: Boolean
    get() = this in setOf(FieldType.Text, FieldType.Multiline, FieldType.Number, FieldType.Phone, FieldType.Email, FieldType.Url, FieldType.Password, FieldType.Money)

private fun keyboardFor(type: FieldType, ime: ImeAction): KeyboardOptions = when (type) {
    FieldType.Number -> KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ime)
    FieldType.Phone -> KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ime)
    FieldType.Email -> KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ime)
    FieldType.Url -> KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ime)
    FieldType.Password -> KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ime)
    FieldType.Multiline -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default)
    else -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ime)
}

@Composable
private fun Field(
    state: FormState,
    spec: FieldSpec,
    isLastText: Boolean,
    enabled: Boolean,
    onPickImage: (String) -> Unit,
    imagePreview: (@Composable (String) -> Unit)?,
    onDone: () -> Unit,
    onLink: (String, String) -> Unit,
) {
    val label = stringResource(spec.label)
    val error = state.visibleError(spec.key)?.message()
    val tag = Modifier.testTag(fieldTestTag(spec.key))
    when (spec.type) {
        FieldType.Text, FieldType.Multiline, FieldType.Number, FieldType.Phone, FieldType.Email, FieldType.Url, FieldType.Password -> {
            val focusManager = LocalFocusManager.current
            val password = spec.type == FieldType.Password
            var revealed by rememberSaveable(spec.key) { mutableStateOf(false) }
            val ime = if (isLastText) ImeAction.Done else ImeAction.Next
            val value = state.text(spec.key)
            val multiline = spec.type == FieldType.Multiline
            if (spec.suggestions.isNotEmpty() && spec.type == FieldType.Text) {
                SuggestionTextField(state, spec, label, error, enabled, ime, tag, onDone)
                return
            }
            AppTextField(
                value = value,
                onValueChange = { input ->
                    state.set(spec.key, if (spec.type == FieldType.Number) input.filter { it.isDigit() || it == '.' || it == '-' } else input)
                },
                label = label,
                error = error,
                counter = if (multiline && spec.maxLength != null) {
                    stringResource(Res.string.form_character_count, value.trim().length, spec.maxLength)
                } else {
                    null
                },
                placeholder = spec.defaultText?.let { stringResource(it) },
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                enabled = enabled,
                tabular = spec.type == FieldType.Number || spec.type == FieldType.Phone,
                keyboardOptions = keyboardFor(spec.type, ime),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = { focusManager.clearFocus(); onDone() },
                ),
                onFocusLost = { state.onFocusLost(spec.key) },
                inputModifier = tag,
                visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = if (password) {
                    { PasswordToggle(revealed, onToggle = { revealed = !revealed }) }
                } else {
                    null
                },
            )
        }
        FieldType.Money -> {
            val focusManager = LocalFocusManager.current
            MoneyField(
                value = state.text(spec.key),
                onValueChange = { state.set(spec.key, it) },
                label = label,
                error = error,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (isLastText) ImeAction.Done else ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = { focusManager.clearFocus(); onDone() },
                ),
                onFocusLost = { state.onFocusLost(spec.key) },
                inputModifier = tag,
            )
        }
        FieldType.Select -> if (spec.segmented) {
            FieldFrame(label, tag, error) {
                SegmentedControl(
                    options = spec.options.map { SegmentOption(it.key, stringResource(it.label)) },
                    selectedKey = state.text(spec.key),
                    onSelect = { if (enabled) { state.set(spec.key, it); state.onFocusLost(spec.key) } },
                )
            }
        } else {
            SelectField(
                options = spec.options.map { ChoiceOption(it.key, stringResource(it.label), it.icon) },
                selectedKey = state.text(spec.key).takeIf { it.isNotEmpty() },
                onSelect = { state.set(spec.key, it); state.onFocusLost(spec.key) },
                label = label,
                modifier = tag,
                error = error,
                enabled = enabled,
            )
        }
        FieldType.Image -> ImagePickerSlot(
            label = label,
            hasImage = state.text(spec.key).isNotBlank(),
            onPick = { onPickImage(spec.key) },
            onRemove = { state.set(spec.key, "") },
            modifier = tag,
            error = error,
            enabled = enabled,
            preview = imagePreview?.let { preview -> { preview(state.text(spec.key)) } },
        )
        FieldType.MultiSelect -> FieldFrame(label, tag, error) {
            @Suppress("UNCHECKED_CAST")
            val selected = (state.value(spec.key) as? List<String>).orEmpty()
            ChoiceChips(
                options = spec.options.map { ChoiceOption(it.key, stringResource(it.label), it.icon) },
                selected = selected.toSet(),
                onToggle = { key ->
                    state.set(spec.key, if (key in selected) selected - key else selected + key)
                    state.onFocusLost(spec.key)
                },
            )
        }
        FieldType.Toggle -> ToggleRow(
            label = label,
            checked = state.value(spec.key) as? Boolean ?: false,
            onCheckedChange = { state.set(spec.key, it) },
            modifier = tag,
            enabled = enabled,
        )
        FieldType.Consent -> ConsentCheckbox(
            text = label,
            checked = state.value(spec.key) as? Boolean ?: false,
            onCheckedChange = { state.set(spec.key, it); state.onFocusLost(spec.key) },
            links = spec.links.map { link -> ConsentLink(stringResource(link.label)) { onLink(spec.key, link.key) } },
            modifier = tag,
            error = error,
            enabled = enabled,
        )
        FieldType.Swatch -> FieldFrame(label, tag, error) {
            SwatchPicker(
                swatches = AccentSwatch.active.map { SwatchOption(it.key, it.label(), it.color()) },
                selectedKey = state.text(spec.key).takeIf { it.isNotEmpty() },
                onSelect = { state.set(spec.key, it) },
            )
        }
        FieldType.Date -> DateField(
            value = state.value(spec.key) as? LocalDate,
            onValueChange = { state.set(spec.key, it); state.onFocusLost(spec.key) },
            label = label,
            modifier = tag,
            error = error,
            enabled = enabled,
        )
        FieldType.ItemList -> ItemListField(state, spec, label, error, enabled, onPickImage, imagePreview, tag)
    }
}

@Composable
private fun ItemListField(
    state: FormState,
    spec: FieldSpec,
    label: String,
    error: String?,
    enabled: Boolean,
    onPickImage: (String) -> Unit,
    imagePreview: (@Composable (String) -> Unit)?,
    modifier: Modifier,
) {
    val items = state.items(spec.key)
    FieldFrame(label, modifier, error) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            items.forEachIndexed { index, item ->
                if (index > 0) HairlineDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.form_item_title, index + 1),
                        style = AgentProTheme.text.strong,
                        modifier = Modifier.weight(1f),
                    )
                    TertiaryButton(stringResource(Res.string.action_remove), onClick = { state.removeItem(spec.key, index) }, enabled = enabled)
                }
                FormRenderer(
                    state = item,
                    enabled = enabled,
                    onPickImage = { key -> onPickImage("${spec.key}.$index.$key") },
                    imagePreview = imagePreview,
                )
            }
            if (spec.maxItems == null || items.size < spec.maxItems) {
                TertiaryButton(
                    stringResource(Res.string.action_add),
                    onClick = { state.addItem(spec.key) },
                    enabled = enabled,
                    icon = Res.drawable.ic_add,
                )
            }
        }
    }
}

/** At most this many autocomplete hints show under a text field with `suggestions`. */
private const val MAX_SUGGESTIONS = 5

/**
 * The hints of [spec] that match [input] (case-insensitive, prefix matches first), excluding an exact
 * match. Empty input shows none.
 */
fun matchingSuggestions(spec: FieldSpec, input: String): List<String> {
    val q = input.trim()
    if (q.isEmpty()) return emptyList()
    val hits = spec.suggestions.filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }
    return hits.sortedBy { if (it.startsWith(q, ignoreCase = true)) 0 else 1 }.take(MAX_SUGGESTIONS)
}

/** A text field with autocomplete hints in a dropdown (app forms only, e.g. the policy plan name). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionTextField(
    state: FormState,
    spec: FieldSpec,
    label: String,
    error: String?,
    enabled: Boolean,
    ime: ImeAction,
    tag: Modifier,
    onDone: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var open by remember { mutableStateOf(false) }
    val value = state.text(spec.key)
    val hints = matchingSuggestions(spec, value)
    ExposedDropdownMenuBox(expanded = open && hints.isNotEmpty(), onExpandedChange = { open = it }) {
        AppTextField(
            value = value,
            onValueChange = { state.set(spec.key, it); open = true },
            label = label,
            error = error,
            enabled = enabled,
            keyboardOptions = keyboardFor(spec.type, ime),
            keyboardActions = KeyboardActions(
                onNext = { open = false; focusManager.moveFocus(FocusDirection.Down) },
                onDone = { open = false; focusManager.clearFocus(); onDone() },
            ),
            onFocusLost = { open = false; state.onFocusLost(spec.key) },
            inputModifier = tag.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = open && hints.isNotEmpty(),
            onDismissRequest = { open = false },
            containerColor = AgentProTheme.colors.elevatedSurface,
        ) {
            hints.forEach { hint ->
                DropdownMenuItem(
                    text = { Text(hint, style = AgentProTheme.text.body) },
                    onClick = { state.set(spec.key, hint); open = false },
                )
            }
        }
    }
}

/** The show/hide toggle of a password field (every icon button has a content description). */
@Composable
private fun PasswordToggle(revealed: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            painterResource(if (revealed) Res.drawable.ic_visibility_off else Res.drawable.ic_visibility),
            contentDescription = stringResource(if (revealed) Res.string.form_hide_password else Res.string.form_show_password),
            modifier = Modifier.size(Dimens.icon),
        )
    }
}
