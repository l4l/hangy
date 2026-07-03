package me.kitsu.hangy.data.ble

/**
 * Because the WH-C06 is advertisement-based, "connected" is a derived state: we are
 * [Connected] as long as advertisements keep arriving, and fall back to [SignalLost] when
 * they stop.
 */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Searching : ConnectionState
    data object Connected : ConnectionState
    data object BluetoothOff : ConnectionState
    data object PermissionRequired : ConnectionState
    data object SignalLost : ConnectionState
    data class Error(val message: String) : ConnectionState

    val isReceiving: Boolean get() = this is Connected
}
