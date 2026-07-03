package me.kitsu.hangy.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.launch

/**
 * Real BLE implementation: filters advertisements by the WH-C06 manufacturer id and decodes
 * each packet with [WhC06Parser]. A watchdog downgrades the state to [ConnectionState.SignalLost]
 * when packets stop arriving.
 */
class BleScaleRepository(private val context: Context, private val scope: CoroutineScope = CoroutineScope(SupervisorJob())) :
    ScaleRepository {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableSharedFlow<ScaleReading>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    override val readings: SharedFlow<ScaleReading> = _readings.asSharedFlow()

    private val bluetoothManager: BluetoothManager? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var scanning = false
    private var watchdog: Job? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // The scan is filtered to the WH-C06's manufacturer id, so any advertisement here is
            // from the scale — proof it's still alive. Keep the connection up even when a packet
            // carries no usable weight (e.g. an idle/zero broadcast); only the reading is skipped.
            armWatchdog()
            val data = result.scanRecord
                ?.getManufacturerSpecificData(WhC06Parser.MANUFACTURER_ID)
            val reading = WhC06Parser.parse(data) ?: return
            _connectionState.value = ConnectionState.Connected
            _readings.tryEmit(reading)
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Error("Bluetooth scan failed (code $errorCode)")
            scanning = false
        }
    }

    override fun start() {
        if (scanning) return

        if (!hasScanPermission()) {
            _connectionState.value = ConnectionState.PermissionRequired
            return
        }
        val currentAdapter = adapter
        if (currentAdapter == null || !currentAdapter.isEnabled) {
            _connectionState.value = ConnectionState.BluetoothOff
            return
        }
        val scanner = currentAdapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = ConnectionState.Error("Bluetooth LE scanner unavailable")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setManufacturerData(WhC06Parser.MANUFACTURER_ID, ByteArray(0), ByteArray(0))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
            scanning = true
            _connectionState.value = ConnectionState.Searching
            armWatchdog()
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.PermissionRequired
        }
    }

    override fun stop() {
        watchdog?.cancel()
        watchdog = null
        if (!scanning) {
            _connectionState.value = ConnectionState.Idle
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        try {
            if (hasScanPermission()) scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // Nothing else to do; we are stopping anyway.
        }
        scanning = false
        _connectionState.value = ConnectionState.Idle
    }

    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(SIGNAL_TIMEOUT_MS)
            if (scanning) _connectionState.value = ConnectionState.SignalLost
        }
    }

    private fun hasScanPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
        PackageManager.PERMISSION_GRANTED

    private companion object {
        const val SIGNAL_TIMEOUT_MS = 2_000L
    }
}
