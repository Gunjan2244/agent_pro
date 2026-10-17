package com.sledje.agentpro.core.forms

import androidx.compose.runtime.Composable
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.validation_email
import com.sledje.agentpro.resources.validation_max
import com.sledje.agentpro.resources.validation_max_items
import com.sledje.agentpro.resources.validation_max_length
import com.sledje.agentpro.resources.validation_min
import com.sledje.agentpro.resources.validation_min_length
import com.sledje.agentpro.resources.validation_mismatch
import com.sledje.agentpro.resources.validation_number
import com.sledje.agentpro.resources.validation_option
import com.sledje.agentpro.resources.validation_phone
import com.sledje.agentpro.resources.validation_range
import com.sledje.agentpro.resources.validation_required
import com.sledje.agentpro.resources.validation_slug
import com.sledje.agentpro.resources.validation_slug_reserved
import com.sledje.agentpro.resources.validation_url
import org.jetbrains.compose.resources.stringResource

/** The one-line, translated message for a validation error. */
@Composable
fun ValidationError.message(): String = when (this) {
    ValidationError.Required -> stringResource(Res.string.validation_required)
    is ValidationError.MaxLength -> stringResource(Res.string.validation_max_length, max)
    is ValidationError.MinLength -> stringResource(Res.string.validation_min_length, min)
    ValidationError.NotANumber -> stringResource(Res.string.validation_number)
    is ValidationError.Range -> when {
        min != null && max != null -> stringResource(Res.string.validation_range, min.toString(), max.toString())
        min != null -> stringResource(Res.string.validation_min, min.toString())
        else -> stringResource(Res.string.validation_max, max.toString())
    }
    ValidationError.Phone -> stringResource(Res.string.validation_phone)
    ValidationError.Email -> stringResource(Res.string.validation_email)
    ValidationError.Url -> stringResource(Res.string.validation_url)
    ValidationError.Slug -> stringResource(Res.string.validation_slug)
    ValidationError.SlugReserved -> stringResource(Res.string.validation_slug_reserved)
    is ValidationError.MaxItems -> stringResource(Res.string.validation_max_items, max)
    ValidationError.Option -> stringResource(Res.string.validation_option)
    ValidationError.Mismatch -> stringResource(Res.string.validation_mismatch)
}
