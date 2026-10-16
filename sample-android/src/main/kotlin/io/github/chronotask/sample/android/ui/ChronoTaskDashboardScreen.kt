package io.github.chronotask.sample.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskState
import io.github.chronotask.sample.android.BuildConfig
import io.github.chronotask.sample.android.model.EventType
import io.github.chronotask.sample.android.model.SampleTaskEvent
import io.github.chronotask.sample.android.model.SampleTaskUiState
import io.github.chronotask.sample.android.viewmodel.ChronoTaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronoTaskDashboardScreen(viewModel: ChronoTaskViewModel) {
    val engineState by viewModel.engineState.collectAsState()
    val tasks by viewModel.taskList.collectAsState()
    val events by viewModel.events.collectAsState()

    var showAboutDialog by remember { mutableStateOf(false) }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About ChronoTask Android Sample", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Library: ChronoTask 1.0.0")
                    Text("• Target SDK: 34 (Android 14)")
                    Text("• Min SDK: 26 (Android 8.0+)")
                    Text("• Build Type: ${BuildConfig.BUILD_TYPE}")
                    Text("• Minified / R8: ${if (BuildConfig.DEBUG) "false (Debug)" else "true (Release R8)"}")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Note: ChronoTask is an in-process scheduling engine. Scheduled tasks execute while the app process is active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ChronoTask 1.0.0", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Multi-Paradigm Scheduling Dashboard",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    EngineStateBadge(engineState)
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About Sample")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(10.dp)
        ) {
            // 1. Engine Control Panel
            EngineControlPanel(
                state = engineState,
                onStart = { viewModel.startEngine() },
                onPause = { viewModel.pauseEngine() },
                onResume = { viewModel.resumeEngine() },
                onStop = { viewModel.stopEngine() },
                onRecreate = { viewModel.recreateEngine() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Task Cards Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Active Tasks (${tasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "KSP • Reflect • Java APT • DSL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onTrigger = { viewModel.triggerTask(task.id) },
                        onPause = { viewModel.pauseTask(task.id) },
                        onResume = { viewModel.resumeTask(task.id) },
                        onCancel = { viewModel.cancelTask(task.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Live Logs Console
            Text(
                "Live Engine Event Stream (${events.size} items)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            LiveLogConsole(
                events = events,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
        }
    }
}

@Composable
fun EngineStateBadge(state: EngineState) {
    val color = when (state) {
        EngineState.RUNNING -> Color(0xFF4CAF50)
        EngineState.PAUSED -> Color(0xFFFF9800)
        EngineState.CREATED -> Color(0xFF2196F3)
        EngineState.STOPPED -> Color(0xFFF44336)
    }

    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = state.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EngineControlPanel(
    state: EngineState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onRecreate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text("Engine Lifecycle Management", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled = state == EngineState.CREATED,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start", fontSize = 11.sp)
                }
                Button(
                    onClick = onPause,
                    enabled = state == EngineState.RUNNING,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pause", fontSize = 11.sp)
                }
                Button(
                    onClick = onResume,
                    enabled = state == EngineState.PAUSED,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Resume", fontSize = 11.sp)
                }
                Button(
                    onClick = onStop,
                    enabled = state == EngineState.RUNNING || state == EngineState.PAUSED,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop", fontSize = 11.sp)
                }
                IconButton(onClick = onRecreate) {
                    Icon(Icons.Default.Refresh, contentDescription = "Recreate Engine")
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    task: SampleTaskUiState,
    onTrigger: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(task.id, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${task.bindingType} • ${task.scheduleDescription} • ${task.concurrencyPolicy}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TaskStateChip(task.state)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Runs: ${task.runCount} (✓${task.successCount} / ✗${task.failureCount} / ⤼${task.skippedCount})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Last: ${task.lastCompletedAt ?: "Never"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onTrigger,
                    enabled = task.state != TaskState.CANCELLED,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text("Trigger Now", fontSize = 11.sp)
                }

                if (task.state == TaskState.PAUSED) {
                    FilledTonalButton(
                        onClick = onResume,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("Resume", fontSize = 11.sp)
                    }
                } else if (task.state != TaskState.CANCELLED) {
                    FilledTonalButton(
                        onClick = onPause,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("Pause", fontSize = 11.sp)
                    }
                }

                if (task.state != TaskState.CANCELLED) {
                    OutlinedButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Cancel", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun TaskStateChip(state: TaskState) {
    val (color, text) = when (state) {
        TaskState.SCHEDULED -> Color(0xFF2196F3) to "SCHEDULED"
        TaskState.RUNNING -> Color(0xFF4CAF50) to "RUNNING"
        TaskState.PAUSED -> Color(0xFFFF9800) to "PAUSED"
        TaskState.REGISTERED -> Color(0xFF9E9E9E) to "REGISTERED"
        TaskState.CANCELLED -> Color(0xFFE91E63) to "CANCELLED"
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun LiveLogConsole(events: List<SampleTaskEvent>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        color = Color(0xFF121212),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            items(events) { evt ->
                val color = when (evt.type) {
                    EventType.FAILED -> Color(0xFFFF5252)
                    EventType.SKIPPED -> Color(0xFFFFB74D)
                    EventType.MANUAL -> Color(0xFF64B5F6)
                    EventType.PAUSED, EventType.CANCELLED -> Color(0xFFE0E0E0)
                    else -> Color(0xFF81C784)
                }
                Text(
                    text = "${evt.timestamp} [${evt.taskId}] ${evt.type}: ${evt.message}",
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
