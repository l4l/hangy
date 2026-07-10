package me.kitsu.hangy.ui.measure

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.kitsu.hangy.R
import me.kitsu.hangy.data.ble.ConnectionState
import me.kitsu.hangy.domain.engine.EngineState
import me.kitsu.hangy.domain.engine.PhaseType
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.Routine
import me.kitsu.hangy.domain.model.SessionDetail
import me.kitsu.hangy.domain.model.TargetType
import me.kitsu.hangy.session.MeasureMode
import me.kitsu.hangy.ui.common.LiveWeightChart
import me.kitsu.hangy.ui.common.SessionTimelineChart
import me.kitsu.hangy.ui.common.appContainer
import me.kitsu.hangy.ui.common.formatCountdown
import me.kitsu.hangy.ui.common.formatKg
import me.kitsu.hangy.ui.common.viewModelFactory
import me.kitsu.hangy.ui.routine.timingLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen() {
    val container = appContainer()
    val vm: MeasureViewModel = viewModel(
        factory = viewModelFactory {
            MeasureViewModel(
                controller = container.sessionController,
                serviceHost = container.serviceHost,
                routineRepository = container.routineRepository,
                settingsRepository = container.settingsRepository,
            )
        },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showStopConfirm by remember { mutableStateOf(false) }

    // Nothing disconnects on dispose: the session lives in the service, and tearing the scan down
    // on every rotation would trip Android's 5-scans-per-30s throttle.

    // Keep the screen awake while actively measuring so it never sleeps mid-hang.
    val view = LocalView.current
    val keepScreenOn = state.mode == MeasureMode.RUNNING || state.mode == MeasureMode.TEST
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.mode) {
                MeasureMode.RUNNING -> {
                    CompactStatus(state.connection)
                    LiveView(vm, showTracker = true, onStop = { showStopConfirm = true })
                }

                MeasureMode.TEST -> {
                    CompactStatus(state.connection)
                    LiveView(vm, showTracker = false, onStop = vm::stopAndDiscard)
                }

                MeasureMode.IDLE -> {
                    ConnectionStatusCard(state.connection, onConnect = vm::connect, onDisconnect = vm::disconnect)
                    RoutinePicker(
                        routines = state.routines,
                        onSelect = vm::selectRoutine,
                        onTest = vm::startTest,
                    )
                }

                MeasureMode.FINISHED -> {
                    val summary = state.finishedSummary
                    if (summary != null) {
                        SessionSummaryPanel(summary = summary, onDone = vm::backToSelection)
                    } else {
                        ConnectionStatusCard(state.connection, onConnect = vm::connect, onDisconnect = vm::disconnect)
                        RoutinePicker(
                            routines = state.routines,
                            onSelect = vm::selectRoutine,
                            onTest = vm::startTest,
                        )
                    }
                }

                MeasureMode.CONFIG -> {
                    ConnectionStatusCard(state.connection, onConnect = vm::connect, onDisconnect = vm::disconnect)
                    TargetConfig(
                        state = state,
                        onTargetType = vm::setTargetType,
                        onTarget = vm::setTarget,
                        onBodyWeight = vm::setSessionBodyWeight,
                        onThreshold = vm::setSessionThreshold,
                        canStart = state.connection.isReceiving,
                        onStart = vm::startRoutine,
                        onBack = vm::backToSelection,
                    )
                }
            }
        }
    }

    if (showStopConfirm && state.mode == MeasureMode.RUNNING) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text(stringResource(R.string.stop_routine_title)) },
            text = { Text(stringResource(R.string.stop_routine_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    vm.stopAndSave()
                }) {
                    Text(stringResource(R.string.save_and_stop))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    vm.stopAndDiscard()
                }) {
                    Text(stringResource(R.string.discard))
                }
            },
        )
    }
}

