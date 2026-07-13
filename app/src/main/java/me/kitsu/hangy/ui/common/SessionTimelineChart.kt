package me.kitsu.hangy.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import me.kitsu.hangy.R
import me.kitsu.hangy.domain.engine.TimedSample
import me.kitsu.hangy.domain.model.RepResult
import me.kitsu.hangy.domain.model.Sample
import me.kitsu.hangy.domain.model.SessionDetail
import kotlin.math.max

/**
 * Plots a session's weight stream on a [Canvas] with the target band drawn horizontally. Rest
 * periods are cut out: each rep's tension window is laid onto a collapsed time axis (width
 * proportional to its duration, small gap between reps), alternate reps get a zebra background
 * band, and the bottom axis labels rep numbers instead of time. Sessions recorded before per-rep
 * windows existed fall back to the full raw timeline with tension windows shaded vertically.
 *
 * The stream is min/max-decimated to the visible width so a multi-thousand-point session renders
 * cheaply. When [interactive] is true the x-axis can be pinch-zoomed and dragged; otherwise the
 * whole session is shown statically (used for the mini overviews in cards and the post-session
 * summary).
 */
@Composable
fun SessionTimelineChart(detail: SessionDetail, modifier: Modifier = Modifier, interactive: Boolean = false, showAxes: Boolean = true) {
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = MaterialTheme.colorScheme.secondaryContainer
    val tensionColor = MaterialTheme.colorScheme.tertiary.copy(alpha = TENSION_ALPHA)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val kgUnit = stringResource(R.string.target_unit_kg)
    val measurer = rememberTextMeasurer()

    val collapsed = remember(detail) { collapseRests(detail) }
    val samples = collapsed?.samples ?: detail.samples
    val firstT = if (collapsed != null) 0L else samples.firstOrNull()?.tOffsetMs ?: 0L
    val lastT = collapsed?.spanMs ?: (samples.lastOrNull()?.tOffsetMs ?: 0L)
    val totalSpan = (lastT - firstT).coerceAtLeast(1L).toFloat()

    var scale by remember(detail.session.id) { mutableFloatStateOf(1f) }
    var startT by remember(detail.session.id) { mutableFloatStateOf(firstT.toFloat()) }

    val buckets = if (interactive) INTERACTIVE_BUCKETS else MINI_BUCKETS
    val visibleSpan = totalSpan / scale
    val maxStart = (totalSpan - visibleSpan).coerceAtLeast(0f)
    val clampedStart = startT.coerceIn(firstT.toFloat(), firstT + maxStart)
    val windowStart = clampedStart.toLong()
    val windowEnd = (clampedStart + visibleSpan).toLong()

    val linePath = remember { Path() }
    val points = downsampleWindow(samples, windowStart, windowEnd, buckets)

    val bandLow = detail.session.targetLowKg
    val bandHigh = detail.session.targetHighKg

    val gestureModifier = if (interactive && totalSpan > 1f) {
        Modifier.pointerInput(detail.session.id) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                // The plot occupies the canvas minus the y-axis gutter, so map gestures into it.
                val leftGutter = if (showAxes) Y_AXIS_GUTTER.toPx() else 0f
                val w = (size.width - leftGutter).coerceAtLeast(1f)
                val cx = (centroid.x - leftGutter).coerceIn(0f, w)
                val oldVisible = totalSpan / scale
                val curStart = startT.coerceIn(firstT.toFloat(), firstT + (totalSpan - oldVisible).coerceAtLeast(0f))
                val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                val newVisible = totalSpan / newScale
                // Keep the time under the pinch centroid fixed, then apply the drag.
                val centroidT = curStart + (cx / w) * oldVisible
                val ns = centroidT - (cx / w) * newVisible - (pan.x / w) * newVisible
                scale = newScale
                startT = ns.coerceIn(firstT.toFloat(), firstT + (totalSpan - newVisible).coerceAtLeast(0f))
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(gestureModifier)) {
        var dMin = 0.0
        var dMax = 0.0
        for (p in points) {
            if (p.weightKg < dMin) dMin = p.weightKg
            if (p.weightKg > dMax) dMax = p.weightKg
        }
        val top = max(max(dMax, bandHigh), MIN_Y_AXIS_KG) * Y_HEADROOM
        val bottom = if (dMin < 0) dMin * Y_HEADROOM else 0.0
        val range = (top - bottom).coerceAtLeast(1.0)

        val leftGutter = if (showAxes) Y_AXIS_GUTTER.toPx() else 0f
        val bottomGutter = if (showAxes) X_AXIS_GUTTER.toPx() else 0f
        if (showAxes) {
            drawWeightAxis(measurer, labelStyle, gridColor, labelColor, kgUnit, bottom, top, leftGutter, bottomGutter)
            if (collapsed != null) {
                drawRepAxis(measurer, labelStyle, labelColor, collapsed.segments, windowStart, windowEnd, leftGutter, bottomGutter)
            } else {
                drawTimeAxis(measurer, labelStyle, gridColor, labelColor, windowStart, windowEnd, leftGutter, bottomGutter)
            }
        } else {
            drawGridLines(gridColor)
        }
        inset(left = leftGutter, top = 0f, right = 0f, bottom = bottomGutter) {
            drawHorizontalBand(bandLow, bandHigh, bottom, range, bandColor)
            if (collapsed != null) {
                drawRepBands(collapsed.segments, windowStart, visibleSpan, tensionColor)
                drawZeroBaseline(bottom, range, gridColor)
                drawSegmentedTrace(linePath, points, collapsed.segments, windowStart, visibleSpan, bottom, range, lineColor)
            } else {
                drawTensionSpans(detail.reps, windowStart, visibleSpan, tensionColor)
                drawZeroBaseline(bottom, range, gridColor)
                drawTrace(linePath, points, windowStart, visibleSpan, bottom, range, lineColor)
            }
        }
    }
}

