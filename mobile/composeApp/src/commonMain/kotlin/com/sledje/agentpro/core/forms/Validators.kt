package com.sledje.agentpro.core.forms

import com.sledje.agentpro.core.catalog.ReservedSlugs
import com.sledje.agentpro.core.format.Phone

/** Why a value is invalid. Mapped to translated copy by `ValidationError.message()` (ValidationMessages.kt). */
sealed interface ValidationError {
    data object Required : ValidationError
    data class MaxLength(val max: Int) : ValidationError
    data class MinLength(val min: Int) : ValidationError
    data object NotANumber : ValidationError
    data class Range(val min: Long?, val max: Long?) : ValidationError
    data object Phone : ValidationError
    data object Email : ValidationError
    data object Url : ValidationError
    data object Slug : ValidationError
    data object SlugReserved : ValidationError
    data class MaxItems(val max: Int) : ValidationError
    data object Option : ValidationError
}

/**
 * Field validation, matching the server's publish validation and `submit_enquiry` limits
 * (docs/BACKEND.md): required, maxLength, min/max, phone (Indian mobile, +91), email, url
 * (http(s)://…), options, maxItems, and the site slug rule.
 */
object Validators {
    private val emailRegex = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    private val urlRegex = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)
    private val slugRegex = Regex("""^[a-z0-9-]{3,30}$""")
    private val numberRegex = Regex("""^-?\d+(\.\d+)?$""")
    private val reservedSlugs: Set<String> by lazy { ReservedSlugs.all.toSet() }

    /** Missing, null, a blank string or an empty collection. */
    fun isBlank(value: Any?): Boolean = when (value) {
        null -> true
        is String -> value.isBlank()
        is Collection<*> -> value.isEmpty()
        else -> false
    }

    fun required(value: Any?): ValidationError? = if (isBlank(value)) ValidationError.Required else null

    fun maxLength(value: String, max: Int): ValidationError? =
        if (value.trim().length > max) ValidationError.MaxLength(max) else null

    /** A number within [min]..[max] (either bound may be absent). */
    fun number(value: String, min: Long? = null, max: Long? = null): ValidationError? {
        val v = value.trim()
        if (!numberRegex.matches(v)) return ValidationError.NotANumber
        val n = v.toDouble()
        if ((min != null && n < min) || (max != null && n > max)) return ValidationError.Range(min, max)
        return null
    }

    /** A ₹ amount ("1,25,000.50"; at most 2 decimals) within [min]..[max]. */
    fun money(value: String, min: Long? = null, max: Long? = null): ValidationError? {
        val n = com.sledje.agentpro.core.format.Money.parse(value) ?: return ValidationError.NotANumber
        if ((min != null && n < min) || (max != null && n > max)) return ValidationError.Range(min, max)
        return null
    }

    fun phone(value: String): ValidationError? = if (Phone.isValid(value)) null else ValidationError.Phone

    fun email(value: String): ValidationError? = if (emailRegex.matches(value.trim())) null else ValidationError.Email

    fun url(value: String): ValidationError? = if (urlRegex.matches(value.trim())) null else ValidationError.Url

    /** `^[a-z0-9-]{3,30}$` and not one of the catalog's reserved slugs. */
    fun slug(value: String, reserved: Set<String> = reservedSlugs): ValidationError? = when {
        !slugRegex.matches(value) -> ValidationError.Slug
        value in reserved -> ValidationError.SlugReserved
        else -> null
    }

    /**
     * The first failure of [value] against [spec], or null when it's valid. Optional blank values are
     * valid. Value types: String for text-like, number, select, swatch, image (URL) and phone fields;
     * Boolean for toggle; List<String> for multiSelect; LocalDate for date; a List for item lists.
     */
    fun validate(spec: FieldSpec, value: Any?): ValidationError? {
        // A required consent is valid only when ticked.
        if (spec.type == FieldType.Consent) return if (spec.required && value != true) ValidationError.Required else null
        if (isBlank(value)) return if (spec.required) ValidationError.Required else null
        val text = value as? String
        spec.maxLength?.let { max -> if (text != null) maxLength(text, max)?.let { return it } }
        spec.minLength?.let { min -> if (text != null && text.length < min) return ValidationError.MinLength(min) }
        when (spec.type) {
            FieldType.Number -> text?.let { number(it, spec.min, spec.max) }?.let { return it }
            FieldType.Money -> text?.let { money(it, spec.min, spec.max) }?.let { return it }
            FieldType.Phone -> text?.let { phone(it) }?.let { return it }
            FieldType.Email -> text?.let { email(it) }?.let { return it }
            FieldType.Url -> text?.let { url(it) }?.let { return it }
            FieldType.Select -> if (spec.options.isNotEmpty() && spec.options.none { it.key == text }) return ValidationError.Option
            FieldType.MultiSelect -> {
                val keys = (value as? Collection<*>).orEmpty()
                if (spec.options.isNotEmpty() && keys.any { k -> spec.options.none { it.key == k } }) return ValidationError.Option
            }
            else -> Unit
        }
        val size = (value as? Collection<*>)?.size
        spec.maxItems?.let { max -> if (size != null && size > max) return ValidationError.MaxItems(max) }
        return null
    }
}
