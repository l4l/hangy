package me.kitsu.hangy.ui.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.kitsu.hangy.R
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.Routine
import me.kitsu.hangy.domain.model.Session
import me.kitsu.hangy.domain.model.SessionDetail
import me.kitsu.hangy.ui.common.HistoryChart
import me.kitsu.hangy.ui.common.RepComparisonChart
import me.kitsu.hangy.ui.common.SessionTimelineChart
import me.kitsu.hangy.ui.common.appContainer
import me.kitsu.hangy.ui.common.formatKg
import me.kitsu.hangy.ui.common.formatSessionDate
import me.kitsu.hangy.ui.common.formatTutSec
import me.kitsu.hangy.ui.common.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(routineId: Long, onClone: (Long) -> Unit, onBack: () -> Unit, onDeleted: () -> Unit) {
    val container = appContainer()
    val vm: RoutineDetailViewModel = viewModel(
        factory = viewModelFactory {
            RoutineDetailViewModel(
                routineId = routineId,
                routineRepository = container.routineRepository,
                measurementRepository = container.measurementRepository,
                settingsRepository = container.settingsRepository,
            )
        },
    )
    val listVm: RoutineListViewModel = viewModel(
        factory = viewModelFactory { RoutineListViewModel(container.routineRepository) },
    )

    val routine by vm.routine.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val endReached by vm.endReached.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routine?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { onClone(routineId) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.clone_routine))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_routine))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            routine?.let { current ->
                item { RoutineConfigCard(current) }

                item {
                    Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium)
                }
                if (summaries.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_history),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item { HistoryChart(summaries = summaries, metric = metric, modifier = Modifier.fillMaxWidth()) }
                }

                item {
                    Text(stringResource(R.string.sessions), style = MaterialTheme.typography.titleMedium)
                }

                items(sessions, key = { it.id }) { session ->
                    LaunchedEffect(session.id) { vm.loadDetail(session.id) }
                    SessionCard(
                        session = session,
                        detail = details[session.id],
                        expanded = expandedId == session.id,
                        onToggle = { expandedId = if (expandedId == session.id) null else session.id },
                    )
                }

                if (!endReached) {
                    item {
                        LaunchedEffect(sessions.size) { vm.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.height(24.dp))
                        }
                    }
                } else if (sessions.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_history),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_routine)) },
            text = { Text(stringResource(R.string.delete_routine_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    listVm.delete(routineId)
                    onDeleted()
                }) { Text(stringResource(R.string.delete_routine)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RoutineConfigCard(routine: Routine) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DetailRow(stringResource(R.string.protocol), routine.protocol.label())
            routine.alternation?.let { DetailRow(stringResource(R.string.alternation), it.label()) }
            DetailRow(stringResource(R.string.total_reps), routine.timingLine())
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SessionCard(session: Session, detail: SessionDetail?, expanded: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatSessionDate(session.startedAt),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!session.completed) {
                    Text(
                        stringResource(R.string.session_partial),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MiniStat(stringResource(R.string.max_weight), formatKg(session.maxLoadKg))
                MiniStat(stringResource(R.string.avg_weight), formatKg(session.avgLoadKg))
                detail?.let { MiniStat(stringResource(R.string.stat_reps), it.reps.size.toString()) }
            }

            if (detail != null && detail.samples.isNotEmpty()) {
                SessionTimelineChart(
                    detail = detail,
                    interactive = expanded,
                    showAxes = expanded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (expanded) 220.dp else 72.dp),
                )
            }

            if (expanded && detail != null) {
                RepBreakdown(detail)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepBreakdown(detail: SessionDetail) {
    val reps = detail.reps.sortedWith(compareBy({ it.repIndex }, { it.hand }))
    if (reps.isEmpty()) {
        Text(
            stringResource(R.string.no_reps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Text(stringResource(R.string.per_rep), style = MaterialTheme.typography.titleSmall)
    RepTableRow(
        stringResource(R.string.col_rep),
        stringResource(R.string.col_hand),
        stringResource(R.string.col_max),
        stringResource(R.string.col_avg),
        stringResource(R.string.col_tut),
        header = true,
    )
    HorizontalDivider()
    reps.forEach { rep ->
        RepTableRow(
            rep.repIndex.toString(),
            rep.hand.label(),
            formatKg(rep.maxKg),
            formatKg(rep.avgKg),
            formatTutSec(rep.actualTutMs),
            header = false,
        )
    }

    // Rep-comparison overlay. Only reps with a recorded tension window can be compared (sessions
    // from before per-rep windows existed have none), so the selector reflects those, and the whole
    // section is hidden when there is nothing to compare.
    val comparableHands = reps.filter { it.tEndMs > it.tStartMs }.map { it.hand }.distinct()
    if (comparableHands.isNotEmpty()) {
        RepComparisonSection(detail, comparableHands)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepComparisonSection(detail: SessionDetail, hands: List<Hand>) {
    var selectedHand by remember(detail.session.id) { mutableStateOf(hands.first()) }
    Text(stringResource(R.string.rep_comparison), style = MaterialTheme.typography.titleSmall)
    if (hands.size > 1) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            hands.forEachIndexed { index, hand ->
                SegmentedButton(
                    selected = selectedHand == hand,
                    onClick = { selectedHand = hand },
                    shape = SegmentedButtonDefaults.itemShape(index, hands.size),
                ) { Text(hand.label()) }
            }
        }
    }
    RepComparisonChart(detail = detail, hand = selectedHand, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun RepTableRow(rep: String, hand: String, max: String, avg: String, tut: String, header: Boolean) {
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.bodySmall
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(rep, style = style, fontWeight = weight, color = color, modifier = Modifier.weight(0.6f))
        Text(hand, style = style, fontWeight = weight, color = color, modifier = Modifier.weight(1f))
        Text(max, style = style, fontWeight = weight, color = color, modifier = Modifier.weight(1f))
        Text(avg, style = style, fontWeight = weight, color = color, modifier = Modifier.weight(1f))
        Text(tut, style = style, fontWeight = weight, color = color, modifier = Modifier.weight(1f))
    }
}