private const val MIN_Y_AXIS_KG = 10.0
private const val Y_HEADROOM = 1.15
private const val GRID_LINES = 4
private const val LINE_WIDTH = 3f
private const val TENSION_ALPHA = 0.18f
private const val MAX_ZOOM = 40f
private const val MINI_BUCKETS = 120
private const val INTERACTIVE_BUCKETS = 260
private const val REP_GAP_FRACTION = 0.02
private const val LABEL_GAP_PX = 4f

/** A rep's slice of the collapsed time axis, `[startMs, endMs]` in collapsed time. */
internal data class RepSegment(val repIndex: Int, val startMs: Long, val endMs: Long)

/** A session re-based onto a rest-free time axis: remapped samples, one segment per rep. */
internal data class CollapsedTimeline(val samples: List<Sample>, val segments: List<RepSegment>, val spanMs: Long)

/**
 * Cuts the rest periods out of [detail]: reps with a recorded tension window are laid end to end
 * (in tension order, widths proportional to duration) with a small gap between them, and each
 * rep's samples are shifted onto that axis. Returns null when no rep has a window, so the caller
 * can fall back to the raw timeline.
 */
internal fun collapseRests(detail: SessionDetail): CollapsedTimeline? {
    val reps = detail.reps.filter { it.tEndMs > it.tStartMs }.sortedBy { it.tStartMs }
    if (reps.isEmpty()) return null

    val tensionMs = reps.sumOf { it.tEndMs - it.tStartMs }
    val gapMs = (tensionMs * REP_GAP_FRACTION).toLong().coerceAtLeast(1L)

    val samples = ArrayList<Sample>()
    val segments = ArrayList<RepSegment>(reps.size)
    val all = detail.samples
    var cursor = 0L
    var i = 0
    for (rep in reps) {
        while (i < all.size && all[i].tOffsetMs < rep.tStartMs) i++
        while (i < all.size && all[i].tOffsetMs <= rep.tEndMs) {
            samples.add(all[i].copy(tOffsetMs = cursor + (all[i].tOffsetMs - rep.tStartMs)))
            i++
        }
        segments.add(RepSegment(rep.repIndex, cursor, cursor + (rep.tEndMs - rep.tStartMs)))
        cursor = segments.last().endMs + gapMs
    }
    return CollapsedTimeline(samples, segments, segments.last().endMs)
}

/**
 * Per-bucket min/max decimation of [samples] within `[startMs, endMs]`. Emitting both the min and
 * the max of each time bucket preserves the trace's envelope (peaks and valleys) that plain
 * every-Nth sampling would drop.
 */
internal fun downsampleWindow(samples: List<Sample>, startMs: Long, endMs: Long, buckets: Int): List<TimedSample> {
    if (samples.isEmpty() || buckets <= 0) return emptyList()
    val visible = samples.filter { it.tOffsetMs in startMs..endMs }
    if (visible.size <= buckets * 2) return visible.map { TimedSample(it.tOffsetMs, it.weightKg) }

    val span = (endMs - startMs).coerceAtLeast(1L)
    val out = ArrayList<TimedSample>(buckets * 2)
    var bucket = -1
    var lo: Sample? = null
    var hi: Sample? = null

    fun flush() {
        val l = lo ?: return
        val h = hi ?: return
        val a = if (l.tOffsetMs <= h.tOffsetMs) l else h
        val b = if (l.tOffsetMs <= h.tOffsetMs) h else l
        out.add(TimedSample(a.tOffsetMs, a.weightKg))
        if (b !== a) out.add(TimedSample(b.tOffsetMs, b.weightKg))
    }

    for (s in visible) {
        val b = (((s.tOffsetMs - startMs) * buckets) / span).toInt().coerceIn(0, buckets - 1)
        if (b != bucket) {
            flush()
            lo = null
            hi = null
            bucket = b
        }
        val curLo = lo
        if (curLo == null || s.weightKg < curLo.weightKg) lo = s
        val curHi = hi
        if (curHi == null || s.weightKg > curHi.weightKg) hi = s
    }
    flush()
    return out
}

