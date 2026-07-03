package me.kitsu.hangy.domain.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.Routine
import kotlin.math.min

enum class PhaseType { GET_READY, WAITING, TENSION, REST, SWITCH, DONE }

/**
 * A snapshot of the routine's progress, emitted on every tick so the UI can render a live
 * countdown, a rep tracker and the upcoming action.
 *
 * [upcomingHand]/[upcomingRepIndex] describe the next hang, so during a rest, switch or the
 * pull-to-start wait the UI can show what comes next.
 */
data class EngineState(
    val phase: PhaseType,
    val repIndex: Int,
    val totalReps: Int,
    val hand: Hand?,
    val remainingMs: Long,
    val upcomingHand: Hand? = null,
    val upcomingRepIndex: Int? = null,
    val finished: Boolean = false,
)

/**
 * Drives a [Routine] over time, emitting an [EngineState] roughly every [tickMs]. Uses `delay`,
 * so under `kotlinx-coroutines-test` virtual time the whole routine plays out deterministically.
 *
 * When [awaitPull] is supplied, each hang is *gated*: the engine emits a [PhaseType.WAITING]
 * state and suspends on [awaitPull] until the caller signals the load threshold has been crossed,
 * and only then counts down the tension. This makes the timer measure actual hang time rather
 * than starting automatically when the get-ready countdown ends.
 */
class RoutineEngine {

    fun run(
        routine: Routine,
        prepSec: Int = RoutinePlan.DEFAULT_PREP_SEC,
        tickMs: Long = DEFAULT_TICK_MS,
        awaitPull: (suspend () -> Unit)? = null,
    ): Flow<EngineState> = flow {
        val steps = RoutinePlan.build(routine, prepSec)
        var lastRep = 0

        steps.forEachIndexed { index, step ->
            val nextTension = steps.drop(index + 1).firstOrNull { it is Step.Tension } as? Step.Tension
            val upcomingHand = nextTension?.hand
            val upcomingRep = nextTension?.repIndex

            if (step is Step.Tension) {
                lastRep = step.repIndex
                val durationMs = step.durationSec * MILLIS_PER_SEC

                if (awaitPull != null) {
                    emit(
                        EngineState(
                            phase = PhaseType.WAITING,
                            repIndex = step.repIndex,
                            totalReps = routine.totalReps,
                            hand = step.hand,
                            remainingMs = durationMs,
                            upcomingHand = step.hand,
                            upcomingRepIndex = step.repIndex,
                        ),
                    )
                    awaitPull()
                }

                countDown(
                    phase = PhaseType.TENSION,
                    durationMs = durationMs,
                    tickMs = tickMs,
                    repIndex = step.repIndex,
                    totalReps = routine.totalReps,
                    hand = step.hand,
                    upcomingHand = upcomingHand,
                    upcomingRep = upcomingRep,
                )
            } else {
                countDown(
                    phase = step.toPhaseType(),
                    durationMs = step.durationSec * MILLIS_PER_SEC,
                    tickMs = tickMs,
                    repIndex = lastRep,
                    totalReps = routine.totalReps,
                    hand = null,
                    upcomingHand = upcomingHand,
                    upcomingRep = upcomingRep,
                )
            }
        }

        emit(
            EngineState(
                phase = PhaseType.DONE,
                repIndex = routine.totalReps,
                totalReps = routine.totalReps,
                hand = null,
                remainingMs = 0,
                finished = true,
            ),
        )
    }

    @Suppress("LongParameterList")
    private suspend fun FlowCollector<EngineState>.countDown(
        phase: PhaseType,
        durationMs: Long,
        tickMs: Long,
        repIndex: Int,
        totalReps: Int,
        hand: Hand?,
        upcomingHand: Hand?,
        upcomingRep: Int?,
    ) {
        var remaining = durationMs
        while (remaining > 0) {
            emit(
                EngineState(
                    phase = phase,
                    repIndex = repIndex,
                    totalReps = totalReps,
                    hand = hand,
                    remainingMs = remaining,
                    upcomingHand = upcomingHand,
                    upcomingRepIndex = upcomingRep,
                ),
            )
            val dt = min(tickMs, remaining)
            delay(dt)
            remaining -= dt
        }
    }

    private fun Step.toPhaseType(): PhaseType = when (this) {
        is Step.GetReady -> PhaseType.GET_READY
        is Step.Tension -> PhaseType.TENSION
        is Step.Rest -> PhaseType.REST
        is Step.Switch -> PhaseType.SWITCH
    }

    companion object {
        const val DEFAULT_TICK_MS = 100L
        private const val MILLIS_PER_SEC = 1_000L
    }
}
