package me.kitsu.hangy.data.db

import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.Routine
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun `two-hand routine survives an entity round trip`() {
        val routine = Routine(
            id = 7,
            name = "test",
            protocol = Protocol.TWO_HAND,
            tensionSec = 7,
            restSec = 120,
            switchSec = 5,
            totalReps = 5,
            createdAt = 42,
        )
        assertEquals(routine, routine.toEntity().toDomain())
    }

    @Test
    fun `single-hand alternation survives an entity round trip`() {
        val routine = Routine(
            name = "bare",
            protocol = Protocol.SINGLE_HAND,
            alternation = Alternation.ALTERNATE_EACH_REP,
            tensionSec = 7,
            restSec = 3,
            totalReps = 6,
        )
        assertEquals(routine, routine.toEntity().toDomain())
    }
}