@Composable
private fun CompactStatus(state: ConnectionState) {
    val color = when (state) {
        is ConnectionState.Connected -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Text(
        state.description(),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ConnectionStatusCard(state: ConnectionState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val context = LocalContext.current

    // A denied POST_NOTIFICATIONS only hides the service notification, so it is bundled with the
    // scan permission rather than gating the connect.
    val required = remember {
        buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun isGranted(permission: String): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // The result map has no BLUETOOTH_SCAN entry when only POST_NOTIFICATIONS was missing.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { if (isGranted(Manifest.permission.BLUETOOTH_SCAN)) onConnect() }

    fun requestConnect() {
        val missing = required.filterNot(::isGranted)
        if (missing.isEmpty()) onConnect() else permissionLauncher.launch(missing.toTypedArray())
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.labelMedium)
                Text(
                    state.description(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (state) {
                is ConnectionState.PermissionRequired ->
                    Button(onClick = ::requestConnect) { Text(stringResource(R.string.grant_permission)) }

                is ConnectionState.BluetoothOff ->
                    Button(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(stringResource(R.string.enable_bluetooth)) }

                // Nothing stops the scan on disposal, so offer an explicit disconnect.
                is ConnectionState.Connected, is ConnectionState.Searching ->
                    OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) }

                else -> Button(onClick = ::requestConnect) { Text(stringResource(R.string.connect_scale)) }
            }
        }
    }
}

@Composable
private fun ColumnScope.RoutinePicker(routines: List<Routine>, onSelect: (Routine) -> Unit, onTest: () -> Unit) {
    OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.test_mode))
    }

    Text(stringResource(R.string.select_routine), style = MaterialTheme.typography.titleMedium)
    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(routines, key = { it.id }) { routine ->
            Card(modifier = Modifier.fillMaxWidth(), onClick = { onSelect(routine) }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        routine.timingLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.TargetConfig(
    state: MeasureUiState,
    onTargetType: (TargetType) -> Unit,
    onTarget: (Double, Double) -> Unit,
    onBodyWeight: (Double) -> Unit,
    onThreshold: (Double) -> Unit,
    canStart: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    // Local text is cleared whenever a different routine is picked, then seeded from that routine's
    // last-used target once it loads (see below).
    val routineId = state.selectedRoutine?.id
    var low by remember(routineId) { mutableStateOf("") }
    var high by remember(routineId) { mutableStateOf("") }
    // Seed the fields from the routine's last-used target when it arrives, but never overwrite what
    // the user has already typed. Keystrokes flow back into state.targetLow/High, so the blank guard
    // keeps this a one-shot seed rather than a fight with the user.
    LaunchedEffect(routineId, state.targetLow, state.targetHigh) {
        if (low.isBlank() && state.targetLow > 0.0) low = formatKg(state.targetLow)
        if (high.isBlank() && state.targetHigh > 0.0) high = formatKg(state.targetHigh)
    }

    val lowVal = low.toDoubleOrNull()
    val highVal = high.toDoubleOrNull()
    val bothBlank = low.isBlank() && high.isBlank()
    // A target is optional (leave both blank to hang freely), but a partial or inverted range is
    // not — the band and in-target colouring would otherwise silently do nothing.
    val targetValid = bothBlank || (lowVal != null && highVal != null && lowVal >= 0.0 && highVal > lowVal)

    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            state.selectedRoutine?.name ?: "",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField(
                label = stringResource(R.string.session_body_weight),
                initial = formatKg(state.sessionBodyWeightKg),
                onValid = onBodyWeight,
                modifier = Modifier.weight(1f),
            )
            DecimalField(
                label = stringResource(R.string.session_threshold),
                initial = formatKg(state.sessionThresholdKg),
                onValid = onThreshold,
                modifier = Modifier.weight(1f),
            )
        }

        Text(stringResource(R.string.target_range), style = MaterialTheme.typography.titleSmall)
        val types = TargetType.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.targetType == type,
                    onClick = { onTargetType(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, types.size),
                ) { Text(type.unitLabel()) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = low,
                onValueChange = {
                    low = it
                    onTarget(low.toDoubleOrNull() ?: 0.0, high.toDoubleOrNull() ?: 0.0)
                },
                label = { Text(stringResource(R.string.target_min)) },
                singleLine = true,
                isError = !targetValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = high,
                onValueChange = {
                    high = it
                    onTarget(low.toDoubleOrNull() ?: 0.0, high.toDoubleOrNull() ?: 0.0)
                },
                label = { Text(stringResource(R.string.target_max)) },
                singleLine = true,
                isError = !targetValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        if (!targetValid) {
            Text(
                stringResource(R.string.target_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.cancel))
        }
        Button(onClick = onStart, enabled = canStart && targetValid, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.start))
        }
    }
}

