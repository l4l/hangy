package me.kitsu.hangy.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.domain.model.Routine

class RoutineListViewModel(private val repository: RoutineRepository) : ViewModel() {

    val routines: StateFlow<List<Routine>> = repository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
