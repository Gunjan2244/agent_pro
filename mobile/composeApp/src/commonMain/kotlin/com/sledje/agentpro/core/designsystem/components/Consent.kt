package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing

/** A tappable part of a consent line: the [label] substring of the text and what it opens. */
data class ConsentLink(val label: String, val onClick: () -> Unit)

/**
 * An unticked-by-default consent checkbox with a line of text whose [links] (substrings such as
 * "Terms" and "Privacy notice") are tappable (docs/PRIVACY.md, Notice and consent). Never pre-ticked.
 * A link whose label isn't found in [text] is simply not linked.
 */
@Composable
fun ConsentCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    links: List<ConsentLink>,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        append(text)
        links.forEach { link ->
            val start = text.indexOf(link.label)
            if (link.label.isNotEmpty() && start >= 0) {
                addLink(
                    LinkAnnotation.Clickable(
                        tag = link.label,
                        styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    ) { link.onClick() },
                    start,
                    start + link.label.length,
                )
            }
        }
    }
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            Text(annotated, style = AgentProTheme.text.body, modifier = Modifier.weight(1f))
        }
        if (error != null) {
            Text(error, style = AgentProTheme.text.bodyMuted, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = Spacing.m))
        }
    }
}
