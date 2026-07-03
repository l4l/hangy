package me.kitsu.hangy.domain.engine

import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.Routine

/** A single timed step in a routine's timeline. */
sealed interface Step {
    val durationSec: Int

    /** Lead-in countdown before the first tension. */
    data class GetReady(override val durationSec: Int) : Step

    /** A working hang. [repIndex] is 1-based (per hand for single-hand routines). */
    data class Tension(val repIndex: Int, val hand: Hand, override val durationSec: Int) : Step

    /** Recovery between reps of the same hand (two-hand: between reps). */
    data class Rest(override val durationSec: Int) : Step

    /** Changeover time when swapping the working hand on a single-hand routine. */
    data class Switch(override val durationSec: Int) : Step
}

/**
 * Pure, deterministic expansion of a [Routine] into its ordered list of [Step]s.
 *
 * Sequencing rules:
 *  - **Two-hand**: `totalReps` hangs with both hands, [Step.Rest] between them.
 *  - **Single-hand, alternate each rep**: one cycle = Left → [Switch] → Right, with [Step.Rest]
 *    *after both hands*, repeated `totalReps` times (reps counted per hand).
 *  - **Single-hand, all one then the other**: `totalReps` reps on the left (rest between), one
 *    [Switch], then `totalReps` reps on the right (rest between).
 *
 * A [Step.Switch] is only emitted when `switchSec > 0`; there is never a trailing rest.
 */
object RoutinePlan {

    const val DEFAULT_PREP_SEC = 5

    fun build(routine: Routine, prepSec: Int = DEFAULT_PREP_SEC): List<Step> {
        val steps = mutableListOf<Step>()
        if (prepSec > 0) steps += Step.GetReady(prepSec)

        when (routine.protocol) {
            Protocol.TWO_HAND -> appendHandBlock(steps, routine, Hand.BOTH)

            Protocol.SINGLE_HAND -> when (routine.alternation) {
                Alternation.ALTERNATE_EACH_REP -> appendAlternating(steps, routine)
                Alternation.ALL_ONE_THEN_OTHER -> {
                    appendHandBlock(steps, routine, Hand.LEFT)
                    appendSwitch(steps, routine)
                    appendHandBlock(steps, routine, Hand.RIGHT)
                }
                null -> appendHandBlock(steps, routine, Hand.LEFT)
            }
        }
        return steps
    }

    /** `totalReps` tensions on [hand] with a rest between each (no trailing rest). */
    private fun appendHandBlock(steps: MutableList<Step>, routine: Routine, hand: Hand) {
        for (rep in 1..routine.totalReps) {
            steps += Step.Tension(rep, hand, routine.tensionSec)
            if (rep < routine.totalReps && routine.restSec > 0) steps += Step.Rest(routine.restSec)
        }
    }

    /** One Left→Switch→Right cycle per rep, resting after both hands (no trailing rest). */
    private fun appendAlternating(steps: MutableList<Step>, routine: Routine) {
        for (rep in 1..routine.totalReps) {
            steps += Step.Tension(rep, Hand.LEFT, routine.tensionSec)
            appendSwitch(steps, routine)
            steps += Step.Tension(rep, Hand.RIGHT, routine.tensionSec)
            if (rep < routine.totalReps && routine.restSec > 0) steps += Step.Rest(routine.restSec)
        }
    }

    private fun appendSwitch(steps: MutableList<Step>, routine: Routine) {
        if (routine.switchSec > 0) steps += Step.Switch(routine.switchSec)
    }
}
