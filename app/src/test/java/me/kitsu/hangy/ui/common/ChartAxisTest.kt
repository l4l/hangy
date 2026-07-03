package me.kitsu.hangy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisTest {

    private fun assertTicks(expected: List<Double>, actual: List<Double>) {
        assertEquals("size", expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-6) }
    }

    @Test
    fun `picks a 2-unit step for a small range`() {
        assertTicks(listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0), niceTicks(0.0, 12.0))
    }

    @Test
    fun `picks a 20-unit step for a large range`() {
        assertTicks(listOf(0.0, 20.0, 40.0, 60.0, 80.0), niceTicks(0.0, 90.0))
    }

    @Test
    fun `min-axis floor range ticks every 2`() {
        assertTicks(listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0), niceTicks(0.0, 10.0))
    }

    @Test
    fun `negative bottom only emits ticks within the range`() {
        // Ticks must all lie inside [min, max] and be multiples of the chosen step.
        val ticks = niceTicks(-5.0, 30.0)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it in -5.0..30.0 })
        assertEquals(0.0, ticks.first(), 1e-6)
    }

    @Test
    fun `relative live window spans negative to zero`() {
        assertTicks(listOf(-10.0, -8.0, -6.0, -4.0, -2.0, 0.0), niceTicks(-10.0, 0.0, target = 4))
    }

    @Test
    fun `degenerate range yields no ticks`() {
        assertTrue(niceTicks(0.0, 0.0).isEmpty())
        assertTrue(niceTicks(5.0, 5.0).isEmpty())
        assertTrue(niceTicks(10.0, 3.0).isEmpty())
    }

    @Test
    fun `non-finite input yields no ticks`() {
        assertTrue(niceTicks(Double.NaN, 10.0).isEmpty())
        assertTrue(niceTicks(0.0, Double.POSITIVE_INFINITY).isEmpty())
    }

    @Test
    fun `formatAxisSeconds drops decimal for whole seconds`() {
        assertEquals("5s", formatAxisSeconds(5_000))
        assertEquals("0s", formatAxisSeconds(0))
        assertEquals("-10s", formatAxisSeconds(-10_000))
        assertEquals("7.5s", formatAxisSeconds(7_500))
    }
}
