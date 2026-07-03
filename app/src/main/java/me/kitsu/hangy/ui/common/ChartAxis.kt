package me.kitsu.hangy.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Axis value-indicator helpers shared by the Canvas charts. Charts reserve a left gutter
 * ([Y_AXIS_GUTTER]) and bottom gutter ([X_AXIS_GUTTER]) via `DrawScope.inset`, draw their trace
 * inside the inset plot rect, then call [drawWeightAxis]/[drawTimeAxis] to label the gutters.
 *
 * Ticks are chosen adaptively from the data range by [niceTicks] — the step follows the range
 * (…1, 2, 5, 10, 20…), so the grid is never pinned to a fixed increment.
 */

val Y_AXIS_GUTTER = 40.dp
val X_AXIS_GUTTER = 16.dp

private const val GRID_STROKE = 1f
private const val LABEL_GAP_PX = 4f
private const val FP_EPS = 1e-6
private const val TICK_GUARD = 200

/**
 * Returns "nice" round tick values covering `[min, max]` — the mantissa of the raw step
 * `(max-min)/target` is snapped to {1, 2, 5, 10} × 10^n, then ticks are emitted from the first
 * multiple ≥ min up to max. Returns empty when the range is degenerate or non-finite.
 */
fun niceTicks(min: Double, max: Double, target: Int = 5): List<Double> {
    val finite = min.isFinite() && max.isFinite()
    if (!finite || max <= min || target < 1) return emptyList()
    val step = niceStep((max - min) / target)
    if (step <= 0.0) return emptyList()
    val first = ceil(min / step - FP_EPS) * step
    val ticks = ArrayList<Double>()
    var v = first
    var guard = 0
    while (v <= max + step * FP_EPS && guard < TICK_GUARD) {
        ticks.add(cleanup(v))
        v += step
        guard++
    }
    return ticks
}

/** Snaps the raw step to the nearest {1, 2, 5, 10} × 10^n. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0.0 || !raw.isFinite()) return 0.0
    val exp = floor(log10(raw))
    val base = 10.0.pow(exp)
    val f = raw / base
    val nice = when {
        f < 1.5 -> 1.0
        f < 3.0 -> 2.0
        f < 7.0 -> 5.0
        else -> 10.0
    }
    return nice * base
}

/** Strips floating-point drift so ticks land on clean values (e.g. 1.9999999 -> 2.0). */
private fun cleanup(v: Double): Double = (v * 1e6).roundToLong() / 1e6

/** Formats a tick's numeric part, dropping a trailing `.0` (e.g. `30.0` -> `30`, `2.5` -> `2.5`). */
private fun formatTick(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

/**
 * Draws adaptive horizontal gridlines across the plot rect and a kg label for each in the left
 * gutter (right-aligned). The unit is appended to the top-most label only, to keep the gutter
 * narrow. `[bottom, top]` is the plot's kg domain; the plot rect is the canvas minus the gutters.
 */
@Suppress("LongParameterList")
fun DrawScope.drawWeightAxis(
    measurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
    labelColor: Color,
    unit: String,
    bottom: Double,
    top: Double,
    leftGutterPx: Float,
    bottomGutterPx: Float,
) {
    val ticks = niceTicks(bottom, top)
    val plotHeight = size.height - bottomGutterPx
    val range = (top - bottom).coerceAtLeast(FP_EPS)
    val topTick = ticks.lastOrNull()
    for (kg in ticks) {
        val y = (plotHeight * (1.0 - (kg - bottom) / range)).toFloat()
        if (y < 0f || y > plotHeight) continue
        drawLine(gridColor, Offset(leftGutterPx, y), Offset(size.width, y), strokeWidth = GRID_STROKE)
        val label = if (kg == topTick) "${formatTick(kg)} $unit" else formatTick(kg)
        val layout = measurer.measure(label, style)
        val tx = (leftGutterPx - layout.size.width - LABEL_GAP_PX).coerceAtLeast(0f)
        val ty = (y - layout.size.height / 2f).coerceIn(0f, plotHeight - layout.size.height)
        drawText(layout, color = labelColor, topLeft = Offset(tx, ty))
    }
}

/**
 * Draws adaptive vertical gridlines across the plot rect and a seconds label for each in the
 * bottom gutter (centred). `[startMs, endMs]` is the visible time window; pass a relative range
 * (e.g. `-windowMs..0`) to label a trailing live window as `-10s … 0s`.
 */
@Suppress("LongParameterList")
fun DrawScope.drawTimeAxis(
    measurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
    labelColor: Color,
    startMs: Long,
    endMs: Long,
    leftGutterPx: Float,
    bottomGutterPx: Float,
) {
    val startSec = startMs / 1000.0
    val endSec = endMs / 1000.0
    val ticks = niceTicks(startSec, endSec, target = 4)
    if (ticks.isEmpty()) return
    val plotWidth = size.width - leftGutterPx
    val plotHeight = size.height - bottomGutterPx
    val span = (endSec - startSec).coerceAtLeast(FP_EPS)
    for (sec in ticks) {
        val x = leftGutterPx + (plotWidth * (sec - startSec) / span).toFloat()
        if (x < leftGutterPx || x > size.width) continue
        drawLine(gridColor, Offset(x, 0f), Offset(x, plotHeight), strokeWidth = GRID_STROKE)
        val layout = measurer.measure(formatAxisSeconds((sec * 1000).roundToLong()), style)
        val tx = (x - layout.size.width / 2f).coerceIn(leftGutterPx, size.width - layout.size.width)
        val ty = plotHeight + (bottomGutterPx - layout.size.height) / 2f
        drawText(layout, color = labelColor, topLeft = Offset(tx, ty))
    }
}
