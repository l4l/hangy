package me.kitsu.hangy.ui.measure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.kitsu.hangy.data.ble.ConnectionState
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.data.settings.AppSettings
import me.kitsu.hangy.data.settings.SettingsRepository
import me.kitsu.hangy.domain.model.Routine
import me.kitsu.hangy.domain.model.SessionDetail
import me.kitsu.hangy.domain.model.TargetType
import me.kitsu.hangy.session.LiveState
import me.kitsu.hangy.session.MeasureMode
import me.kitsu.hangy.session.ServiceHost
import me.kitsu.hangy.session.SessionController

/** Structural state that changes rarely (not on every reading). */
data class MeasureUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val routines: List<Routine> = emptyList(),
    val avgWindowSec: Int = AppSettings.DEFAULT_AVG_WINDOW_SEC,
    val soundEnabled: Boolean = true,
    val defaultBodyWeightKg: Double = AppSettings.DEFAULT_BODY_WEIGHT_KG,
    val defaultThresholdKg: Double = AppSettings.DEFAULT_START_THRESHOLD_KG,
    val mode: MeasureMode = MeasureMode.IDLE,
    val selectedRoutine: Routine? = null,
    val targetType: TargetType = TargetType.KG,
    val targetLow: Double = 0.0,
    val targetHigh: Double = 0.0,
    val sessionBodyWeightKg: Double = AppSettings.DEFAULT_BODY_WEIGHT_KG,
    val sessionThresholdKg: Double = AppSettings.DEFAULT_START_THRESHOLD_KG,
    val savedSessionId: Long? = null,
    val finishedSummary: SessionDetail? = null,
)

/**
 * Thin client over [SessionController]. The session outlives this ViewModel, so [onCleared] must
 * not stop the scale.
 */
class MeasureViewModel(
    private val controller: SessionController,
    private val serviceHost: ServiceHost,
    routineRepository: RoutineRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<MeasureUiState> = combine(
        controller.state,
        controller.connectionState,
        routineRepository.observeAll(),
        settingsRepository.settings,
    ) { session, connection, routines, settings ->
        MeasureUiState(
            connection = connection,
            routines = routines,
            avgWindowSec = settings.avgWindowSec,
            soundEnabled = settings.soundEnabled,
            defaultBodyWeightKg = settings.bodyWeightKg,
            defaultThresholdKg = settings.startThresholdKg,
            mode = session.mode,
            selectedRoutine = session.selectedRoutine,
            targetType = session.targetType,
            targetLow = session.targetLow,
            targetHigh = session.targetHigh,
            sessionBodyWeightKg = session.sessionBodyWeightKg,
            sessionThresholdKg = session.sessionThresholdKg,
            savedSessionId = session.savedSessionId,
            finishedSummary = session.finishedSummary,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), MeasureUiState())

    val live: StateFlow<LiveState> = controller.live

    /** The service calls [SessionController.connect] once it holds foreground importance. */
    fun connect() = serviceHost.start()

    fun disconnect() = controller.disconnect()

    fun selectRoutine(routine: Routine) = controller.selectRoutine(routine)

    fun setTargetType(type: TargetType) = controller.setTargetType(type)

    fun setTarget(low: Double, high: Double) = controller.setTarget(low, high)

    fun setSessionBodyWeight(kg: Double) = controller.setSessionBodyWeight(kg)

    fun setSessionThreshold(kg: Double) = controller.setSessionThreshold(kg)

    fun backToSelection() = controller.backToSelection()

    fun startTest() = controller.startTest()

    fun startRoutine() = controller.startRoutine()

    fun stopAndSave() = controller.stopAndSave()

    fun stopAndDiscard() = controller.stopAndDiscard()

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
