package me.kitsu.hangy.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import me.kitsu.hangy.R
import me.kitsu.hangy.domain.engine.TimedSample
import kotlin.math.max

/**
 * Real-time scrolling plot of the trailing [windowMs] of readings. Drawn on a raw [Canvas] so it
 * repaints smoothly at the scale's ~10 Hz update rate and stays fully offline. Segments where the
 * reading sits inside the target band are drawn in [inTargetColor] for immediate visual feedback,
 * and the axis extends below zero so negative (post-tare) readings render correctly.
 *
 * The series is rendered as two reused [Path]s (base polyline + in-target overlay) stroked once
 * each, rather than a `drawLine` per segment — this avoids O(N) draw commands and per-frame
 * `Offset` allocations on the UI thread.
 */
@Composable
fun LiveWeightChart(
    samples: List<TimedSample>,
    latestTMs: Long,
    windowMs: Long,
    targetLowKg: Double,
    targetHighKg: Double,
    lineColor: Color,
    inTargetColor: Color,
    targetColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    val linePath = remember { Path() }
    val bandPath = remember { Path() }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val kgUnit = stringResource(R.string.target_unit_kg)
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        // Single pass for the y-range (avoids two maxOf/minOf lambda passes).
        var dataMin = 0.0
        var dataMax = 0.0
        for (sample in samples) {
            val v = sample.weightKg
            if (v < dataMin) dataMin = v
            if (v > dataMax) dataMax = v
        }
        val top = max(max(dataMax, targetHighKg), MIN_Y_AXIS_KG) * Y_HEADROOM
        val bottom = if (dataMin < 0) dataMin * Y_HEADROOM else 0.0
        val range = (top - bottom).coerceAtLeast(1.0)
        val startT = latestTMs - windowMs

        val leftGutter = Y_AXIS_GUTTER.toPx()
        val bottomGutter = X_AXIS_GUTTER.toPx()
        // Time labels count back from "now" (right edge), so the trailing window reads -Ns … 0s.
        drawWeightAxis(measurer, labelStyle, gridColor, labelColor, kgUnit, bottom, top, leftGutter, bottomGutter)
        drawTimeAxis(measurer, labelStyle, gridColor, labelColor, -windowMs, 0L, leftGutter, bottomGutter)
        inset(left = leftGutter, top = 0f, right = 0f, bottom = bottomGutter) {
            drawZeroLine(bottom, range, gridColor)
            drawTargetBand(targetLowKg, targetHighKg, bottom, range, targetColor)
            drawSeries(linePath, bandPath, samples, startT, windowMs, bottom, range, targetLowKg, targetHighKg, lineColor, inTargetColor)
        }
    }
}

private const val MIN_Y_AXIS_KG = 10.0
private const val Y_HEADROOM = 1.15
private const val LINE_WIDTH = 4f

private fun DrawScope.yFor(kg: Double, bottom: Double, range: Double): Float =
    (size.height * (1f - ((kg - bottom) / range).toFloat())).coerceIn(0f, size.height)

private fun DrawScope.drawZeroLine(bottom: Double, range: Double, gridColor: Color) {
    if (bottom >= 0) return
    val y = yFor(0.0, bottom, range)
    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
}

private fun DrawScope.drawTargetBand(low: Double, high: Double, bottom: Double, range: Double, color: Color) {
    if (high <= 0.0 || high <= low) return
    val topY = yFor(high, bottom, range)
    val bottomY = yFor(low, bottom, range)
    drawRect(color, topLeft = Offset(0f, topY), size = Size(size.width, (bottomY - topY).coerceAtLeast(0f)))
}

@Suppress("LongParameterList")
private fun DrawScope.drawSeries(
    linePath: Path,
    bandPath: Path,
    samples: List<TimedSample>,
    startT: Long,
    windowMs: Long,
    bottom: Double,
    range: Double,
    targetLow: Double,
    targetHigh: Double,
    lineColor: Color,
    inTargetColor: Color,
) {
    if (samples.size < 2 || windowMs <= 0) return
    linePath.rewind()
    bandPath.rewind()

    val w = size.width
    val h = size.height
    val sx = w / windowMs.toFloat()
    fun xAt(tMs: Long): Float = ((tMs - startT).toFloat() * sx).coerceIn(0f, w)
    fun yAt(kg: Double): Float = (h * (1f - ((kg - bottom) / range).toFloat())).coerceIn(0f, h)

    // Base polyline: one continuous stroke.
    samples.forEachIndexed { index, sample ->
        val x = xAt(sample.tMs)
        val y = yAt(sample.weightKg)
        if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
    }

    // In-target segments overlaid on top, so they read green wherever the reading is in band.
    val hasBand = targetHigh > 0.0 && targetHigh > targetLow
    if (hasBand) {
        for (i in 0 until samples.size - 1) {
            val end = samples[i + 1]
            if (end.weightKg >= targetLow && end.weightKg <= targetHigh) {
                val start = samples[i]
                bandPath.moveTo(xAt(start.tMs), yAt(start.weightKg))
                bandPath.lineTo(xAt(end.tMs), yAt(end.weightKg))
            }
        }
    }

    drawPath(linePath, color = lineColor, style = Stroke(width = LINE_WIDTH))
    if (hasBand) drawPath(bandPath, color = inTargetColor, style = Stroke(width = LINE_WIDTH))
}
