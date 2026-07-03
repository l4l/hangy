package me.kitsu.hangy.ui.measure

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kitsu.hangy.audio.SoundCue
import me.kitsu.hangy.data.ble.ConnectionState
import me.kitsu.hangy.data.ble.ScaleRepository
import me.kitsu.hangy.data.repository.CompletedSession
import me.kitsu.hangy.data.repository.MeasurementRepository
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.data.settings.AppSettings
import me.kitsu.hangy.data.settings.SettingsRepository
import me.kitsu.hangy.domain.engine.EngineState
import me.kitsu.hangy.domain.engine.PhaseType
import me.kitsu.hangy.domain.engine.RoutineEngine
import me.kitsu.hangy.domain.engine.TimedSample
import me.kitsu.hangy.domain.engine.WindowStats
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.RepResult
import me.kitsu.hangy.domain.model.Routine
import me.kitsu.hangy.domain.model.Sample
import me.kitsu.hangy.domain.model.Session
import me.kitsu.hangy.domain.model.SessionDetail
import me.kitsu.hangy.domain.model.TargetType
import kotlin.math.ceil

enum class MeasureMode { IDLE, CONFIG, RUNNING, TEST, FINISHED }

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

/** High-frequency (~10 Hz) live data, kept separate from [MeasureUiState]. */
data class LiveUiState(
    val currentKg: Double = 0.0,
    val avgKg: Double = 0.0,
    val maxKg: Double = 0.0,
    val plot: List<TimedSample> = emptyList(),
    val latestTMs: Long = 0,
    val windowMs: Long = AppSettings.DEFAULT_AVG_WINDOW_SEC * 1_000L,
    val targetLowKg: Double = 0.0,
    val targetHighKg: Double = 0.0,
    val engineState: EngineState? = null,
)

/** Accumulates per-rep statistics from readings that arrive while a rep is under tension. */
private class RepAccumulator(val hand: Hand) {
    private var sum = 0.0
    private var count = 0
    private var max = 0.0
    private var firstTMs = -1L
    private var lastTMs = 0L

    fun add(tMs: Long, kg: Double) {
        if (firstTMs < 0) firstTMs = tMs
        lastTMs = tMs
        sum += kg
        count++
        if (kg > max) max = kg
    }

