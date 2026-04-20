package com.maaend.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.maaend.android.model.TaskDescriptor
import com.maaend.android.model.TaskOptionDescriptor
import com.maaend.android.model.TaskOptionType
import com.maaend.android.preview.DefaultDisplayConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaaEndApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    var isFullscreenPreview by rememberSaveable { mutableStateOf(false) }
    var lastSurface by remember { mutableStateOf<Surface?>(null) }

    val previewContent = remember {
        movableContentOf {
            val scope = rememberCoroutineScope()
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.aspectRatio(DefaultDisplayConfig.ASPECT_RATIO),
                ) {
                    AndroidView(
                        factory = { context ->
                            SurfaceView(context).apply {
                                holder.setFormat(PixelFormat.RGBA_8888)
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        scope.launch {
                                            delay(50)
                                            holder.setFixedSize(DefaultDisplayConfig.WIDTH, DefaultDisplayConfig.HEIGHT)
                                        }
                                    }

                                    override fun surfaceChanged(
                                        holder: SurfaceHolder,
                                        format: Int,
                                        width: Int,
                                        height: Int,
                                    ) {
                                        if (width == DefaultDisplayConfig.WIDTH && height == DefaultDisplayConfig.HEIGHT) {
                                            if (lastSurface !== holder.surface) {
                                                lastSurface = holder.surface
                                                viewModel.setPreviewSurface(holder.surface)
                                            }
                                        }
                                    }

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        if (lastSurface != null) {
                                            lastSurface = null
                                            viewModel.setPreviewSurface(null)
                                        }
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        checkedTaskIds = state.checkedTaskIds,
                        taskOptionSelectionsByTask = state.taskOptionSelectionsByTask,
                        taskInputValuesByTask = state.taskInputValuesByTask,
                        onSelect = viewModel::selectTask,
                        onToggleChecked = viewModel::toggleTaskChecked,
                        onStart = viewModel::startSelectedTask,
                        onSwitchOption = viewModel::updateTaskSwitchOption,
                        onToggleCheckboxOption = viewModel::toggleTaskCheckboxOption,
                        onInputValueChange = viewModel::updateTaskInputValue,
                    )
                    MaaEndTab.RUNNING -> RunningScreen(
                        viewModel = viewModel,
                        state = state,
                        isFullscreenPreview = isFullscreenPreview,
                        onExpandPreview = { isFullscreenPreview = true },
                        previewContent = previewContent,
                    )
                    MaaEndTab.SETTINGS -> SettingsScreen(
                        logLevel = state.settings.logLevel,
                        onLogLevelChange = viewModel::updateLogLevel,
                    )
                    MaaEndTab.LOGS -> LogsScreen(lines = state.runtimeState.recentLogs)
                }
            }
        }

        if (isFullscreenPreview) {
            FullscreenPreviewOverlay(
                viewModel = viewModel,
                previewContent = previewContent,
                onDismissRequest = { isFullscreenPreview = false },
            )
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
        Button(onClick = viewModel::startWindowedGame) {
            Text("应用内窗口打开游戏")
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
    checkedTaskIds: Set<String>,
    taskOptionSelectionsByTask: Map<String, Map<String, Set<String>>>,
    taskInputValuesByTask: Map<String, Map<String, Map<String, String>>>,
    onSelect: (String) -> Unit,
    onToggleChecked: (String, Boolean) -> Unit,
    onStart: () -> Unit,
    onSwitchOption: (String, String, String) -> Unit,
    onToggleCheckboxOption: (String, String, String) -> Unit,
    onInputValueChange: (String, String, String, String) -> Unit,
) {
    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TaskListPanel(
                tasks = tasks,
                selectedTaskId = selectedTaskId,
                checkedTaskIds = checkedTaskIds,
                onSelect = onSelect,
                onToggleChecked = onToggleChecked,
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight(),
            )
            TaskConfigPanel(
                task = selectedTask,
                selectedCaseNamesByOption = selectedTask?.let { taskOptionSelectionsByTask[it.id].orEmpty() }.orEmpty(),
                inputValuesByOption = selectedTask?.let { taskInputValuesByTask[it.id].orEmpty() }.orEmpty(),
                onSwitchOption = onSwitchOption,
                onToggleCheckboxOption = onToggleCheckboxOption,
                onInputValueChange = onInputValueChange,
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight(),
            )
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (checkedTaskIds.isEmpty()) "开始任务" else "开始任务（${checkedTaskIds.size}）")
        }
    }
}

