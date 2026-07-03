package me.kitsu.hangy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.kitsu.hangy.R
import me.kitsu.hangy.domain.model.HistoryMetric
import me.kitsu.hangy.ui.common.appContainer
import me.kitsu.hangy.ui.common.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { SettingsViewModel(container.settingsRepository) },
    )
    val settings by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            NumberSetting(
                label = stringResource(R.string.body_weight),
                initial = settings.bodyWeightKg.toString(),
                onValid = { vm.setBodyWeight(it) },
                decimal = true,
            )

            NumberSetting(
                label = stringResource(R.string.start_threshold),
                initial = settings.startThresholdKg.toString(),
                onValid = { vm.setStartThreshold(it) },
                decimal = true,
            )

            NumberSetting(
                label = stringResource(R.string.avg_window_sec),
                initial = settings.avgWindowSec.toString(),
                onValid = { vm.setAvgWindow(it.toInt().coerceAtLeast(1)) },
                decimal = false,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sound_cues), style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = settings.soundEnabled,
                    onCheckedChange = { vm.setSoundEnabled(it) },
                )
            }

            Column {
                Text(
                    stringResource(R.string.history_metric),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val metrics = HistoryMetric.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    metrics.forEachIndexed { index, metric ->
                        SegmentedButton(
                            selected = settings.historyMetric == metric,
                            onClick = { vm.setHistoryMetric(metric) },
                            shape = SegmentedButtonDefaults.itemShape(index, metrics.size),
                        ) {
                            Text(metric.label(), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberSetting(label: String, initial: String, onValid: (Double) -> Unit, decimal: Boolean) {
    var text by rememberSaveable { mutableStateOf(initial) }
    // The field also drives the persisted value, so `initial` echoes back on every keystroke.
    // Only adopt it when it represents a genuinely different number, otherwise typing (e.g. "7.5",
    // "80") would be reset to the canonical form mid-edit.
    LaunchedEffect(initial) {
        if (text.toDoubleOrNull() != initial.toDoubleOrNull()) text = initial
    }
    val keyboard = remember(decimal) {
        KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let { value -> if (value >= 0) onValid(value) }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = keyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HistoryMetric.label(): String = when (this) {
    HistoryMetric.MAX -> stringResource(R.string.metric_max)
    HistoryMetric.AVERAGE -> stringResource(R.string.metric_avg)
    HistoryMetric.BOTH -> stringResource(R.string.metric_both)
}