    fun toResult(repIndex: Int): RepResult = RepResult(
        sessionId = 0,
        repIndex = repIndex,
        hand = hand,
        maxKg = max,
        avgKg = if (count == 0) 0.0 else sum / count,
        actualTutMs = (lastTMs - firstTMs).coerceAtLeast(0),
        tStartMs = firstTMs.coerceAtLeast(0),
        tEndMs = lastTMs.coerceAtLeast(0),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModel(
    private val scale: ScaleRepository,
    private val routineRepository: RoutineRepository,
    private val measurementRepository: MeasurementRepository,
    private val settingsRepository: SettingsRepository,
    private val engine: RoutineEngine,
    private val sound: SoundCue = SoundCue.NoOp,
    private val elapsedClock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    // Serialized (limitedParallelism 1) so measurement aggregation stays single-threaded off the UI thread.
    private val processing: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    private val _live = MutableStateFlow(LiveUiState())
    val live: StateFlow<LiveUiState> = _live.asStateFlow()

    private val stats = WindowStats(windowMs = AppSettings.DEFAULT_AVG_WINDOW_SEC * 1_000L)
    private val rawSamples = mutableListOf<Sample>()
    private val reps = linkedMapOf<Pair<Hand, Int>, RepAccumulator>()
    private val currentWeight = MutableStateFlow(0.0)

    private var tensionSum = 0.0
    private var tensionCount = 0
    private var startWall = 0L
    private var startElapsed = 0L
    private var recordThresholdKg = 0.0

    private var collectJob: Job? = null
    private var engineJob: Job? = null
    private var latestEngine: EngineState? = null

    private var soundEnabledNow = true
    private var lastTickSec = -1
    private var previousPhase: PhaseType? = null

    init {
        viewModelScope.launch {
            scale.connectionState.collect { state -> _uiState.update { it.copy(connection = state) } }
        }
        viewModelScope.launch {
            routineRepository.observeAll().collect { list -> _uiState.update { it.copy(routines = list) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings -> onSettings(settings) }
        }
    }

    private suspend fun onSettings(settings: AppSettings) {
        _live.update { it.copy(windowMs = settings.avgWindowSec * 1_000L) }
        _uiState.update {
            val idle = it.mode == MeasureMode.IDLE || it.mode == MeasureMode.FINISHED
            it.copy(
                avgWindowSec = settings.avgWindowSec,
                soundEnabled = settings.soundEnabled,
                defaultBodyWeightKg = settings.bodyWeightKg,
                defaultThresholdKg = settings.startThresholdKg,
                // Keep the session values tracking the defaults until the user starts configuring.
                sessionBodyWeightKg = if (idle) settings.bodyWeightKg else it.sessionBodyWeightKg,
                sessionThresholdKg = if (idle) settings.startThresholdKg else it.sessionThresholdKg,
            )
        }
        // Read by the aggregation thread, so update them there to stay confined to it.
        withContext(processing) {
            stats.windowMs = settings.avgWindowSec * 1_000L
            soundEnabledNow = settings.soundEnabled
        }
    }

    fun connect() = scale.start()

    fun disconnect() = scale.stop()

    fun selectRoutine(routine: Routine) {
        _uiState.update {
            it.copy(
                mode = MeasureMode.CONFIG,
                selectedRoutine = routine,
                sessionBodyWeightKg = it.defaultBodyWeightKg,
                sessionThresholdKg = it.defaultThresholdKg,
                targetLow = 0.0,
                targetHigh = 0.0,
                savedSessionId = null,
            )
        }
        // Pre-fill the target from the routine's most recent session so it need not be re-entered
        // every time. Loaded off the main thread; ignored if the user has since picked another.
        viewModelScope.launch {
            val last = measurementRepository.lastTarget(routine.id) ?: return@launch
            _uiState.update {
                if (it.selectedRoutine?.id != routine.id) return@update it
                it.copy(targetType = last.type, targetLow = last.low, targetHigh = last.high)
            }
        }
    }

    fun setTargetType(type: TargetType) = _uiState.update { it.copy(targetType = type) }

    fun setTarget(low: Double, high: Double) = _uiState.update { it.copy(targetLow = low, targetHigh = high) }

    fun setSessionBodyWeight(kg: Double) = _uiState.update { it.copy(sessionBodyWeightKg = kg) }

    fun setSessionThreshold(kg: Double) = _uiState.update { it.copy(sessionThresholdKg = kg) }

    fun backToSelection() {
        stopCollection()
        _uiState.update {
            it.copy(mode = MeasureMode.IDLE, selectedRoutine = null, finishedSummary = null)
        }
        viewModelScope.launch(processing) { resetMeasurement() }
    }

    fun startTest() {
        _uiState.update { it.copy(mode = MeasureMode.TEST, savedSessionId = null) }
        startMeasurement(taggingReps = false, targetLowKg = 0.0, targetHighKg = 0.0, routine = null, threshold = 0.0)
    }

    fun startRoutine() {
        val state = _uiState.value
        val routine = state.selectedRoutine ?: return
        val bodyWeight = state.sessionBodyWeightKg
        _uiState.update { it.copy(mode = MeasureMode.RUNNING, savedSessionId = null) }
        startMeasurement(
            taggingReps = true,
            targetLowKg = resolveKg(state.targetLow, state.targetType, bodyWeight),
            targetHighKg = resolveKg(state.targetHigh, state.targetType, bodyWeight),
            routine = routine,
            threshold = state.sessionThresholdKg,
        )
    }

    /** Stop a routine early and keep what was measured as a partial session (user confirmed). */
    fun stopAndSave() {
        val state = _uiState.value
        if (state.mode != MeasureMode.RUNNING) return
        val routine = state.selectedRoutine
        stopCollection()
        viewModelScope.launch(processing) {
            if (routine != null && rawSamples.isNotEmpty()) {
                saveSession(routine, completed = false)
            } else {
                _uiState.update { it.copy(mode = MeasureMode.CONFIG) }
            }
        }
    }

    /** Stop without saving — used for Test mode and for discarding a partial routine. */
    fun stopAndDiscard() {
        val mode = _uiState.value.mode
        stopCollection()
        _uiState.update {
            it.copy(mode = if (mode == MeasureMode.TEST) MeasureMode.IDLE else MeasureMode.CONFIG)
        }
        viewModelScope.launch(processing) { resetMeasurement() }
    }

    private fun onEngine(state: EngineState, routine: Routine) {
        latestEngine = state
        _live.update { it.copy(engineState = state) }
        handleSound(state)
        if (state.finished) {
            // The engine flow ends on its own here; stop ingesting readings and save from a fresh
            // coroutine (can't cancel-then-save within the just-finished engine coroutine).
            collectJob?.cancel()
            viewModelScope.launch(processing) { saveSession(routine, completed = true) }
        }
    }

    private fun handleSound(state: EngineState) {
        if (!soundEnabledNow) {
            previousPhase = state.phase
            return
        }
        val prev = previousPhase
        if (state.phase != prev) {
            lastTickSec = -1
            when {
                state.phase == PhaseType.TENSION && prev != PhaseType.TENSION -> sound.start()
                prev == PhaseType.TENSION && state.phase != PhaseType.TENSION -> sound.end()
            }
        }
        if (state.phase in COUNTDOWN_PHASES) {
            val sec = ceil(state.remainingMs / MILLIS_PER_SEC.toDouble()).toInt()
            if (sec in 1..COUNTDOWN_SECONDS && sec != lastTickSec) {
                sound.tick()
                lastTickSec = sec
            }
        }
        previousPhase = state.phase
    }

    private suspend fun saveSession(routine: Routine, completed: Boolean) {
        val s = _uiState.value
        val session = Session(
            routineId = routine.id,
            startedAt = startWall,
            bodyWeightKg = s.sessionBodyWeightKg,
            targetType = s.targetType,
            targetLow = s.targetLow,
            targetHigh = s.targetHigh,
            maxLoadKg = stats.sessionMaxKg,
            avgLoadKg = if (tensionCount == 0) 0.0 else tensionSum / tensionCount,
            completed = completed,
        )
        val repResults = reps.map { (key, acc) -> acc.toResult(key.second) }
        val samples = rawSamples.toList()
        val record = CompletedSession(session, repResults, samples)
        val id = measurementRepository.save(record)
        // Build the summary from what we just measured (no DB round-trip); the persisted id lets
        // the detail charts resolve the session's target.
        val summary = SessionDetail(session.copy(id = id), repResults, samples)
        _uiState.update { it.copy(mode = MeasureMode.FINISHED, savedSessionId = id, finishedSummary = summary) }
    }

    private fun startMeasurement(taggingReps: Boolean, targetLowKg: Double, targetHighKg: Double, routine: Routine?, threshold: Double) {
        stopCollection()
        recordThresholdKg = threshold
        collectJob = viewModelScope.launch(processing) {
            resetMeasurement()
            _live.update { it.copy(targetLowKg = targetLowKg, targetHighKg = targetHighKg) }
            startWall = wallClock()
            startElapsed = elapsedClock()
            scale.readings.collect { reading ->
                onReading(elapsedClock() - startElapsed, reading.weightKg, taggingReps)
            }
        }
        if (routine != null) {
            engineJob = viewModelScope.launch(processing) {
                engine.run(
                    routine = routine,
                    awaitPull = { currentWeight.first { kg -> kg >= threshold } },
                ).collect { engineState -> onEngine(engineState, routine) }
            }
        }
    }

    private fun onReading(tMs: Long, kg: Double, taggingReps: Boolean) {
        stats.add(tMs, kg)
        currentWeight.value = kg

        if (taggingReps) {
            val engineState = latestEngine
            if (engineState != null && engineState.phase == PhaseType.TENSION && engineState.hand != null) {
                val acc = reps.getOrPut(engineState.hand to engineState.repIndex) {
                    RepAccumulator(engineState.hand)
                }
                acc.add(tMs, kg)
                tensionSum += kg
                tensionCount++
                // Persist the raw curve only while actually loaded (at/above the start threshold),
                // so rests contribute nothing to memory.
                if (kg >= recordThresholdKg) {
                    rawSamples += Sample(sessionId = 0, tOffsetMs = tMs, weightKg = kg)
                }
            }
        }

        _live.update {
            it.copy(
                currentKg = stats.currentKg,
                avgKg = stats.windowAvgKg,
                maxKg = stats.sessionMaxKg,
                plot = stats.windowSamples(),
                latestTMs = tMs,
            )
        }
    }

    private fun stopCollection() {
        collectJob?.cancel()
        collectJob = null
        engineJob?.cancel()
        engineJob = null
    }

    private fun resetMeasurement() {
        stats.reset()
        rawSamples.clear()
        reps.clear()
        tensionSum = 0.0
        tensionCount = 0
        latestEngine = null
        currentWeight.value = 0.0
        lastTickSec = -1
        previousPhase = null
        _live.update {
            it.copy(
                currentKg = 0.0,
                avgKg = 0.0,
                maxKg = 0.0,
                plot = emptyList(),
                latestTMs = 0,
                engineState = null,
                targetLowKg = 0.0,
                targetHighKg = 0.0,
            )
        }
    }

    private fun resolveKg(value: Double, type: TargetType, bodyWeightKg: Double): Double = when (type) {
        TargetType.KG -> value
        TargetType.PERCENT_BW -> value / 100.0 * bodyWeightKg
    }

    override fun onCleared() {
        stopCollection()
        scale.stop()
        sound.release()
        super.onCleared()
    }

    private companion object {
        const val MILLIS_PER_SEC = 1_000L
        const val COUNTDOWN_SECONDS = 3
        val COUNTDOWN_PHASES = setOf(
            PhaseType.GET_READY,
            PhaseType.TENSION,
            PhaseType.REST,
            PhaseType.SWITCH,
        )
    }
}
