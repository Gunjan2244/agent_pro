package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.sledje.agentpro.core.catalog.CatalogTonedEntry
import com.sledje.agentpro.core.catalog.DueBucket
import com.sledje.agentpro.core.catalog.Tone
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.rememberMonthNames
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.urgency_due_in
import com.sledje.agentpro.resources.urgency_due_on
import com.sledje.agentpro.resources.urgency_due_today
import com.sledje.agentpro.resources.urgency_overdue
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A fully rounded, tonal status pill. A tone is never colour alone: it always has an icon and a
 * text label (docs/DESIGN_SYSTEM.md, Semantic tones).
 */
@Composable
fun StatusPill(tone: Tone, icon: DrawableResource, label: String, modifier: Modifier = Modifier) {
    val colors = AgentProTheme.tone(tone)
    Row(
        modifier
            .heightIn(min = Dimens.pillHeight)
            .background(colors.container, AppShapes.full)
            .padding(horizontal = Spacing.s)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.text, modifier = Modifier.size(Dimens.iconSmall))
        Spacer(Modifier.width(Spacing.s))
        Text(label, style = AgentProTheme.text.label, color = colors.text)
    }
}

/** A pill for a catalog status (enquiry, policy, plan or domain status). */
@Composable
fun StatusPill(entry: CatalogTonedEntry, modifier: Modifier = Modifier) {
    StatusPill(entry.tone, entry.icon, entry.label(), modifier)
}

/** The due bucket of a date [days] away, from catalog `dueBuckets`; later dates use the last bucket. */
fun dueBucketFor(days: Int): DueBucket {
    val buckets = DueBucket.active
    return buckets.firstOrNull { (it.minDays == null || days >= it.minDays!!) && days <= it.maxDays }
        ?: buckets.last()
}

/** What an [UrgencyChip] says, before translation. */
sealed interface UrgencyLabel {
    data class Overdue(val days: Int) : UrgencyLabel
    data object Today : UrgencyLabel
    data class DueIn(val days: Int) : UrgencyLabel
    data class DueOn(val date: LocalDate) : UrgencyLabel
}

/**
 * "Overdue 3d" while overdue, "Due today", "Due in 5d" within the first upcoming bucket (this week),
 * then "Due 15 Jan".
 */
fun urgencyLabel(dueDate: LocalDate, today: LocalDate): UrgencyLabel {
    val days = Dates.daysBetween(today, dueDate)
    val bucket = dueBucketFor(days)
    val firstUpcoming = DueBucket.active.firstOrNull { (it.minDays ?: Int.MIN_VALUE) >= 0 }
    return when {
        days < 0 -> UrgencyLabel.Overdue(-days)
        days == 0 -> UrgencyLabel.Today
        bucket == firstUpcoming -> UrgencyLabel.DueIn(days)
        else -> UrgencyLabel.DueOn(dueDate)
    }
}

/**
 * A due date's urgency: works out its own tone, icon and label from the catalog due buckets
 * (overdue = danger, ≤ 7 days = warning, ≤ 30 days = info, later = neutral).
 */
@Composable
fun UrgencyChip(dueDate: LocalDate, modifier: Modifier = Modifier, today: LocalDate = Dates.today()) {
    val bucket = dueBucketFor(Dates.daysBetween(today, dueDate))
    val label = when (val l = urgencyLabel(dueDate, today)) {
        is UrgencyLabel.Overdue -> stringResource(Res.string.urgency_overdue, l.days)
        UrgencyLabel.Today -> stringResource(Res.string.urgency_due_today)
        is UrgencyLabel.DueIn -> stringResource(Res.string.urgency_due_in, l.days)
        is UrgencyLabel.DueOn -> stringResource(Res.string.urgency_due_on, Dates.formatDayMonth(l.date, rememberMonthNames()))
    }
    StatusPill(bucket.tone, bucket.icon, label, modifier)
}
