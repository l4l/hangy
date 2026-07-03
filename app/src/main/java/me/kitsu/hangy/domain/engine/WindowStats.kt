package me.kitsu.hangy.domain.engine

/** A weight reading tagged with the elapsed time (ms) since the measurement started. */
data class TimedSample(val tMs: Long, val weightKg: Double)

/**
 * Maintains the rolling statistics shown during a measurement:
 *  - the most recent reading,
 *  - the average over the trailing [windowMs] window (also what the live plot spans),
 *  - the maximum over the entire measurement.
 *
 * [windowMs] is configurable (Settings → averaging window), defaulting to 15 s.
 */
class WindowStats(var windowMs: Long) {

    private val window = ArrayDeque<TimedSample>()

    var sessionMaxKg: Double = 0.0
        private set

    val currentKg: Double get() = window.lastOrNull()?.weightKg ?: 0.0

    val windowAvgKg: Double
        get() = if (window.isEmpty()) 0.0 else window.sumOf { it.weightKg } / window.size

    fun windowSamples(): List<TimedSample> = window.toList()

    /** Adds a reading at absolute elapsed time [tMs] and evicts anything older than the window. */
    fun add(tMs: Long, weightKg: Double) {
        window.addLast(TimedSample(tMs, weightKg))
        val cutoff = tMs - windowMs
        while (window.isNotEmpty() && window.first().tMs < cutoff) {
            window.removeFirst()
        }
        if (weightKg > sessionMaxKg) sessionMaxKg = weightKg
    }

    fun reset() {
        window.clear()
        sessionMaxKg = 0.0
    }
}
