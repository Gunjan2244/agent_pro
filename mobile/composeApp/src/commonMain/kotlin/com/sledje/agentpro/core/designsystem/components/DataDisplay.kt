package com.sledje.agentpro.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.catalog.Tone
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppChart
import com.sledje.agentpro.core.designsystem.ChartData
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Motion
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.TABULAR_FIGURES
import com.sledje.agentpro.core.format.Money
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.chart_empty
import com.sledje.agentpro.resources.chart_table_period
import com.sledje.agentpro.resources.chart_view_chart
import com.sledje.agentpro.resources.chart_view_table
import com.sledje.agentpro.resources.ic_arrow_downward
import com.sledje.agentpro.resources.ic_arrow_upward
import com.sledje.agentpro.resources.ic_bar_chart
import com.sledje.agentpro.resources.ic_check_circle
import com.sledje.agentpro.resources.ic_error
import com.sledje.agentpro.resources.ic_radio_button_checked
import com.sledje.agentpro.resources.ic_radio_button_unchecked
import com.sledje.agentpro.resources.kpi_delta_down
import com.sledje.agentpro.resources.kpi_delta_up
import com.sledje.agentpro.resources.price_total
import com.sledje.agentpro.resources.stepper_current
import com.sledje.agentpro.resources.stepper_done
import com.sledje.agentpro.resources.stepper_failed
import com.sledje.agentpro.resources.stepper_pending
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** ₹ amount with Indian grouping and tabular figures (₹1,25,000; [compact]: ₹1.2L). */
@Composable
fun MoneyText(
    amount: Double,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    style: TextStyle = AgentProTheme.text.strong,
) {
    Text(
        if (compact) Money.compact(amount) else Money.format(amount),
        modifier = modifier,
        style = style.copy(fontFeatureSettings = TABULAR_FIGURES),
    )
}

/** A KPI change: arrow direction plus muted text such as "12% vs last month". */
data class KpiDelta(val up: Boolean, val text: String)

/** Label (`label`), value (`number`, 23sp) and an optional delta (arrow icon + `bodyMuted`). */
@Composable
fun KpiTile(label: String, value: String, modifier: Modifier = Modifier, delta: KpiDelta? = null) {
    AppCard(modifier) {
        Text(label, style = AgentProTheme.text.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.s))
        Text(value, style = AgentProTheme.text.number)
        if (delta != null) {
            Spacer(Modifier.height(Spacing.s))
            val direction = stringResource(if (delta.up) Res.string.kpi_delta_up else Res.string.kpi_delta_down)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$direction, ${delta.text}" },
            ) {
                Icon(
                    painterResource(if (delta.up) Res.drawable.ic_arrow_upward else Res.drawable.ic_arrow_downward),
                    contentDescription = null,
                    tint = AgentProTheme.colors.muted,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
                Spacer(Modifier.width(Spacing.s))
                Text(delta.text, style = AgentProTheme.text.bodyMuted)
            }
        }
    }
}

/**
 * A chart card: title, one-line takeaway, the chart, a "View as table" toggle and an empty state.
 * The table view reads each row as one item for TalkBack.
 */
@Composable
fun ChartCard(
    title: String,
    takeaway: String,
    data: ChartData,
    modifier: Modifier = Modifier,
    emptyMessage: String = stringResource(Res.string.chart_empty),
) {
    var showTable by rememberSaveable { mutableStateOf(false) }
    AppCard(modifier.fillMaxWidth()) {
        Text(title, style = AgentProTheme.text.strong, modifier = Modifier.semantics { heading() })
        Text(takeaway, style = AgentProTheme.text.bodyMuted)
        Spacer(Modifier.height(Spacing.m))
        if (data.isEmpty) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = Dimens.chartHeight),
                verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(Res.drawable.ic_bar_chart), null, Modifier.size(Dimens.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(emptyMessage, style = AgentProTheme.text.bodyMuted, textAlign = TextAlign.Center)
            }
        } else {
            // Chart ⇄ table crossfades while the card eases to the new height.
            AnimatedContent(
                showTable,
                transitionSpec = { Motion.crossfade() using SizeTransform { _, _ -> Motion.navigation() } },
                label = "chartTable",
            ) { table -> if (table) ChartTable(data) else AppChart(data) }
        }
        if (!data.isEmpty) {
            TertiaryButton(
                stringResource(if (showTable) Res.string.chart_view_chart else Res.string.chart_view_table),
                onClick = { showTable = !showTable },
            )
        }
    }
}

