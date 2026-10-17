package com.sledje.agentpro.feature.privacy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.ui.LocalIsOnline
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One block of a notice, in display order. */
sealed interface NoticeBlock {
    data class Title(val text: StringResource) : NoticeBlock
    data class Paragraph(val text: StringResource) : NoticeBlock

    /** An itemised row: what is kept, why, and for how long. */
    data class Item(val data: StringResource, val purpose: StringResource, val kept: StringResource) : NoticeBlock
}

/** The column labels of an itemised notice (the notice's own `cat_privacy_notice_col_*` strings). */
data class NoticeColumns(val data: StringResource, val purpose: StringResource, val kept: StringResource)

/** A notice laid out for reading. */
data class NoticeLayout(val blocks: List<NoticeBlock>, val columns: NoticeColumns?)

/**
 * Lays out [notice]'s ordered texts (generated; never retyped): the title is the heading, the `_data` /
 * `_purpose` / `_kept` triples of the same item become one itemised row, the consent line, its link labels
 * and the column headers are not body text, and everything else is a paragraph in the generated order.
 * Keyed on the generated resource keys (`cat_privacy_<notice>_<part>`), so a new part shows without code.
 */
fun layoutNotice(notice: PrivacyNotice): NoticeLayout {
    val prefix = "cat_privacy_${notice.key}_"
    val byKey = notice.texts.associateBy { it.key }
    fun part(r: StringResource) = r.key.removePrefix(prefix)
    val blocks = mutableListOf<NoticeBlock>()
    val done = mutableSetOf<String>()
    for (text in notice.texts) {
        val key = text.key
        if (key in done || key.startsWith("cat_privacy_notice_col_")) continue
        val part = part(text)
        when {
            part == "title" -> blocks += NoticeBlock.Title(text)
            part == "consent" || part.startsWith("link") || part == "action" -> Unit
            part.endsWith("_data") -> {
                val stem = prefix + part.removeSuffix("_data")
                val purpose = byKey[stem + "_purpose"]
                val kept = byKey[stem + "_kept"]
                if (purpose != null && kept != null) {
                    blocks += NoticeBlock.Item(text, purpose, kept)
                    done += purpose.key
                    done += kept.key
                } else {
                    blocks += NoticeBlock.Paragraph(text)
                }
            }
            else -> blocks += NoticeBlock.Paragraph(text)
        }
    }
    val columns = notice.texts.let { all ->
        val data = all.firstOrNull { it.key == "cat_privacy_notice_col_data" }
        val purpose = all.firstOrNull { it.key == "cat_privacy_notice_col_purpose" }
        val kept = all.firstOrNull { it.key == "cat_privacy_notice_col_kept" }
        if (data != null && purpose != null && kept != null) NoticeColumns(data, purpose, kept) else null
    }
    return NoticeLayout(blocks, columns)
}

/**
 * The in-app notice (docs/PRIVACY.md, section 1; docs/UX.md, Privacy notice): the heading, the intro, an
 * itemised list (data, purpose, kept) and the plain paragraphs, in the app language. Opened from Create
 * account and from Settings.
 */
@Composable
fun NoticeScreen(notice: PrivacyNotice, onBack: () -> Unit) {
    val layout = layoutNotice(notice)
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(notice.label), onBack = onBack) },
        offline = !LocalIsOnline.current,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            layout.blocks.forEach { block ->
                when (block) {
                    is NoticeBlock.Title -> Text(
                        stringResource(block.text),
                        style = AgentProTheme.text.title,
                        modifier = Modifier.semantics { heading() },
                    )
                    is NoticeBlock.Paragraph -> Text(stringResource(block.text), style = AgentProTheme.text.body)
                    is NoticeBlock.Item -> NoticeItem(block, layout.columns)
                }
            }
        }
    }
}

@Composable
private fun NoticeItem(item: NoticeBlock.Item, columns: NoticeColumns?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        HairlineDivider()
        Text(stringResource(item.data), style = AgentProTheme.text.strong)
        NoticeDetail(columns?.purpose, item.purpose)
        NoticeDetail(columns?.kept, item.kept)
    }
}

@Composable
private fun NoticeDetail(label: StringResource?, value: StringResource) {
    Column {
        if (label != null) Text(stringResource(label), style = AgentProTheme.text.bodyMuted)
        Text(stringResource(value), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurface)
    }
}
