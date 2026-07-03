package me.kitsu.hangy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTargetTest {

    @Test
    fun `kg target resolves to itself`() {
        val session = Session(
            routineId = 1,
            startedAt = 0,
            bodyWeightKg = 80.0,
            targetType = TargetType.KG,
            targetLow = 40.0,
            targetHigh = 50.0,
        )
        assertEquals(40.0, session.targetLowKg, 1e-9)
        assertEquals(50.0, session.targetHighKg, 1e-9)
    }

    @Test
    fun `percent-bodyweight target uses the bodyweight stored on the session`() {
        val session = Session(
            routineId = 1,
            startedAt = 0,
            bodyWeightKg = 80.0,
            targetType = TargetType.PERCENT_BW,
            targetLow = 50.0,
            targetHigh = 60.0,
        )
        assertEquals(40.0, session.targetLowKg, 1e-9)
        assertEquals(48.0, session.targetHighKg, 1e-9)
    }

    @Test
    fun `historical percent target is unaffected by a later bodyweight change`() {
        // Two sessions of the same 50% target recorded at different bodyweights must resolve
        // against the weight captured at the time — not a single "current" value.
        val lighter = Session(
            routineId = 1,
            startedAt = 0,
            bodyWeightKg = 70.0,
            targetType = TargetType.PERCENT_BW,
            targetLow = 50.0,
            targetHigh = 50.0,
        )
        val heavier = lighter.copy(bodyWeightKg = 90.0)
        assertEquals(35.0, lighter.targetLowKg, 1e-9)
        assertEquals(45.0, heavier.targetLowKg, 1e-9)
    }
}
