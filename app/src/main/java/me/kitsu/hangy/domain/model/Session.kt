package me.kitsu.hangy.domain.model

/**
 * A completed (or in-progress) measurement of a routine.
 *
 * [bodyWeightKg] is captured at the moment the session starts, so any %-of-bodyweight
 * figure derived later always reflects the weight used *then* — not whatever the current
 * Settings value happens to be.
 *
 * [targetLow]/[targetHigh] are stored in the unit the user entered ([targetType]); use
 * [targetLowKg]/[targetHighKg] to get the resolved kilograms for plotting.
 */
data class Session(
    val id: Long = 0,
    val routineId: Long,
    val startedAt: Long,
    val bodyWeightKg: Double,
    val targetType: TargetType,
    val targetLow: Double,
    val targetHigh: Double,
    val maxLoadKg: Double = 0.0,
    val avgLoadKg: Double = 0.0,
    val completed: Boolean = false,
) {
    val targetLowKg: Double get() = resolveKg(targetLow)
    val targetHighKg: Double get() = resolveKg(targetHigh)

    private fun resolveKg(value: Double): Double = when (targetType) {
        TargetType.KG -> value
        TargetType.PERCENT_BW -> value / 100.0 * bodyWeightKg
    }
}

/**
 * Per-rep statistics recorded during a session.
 *
 * [tStartMs]/[tEndMs] are the rep's tension window as offsets from the session start, so its
 * slice of the raw sample stream can be reconstructed for the timeline and rep-comparison charts.
 */
data class RepResult(
    val id: Long = 0,
    val sessionId: Long,
    val repIndex: Int,
    val hand: Hand,
    val maxKg: Double,
    val avgKg: Double,
    val actualTutMs: Long,
    val tStartMs: Long = 0,
    val tEndMs: Long = 0,
)

/** A single raw weight reading persisted as part of a session's full time-series. */
data class Sample(val id: Long = 0, val sessionId: Long, val tOffsetMs: Long, val weightKg: Double)

/** A lightweight projection used to draw a routine's progress graph. */
data class SessionSummary(val sessionId: Long, val startedAt: Long, val bodyWeightKg: Double, val maxLoadKg: Double, val avgLoadKg: Double)

/** A session with its full detail: per-rep stats and the raw sample stream, loaded on demand. */
data class SessionDetail(val session: Session, val reps: List<RepResult>, val samples: List<Sample>)
