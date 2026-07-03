package me.kitsu.hangy.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.kitsu.hangy.R
import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.ui.common.appContainer
import me.kitsu.hangy.ui.common.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineCreateScreen(cloneFrom: Long?, onSaved: (Long) -> Unit, onCancel: () -> Unit) {
    val container = appContainer()
    val vm: RoutineCreateViewModel = viewModel(
        factory = viewModelFactory {
            RoutineCreateViewModel(container.routineRepository, cloneFrom)
        },
    )
    val form by vm.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_routine)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.save(onSaved) }, enabled = form.isValid) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { name -> vm.update { it.copy(name = name) } },
                label = { Text(stringResource(R.string.routine_name)) },
                singleLine = true,
                isError = form.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            Labeled(stringResource(R.string.protocol)) {
                val protocols = Protocol.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    protocols.forEachIndexed { index, protocol ->
                        SegmentedButton(
                            selected = form.protocol == protocol,
                            onClick = { vm.update { it.copy(protocol = protocol) } },
                            shape = SegmentedButtonDefaults.itemShape(index, protocols.size),
                        ) { Text(protocol.label()) }
                    }
                }
            }

            if (form.protocol == Protocol.SINGLE_HAND) {
                Labeled(stringResource(R.string.alternation)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Alternation.entries.forEach { alt ->
                            FilterChip(
                                selected = form.alternation == alt,
                                onClick = { vm.update { it.copy(alternation = alt) } },
                                label = { Text(alt.label()) },
                            )
                        }
                    }
                }
            }

            val singleHand = form.protocol == Protocol.SINGLE_HAND
            IntField(stringResource(R.string.tension_sec), form.tensionSec) { v ->
                vm.update { it.copy(tensionSec = v) }
            }
            IntField(stringResource(R.string.rest_sec), form.restSec) { v ->
                vm.update { it.copy(restSec = v) }
            }
            if (singleHand) {
                IntField(stringResource(R.string.switch_sec), form.switchSec) { v ->
                    vm.update { it.copy(switchSec = v) }
                }
            }
            val repsLabel = if (singleHand) {
                stringResource(R.string.reps_per_hand)
            } else {
                stringResource(R.string.total_reps)
            }
            IntField(repsLabel, form.totalReps) { v ->
                vm.update { it.copy(totalReps = v) }
            }
        }
    }
}

@Composable
private fun IntField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Labeled(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}
