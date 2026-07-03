package me.kitsu.hangy.data.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * Hardware-free [ScaleRepository] that emits a smooth synthetic pull at ~10 Hz. Used by Compose
 * previews and instrumented UI tests so screens can be exercised without a real scale.
 */
class FakeScaleRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val peakKg: Double = 45.0,
    private val periodMs: Long = 6_000L,
) : ScaleRepository {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableSharedFlow<ScaleReading>(replay = 1, extraBufferCapacity = 64)
    override val readings: SharedFlow<ScaleReading> = _readings.asSharedFlow()

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        _connectionState.value = ConnectionState.Searching
        job = scope.launch {
            var t = 0L
            while (isActive) {
                val phase = (t % periodMs).toDouble() / periodMs * 2 * Math.PI
                val kg = abs(sin(phase)) * peakKg
                val reading = ScaleReading(weightKg = kg, stable = true)
                _readings.tryEmit(reading)
                _connectionState.value = ConnectionState.Connected
                delay(SAMPLE_PERIOD_MS)
                t += SAMPLE_PERIOD_MS
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _connectionState.value = ConnectionState.Idle
    }

    private companion object {
        const val SAMPLE_PERIOD_MS = 100L
    }
}
