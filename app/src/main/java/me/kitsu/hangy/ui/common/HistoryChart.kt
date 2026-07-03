package me.kitsu.hangy.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import me.kitsu.hangy.R
import me.kitsu.hangy.domain.model.HistoryMetric
import me.kitsu.hangy.domain.model.SessionSummary
import kotlin.math.max

/**
 * Simple progress line chart of past sessions for a routine, drawn on a [Canvas] (no external
 * chart dependency). Plots the max and/or average load per session depending on [metric].
 */
@Composable
fun HistoryChart(summaries: List<SessionSummary>, metric: HistoryMetric, modifier: Modifier = Modifier) {
    val maxColor = MaterialTheme.colorScheme.primary
    val avgColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val kgUnit = stringResource(R.string.target_unit_kg)
    val measurer = rememberTextMeasurer()

    val showMax = metric == HistoryMetric.MAX || metric == HistoryMetric.BOTH
    val showAvg = metric == HistoryMetric.AVERAGE || metric == HistoryMetric.BOTH

    val peak = summaries.maxOfOrNull { max(it.maxLoadKg, it.avgLoadKg) } ?: 0.0
    val yMax = max(peak, MIN_Y_AXIS_KG) * Y_HEADROOM

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(vertical = 4.dp),
        ) {
            // X is discrete sessions, not a time axis, so only the weight (Y) axis is labelled.
            val leftGutter = Y_AXIS_GUTTER.toPx()
            drawWeightAxis(measurer, labelStyle, gridColor, labelColor, kgUnit, 0.0, yMax, leftGutter, 0f)
            inset(left = leftGutter, top = 0f, right = 0f, bottom = 0f) {
                if (showMax) drawSeries(summaries.map { it.maxLoadKg }, yMax, maxColor)
                if (showAvg) drawSeries(summaries.map { it.avgLoadKg }, yMax, avgColor)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showMax) LegendDot(stringResource(R.string.metric_max), maxColor)
            if (showAvg) LegendDot(stringResource(R.string.metric_avg), avgColor)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private const val MIN_Y_AXIS_KG = 10.0
private const val Y_HEADROOM = 1.15
private const val POINT_RADIUS = 5f

private fun DrawScope.drawSeries(values: List<Double>, yMax: Double, color: Color) {
    if (values.isEmpty()) return
    val stepX = if (values.size == 1) 0f else size.width / (values.size - 1)
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = if (values.size == 1) size.width / 2 else stepX * index
        val y = (size.height * (1f - (value / yMax).toFloat())).coerceIn(0f, size.height)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        drawCircle(color, radius = POINT_RADIUS, center = Offset(x, y))
    }
    if (values.size > 1) drawPath(path, color = color, style = Stroke(width = 3f))
}
