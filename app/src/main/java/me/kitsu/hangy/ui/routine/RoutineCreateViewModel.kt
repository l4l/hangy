package me.kitsu.hangy.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.Routine

data class RoutineFormState(
    val name: String = "",
    val protocol: Protocol = Protocol.TWO_HAND,
    val alternation: Alternation = Alternation.ALTERNATE_EACH_REP,
    val tensionSec: String = "7",
    val restSec: String = "180",
    val switchSec: String = "5",
    val totalReps: String = "5",
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
            (tensionSec.toIntOrNull()?.let { it > 0 } == true) &&
            (restSec.toIntOrNull()?.let { it >= 0 } == true) &&
            (switchSec.toIntOrNull()?.let { it >= 0 } == true) &&
            (totalReps.toIntOrNull()?.let { it > 0 } == true)
}

class RoutineCreateViewModel(private val repository: RoutineRepository, private val cloneFromId: Long? = null) : ViewModel() {

    private val _form = MutableStateFlow(RoutineFormState())
    val form: StateFlow<RoutineFormState> = _form.asStateFlow()

    init {
        if (cloneFromId != null && cloneFromId > 0) {
            viewModelScope.launch { repository.getById(cloneFromId)?.let { prefill(it) } }
        }
    }

    private fun prefill(routine: Routine) {
        _form.value = RoutineFormState(
            name = routine.name + " (copy)",
            protocol = routine.protocol,
            alternation = routine.alternation ?: Alternation.ALTERNATE_EACH_REP,
            tensionSec = routine.tensionSec.toString(),
            restSec = routine.restSec.toString(),
            switchSec = routine.switchSec.toString(),
            totalReps = routine.totalReps.toString(),
        )
    }

    fun update(transform: (RoutineFormState) -> RoutineFormState) = _form.update(transform)

    /** Builds and persists the routine; invokes [onSaved] with the new id on success. */
    fun save(onSaved: (Long) -> Unit) {
        val state = _form.value
        if (!state.isValid) return
        val routine = state.toRoutine()
        viewModelScope.launch {
            val id = repository.create(routine)
            onSaved(id)
        }
    }

    private fun RoutineFormState.toRoutine(): Routine {
        val singleHand = protocol == Protocol.SINGLE_HAND
        return Routine(
            name = name.trim(),
            protocol = protocol,
            alternation = if (singleHand) alternation else null,
            tensionSec = tensionSec.toInt(),
            restSec = restSec.toInt(),
            switchSec = switchSec.toInt(),
            totalReps = totalReps.toInt(),
        )
    }
}
