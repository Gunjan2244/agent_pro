package com.sledje.agentpro.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.CartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.Insets
import com.patrykandpatrick.vico.multiplatform.common.LegendItem
import com.patrykandpatrick.vico.multiplatform.common.component.LineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import com.patrykandpatrick.vico.multiplatform.common.data.ExtraStore
import com.patrykandpatrick.vico.multiplatform.common.rememberHorizontalLegend
import com.sledje.agentpro.core.format.Money
import kotlin.math.roundToLong

/**
 * Chart styling, implemented once (docs/DESIGN_SYSTEM.md, Charts; DECISIONS #10). Wraps Vico:
 * - single series: chart blue, no legend (the card title names the chart);
 * - two series: blue + orange, a legend, and a direct label of the latest values;
 * - ordinal stages (pipeline): the blue ramp, one colour per stage;
 * - columns with 4dp rounded tops and a 2dp gap; 2dp lines with 8dp markers;
 * - horizontal gridlines only, in outlineVariant; muted axis text;
 * - values as compact ₹ when [ChartData.money]; tapping a mark shows a tooltip;
 * - never dual axes, never status tones.
 */
enum class ChartKind { Column, Line, Ordinal }

/** One named series; [values] align with [ChartData.xLabels]. */
@Immutable
data class ChartSeries(val name: String, val values: List<Double>)

@Immutable
data class ChartData(
    val kind: ChartKind,
    val xLabels: List<String>,
    val series: List<ChartSeries>,
    /** Format values as compact ₹ (₹1.2L) instead of counts. */
    val money: Boolean = false,
) {
    init {
        require(series.size <= 2) { "At most 2 series per chart (docs/DESIGN_SYSTEM.md, Charts)" }
        require(kind != ChartKind.Ordinal || series.size == 1) { "Ordinal charts have one series" }
    }

    val isEmpty: Boolean get() = xLabels.isEmpty() || series.isEmpty() || series.all { s -> s.values.all { it == 0.0 } }

    /** How a value reads in axis labels, tooltips and the table. */
    fun formatValue(value: Double): String = if (money) Money.compact(value) else value.roundToLong().toString()
}

/** Per-stage colours for [ChartKind.Ordinal], spread across the ramp. */
private data class OrdinalColumns(val columns: List<LineComponent>, val stageCount: Int) : ColumnCartesianLayer.ColumnProvider {
    override fun getColumn(entry: ColumnCartesianLayerModel.Entry, seriesIndex: Int, extraStore: ExtraStore): LineComponent {
        val i = entry.x.toInt()
        val rampIndex = if (stageCount <= 1) columns.lastIndex else (i * columns.lastIndex + (stageCount - 1) / 2) / (stageCount - 1)
        return columns[rampIndex.coerceIn(columns.indices)]
    }

    override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore): LineComponent = columns.last()
}

/** Tooltip text: each target's values, formatted like the axis. */
private class MarkerValues(private val data: ChartData) : DefaultCartesianMarker.ValueFormatter {
    override fun format(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>,
    ): CharSequence = targets.flatMap { target ->
        when (target) {
            is ColumnCartesianLayerMarkerTarget -> target.columns.map { it.entry.y }
            is LineCartesianLayerMarkerTarget -> target.points.map { it.entry.y }
            else -> emptyList()
        }
    }.joinToString(" · ") { data.formatValue(it) }

    override fun equals(other: Any?): Boolean = other is MarkerValues && other.data == data
    override fun hashCode(): Int = data.hashCode()
}

