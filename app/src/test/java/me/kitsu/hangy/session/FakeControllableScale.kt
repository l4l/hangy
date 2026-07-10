package me.kitsu.hangy.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import me.kitsu.hangy.data.ble.ConnectionState
import me.kitsu.hangy.data.ble.ScaleReading
import me.kitsu.hangy.data.ble.ScaleRepository

/** Test double that lets a test push readings and connection states on demand. */
class FakeControllableScale : ScaleRepository {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _readings = MutableSharedFlow<ScaleReading>(replay = 0, extraBufferCapacity = 64)
    override val readings: SharedFlow<ScaleReading> = _readings

    var started = false
        private set

    override fun start() {
        started = true
        _connectionState.value = ConnectionState.Searching
    }

    override fun stop() {
        started = false
        _connectionState.value = ConnectionState.Idle
    }

    fun emit(kg: Double) {
        _connectionState.value = ConnectionState.Connected
        _readings.tryEmit(ScaleReading(weightKg = kg, stable = true))
    }
}
