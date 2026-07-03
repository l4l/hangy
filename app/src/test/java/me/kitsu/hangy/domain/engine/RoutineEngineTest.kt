package me.kitsu.hangy.domain.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEngineTest {

    private val engine = RoutineEngine()

    @Test
    fun `two-hand emits one state per second-step and a final DONE`() = runTest {
        val routine = Routine(
            name = "r",
            protocol = Protocol.TWO_HAND,
            tensionSec = 1,
            restSec = 1,
            totalReps = 2,
        )

        val states = engine.run(routine, prepSec = 0, tickMs = 1_000).toList()

        assertEquals(
            listOf(PhaseType.TENSION, PhaseType.REST, PhaseType.TENSION, PhaseType.DONE),
            states.map { it.phase },
        )
        assertEquals(listOf(1, 1, 2, 2), states.map { it.repIndex })
        assertTrue(states.last().finished)
        assertEquals(Hand.BOTH, states.first().hand)
    }

    @Test
    fun `single-hand alternation produces left then right tensions`() = runTest {
        val routine = Routine(
            name = "r",
            protocol = Protocol.SINGLE_HAND,
            alternation = Alternation.ALTERNATE_EACH_REP,
            tensionSec = 1,
            restSec = 0,
            switchSec = 0,
            totalReps = 1,
        )

        val hands = engine.run(routine, prepSec = 0, tickMs = 1_000).toList()
            .filter { it.phase == PhaseType.TENSION }
            .map { it.hand }

        assertEquals(listOf(Hand.LEFT, Hand.RIGHT), hands)
    }

    @Test
    fun `rest exposes the upcoming hand as the next action`() = runTest {
        val routine = Routine(
            name = "r",
            protocol = Protocol.TWO_HAND,
            tensionSec = 1,
            restSec = 1,
            totalReps = 2,
        )
        val rest = engine.run(routine, prepSec = 0, tickMs = 1_000).toList()
            .first { it.phase == PhaseType.REST }
        assertEquals(Hand.BOTH, rest.upcomingHand)
        assertEquals(2, rest.upcomingRepIndex)
    }

    @Test
    fun `awaitPull gates the tension until the threshold is crossed`() = runTest {
        val routine = Routine(
            name = "r",
            protocol = Protocol.TWO_HAND,
            tensionSec = 1,
            restSec = 0,
            totalReps = 1,
        )
        val gate = CompletableDeferred<Unit>()
        val states = mutableListOf<EngineState>()
        val job = launch {
            engine.run(routine, prepSec = 0, tickMs = 1_000, awaitPull = { gate.await() })
                .collect { states += it }
        }

        advanceUntilIdle()
        // Blocked waiting for the pull: WAITING emitted, no TENSION yet.
        assertEquals(PhaseType.WAITING, states.last().phase)
        assertFalse(states.any { it.phase == PhaseType.TENSION })

        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(states.any { it.phase == PhaseType.TENSION })
        assertTrue(states.last().finished)
        job.join()
    }
}
