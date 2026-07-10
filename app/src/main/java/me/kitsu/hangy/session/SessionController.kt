package me.kitsu.hangy.session

import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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

/** Structural session state that changes rarely (not on every reading). */
data class SessionState(
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

/** High-frequency (~10 Hz) live data, kept separate from [SessionState]. */
data class LiveState(
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

/**
 * Owns a measurement session end to end: scale scan, [RoutineEngine], rep aggregation, sound cues
 * and persistence. A process singleton so a running routine survives Activity death; [ScaleService]
 * holds foreground importance while [isActive] is true so the scan keeps its reading rate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionController(
    private val scale: ScaleRepository,
    private val measurementRepository: MeasurementRepository,
    settingsRepository: SettingsRepository,
    private val engine: RoutineEngine,
    private val sound: SoundCue = SoundCue.NoOp,
    private val scope: CoroutineScope,
    private val elapsedClock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    // Serialized (limitedParallelism 1) so measurement aggregation stays single-threaded off the UI thread.
    private val processing: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    val connectionState: StateFlow<ConnectionState> get() = scale.connectionState

    private val _isActive = MutableStateFlow(false)

    /** True while the scan should be running. [ScaleService] stops itself when this goes false. */
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

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

    private var defaults = AppSettings()
    private var soundEnabledNow = true
    private var lastTickSec = -1
    private var previousPhase: PhaseType? = null

    init {
        scope.launch {
            settingsRepository.settings.collect { settings -> onSettings(settings) }
        }
    }

    private suspend fun onSettings(settings: AppSettings) {
        defaults = settings
        _live.update { it.copy(windowMs = settings.avgWindowSec * 1_000L) }
        _state.update {
            val idle = it.mode == MeasureMode.IDLE || it.mode == MeasureMode.FINISHED
            it.copy(
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

    fun connect() {
        _isActive.value = true
        scale.start()
        // start() reports failures synchronously; without a running scan the service has no job.
        if (!scale.connectionState.value.isScanning) _isActive.value = false
    }

    /**
     * Stops scanning and returns to idle. Leaves the aggregation buffers untouched — a shutdown
     * save may still be queued on [processing]; the next [startMeasurement] resets them anyway.
     */
    fun disconnect() {
        stopCollection()
        scale.stop()
        _isActive.value = false
        _state.update { it.copy(mode = MeasureMode.IDLE, selectedRoutine = null, finishedSummary = null) }
    }

    fun selectRoutine(routine: Routine) {
        _state.update {
            it.copy(
                mode = MeasureMode.CONFIG,
                selectedRoutine = routine,
                sessionBodyWeightKg = defaults.bodyWeightKg,
                sessionThresholdKg = defaults.startThresholdKg,
                targetLow = 0.0,
                targetHigh = 0.0,
                savedSessionId = null,
            )
        }
        // Pre-fill the target from the routine's last session; ignored if the user has since
        // picked another.
        scope.launch {
            val last = measurementRepository.lastTarget(routine.id) ?: return@launch
            _state.update {
                if (it.selectedRoutine?.id != routine.id) return@update it
                it.copy(targetType = last.type, targetLow = last.low, targetHigh = last.high)
            }
        }
    }

    fun setTargetType(type: TargetType) = _state.update { it.copy(targetType = type) }

    fun setTarget(low: Double, high: Double) = _state.update { it.copy(targetLow = low, targetHigh = high) }

    fun setSessionBodyWeight(kg: Double) = _state.update { it.copy(sessionBodyWeightKg = kg) }

    fun setSessionThreshold(kg: Double) = _state.update { it.copy(sessionThresholdKg = kg) }

    fun backToSelection() {
        stopCollection()
        _state.update { it.copy(mode = MeasureMode.IDLE, selectedRoutine = null, finishedSummary = null) }
        scope.launch(processing) { resetMeasurement() }
    }

    fun startTest() {
        _state.update { it.copy(mode = MeasureMode.TEST, savedSessionId = null) }
        startMeasurement(taggingReps = false, targetLowKg = 0.0, targetHighKg = 0.0, routine = null, threshold = 0.0)
    }

    fun startRoutine() {
        val state = _state.value
        val routine = state.selectedRoutine ?: return
        val bodyWeight = state.sessionBodyWeightKg
        _state.update { it.copy(mode = MeasureMode.RUNNING, savedSessionId = null) }
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
        scope.launch { stopAndSaveInternal() }
    }

    /** Stop without saving — used for Test mode and for discarding a partial routine. */
    fun stopAndDiscard() {
        val mode = _state.value.mode
        stopCollection()
        _state.update {
            it.copy(mode = if (mode == MeasureMode.TEST) MeasureMode.IDLE else MeasureMode.CONFIG)
        }
        scope.launch(processing) { resetMeasurement() }
    }

    /**
     * Salvage the in-flight session before the process goes away; suspends until the partial
     * session is on disk so the caller can hold the foreground service alive until then.
     */
    suspend fun finishForShutdown() {
        when (_state.value.mode) {
            MeasureMode.RUNNING -> stopAndSaveInternal()
            MeasureMode.TEST -> stopAndDiscard()
            else -> Unit
        }
    }

    private suspend fun stopAndSaveInternal() {
        val state = _state.value
        if (state.mode != MeasureMode.RUNNING) return
        val routine = state.selectedRoutine
        stopCollection()
        withContext(processing) {
            if (routine != null && rawSamples.isNotEmpty()) {
                saveSession(routine, completed = false)
            } else {
                _state.update { it.copy(mode = MeasureMode.CONFIG) }
            }
        }
    }

    private fun onEngine(state: EngineState, routine: Routine) {
        latestEngine = state
        _live.update { it.copy(engineState = state) }
        handleSound(state)
        if (state.finished) {
            // Save from a fresh coroutine — can't cancel-then-save inside the just-finished engine coroutine.
            collectJob?.cancel()
            scope.launch(processing) { saveSession(routine, completed = true) }
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
        val s = _state.value
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
        val summary = SessionDetail(session.copy(id = id), repResults, samples)
        _state.update { it.copy(mode = MeasureMode.FINISHED, savedSessionId = id, finishedSummary = summary) }
    }

    private fun startMeasurement(taggingReps: Boolean, targetLowKg: Double, targetHighKg: Double, routine: Routine?, threshold: Double) {
        stopCollection()
        recordThresholdKg = threshold
        collectJob = scope.launch(processing) {
            resetMeasurement()
            _live.update { it.copy(targetLowKg = targetLowKg, targetHighKg = targetHighKg) }
            startWall = wallClock()
            startElapsed = elapsedClock()
            scale.readings.collect { reading ->
                onReading(elapsedClock() - startElapsed, reading.weightKg, taggingReps)
            }
        }
        if (routine != null) {
            engineJob = scope.launch(processing) {
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
                // Persist the raw curve only while loaded, so rests contribute nothing to memory.
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
