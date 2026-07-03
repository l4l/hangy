package me.kitsu.hangy.domain.model

/**
 * An immutable training protocol. Routines are never edited once created — the UI only supports
 * create, clone (create pre-filled) and delete. Any hold detail lives in the [name].
 *
 * [totalReps] counts differently per protocol: for [Protocol.TWO_HAND] it is the total number of
 * hangs; for [Protocol.SINGLE_HAND] it is the number of reps **per hand**. [switchSec] is the
 * changeover time allowed between hands and is only meaningful for single-hand routines.
 */
data class Routine(
    val id: Long = 0,
    val name: String,
    val protocol: Protocol,
    val alternation: Alternation? = null,
    val tensionSec: Int,
    val restSec: Int,
    val switchSec: Int = DEFAULT_SWITCH_SEC,
    val totalReps: Int,
    val createdAt: Long = 0,
) {
    init {
        require(name.isNotBlank()) { "Routine name must not be blank" }
        require(tensionSec > 0) { "Time under tension must be positive" }
        require(restSec >= 0) { "Rest must not be negative" }
        require(switchSec >= 0) { "Switch time must not be negative" }
        require(totalReps > 0) { "Total reps must be positive" }
        if (protocol == Protocol.SINGLE_HAND) {
            require(alternation != null) { "Single-hand routines require an alternation" }
        }
    }

    companion object {
        const val DEFAULT_SWITCH_SEC = 5
    }
}
