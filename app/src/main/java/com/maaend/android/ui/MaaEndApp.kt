package com.maaend.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maaend.android.model.PresetDescriptor
import com.maaend.android.model.TaskDescriptor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaaEndApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                MaaEndTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.activeTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {},
                        label = { Text(tab.label()) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(
                rootAvailable = state.rootAvailable,
                rootGranted = state.rootGranted,
                rootConnected = state.rootConnected,
                ping = state.servicePing,
                message = state.lastMessage,
            )

            when (state.activeTab) {
                MaaEndTab.HOME -> HomeScreen(viewModel)
                MaaEndTab.TASKS -> TaskScreen(
                    tasks = state.catalog.tasks,
                    selectedTaskId = state.selectedTaskId,
                    onSelect = viewModel::selectTask,
                    onStart = viewModel::startSelectedTask,
                )
                MaaEndTab.PRESETS -> PresetScreen(
                    presets = state.catalog.presets,
                    selectedPresetId = state.selectedPresetId,
                    onSelect = viewModel::selectPreset,
                    onStart = viewModel::startSelectedPreset,
                )
                MaaEndTab.RUNNING -> RunningScreen(viewModel = viewModel, state = state)
                MaaEndTab.SETTINGS -> SettingsScreen(
                    logLevel = state.settings.logLevel,
                    onLogLevelChange = viewModel::updateLogLevel,
                )
                MaaEndTab.LOGS -> LogsScreen(lines = state.runtimeState.recentLogs)
            }
        }
    }
}

@Composable
private fun StatusCard(
    rootAvailable: Boolean,
    rootGranted: Boolean,
    rootConnected: Boolean,
    ping: String,
    message: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "Root 可用: $rootAvailable")
            Text(text = "Root 已授权: $rootGranted")
            Text(text = "Root Runtime 已连接: $rootConnected")
            if (ping.isNotBlank()) {
                Text(text = ping, style = MaterialTheme.typography.bodySmall)
            }
            if (message.isNotBlank()) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HomeScreen(viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = viewModel::requestRootAndConnect) {
            Text("申请 Root 并连接 Runtime")
        }
        Button(onClick = viewModel::prepareRuntime) {
            Text("准备运行时")
        }
        Button(onClick = viewModel::exportDiagnostics) {
            Text("导出诊断包")
        }
    }
}

@Composable
private fun TaskScreen(
    tasks: List<TaskDescriptor>,
    selectedTaskId: String?,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onStart) {
            Text("启动选中任务")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks) { task ->
                TaskCard(
                    task = task,
                    selected = task.id == selectedTaskId,
                    onSelect = { onSelect(task.id) },
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskDescriptor,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = task.label)
            Text(text = task.id, style = MaterialTheme.typography.bodySmall)
            if (task.description.isNotBlank()) {
                Text(text = task.description, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSelect) {
                    Text(if (selected) "已选中" else "选择")
                }
                Text(text = task.tier.name)
            }
        }
    }
}

@Composable
private fun PresetScreen(
    presets: List<PresetDescriptor>,
    selectedPresetId: String?,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onStart) {
            Text("启动选中预设")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { preset ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = preset.label)
                        Text(text = preset.taskIds.joinToString(" -> "), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onSelect(preset.id) }) {
                            Text(if (preset.id == selectedPresetId) "已选中" else "选择")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningScreen(
    viewModel: MainViewModel,
    state: MainUiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "阶段: ${state.runtimeState.phase}")
        Text(text = "当前任务: ${state.runtimeState.currentTaskId ?: "无"}")
        Text(text = "运行时目录: ${state.runtimeState.runtimeRoot ?: "未准备"}")
        Text(text = "说明: ${state.runtimeState.lastMessage}")
        Text(
            text = "能力: go=${state.runtimeState.capabilities.hasBundledGoService}, maafw=${state.runtimeState.capabilities.hasBundledMaaFramework}",
            style = MaterialTheme.typography.bodySmall,
        )
        state.runtimeState.lastFailure?.let { failure ->
            Text(text = "失败截图: ${failure.screenshotPath ?: "无"}", style = MaterialTheme.typography.bodySmall)
        }
        if (!state.runtimeState.lastDiagnosticsPath.isNullOrBlank()) {
            Text(text = "最近诊断包: ${state.runtimeState.lastDiagnosticsPath}", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::stopRun) {
                Text("停止任务")
            }
            Button(onClick = viewModel::exportDiagnostics) {
                Text("导出诊断包")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    logLevel: String,
    onLogLevelChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = logLevel,
            onValueChange = onLogLevelChange,
            label = { Text("日志级别") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("当前仅使用基本 SharedPreferences 持久化设置。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LogsScreen(lines: List<String>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lines) { line ->
            Text(text = line, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun MaaEndTab.label(): String {
    return when (this) {
        MaaEndTab.HOME -> "主页"
        MaaEndTab.TASKS -> "任务"
        MaaEndTab.PRESETS -> "预设"
        MaaEndTab.RUNNING -> "运行中"
        MaaEndTab.SETTINGS -> "设置"
        MaaEndTab.LOGS -> "日志"
    }
}
