package me.kitsu.hangy.data.db

import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.Routine

/** Pre-created routines inserted on first launch so the app is usable out of the box. */
object Seed {
    fun defaultRoutines(now: Long): List<Routine> = listOf(
        Routine(
            name = "Two-hand max hang · 20 mm half-crimp",
            protocol = Protocol.TWO_HAND,
            tensionSec = 7,
            restSec = 180,
            totalReps = 5,
            createdAt = now,
        ),
        Routine(
            name = "Single-hand repeaters · alternating",
            protocol = Protocol.SINGLE_HAND,
            alternation = Alternation.ALTERNATE_EACH_REP,
            tensionSec = 7,
            restSec = 3,
            switchSec = 3,
            totalReps = 6, // per hand
            createdAt = now + 1,
        ),
        Routine(
            name = "Single-hand 3-finger open-hand block",
            protocol = Protocol.SINGLE_HAND,
            alternation = Alternation.ALL_ONE_THEN_OTHER,
            tensionSec = 10,
            restSec = 60,
            switchSec = 15,
            totalReps = 3, // per hand
            createdAt = now + 2,
        ),
    )
}
