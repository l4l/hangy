package me.kitsu.hangy.domain.engine

import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutinePlanTest {

    private fun routine(protocol: Protocol, alternation: Alternation? = null, reps: Int = 4, switchSec: Int = 5, restSec: Int = 60) =
        Routine(
            name = "r",
            protocol = protocol,
            alternation = alternation,
            tensionSec = 7,
            restSec = restSec,
            switchSec = switchSec,
            totalReps = reps,
        )

    private fun List<Step>.tensions() = filterIsInstance<Step.Tension>()

    @Test
    fun `two-hand is reps with rests between and no switches`() {
        val steps = RoutinePlan.build(routine(Protocol.TWO_HAND, reps = 3), prepSec = 5)
        assertEquals(Step.GetReady(5), steps.first())
        assertEquals(List(3) { Hand.BOTH }, steps.tensions().map { it.hand })
        assertEquals(2, steps.filterIsInstance<Step.Rest>().size)
        assertEquals(0, steps.filterIsInstance<Step.Switch>().size)
    }

    @Test
    fun `alternate each rep does left-switch-right per cycle, resting after both hands`() {
        val steps = RoutinePlan.build(
            routine(Protocol.SINGLE_HAND, Alternation.ALTERNATE_EACH_REP, reps = 2, switchSec = 3),
            prepSec = 0,
        )
        assertEquals(
            listOf(Hand.LEFT, Hand.RIGHT, Hand.LEFT, Hand.RIGHT),
            steps.tensions().map { it.hand },
        )
        assertEquals(listOf(1, 1, 2, 2), steps.tensions().map { it.repIndex })
        assertEquals(2, steps.filterIsInstance<Step.Switch>().size) // one per cycle
        assertEquals(1, steps.filterIsInstance<Step.Rest>().size) // only after the first cycle
    }

    @Test
    fun `all one then other does N per hand with a single switch between blocks`() {
        val steps = RoutinePlan.build(
            routine(Protocol.SINGLE_HAND, Alternation.ALL_ONE_THEN_OTHER, reps = 2, switchSec = 5),
            prepSec = 0,
        )
        assertEquals(
            listOf(Hand.LEFT, Hand.LEFT, Hand.RIGHT, Hand.RIGHT),
            steps.tensions().map { it.hand },
        )
        assertEquals(listOf(1, 2, 1, 2), steps.tensions().map { it.repIndex })
        assertEquals(1, steps.filterIsInstance<Step.Switch>().size)
        assertEquals(2, steps.filterIsInstance<Step.Rest>().size) // one within each block
    }

    @Test
    fun `zero switch time emits no switch steps`() {
        val steps = RoutinePlan.build(
            routine(Protocol.SINGLE_HAND, Alternation.ALTERNATE_EACH_REP, reps = 2, switchSec = 0),
            prepSec = 0,
        )
        assertEquals(0, steps.filterIsInstance<Step.Switch>().size)
        assertEquals(listOf(Hand.LEFT, Hand.RIGHT, Hand.LEFT, Hand.RIGHT), steps.tensions().map { it.hand })
    }

    @Test
    fun `no get-ready when prep is zero`() {
        val steps = RoutinePlan.build(routine(Protocol.TWO_HAND, reps = 2), prepSec = 0)
        assertEquals(0, steps.filterIsInstance<Step.GetReady>().size)
    }
}
