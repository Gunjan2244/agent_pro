package com.sledje.agentpro.core.forms

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** Field types of shared/site-schema.json (docs/SHARED_DEFINITIONS.md, "Field types"). */
enum class FieldType(val key: String) {
    Text("text"),
    Multiline("multiline"),
    Number("number"),
    Phone("phone"),
    Email("email"),
    Url("url"),
    Image("image"),
    Select("select"),
    MultiSelect("multiSelect"),
    Toggle("toggle"),
    Swatch("swatch"),
    Date("date"),

    /** `list`: a repeatable group of [FieldSpec.itemFields]. */
    ItemList("list"),

    /** App-only (sign-in and sign-up forms): masked with a show/hide toggle. Never a site-schema type. */
    Password("password"),

    /** App-only (policy premium, payment amount): a ₹ amount with Indian grouping (MoneyField). Never a site-schema type. */
    Money("money"),
    ;

    companion object {
        fun fromKey(key: String): FieldType? = entries.firstOrNull { it.key == key }
    }
}

/** One choice of a select / multiSelect field. */
data class FieldOption(
    val key: String,
    val label: StringResource,
    val icon: DrawableResource? = null,
)

/** A field's `default` value from the schema. */
sealed interface FieldDefault {
    data class Bool(val value: Boolean) : FieldDefault
    data class Number(val value: Double) : FieldDefault
    data class Text(val value: String) : FieldDefault
}

/**
 * One form field. The rules are those of the schema ("Field rules"): required, maxLength, min, max,
 * maxItems, options / optionsFrom, default / defaultKey and aspect. Rendered only by FormRenderer.
 */
data class FieldSpec(
    val key: String,
    val type: FieldType,
    val label: StringResource,
    val required: Boolean = false,
    val maxLength: Int? = null,
    /** Minimum length (app-only forms, e.g. the 8-character password rule); not a site-schema rule. */
    val minLength: Int? = null,
    val min: Long? = null,
    val max: Long? = null,
    val maxItems: Int? = null,
    /** Choices for select / multiSelect; for `optionsFrom` fields, the resolved catalog entries. */
    val options: List<FieldOption> = emptyList(),
    /** e.g. "catalog.interests" when [options] came from a catalog list. */
    val optionsFrom: String? = null,
    val default: FieldDefault? = null,
    /** Translated default text (`defaultKey`), e.g. the hero button label. */
    val defaultText: StringResource? = null,
    /** Image crop aspect ratio, e.g. "1:1". */
    val aspect: String? = null,
    /** Fields of each item of an [FieldType.ItemList] field. */
    val itemFields: List<FieldSpec> = emptyList(),
    /** App forms only: autocomplete hints for a text field (e.g. `PlanSuggestions.all`); any text is still allowed. */
    val suggestions: List<String> = emptyList(),
    /** App forms only: show a select as a segmented control (short option lists, e.g. the premium mode). */
    val segmented: Boolean = false,
)

/** A website section of shared/site-schema.json. */
data class SectionSpec(
    val key: String,
    val label: StringResource,
    val icon: DrawableResource,
    val pinned: Boolean = false,
    val defaultVisible: Boolean = true,
    val fields: List<FieldSpec>,
)
