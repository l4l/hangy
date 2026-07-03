package me.kitsu.hangy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.kitsu.hangy.data.settings.AppSettings
import me.kitsu.hangy.data.settings.SettingsRepository
import me.kitsu.hangy.domain.model.HistoryMetric

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<AppSettings> = settings.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AppSettings(),
    )

    fun setBodyWeight(kg: Double) = viewModelScope.launch { settings.setBodyWeight(kg) }

    fun setAvgWindow(seconds: Int) = viewModelScope.launch { settings.setAvgWindowSec(seconds) }

    fun setHistoryMetric(metric: HistoryMetric) = viewModelScope.launch { settings.setHistoryMetric(metric) }

    fun setStartThreshold(kg: Double) = viewModelScope.launch { settings.setStartThreshold(kg) }

    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch { settings.setSoundEnabled(enabled) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
