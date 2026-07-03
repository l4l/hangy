package me.kitsu.hangy.ui.routine

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.kitsu.hangy.R
import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.Routine

@Composable
fun Protocol.label(): String = when (this) {
    Protocol.SINGLE_HAND -> stringResource(R.string.protocol_single)
    Protocol.TWO_HAND -> stringResource(R.string.protocol_two)
}

@Composable
fun Hand.label(): String = when (this) {
    Hand.LEFT -> stringResource(R.string.hand_left)
    Hand.RIGHT -> stringResource(R.string.hand_right)
    Hand.BOTH -> stringResource(R.string.hand_both)
}

@Composable
fun Alternation.label(): String = when (this) {
    Alternation.ALL_ONE_THEN_OTHER -> stringResource(R.string.alt_all_one)
    Alternation.ALTERNATE_EACH_REP -> stringResource(R.string.alt_each_rep)
}

/** One-line timing summary, e.g. "5 reps · 7s on / 180s off". */
@Composable
fun Routine.timingLine(): String {
    val singleHand = protocol == Protocol.SINGLE_HAND
    val reps = if (singleHand) "$totalReps reps/hand" else "$totalReps reps"
    val base = "$reps · ${tensionSec}s on / ${restSec}s off"
    return if (singleHand && switchSec > 0) "$base · ${switchSec}s switch" else base
}
