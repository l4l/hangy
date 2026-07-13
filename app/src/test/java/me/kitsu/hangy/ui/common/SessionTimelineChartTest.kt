package me.kitsu.hangy.ui.common

import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.RepResult
import me.kitsu.hangy.domain.model.Sample
import me.kitsu.hangy.domain.model.Session
import me.kitsu.hangy.domain.model.SessionDetail
import me.kitsu.hangy.domain.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTimelineChartTest {

    private fun session() = Session(
        id = 1,
        routineId = 1,
        startedAt = 0,
        bodyWeightKg = 70.0,
        targetType = TargetType.KG,
        targetLow = 20.0,
        targetHigh = 30.0,
    )

    private fun rep(index: Int, startMs: Long, endMs: Long) = RepResult(
        sessionId = 1,
        repIndex = index,
        hand = Hand.BOTH,
        maxKg = 25.0,
        avgKg = 22.0,
        actualTutMs = endMs - startMs,
        tStartMs = startMs,
        tEndMs = endMs,
    )

    private fun samples(vararg tMs: Long) = tMs.map { Sample(sessionId = 1, tOffsetMs = it, weightKg = it.toDouble()) }

    @Test
    fun `returns null when no rep has a tension window`() {
        val detail = SessionDetail(session(), listOf(rep(1, 0, 0)), samples(0, 100, 200))
        assertNull(collapseRests(detail))
    }

    @Test
    fun `segments are proportional, ordered by tension start and separated by the gap`() {
        // Reps listed out of order; 10s + 5s tension -> gap = 15_000 * 0.02 = 300ms.
        val detail = SessionDetail(
            session(),
            listOf(rep(2, 30_000, 35_000), rep(1, 5_000, 15_000)),
            emptyList(),
        )
        val collapsed = collapseRests(detail)!!
        assertEquals(
            listOf(RepSegment(1, 0, 10_000), RepSegment(2, 10_300, 15_300)),
            collapsed.segments,
        )
        assertEquals(15_300, collapsed.spanMs)
    }

    @Test
    fun `samples inside rep windows are shifted onto the collapsed axis, rest samples dropped`() {
        val detail = SessionDetail(
            session(),
            listOf(rep(1, 1_000, 2_000), rep(2, 5_000, 6_000)),
            samples(0, 1_000, 1_500, 2_000, 3_500, 5_000, 6_000, 7_000),
        )
        val collapsed = collapseRests(detail)!!
        val gap = (2_000L * 0.02).toLong()
        assertEquals(
            listOf(0L, 500L, 1_000L, 1_000L + gap, 2_000L + gap),
            collapsed.samples.map { it.tOffsetMs },
        )
        // Weights ride along unchanged (weight == original offset in this fixture).
        assertEquals(
            listOf(1_000.0, 1_500.0, 2_000.0, 5_000.0, 6_000.0),
            collapsed.samples.map { it.weightKg },
        )
    }

    @Test
    fun `windowless reps are excluded while windowed ones collapse`() {
        val detail = SessionDetail(
            session(),
            listOf(rep(1, 0, 0), rep(2, 4_000, 6_000)),
            samples(4_000, 5_000, 6_000),
        )
        val collapsed = collapseRests(detail)!!
        assertEquals(listOf(RepSegment(2, 0, 2_000)), collapsed.segments)
        assertEquals(listOf(0L, 1_000L, 2_000L), collapsed.samples.map { it.tOffsetMs })
    }

    @Test
    fun `gap is at least one millisecond for very short reps`() {
        val detail = SessionDetail(
            session(),
            listOf(rep(1, 0, 10), rep(2, 100, 110)),
            emptyList(),
        )
        val collapsed = collapseRests(detail)!!
        assertEquals(listOf(RepSegment(1, 0, 10), RepSegment(2, 11, 21)), collapsed.segments)
    }
}
