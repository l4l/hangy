package me.kitsu.hangy.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import me.kitsu.hangy.R
import me.kitsu.hangy.domain.engine.TimedSample
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.SessionDetail
import kotlin.math.max

private data class RepSeries(val repIndex: Int, val color: Color, val points: List<TimedSample>)

private const val MIN_Y_AXIS_KG = 10.0
private const val Y_HEADROOM = 1.15
private const val LINE_WIDTH = 3f

/**
 * Overlays every rep performed with [hand], each rep's tension curve re-based to t=0, so reps can be
 * compared against one another directly. Only reps for the same hand are shown, since comparing a
 * left-hand hang to a right-hand one is not meaningful.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepComparisonChart(detail: SessionDetail, hand: Hand, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val kgUnit = stringResource(R.string.target_unit_kg)
    val measurer = rememberTextMeasurer()
    val reps = detail.reps
        .filter { it.hand == hand && it.tEndMs > it.tStartMs }
        .sortedBy { it.repIndex }

    if (reps.isEmpty()) {
        Text(
            stringResource(R.string.no_reps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val series = reps.mapIndexed { i, rep ->
        val color = Color.hsv((i * 360f / reps.size) % 360f, HSV_SAT, HSV_VALUE)
        val slice = detail.samples
            .asSequence()
            .filter { it.tOffsetMs in rep.tStartMs..rep.tEndMs }
            .map { TimedSample(it.tOffsetMs - rep.tStartMs, it.weightKg) }
            .toList()
        RepSeries(rep.repIndex, color, slice)
    }

    val maxDur = series.maxOf { s -> s.points.lastOrNull()?.tMs ?: 0L }.coerceAtLeast(1L).toFloat()
    val maxKg = series.maxOf { s -> s.points.maxOfOrNull { it.weightKg } ?: 0.0 }
    val top = max(maxKg, MIN_Y_AXIS_KG) * Y_HEADROOM

    val path = androidx.compose.runtime.remember { Path() }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(vertical = 4.dp),
        ) {
            val leftGutter = Y_AXIS_GUTTER.toPx()
            val bottomGutter = X_AXIS_GUTTER.toPx()
            inset(left = leftGutter, top = 0f, right = 0f, bottom = bottomGutter) {
                series.forEach { s -> drawRepLine(path, s.points, maxDur, top, s.color) }
            }
            drawWeightAxis(measurer, labelStyle, gridColor, labelColor, kgUnit, 0.0, top, leftGutter, bottomGutter)
            drawTimeAxis(measurer, labelStyle, gridColor, labelColor, 0L, maxDur.toLong(), leftGutter, bottomGutter)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(s.color),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.rep_n, s.repIndex),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private const val HSV_SAT = 0.6f
private const val HSV_VALUE = 0.85f

private fun DrawScope.drawRepLine(path: Path, points: List<TimedSample>, maxDur: Float, top: Double, color: Color) {
    if (points.size < 2) return
    path.rewind()
    points.forEachIndexed { index, p ->
        val x = (size.width * p.tMs.toFloat() / maxDur).coerceIn(0f, size.width)
        val y = (size.height * (1f - (p.weightKg / top).toFloat())).coerceIn(0f, size.height)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = color, style = Stroke(width = LINE_WIDTH))
}