private fun DrawScope.yFor(kg: Double, bottom: Double, range: Double): Float =
    (size.height * (1f - ((kg - bottom) / range).toFloat())).coerceIn(0f, size.height)

private fun DrawScope.xFor(tMs: Long, startMs: Long, visibleSpan: Float): Float =
    (size.width * (tMs - startMs).toFloat() / visibleSpan.coerceAtLeast(1f)).coerceIn(0f, size.width)

private fun DrawScope.drawGridLines(gridColor: Color) {
    for (i in 0..GRID_LINES) {
        val y = size.height * i / GRID_LINES
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawZeroBaseline(bottom: Double, range: Double, gridColor: Color) {
    if (bottom >= 0) return
    val y = yFor(0.0, bottom, range)
    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
}

private fun DrawScope.drawHorizontalBand(low: Double, high: Double, bottom: Double, range: Double, color: Color) {
    if (high <= 0.0 || high <= low) return
    val topY = yFor(high, bottom, range)
    val bottomY = yFor(low, bottom, range)
    drawRect(color, topLeft = Offset(0f, topY), size = Size(size.width, (bottomY - topY).coerceAtLeast(0f)))
}

private fun DrawScope.drawTensionSpans(reps: List<RepResult>, startMs: Long, visibleSpan: Float, color: Color) {
    for (rep in reps) {
        if (rep.tEndMs > rep.tStartMs) {
            val x1 = xFor(rep.tStartMs, startMs, visibleSpan)
            val x2 = xFor(rep.tEndMs, startMs, visibleSpan)
            val w = x2 - x1
            if (w > 0f) drawRect(color, topLeft = Offset(x1, 0f), size = Size(w, size.height))
        }
    }
}

/** Zebra shading: every other rep segment gets a full-height band so reps read as distinct. */
private fun DrawScope.drawRepBands(segments: List<RepSegment>, startMs: Long, visibleSpan: Float, color: Color) {
    segments.forEachIndexed { index, seg ->
        if (index % 2 == 0) {
            val x1 = xFor(seg.startMs, startMs, visibleSpan)
            val x2 = xFor(seg.endMs, startMs, visibleSpan)
            val w = x2 - x1
            if (w > 0f) drawRect(color, topLeft = Offset(x1, 0f), size = Size(w, size.height))
        }
    }
}

/** Labels each visible rep segment with its rep number, centred in the bottom gutter. */
@Suppress("LongParameterList")
private fun DrawScope.drawRepAxis(
    measurer: TextMeasurer,
    style: TextStyle,
    labelColor: Color,
    segments: List<RepSegment>,
    startMs: Long,
    endMs: Long,
    leftGutterPx: Float,
    bottomGutterPx: Float,
) {
    val plotWidth = size.width - leftGutterPx
    val plotHeight = size.height - bottomGutterPx
    val span = (endMs - startMs).coerceAtLeast(1L).toFloat()
    var lastLabelEnd = Float.NEGATIVE_INFINITY
    for (seg in segments) {
        val visStart = max(seg.startMs, startMs)
        val visEnd = minOf(seg.endMs, endMs)
        if (visEnd <= visStart) continue
        val x1 = leftGutterPx + plotWidth * (visStart - startMs) / span
        val x2 = leftGutterPx + plotWidth * (visEnd - startMs) / span
        val layout = measurer.measure(seg.repIndex.toString(), style)
        val tx = ((x1 + x2 - layout.size.width) / 2f).coerceIn(leftGutterPx, size.width - layout.size.width)
        if (tx >= lastLabelEnd + LABEL_GAP_PX) {
            val ty = plotHeight + (bottomGutterPx - layout.size.height) / 2f
            drawText(layout, color = labelColor, topLeft = Offset(tx, ty))
            lastLabelEnd = tx + layout.size.width
        }
    }
}

/** Draws the trace one rep segment at a time so the line never bridges the inter-rep gaps. */
@Suppress("LongParameterList")
private fun DrawScope.drawSegmentedTrace(
    path: Path,
    points: List<TimedSample>,
    segments: List<RepSegment>,
    startMs: Long,
    visibleSpan: Float,
    bottom: Double,
    range: Double,
    color: Color,
) {
    var i = 0
    for (seg in segments) {
        while (i < points.size && points[i].tMs < seg.startMs) i++
        val from = i
        while (i < points.size && points[i].tMs <= seg.endMs) i++
        drawTrace(path, points.subList(from, i), startMs, visibleSpan, bottom, range, color)
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawTrace(
    path: Path,
    points: List<TimedSample>,
    startMs: Long,
    visibleSpan: Float,
    bottom: Double,
    range: Double,
    color: Color,
) {
    if (points.size < 2) return
    path.rewind()
    points.forEachIndexed { index, p ->
        val x = xFor(p.tMs, startMs, visibleSpan)
        val y = yFor(p.weightKg, bottom, range)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = color, style = Stroke(width = LINE_WIDTH))
}
