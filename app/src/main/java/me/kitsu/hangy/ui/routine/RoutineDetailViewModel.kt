package me.kitsu.hangy.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.kitsu.hangy.data.repository.MeasurementRepository
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.data.settings.SettingsRepository
import me.kitsu.hangy.domain.model.HistoryMetric
import me.kitsu.hangy.domain.model.Routine
import me.kitsu.hangy.domain.model.Session
import me.kitsu.hangy.domain.model.SessionDetail
import me.kitsu.hangy.domain.model.SessionSummary

data class RoutineDetailUiState(
    val routine: Routine? = null,
    val summaries: List<SessionSummary> = emptyList(),
    val metric: HistoryMetric = HistoryMetric.MAX,
)

class RoutineDetailViewModel(
    private val routineId: Long,
    routineRepository: RoutineRepository,
    private val measurementRepository: MeasurementRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val routine: StateFlow<Routine?> = routineRepository.observeById(routineId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null,
    )

    val summaries: StateFlow<List<SessionSummary>> =
        measurementRepository.observeSummaries(routineId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    val metric: StateFlow<HistoryMetric> = settingsRepository.settings
        .map { it.historyMetric }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HistoryMetric.MAX,
        )

    // Manually paged session list (newest first). A fresh snapshot per screen open; no new
    // dependency. loadMore() is called from the UI as the last row scrolls into view.
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached.asStateFlow()

    // Full detail (reps + raw stream) for the sessions whose cards are expanded, loaded on demand.
    private val _details = MutableStateFlow<Map<Long, SessionDetail>>(emptyMap())
    val details: StateFlow<Map<Long, SessionDetail>> = _details.asStateFlow()

    private var loading = false

    init {
        loadMore()
    }

    fun loadMore() {
        if (loading || _endReached.value) return
        loading = true
        viewModelScope.launch {
            val offset = _sessions.value.size
            val page = measurementRepository.sessionsPage(routineId, PAGE_SIZE, offset)
            _sessions.update { it + page }
            if (page.size < PAGE_SIZE) _endReached.value = true
            loading = false
        }
    }

    fun loadDetail(sessionId: Long) {
        if (_details.value.containsKey(sessionId)) return
        viewModelScope.launch {
            val detail = measurementRepository.sessionDetail(sessionId) ?: return@launch
            _details.update { it + (sessionId to detail) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PAGE_SIZE = 15
    }
}
