package com.sledje.agentpro.core.forms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.sledje.agentpro.core.format.Phone

/**
 * The state of a form built by [FormRenderer]: field values, which errors are showing, and list items.
 *
 * Value types (also accepted in `initial`): String for text, multiline, number, phone, email, url,
 * image (URL), select and swatch; Boolean for toggle; List<String> for multiSelect; LocalDate for
 * date; a List of Maps for item lists. [values] returns numbers as Long/Double and blank text as null.
 *
 * Errors show after a field loses focus ([onFocusLost]) or after [validateAll].
 */
@Stable
class FormState(val fields: List<FieldSpec>, initial: Map<String, Any?> = emptyMap()) {
    private val current = mutableStateMapOf<String, Any?>()
    private val shown = mutableStateMapOf<String, Boolean>()
    private val lists = mutableMapOf<String, SnapshotStateList<FormState>>()
    private val initialValues: Map<String, Any?>

    init {
        fields.forEach { spec ->
            if (spec.type == FieldType.ItemList) {
                val rows = (initial[spec.key] as? List<*>).orEmpty().map { row ->
                    @Suppress("UNCHECKED_CAST")
                    FormState(spec.itemFields, (row as? Map<String, Any?>).orEmpty())
                }
                lists[spec.key] = mutableStateListOf<FormState>().apply { addAll(rows) }
            } else {
                current[spec.key] = initial[spec.key] ?: defaultValue(spec)
            }
        }
        initialValues = values()
    }

    fun spec(key: String): FieldSpec = fields.first { it.key == key }

    fun value(key: String): Any? = current[key]

    fun text(key: String): String = current[key] as? String ?: ""

    fun set(key: String, value: Any?) {
        current[key] = value
    }

    /** Items of an item-list field. */
    fun items(key: String): List<FormState> = lists[key].orEmpty()

    fun addItem(key: String) {
        val spec = spec(key)
        val list = lists.getValue(key)
        if (spec.maxItems == null || list.size < spec.maxItems) list.add(FormState(spec.itemFields))
    }

    fun removeItem(key: String, index: Int) {
        lists[key]?.removeAt(index)
    }

    /** The field's error, whether or not it's showing yet. */
    fun error(key: String): ValidationError? {
        val spec = spec(key)
        val value = if (spec.type == FieldType.ItemList) items(key) else current[key]
        return Validators.validate(spec, value)
    }

    /** The error to display: only once the field was left or the form was submitted. */
    fun visibleError(key: String): ValidationError? = if (shown[key] == true) error(key) else null

    /** Call when a field loses focus: shows its error, and normalises a valid phone to +91. */
    fun onFocusLost(key: String) {
        val spec = spec(key)
        if (spec.type == FieldType.Phone) Phone.normalize(text(key))?.let { current[key] = it }
        shown[key] = true
    }

    val isValid: Boolean
        get() = fields.all { error(it.key) == null } && lists.values.all { rows -> rows.all { it.isValid } }

    /** Shows every error (including list items) and returns whether the form is valid. */
    fun validateAll(): Boolean {
        fields.forEach { spec ->
            if (spec.type == FieldType.Phone) Phone.normalize(text(spec.key))?.let { current[spec.key] = it }
            shown[spec.key] = true
        }
        lists.values.forEach { rows -> rows.forEach { it.validateAll() } }
        return isValid
    }

    /** Typed values for saving: numbers as Long/Double, blank text as null, item lists as List<Map>. */
    fun values(): Map<String, Any?> = fields.associate { spec ->
        spec.key to when (spec.type) {
            FieldType.ItemList -> items(spec.key).map { it.values() }
            FieldType.Number -> text(spec.key).trim().takeIf { it.isNotEmpty() }?.let { it.toLongOrNull() ?: it.toDoubleOrNull() }
            FieldType.Money -> text(spec.key).trim().takeIf { it.isNotEmpty() }?.let { com.sledje.agentpro.core.format.Money.parse(it) }
            // Passwords are never trimmed: spaces are part of the password.
            FieldType.Password -> text(spec.key).takeIf { it.isNotEmpty() }
            else -> when (val v = current[spec.key]) {
                is String -> v.trim().takeIf { it.isNotEmpty() }
                else -> v
            }
        }
    }

    /** True when any value differs from the initial one ("Discard changes?"). */
    val isDirty: Boolean get() = values() != initialValues

    private companion object {
        fun defaultValue(spec: FieldSpec): Any? = when (val d = spec.default) {
            is FieldDefault.Bool -> d.value
            is FieldDefault.Text -> d.value
            is FieldDefault.Number -> if (d.value % 1.0 == 0.0) d.value.toLong().toString() else d.value.toString()
            null -> when (spec.type) {
                FieldType.Toggle -> false
                FieldType.MultiSelect -> emptyList<String>()
                FieldType.Date -> null
                else -> ""
            }
        }
    }
}

@Composable
fun rememberFormState(fields: List<FieldSpec>, initial: Map<String, Any?> = emptyMap()): FormState =
    remember(fields) { FormState(fields, initial) }
