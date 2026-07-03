package me.kitsu.hangy.data.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of live weight readings from a scale. Abstracted behind an interface so the UI,
 * previews and tests can swap in a [FakeScaleRepository] without any Bluetooth hardware.
 */
interface ScaleRepository {
    val connectionState: StateFlow<ConnectionState>
    val readings: Flow<ScaleReading>

    /** Begins scanning / streaming. Safe to call when already started. */
    fun start()

    /** Stops scanning / streaming to save battery. */
    fun stop()
}
