package me.kitsu.hangy.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowStatsTest {

    @Test
    fun `average is over samples currently inside the window`() {
        val stats = WindowStats(windowMs = 1_000)
        stats.add(0, 10.0)
        stats.add(500, 20.0)
        stats.add(1_000, 30.0)
        assertEquals(20.0, stats.windowAvgKg, 1e-9)
        assertEquals(30.0, stats.currentKg, 1e-9)
    }

    @Test
    fun `samples older than the window are evicted`() {
        val stats = WindowStats(windowMs = 1_000)
        stats.add(0, 10.0)
        stats.add(500, 20.0)
        stats.add(1_000, 30.0)
        stats.add(1_600, 40.0) // cutoff 600 evicts t=0 and t=500
        assertEquals(35.0, stats.windowAvgKg, 1e-9)
        assertEquals(2, stats.windowSamples().size)
    }

    @Test
    fun `session max persists even after the peak leaves the window`() {
        val stats = WindowStats(windowMs = 1_000)
        stats.add(0, 55.0)
        stats.add(2_000, 10.0) // 55 kg is now outside the window
        assertEquals(55.0, stats.sessionMaxKg, 1e-9)
        assertEquals(10.0, stats.windowAvgKg, 1e-9)
    }

    @Test
    fun `reset clears everything`() {
        val stats = WindowStats(windowMs = 1_000)
        stats.add(0, 55.0)
        stats.reset()
        assertEquals(0.0, stats.sessionMaxKg, 1e-9)
        assertEquals(0.0, stats.currentKg, 1e-9)
        assertEquals(0, stats.windowSamples().size)
    }
}