@Composable
private fun TaskListPanel(
    tasks: List<TaskDescriptor>,
    selectedTaskId: String?,
    checkedTaskIds: Set<String>,
    onSelect: (String) -> Unit,
    onToggleChecked: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("任务列表", style = MaterialTheme.typography.titleMedium)
            tasks.forEach { task ->
                TaskCard(
                    task = task,
                    selected = task.id == selectedTaskId,
                    checked = task.id in checkedTaskIds,
                    onSelect = { onSelect(task.id) },
                    onToggleChecked = { checked -> onToggleChecked(task.id, checked) },
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun TaskConfigPanel(
    task: TaskDescriptor?,
    selectedCaseNamesByOption: Map<String, Set<String>>,
    inputValuesByOption: Map<String, Map<String, String>>,
    onSwitchOption: (String, String, String) -> Unit,
    onToggleCheckboxOption: (String, String, String) -> Unit,
    onInputValueChange: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        if (task == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("任务配置", style = MaterialTheme.typography.titleMedium)
                Text(
                    "先从左侧选择一个任务，再在这里配置参数。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(task.label, style = MaterialTheme.typography.titleMedium)
                if (task.description.isNotBlank()) {
                    Text(
                        task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (task.options.isEmpty()) {
                    Text(
                        "这个任务当前没有可配置参数。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    task.options.forEach { option ->
                        TaskOptionBlock(
                            taskId = task.id,
                            option = option,
                            selectedCaseNamesByOption = selectedCaseNamesByOption,
                            inputValuesByOption = inputValuesByOption,
                            onSwitchOption = onSwitchOption,
                            onToggleCheckboxOption = onToggleCheckboxOption,
                            onInputValueChange = onInputValueChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskOptionBlock(
    taskId: String,
    option: TaskOptionDescriptor,
    selectedCaseNamesByOption: Map<String, Set<String>>,
    inputValuesByOption: Map<String, Map<String, String>>,
    onSwitchOption: (String, String, String) -> Unit,
    onToggleCheckboxOption: (String, String, String) -> Unit,
    onInputValueChange: (String, String, String, String) -> Unit,
) {
    val selectedCaseNames = selectedCaseNamesByOption[option.id].takeUnless { it.isNullOrEmpty() }
        ?: option.defaultCaseNames.toSet()
    val inputValues = inputValuesByOption[option.id].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(option.label, style = MaterialTheme.typography.titleSmall)
        if (option.description.isNotBlank()) {
            Text(
                option.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (option.type) {
            TaskOptionType.Switch,
            TaskOptionType.Select -> {
                option.cases.forEach { optionCase ->
                    val selected = optionCase.name in selectedCaseNames
                    val onClick = { onSwitchOption(taskId, option.id, optionCase.name) }
                    if (selected) {
                        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                            Text(optionCase.label)
                        }
                    } else {
                        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                            Text(optionCase.label)
                        }
                    }
                    if (selected && optionCase.nestedOptions.isNotEmpty()) {
                        NestedTaskOptions(
                            taskId = taskId,
                            options = optionCase.nestedOptions,
                            selectedCaseNamesByOption = selectedCaseNamesByOption,
                            inputValuesByOption = inputValuesByOption,
                            onSwitchOption = onSwitchOption,
                            onToggleCheckboxOption = onToggleCheckboxOption,
                            onInputValueChange = onInputValueChange,
                        )
                    }
                }
            }

            TaskOptionType.Checkbox -> {
                option.cases.forEach { optionCase ->
                    val selected = optionCase.name in selectedCaseNames
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCheckboxOption(taskId, option.id, optionCase.name) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleCheckboxOption(taskId, option.id, optionCase.name) },
                        )
                        Text(optionCase.label)
                    }
                    if (selected && optionCase.nestedOptions.isNotEmpty()) {
                        NestedTaskOptions(
                            taskId = taskId,
                            options = optionCase.nestedOptions,
                            selectedCaseNamesByOption = selectedCaseNamesByOption,
                            inputValuesByOption = inputValuesByOption,
                            onSwitchOption = onSwitchOption,
                            onToggleCheckboxOption = onToggleCheckboxOption,
                            onInputValueChange = onInputValueChange,
                        )
                    }
                }
            }

            TaskOptionType.Input -> {
                option.inputs.forEach { input ->
                    OutlinedTextField(
                        value = inputValues[input.name] ?: input.defaultValue,
                        onValueChange = { value ->
                            onInputValueChange(taskId, option.id, input.name, value)
                        },
                        label = { Text(input.label) },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = if (input.description.isNotBlank()) {
                            { Text(input.description) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NestedTaskOptions(
    taskId: String,
    options: List<TaskOptionDescriptor>,
    selectedCaseNamesByOption: Map<String, Set<String>>,
    inputValuesByOption: Map<String, Map<String, String>>,
    onSwitchOption: (String, String, String) -> Unit,
    onToggleCheckboxOption: (String, String, String) -> Unit,
    onInputValueChange: (String, String, String, String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { nestedOption ->
            TaskOptionBlock(
                taskId = taskId,
                option = nestedOption,
                selectedCaseNamesByOption = selectedCaseNamesByOption,
                inputValuesByOption = inputValuesByOption,
                onSwitchOption = onSwitchOption,
                onToggleCheckboxOption = onToggleCheckboxOption,
                onInputValueChange = onInputValueChange,
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskDescriptor,
    selected: Boolean,
    checked: Boolean,
    onSelect: () -> Unit,
    onToggleChecked: (Boolean) -> Unit,
    compact: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        if (compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggleChecked(it) },
                )
                Text(
                    text = compactTaskLabel(task),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
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
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggleChecked(it) },
                    )
                    Text(
                        text = if (selected) "当前配置" else if (checked) "加入执行" else "点击选择",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = task.tier.name)
                }
            }
        }
    }
}

@Composable
private fun RunningScreen(
    viewModel: MainViewModel,
    state: MainUiState,
    isFullscreenPreview: Boolean,
    onExpandPreview: () -> Unit,
    previewContent: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PreviewCard(
            isFullscreenPreview = isFullscreenPreview,
            onExpandPreview = onExpandPreview,
            previewContent = previewContent,
        )
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
private fun PreviewCard(
    isFullscreenPreview: Boolean,
    onExpandPreview: () -> Unit,
    previewContent: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("应用内窗口", style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(DefaultDisplayConfig.ASPECT_RATIO),
            ) {
                if (!isFullscreenPreview) {
                    previewContent()
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.18f))
                            .clickable(onClick = onExpandPreview),
                    )
                    Text(
                        text = "点击展开横向预览",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                text = "窗口固定为 1280x720。展开逻辑参考 MAA-Meow，直接在当前页面盖全屏层。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FullscreenPreviewOverlay(
    viewModel: MainViewModel,
    previewContent: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val fullscreenProgress = remember { Animatable(0f) }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation
        onDispose {
            if (activity != null && originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    LaunchedEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        fullscreenProgress.snapTo(0f)
        fullscreenProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }

    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var touchActive = false
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val point = mapViewToVirtualDisplay(
                            viewX = change.position.x,
                            viewY = change.position.y,
                            viewWidth = size.width,
                            viewHeight = size.height,
                            bufferWidth = DefaultDisplayConfig.WIDTH,
                            bufferHeight = DefaultDisplayConfig.HEIGHT,
                            clampToBounds = touchActive && event.type != PointerEventType.Press,
                        )
                        when (event.type) {
                            PointerEventType.Press -> {
                                touchActive = point?.let { viewModel.onPreviewTouchDown(it.x, it.y) } == true
                            }

                            PointerEventType.Move -> {
                                if (touchActive && change.pressed && point != null) {
                                    viewModel.onPreviewTouchMove(point.x, point.y)
                                }
                            }

                            PointerEventType.Release -> {
                                if (touchActive && point != null) {
                                    viewModel.onPreviewTouchUp(point.x, point.y)
                                }
                                touchActive = false
                            }

                            else -> Unit
                        }
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = fullscreenProgress.value
                },
        ) {
            previewContent()
        }

        IconButton(
            onClick = onDismissRequest,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭预览",
                tint = Color.White.copy(alpha = 0.7f),
            )
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

private data class PreviewPoint(
    val x: Int,
    val y: Int,
)

private fun mapViewToVirtualDisplay(
    viewX: Float,
    viewY: Float,
    viewWidth: Int,
    viewHeight: Int,
    bufferWidth: Int,
    bufferHeight: Int,
    clampToBounds: Boolean,
): PreviewPoint? {
    val bufferW = bufferWidth.toFloat()
    val bufferH = bufferHeight.toFloat()
    val scale = minOf(viewWidth / bufferW, viewHeight / bufferH)
    val offsetX = (viewWidth - bufferW * scale) / 2f
    val offsetY = (viewHeight - bufferH * scale) / 2f
    var mappedX = (viewX - offsetX) / scale
    var mappedY = (viewY - offsetY) / scale

    if (!clampToBounds && (mappedX < 0f || mappedX >= bufferW || mappedY < 0f || mappedY >= bufferH)) {
        return null
    }

    mappedX = mappedX.coerceIn(0f, bufferW - 1f)
    mappedY = mappedY.coerceIn(0f, bufferH - 1f)
    return PreviewPoint(mappedX.toInt(), mappedY.toInt())
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

private fun MaaEndTab.label(): String {
    return when (this) {
        MaaEndTab.HOME -> "主页"
        MaaEndTab.TASKS -> "任务"
        MaaEndTab.RUNNING -> "运行中"
        MaaEndTab.SETTINGS -> "设置"
        MaaEndTab.LOGS -> "日志"
    }
}

private fun compactTaskLabel(task: TaskDescriptor): String {
    return when (task.id) {
        "AndroidOpenGame" -> "打开游戏"
        "DailyRewards" -> "日常奖励"
        "DijiangRewards" -> "基建任务"
        "CreditShoppingN2" -> "信用购物"
        "VisitFriends" -> "拜访好友"
        "SellProduct" -> "售卖产品"
        "AutoEssence" -> "基质刷取"
        "EnvironmentMonitoring" -> "环境监测"
        "DeliveryJobs" -> "转交委托"
        "GearAssembly" -> "装备制造"
        else -> task.label
            .replace(Regex("[^\\p{L}\\p{N}\\p{IsHan}]"), "")
            .take(4)
            .ifBlank { task.id.take(4) }
    }
}