@Composable
private fun ColumnScope.SessionSummaryPanel(summary: SessionDetail, onDone: () -> Unit) {
    val session = summary.session
    Text(
        stringResource(if (session.completed) R.string.session_complete else R.string.session_stopped),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(stringResource(R.string.max_weight), session.maxLoadKg)
        Stat(stringResource(R.string.avg_weight), session.avgLoadKg)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.stat_reps), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(
                summary.reps.size.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    if (summary.samples.isNotEmpty()) {
        SessionTimelineChart(
            detail = summary,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 120.dp),
        )
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.done))
    }
}

@Composable
private fun ColumnScope.LiveView(vm: MeasureViewModel, showTracker: Boolean, onStop: () -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()

    LiveWeightChart(
        samples = live.plot,
        latestTMs = live.latestTMs,
        windowMs = live.windowMs,
        targetLowKg = live.targetLowKg,
        targetHighKg = live.targetHighKg,
        lineColor = MaterialTheme.colorScheme.primary,
        inTargetColor = MaterialTheme.colorScheme.tertiary,
        targetColor = MaterialTheme.colorScheme.secondaryContainer,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .heightIn(min = 120.dp),
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(stringResource(R.string.current_weight), live.currentKg)
        Stat(stringResource(R.string.avg_weight), live.avgKg)
        Stat(stringResource(R.string.max_weight), live.maxKg)
    }

    if (showTracker) RepTracker(live.engineState)

    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.stop))
    }
}

@Composable
private fun Stat(label: String, kg: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(formatKg(kg), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RepTracker(engine: EngineState?) {
    engine ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.rep_of, engine.repIndex, engine.totalReps),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    engine.phaseLabel(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                engine.nextLabel()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (engine.phase != PhaseType.WAITING) {
                Text(
                    formatCountdown(engine.remainingMs),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DecimalField(label: String, initial: String, onValid: (Double) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let { value -> if (value >= 0) onValid(value) }
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun EngineState.phaseLabel(): String {
    val base = when (phase) {
        PhaseType.GET_READY -> stringResource(R.string.phase_get_ready)
        PhaseType.WAITING -> stringResource(R.string.phase_waiting)
        PhaseType.TENSION -> stringResource(R.string.phase_tension)
        PhaseType.REST -> stringResource(R.string.phase_rest)
        PhaseType.SWITCH -> stringResource(R.string.phase_switch)
        PhaseType.DONE -> stringResource(R.string.phase_done)
    }
    val handLabel = hand?.label()
    return if (handLabel != null) "$base · $handLabel" else base
}

@Composable
private fun EngineState.nextLabel(): String? {
    val showNext = phase == PhaseType.REST || phase == PhaseType.SWITCH || phase == PhaseType.WAITING
    val handLabel = upcomingHand?.label() ?: return null
    return if (showNext) stringResource(R.string.next_up, handLabel) else null
}

@Composable
private fun Hand.label(): String = when (this) {
    Hand.LEFT -> stringResource(R.string.hand_left)
    Hand.RIGHT -> stringResource(R.string.hand_right)
    Hand.BOTH -> stringResource(R.string.hand_both)
}

@Composable
private fun ConnectionState.description(): String = when (this) {
    is ConnectionState.Idle -> stringResource(R.string.status_idle)
    is ConnectionState.Searching -> stringResource(R.string.status_searching)
    is ConnectionState.Connected -> stringResource(R.string.status_connected)
    is ConnectionState.BluetoothOff -> stringResource(R.string.status_bluetooth_off)
    is ConnectionState.PermissionRequired -> stringResource(R.string.status_permission_required)
    is ConnectionState.SignalLost -> stringResource(R.string.status_signal_lost)
    is ConnectionState.Error -> message
}

@Composable
private fun TargetType.unitLabel(): String = when (this) {
    TargetType.KG -> stringResource(R.string.target_unit_kg)
    TargetType.PERCENT_BW -> stringResource(R.string.target_unit_pct)
}