/** Renders [data] with the app's chart style. Wrap it in a `ChartCard`. */
@Composable
fun AppChart(data: ChartData, modifier: Modifier = Modifier) {
    val colors = AgentProTheme.colors
    val text = AgentProTheme.text
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val seriesColors: List<Color> = colors.chartSeries

    val valueFormatter = remember(data) { CartesianValueFormatter { _, value, _ -> data.formatValue(value) } }
    val xFormatter = remember(data.xLabels) {
        CartesianValueFormatter { _, x, _ -> data.xLabels.getOrNull(x.roundToLong().toInt()) ?: " " }
    }
    val axisLabel = rememberAxisLabelComponent(style = text.bodyMuted)
    val gridline = rememberLineComponent(fill = Fill(outlineVariant), thickness = Dimens.hairline)
    val baseline = rememberLineComponent(fill = Fill(outlineVariant), thickness = Dimens.hairline)
    val startAxis = VerticalAxis.rememberStart(
        line = null,
        label = axisLabel,
        valueFormatter = valueFormatter,
        tick = null,
        guideline = gridline,
    )
    val bottomAxis = HorizontalAxis.rememberBottom(
        line = baseline,
        label = axisLabel,
        valueFormatter = xFormatter,
        tick = null,
        guideline = null,
    )

    val columnShape = RoundedCornerShape(topStart = Dimens.chartColumnRadius, topEnd = Dimens.chartColumnRadius)
    val layer: CartesianLayer<*> = when (data.kind) {
        ChartKind.Column -> rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                seriesColors.map { rememberLineComponent(Fill(it), Dimens.chartColumnWidth, columnShape) },
            ),
            mergeMode = { ColumnCartesianLayer.MergeMode.Grouped(columnSpacing = Dimens.chartColumnGap) },
        )
        ChartKind.Ordinal -> {
            val ramp = colors.chartOrdinalRamp.map { rememberLineComponent(Fill(it), Dimens.chartColumnWidth, columnShape) }
            rememberColumnCartesianLayer(columnProvider = remember(ramp, data.xLabels.size) { OrdinalColumns(ramp, data.xLabels.size) })
        }
        ChartKind.Line -> rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                seriesColors.map { color ->
                    LineCartesianLayer.rememberLine(
                        fill = remember(color) { LineCartesianLayer.LineFill.single(Fill(color)) },
                        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = Dimens.chartLineThickness),
                        pointProvider = LineCartesianLayer.PointProvider.single(
                            LineCartesianLayer.Point(rememberShapeComponent(Fill(color), CircleShape), Dimens.chartPoint),
                        ),
                    )
                },
            ),
        )
    }

    val tooltipBackground = rememberShapeComponent(
        fill = Fill(colors.elevatedSurface),
        shape = AppShapes.sm,
        strokeFill = Fill(outlineVariant),
        strokeThickness = Dimens.hairline,
    )
    val tooltipLabel = rememberTextComponent(
        style = text.label.copy(color = MaterialTheme.colorScheme.onSurface),
        padding = Insets(Spacing.s, Spacing.s, Spacing.s, Spacing.s),
        background = tooltipBackground,
    )
    val marker = rememberDefaultCartesianMarker(label = tooltipLabel, valueFormatter = remember(data) { MarkerValues(data) })

    val twoSeries = data.series.size == 2
    val legendLabel = rememberTextComponent(style = text.bodyMuted)
    val legendIcons = seriesColors.map { rememberShapeComponent(Fill(it), CircleShape) }
    val legend = if (twoSeries) {
        rememberHorizontalLegend<CartesianMeasuringContext, CartesianDrawingContext>(
            items = { _ -> data.series.forEachIndexed { i, s -> add(LegendItem(legendIcons[i], legendLabel, s.name)) } },
            padding = Insets(top = Spacing.s),
        )
    } else {
        null
    }

    val lastX = data.xLabels.lastIndex
    val chart = rememberCartesianChart(
        layer,
        startAxis = startAxis,
        bottomAxis = bottomAxis,
        marker = marker,
        legend = legend,
        // Two series: the latest values carry a direct label.
        persistentMarkers = if (twoSeries && lastX >= 0) { _ -> marker at lastX } else null,
    )
    val model = remember(data) {
        CartesianChartModel(
            when (data.kind) {
                ChartKind.Line -> LineCartesianLayerModel.build { data.series.forEach { series(it.values) } }
                else -> ColumnCartesianLayerModel.build { data.series.forEach { series(it.values) } }
            },
        )
    }
    CartesianChartHost(
        chart = chart,
        model = model,
        modifier = modifier.fillMaxWidth().height(Dimens.chartHeight),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}