@Composable
private fun ChartTable(data: ChartData) {
    Column(Modifier.fillMaxWidth()) {
        TableRow(listOf(stringResource(Res.string.chart_table_period)) + data.series.map { it.name }, header = true)
        data.xLabels.forEachIndexed { i, x ->
            HairlineDivider()
            TableRow(listOf(x) + data.series.map { s -> s.values.getOrNull(i)?.let(data::formatValue).orEmpty() }, header = false)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean) {
    val rowDescription = cells.joinToString(", ")
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s).semantics(mergeDescendants = true) {
            contentDescription = rowDescription
        },
    ) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                style = if (header) AgentProTheme.text.label else AgentProTheme.text.body.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One line of a [PriceSummary]. */
data class PriceLine(val label: String, val amount: Double)

/** Checkout summary: line items, the total "incl. GST" and the renewal note. */
@Composable
fun PriceSummary(lines: List<PriceLine>, total: Double, modifier: Modifier = Modifier, renewalNote: String? = null) {
    Column(modifier.fillMaxWidth()) {
        lines.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Text(line.label, style = AgentProTheme.text.body, modifier = Modifier.weight(1f))
                MoneyText(line.amount, style = AgentProTheme.text.body)
            }
        }
        HairlineDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.price_total), style = AgentProTheme.text.strong, modifier = Modifier.weight(1f))
            MoneyText(total, style = AgentProTheme.text.number)
        }
        if (renewalNote != null) Text(renewalNote, style = AgentProTheme.text.bodyMuted)
    }
}

enum class StepStatus { Done, Current, Pending, Failed }

data class Step(val label: String, val status: StepStatus, val supporting: String? = null)

/** Vertical steps with status (domain setup). Status colours are tones: done = success, current = info, failed = danger. */
@Composable
fun ProgressStepper(steps: List<Step>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            val (icon, tint) = when (step.status) {
                StepStatus.Done -> Res.drawable.ic_check_circle to AgentProTheme.tone(Tone.Success).text
                StepStatus.Current -> Res.drawable.ic_radio_button_checked to AgentProTheme.tone(Tone.Info).text
                StepStatus.Pending -> Res.drawable.ic_radio_button_unchecked to AgentProTheme.colors.muted
                StepStatus.Failed -> Res.drawable.ic_error to AgentProTheme.tone(Tone.Danger).text
            }
            val statusText = stringResource(
                when (step.status) {
                    StepStatus.Done -> Res.string.stepper_done
                    StepStatus.Current -> Res.string.stepper_current
                    StepStatus.Pending -> Res.string.stepper_pending
                    StepStatus.Failed -> Res.string.stepper_failed
                },
            )
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(step.label, statusText, step.supporting).joinToString(", ")
                },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(icon), null, Modifier.size(Dimens.icon), tint = tint)
                    if (index < steps.lastIndex) {
                        Box(
                            Modifier.width(Dimens.stepperConnector).weight(1f).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f).padding(bottom = Spacing.m)) {
                    Text(
                        step.label,
                        style = if (step.status == StepStatus.Current) AgentProTheme.text.strong else AgentProTheme.text.body,
                        color = if (step.status == StepStatus.Pending) AgentProTheme.colors.muted else LocalContentColor.current,
                    )
                    if (step.supporting != null) Text(step.supporting, style = AgentProTheme.text.bodyMuted)
                }
            }
        }
    }
}
