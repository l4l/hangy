package me.kitsu.hangy.ui.common

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Formats a weight for display, e.g. `42.3 kg`. */
fun formatKg(kg: Double): String = String.format(Locale.US, "%.1f", kg)

/** Formats a millisecond countdown as whole seconds, rounding up so it never shows 0 mid-step. */
fun formatCountdown(ms: Long): String {
    val seconds = ((ms + 999) / 1000).coerceAtLeast(0)
    return "${seconds}s"
}

/** Formats a tension duration in seconds with one decimal, e.g. `7.3 s`. */
fun formatTutSec(ms: Long): String = String.format(Locale.US, "%.1f s", ms / 1000.0)

/**
 * Formats a millisecond value as a compact chart-axis time label with a bare `s` suffix, e.g.
 * `5s` or `7.5s`. Whole seconds drop the decimal; sub-second and fractional ticks keep one.
 */
fun formatAxisSeconds(ms: Long): String {
    val seconds = ms / 1000.0
    return if (seconds == kotlin.math.floor(seconds)) {
        "${seconds.toLong()}s"
    } else {
        String.format(Locale.US, "%.1fs", seconds)
    }
}

/** Formats a wall-clock timestamp (ms) as a short local date + time for session labels. */
fun formatSessionDate(epochMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
