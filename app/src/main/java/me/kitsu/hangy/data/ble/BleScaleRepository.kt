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
import kotlinx.coroutines.Dispatchers
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
 * when packets stop arriving, and the scan is silently re-registered every
 * [SCAN_REFRESH_INTERVAL_MS] to dodge the Bluetooth stack's scan-timeout downgrade.
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
    private var refresh: Job? = null

    private val scanFilters = listOf(
        ScanFilter.Builder()
            .setManufacturerData(WhC06Parser.MANUFACTURER_ID, ByteArray(0), ByteArray(0))
            .build(),
    )
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setReportDelay(0)
        .build()

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

        try {
            scanner.startScan(scanFilters, scanSettings, scanCallback)
            scanning = true
            _connectionState.value = ConnectionState.Searching
            armWatchdog()
            scheduleScanRefresh()
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.PermissionRequired
        }
    }

    override fun stop() {
        refresh?.cancel()
        refresh = null
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

    /**
     * The Bluetooth stack downgrades a scan client running longer than its `scan_timeout_millis`
     * (a DeviceConfig value, ~5 min observed) to a low-power duty cycle, collapsing the reading
     * rate. Re-registering the scan before that deadline resets the per-client clock.
     * Main-dispatched so the restart cannot race [start]/[stop].
     */
    private fun scheduleScanRefresh() {
        refresh?.cancel()
        refresh = scope.launch(Dispatchers.Main) {
            while (true) {
                delay(SCAN_REFRESH_INTERVAL_MS)
                refreshScan()
            }
        }
    }

    private fun refreshScan() {
        if (!scanning) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(scanCallback)
            scanner.startScan(scanFilters, scanSettings, scanCallback)
        } catch (e: SecurityException) {
            // Permission revoked mid-session; the watchdog surfaces the dead scan.
        }
    }

    private fun hasScanPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
        PackageManager.PERMISSION_GRANTED

    private companion object {
        const val SIGNAL_TIMEOUT_MS = 2_000L

        // Below the OS scan timeout, above the 5-scan-starts-per-30s throttle.
        const val SCAN_REFRESH_INTERVAL_MS = 2 * 60 * 1000L
    }
}
