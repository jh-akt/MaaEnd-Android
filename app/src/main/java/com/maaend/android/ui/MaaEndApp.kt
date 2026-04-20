@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.maaend.android.model.RuntimeStateSnapshot
import com.maaend.android.model.RunSessionPhase
import com.maaend.android.model.TaskDescriptor
import com.maaend.android.model.TaskOptionDescriptor
import com.maaend.android.model.TaskOptionType
import com.maaend.android.preview.DefaultDisplayConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MaaEndApp(viewModel: MainViewModel) {
    MaaEndTheme {
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
                                                holder.setFixedSize(
                                                    DefaultDisplayConfig.WIDTH,
                                                    DefaultDisplayConfig.HEIGHT,
                                                )
                                            }
                                        }

                                        override fun surfaceChanged(
                                            holder: SurfaceHolder,
                                            format: Int,
                                            width: Int,
                                            height: Int,
                                        ) {
                                            if (width == DefaultDisplayConfig.WIDTH &&
                                                height == DefaultDisplayConfig.HEIGHT
                                            ) {
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
                containerColor = Color.Transparent,
                bottomBar = {
                    AppBottomBar(
                        activeTab = state.activeTab,
                        onTabSelected = viewModel::selectTab,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(
                            horizontal = MaaEndDesignTokens.Spacing.lg,
                            vertical = MaaEndDesignTokens.Spacing.md,
                        ),
                    verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
                ) {
                    AppHeader(
                        title = state.activeTab.title(),
                        subtitle = state.activeTab.subtitle(state),
                        connected = state.rootConnected,
                    )

                    if (state.activeTab != MaaEndTab.TASKS && state.activeTab != MaaEndTab.HOME) {
                        StatusOverviewCard(state = state)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        when (state.activeTab) {
                            MaaEndTab.HOME -> HomeScreen(
                                state = state,
                                viewModel = viewModel,
                            )

                            MaaEndTab.TASKS -> TaskScreen(
                                viewModel = viewModel,
                                state = state,
                                tasks = state.catalog.tasks,
                                selectedTaskId = state.selectedTaskId,
                                checkedTaskIds = state.checkedTaskIds,
                                taskOptionSelectionsByTask = state.taskOptionSelectionsByTask,
                                taskInputValuesByTask = state.taskInputValuesByTask,
                                isFullscreenPreview = isFullscreenPreview,
                                onExpandPreview = { isFullscreenPreview = true },
                                previewContent = previewContent,
                                onSelect = viewModel::selectTask,
                                onToggleChecked = viewModel::toggleTaskChecked,
                                onStart = viewModel::startSelectedTask,
                                onSwitchOption = viewModel::updateTaskSwitchOption,
                                onToggleCheckboxOption = viewModel::toggleTaskCheckboxOption,
                                onInputValueChange = viewModel::updateTaskInputValue,
                            )

                            MaaEndTab.SETTINGS -> SettingsScreen(
                                logLevel = state.settings.logLevel,
                                onLogLevelChange = viewModel::updateLogLevel,
                            )

                            MaaEndTab.LOGS -> LogsScreen(lines = state.runtimeState.recentLogs)
                        }
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
}

@Composable
private fun AppHeader(
    title: String,
    subtitle: String,
    connected: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MaaEnd Android",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            StatusPill(
                label = if (connected) "Runtime 已连接" else "等待连接",
                active = connected,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusOverviewCard(state: MainUiState) {
    SectionCard(
        title = "运行状态",
        subtitle = state.lastMessage.ifBlank { "Root-only MVP scaffold ready" },
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            StatusPill("Root 可用", state.rootAvailable)
            StatusPill("授权通过", state.rootGranted)
            StatusPill("服务在线", state.rootConnected)
            if (state.busy) {
                StatusPill("处理中", active = true, accent = MaterialTheme.colorScheme.secondaryContainer)
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            MetricTile(
                label = "任务数",
                value = state.catalog.tasks.size.toString(),
                modifier = Modifier.width(110.dp),
            )
            MetricTile(
                label = "已勾选",
                value = state.checkedTaskIds.size.toString(),
                modifier = Modifier.width(110.dp),
            )
            MetricTile(
                label = "阶段",
                value = state.runtimeState.phase.name,
                modifier = Modifier.width(140.dp),
            )
        }

        if (state.servicePing.isNotBlank()) {
            Text(
                text = state.servicePing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val screenSizeLabel = "${displayMetrics.widthPixels} × ${displayMetrics.heightPixels}"
    val resourceSummary = if (state.catalog.tasks.isEmpty()) {
        "接口资源未加载"
    } else {
        "${state.catalog.tasks.size} 个任务 / ${state.catalog.presets.size} 个预设"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 620.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
        ) {
            SectionCard(
                title = "设备与服务",
                subtitle = "参考 MAA-Meow，把设备信息、接口资源和 Runtime 状态先收在首页第一屏。",
            ) {
                HomeInfoRow(
                    label = "屏幕分辨率",
                    value = screenSizeLabel,
                )
                Divider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
                HomeInfoRow(
                    label = "接口资源",
                    value = resourceSummary,
                    valueColor = if (state.catalog.tasks.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Divider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
                HomeServiceRow(
                    label = "Runtime 服务",
                    value = homeServiceStatusLabel(state),
                    color = homeServiceStatusColor(state),
                    loading = state.busy && !state.rootConnected,
                )
            }

            if (twoColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    HomeRootAccessCard(
                        state = state,
                        onReconnect = viewModel::requestRootAndConnect,
                        modifier = Modifier.weight(1f),
                    )
                    HomeQuickActionsCard(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                HomeRootAccessCard(
                    state = state,
                    onReconnect = viewModel::requestRootAndConnect,
                )
                HomeQuickActionsCard(
                    state = state,
                    viewModel = viewModel,
                )
            }

            SectionCard(
                title = "当前执行环境",
                subtitle = "把后台任务前需要确认的运行信息也提前放在首页。",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
                ) {
                    MetricTile(
                        label = "运行阶段",
                        value = state.runtimeState.phase.name,
                        modifier = Modifier.width(130.dp),
                    )
                    MetricTile(
                        label = "应用内窗口",
                        value = "${DefaultDisplayConfig.WIDTH} × ${DefaultDisplayConfig.HEIGHT}",
                        modifier = Modifier.width(150.dp),
                    )
                    MetricTile(
                        label = "已勾选任务",
                        value = state.checkedTaskIds.size.toString(),
                        modifier = Modifier.width(120.dp),
                    )
                    MetricTile(
                        label = "能力",
                        value = buildCapabilitiesLabel(state.runtimeState),
                        modifier = Modifier.width(160.dp),
                    )
                }

                InfoLine(
                    label = "运行目录",
                    value = state.runtimeState.runtimeRoot ?: "尚未准备",
                )
                InfoLine(
                    label = "当前任务",
                    value = state.runtimeState.currentTaskId ?: "暂无",
                )
                if (!state.runtimeState.lastDiagnosticsPath.isNullOrBlank()) {
                    InfoLine(
                        label = "最近诊断包",
                        value = state.runtimeState.lastDiagnosticsPath.orEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRootAccessCard(
    state: MainUiState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Root 接入",
        subtitle = "启动时会静默尝试获取 Root 并自动连接 Runtime，不再把手动连接当成必经步骤。",
        modifier = modifier,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            StatusPill("Root 可用", state.rootAvailable)
            StatusPill("授权通过", state.rootGranted)
            StatusPill("服务在线", state.rootConnected)
        }

        Text(
            text = homeRootHint(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.lastMessage.isNotBlank()) {
            Text(
                text = state.lastMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }

        OutlinedButton(
            onClick = onReconnect,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
        ) {
            Text(
                text = if (state.busy && !state.rootConnected) {
                    "正在连接 Runtime"
                } else if (state.rootConnected) {
                    "重新连接 Runtime"
                } else {
                    "手动重试连接"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HomeQuickActionsCard(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "快捷操作",
        subtitle = "常用动作继续放在首页，但它们会自动补齐 Root Runtime 连接。",
        modifier = modifier,
    ) {
        HomeActionButton(
            title = "准备运行时",
            description = if (state.runtimeState.runtimePrepared) {
                "运行目录已经准备完成。"
            } else {
                "同步资源并初始化运行目录。"
            },
            active = state.runtimeState.runtimePrepared,
            activeLabel = "已就绪",
            idleLabel = "准备",
            onClick = viewModel::prepareRuntime,
        )
        HomeActionButton(
            title = "窗口打开游戏",
            description = "直接在应用内拉起横屏预览窗口。",
            active = false,
            activeLabel = "已开",
            idleLabel = "打开",
            onClick = viewModel::startWindowedGame,
        )
        HomeActionButton(
            title = "导出诊断包",
            description = state.runtimeState.lastDiagnosticsPath?.takeIf { it.isNotBlank() }
                ?.let { "最近导出：$it" }
                ?: "导出最近一次运行的诊断信息。",
            active = !state.runtimeState.lastDiagnosticsPath.isNullOrBlank(),
            activeLabel = "最新",
            idleLabel = "导出",
            onClick = viewModel::exportDiagnostics,
        )
    }
}

@Composable
private fun TaskScreen(
    viewModel: MainViewModel,
    state: MainUiState,
    tasks: List<TaskDescriptor>,
    selectedTaskId: String?,
    checkedTaskIds: Set<String>,
    taskOptionSelectionsByTask: Map<String, Map<String, Set<String>>>,
    taskInputValuesByTask: Map<String, Map<String, Map<String, String>>>,
    isFullscreenPreview: Boolean,
    onExpandPreview: () -> Unit,
    previewContent: @Composable () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
    ) {
        PreviewCard(
            isFullscreenPreview = isFullscreenPreview,
            onExpandPreview = onExpandPreview,
            previewContent = previewContent,
        )

        TaskRuntimeCard(
            state = state,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            TaskListPanel(
                tasks = tasks,
                selectedTaskId = selectedTaskId,
                checkedTaskIds = checkedTaskIds,
                onSelect = onSelect,
                onToggleChecked = onToggleChecked,
                modifier = Modifier
                    .weight(0.42f)
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
                    .weight(0.58f)
                    .fillMaxHeight(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = if (checkedTaskIds.isEmpty()) "开始任务" else "开始任务（${checkedTaskIds.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = viewModel::stopRun,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
                enabled = canStopRun(state.runtimeState),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    text = "停止任务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
    SectionCard(
        title = "任务列表",
        subtitle = "勾选执行项，点击任务名称切换右侧配置。",
        modifier = modifier.fillMaxWidth(),
    ) {
        if (tasks.isEmpty()) {
            EmptyStateBlock(
                title = "暂无任务",
                description = "目录加载完成后，任务会显示在这里。",
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            ) {
                tasks.forEach { task ->
                    TaskCard(
                        task = task,
                        selected = task.id == selectedTaskId,
                        checked = task.id in checkedTaskIds,
                        onSelect = { onSelect(task.id) },
                        onToggleChecked = { checked -> onToggleChecked(task.id, checked) },
                    )
                }
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
    SectionCard(
        title = task?.label ?: "任务配置",
        subtitle = task?.description?.takeIf { it.isNotBlank() }
            ?: "从左侧选择任务后，这里会切换到对应配置。",
        modifier = modifier.fillMaxWidth(),
    ) {
        if (task == null) {
            EmptyStateBlock(
                title = "还没有选中任务",
                description = "先在左侧点一个任务，再到这里调整执行参数。",
            )
        } else if (task.options.isEmpty()) {
            EmptyStateBlock(
                title = "这个任务暂无额外参数",
                description = "可以直接勾选并开始执行。",
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
            ) {
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

    Surface(
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(MaaEndDesignTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (option.description.isNotBlank()) {
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (option.type) {
                TaskOptionType.Switch,
                TaskOptionType.Select -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
                    ) {
                        option.cases.forEach { optionCase ->
                            val selected = optionCase.name in selectedCaseNames
                            OptionChip(
                                label = optionCase.label,
                                selected = selected,
                                onClick = { onSwitchOption(taskId, option.id, optionCase.name) },
                            )
                        }
                    }
                    option.cases.forEach { optionCase ->
                        if (optionCase.name in selectedCaseNames && optionCase.nestedOptions.isNotEmpty()) {
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.xs),
                    ) {
                        option.cases.forEach { optionCase ->
                            val selected = optionCase.name in selectedCaseNames
                            CheckboxOptionRow(
                                label = optionCase.label,
                                checked = selected,
                                onCheckedChange = { onToggleCheckboxOption(taskId, option.id, optionCase.name) },
                            )
                        }
                    }
                    option.cases.forEach { optionCase ->
                        if (optionCase.name in selectedCaseNames && optionCase.nestedOptions.isNotEmpty()) {
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
                            supportingText = if (input.description.isNotBlank()) {
                                { Text(input.description) }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
                        )
                    }
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
    Surface(
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaaEndDesignTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
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
}

@Composable
private fun TaskCard(
    task: TaskDescriptor,
    selected: Boolean,
    checked: Boolean,
    onSelect: () -> Unit,
    onToggleChecked: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onToggleChecked,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = task.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CheckboxOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TaskRuntimeCard(
    state: MainUiState,
) {
    SectionCard(
        title = "运行信息",
        subtitle = null,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            InfoPill("阶段", state.runtimeState.phase.name)
            InfoPill("当前任务", state.runtimeState.currentTaskId ?: "无")
            InfoPill("运行目录", state.runtimeState.runtimeRoot ?: "未准备")
            InfoPill("能力", buildCapabilitiesLabel(state.runtimeState))
        }

        if (state.runtimeState.lastFailure != null || !state.runtimeState.lastDiagnosticsPath.isNullOrBlank()) {
            state.runtimeState.lastFailure?.let { failure ->
                InfoLine(
                    label = "失败截图",
                    value = failure.screenshotPath ?: "无",
                )
            }
            if (!state.runtimeState.lastDiagnosticsPath.isNullOrBlank()) {
                InfoLine(
                    label = "最近诊断包",
                    value = state.runtimeState.lastDiagnosticsPath.orEmpty(),
                )
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
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaaEndDesignTokens.Spacing.lg,
                        end = MaaEndDesignTokens.Spacing.lg,
                        top = MaaEndDesignTokens.Spacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.xs),
            ) {
                Text(
                    text = "应用内窗口",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "固定 1280 x 720，手机竖屏下尽量横向铺满，方便直接触控。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .aspectRatio(DefaultDisplayConfig.ASPECT_RATIO)
                    .clip(RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner))
                    .background(Color.Black),
            ) {
                if (!isFullscreenPreview) {
                    previewContent()
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.45f),
                                    ),
                                ),
                            )
                            .clickable(onClick = onExpandPreview),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(MaaEndDesignTokens.Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.xs),
                    ) {
                        StatusPill("点击展开横屏预览", active = true, accent = Color.White.copy(alpha = 0.18f))
                        Text(
                            text = "展开后可直接在预览上进行触控操作。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
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
                .padding(top = MaaEndDesignTokens.Spacing.sm, end = MaaEndDesignTokens.Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭预览",
                tint = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    logLevel: String,
    onLogLevelChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
    ) {
        SectionCard(
            title = "日志设置",
            subtitle = "当前先保留基础配置，后续可以继续往这里扩展。",
        ) {
            OutlinedTextField(
                value = logLevel,
                onValueChange = onLogLevelChange,
                label = { Text("日志级别") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
            )
            Text(
                text = "当前仅使用 SharedPreferences 持久化设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogsScreen(lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
        ),
    ) {
        if (lines.isEmpty()) {
            EmptyStateBlock(
                title = "暂无日志",
                description = "开始一次任务后，这里会滚动显示最近的运行记录。",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MaaEndDesignTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            ) {
                items(lines) { line ->
                    Surface(
                        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                    ) {
                        Text(
                            text = line,
                            modifier = Modifier.padding(MaaEndDesignTokens.Spacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    activeTab: MaaEndTab,
    onTabSelected: (MaaEndTab) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Column {
            Divider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = MaaEndDesignTokens.Spacing.xl, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaaEndTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    val contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        modifier = Modifier
                            .clickable { onTabSelected(tab) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = MaaEndDesignTokens.Spacing.sm, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = tab.icon(),
                            contentDescription = tab.label(),
                            tint = contentColor,
                        )
                        Text(
                            text = tab.label(),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaaEndDesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun ActionTile(
    title: String,
    description: String,
    active: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaaEndDesignTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    StatusPill(
                        label = if (active) "已就绪" else "执行",
                        active = active,
                        accent = if (active) null else MaterialTheme.colorScheme.surface,
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun HomeInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeServiceRow(
    label: String,
    value: String,
    color: Color,
    loading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (loading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.8.dp,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun HomeActionButton(
    title: String,
    description: String,
    active: Boolean,
    activeLabel: String,
    idleLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    }
    val borderColor = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaaEndDesignTokens.Spacing.md, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(
                label = if (active) activeLabel else idleLabel,
                active = active,
                accent = if (active) null else MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
    accent: Color? = null,
) {
    val backgroundColor = accent ?: if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }
    val contentColor = if (accent != null) {
        if (accent.luminance() < 0.45f) Color.White else MaterialTheme.colorScheme.onSurface
    } else if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.pill),
        color = backgroundColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier.padding(MaaEndDesignTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InfoPill(
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.pill),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun EmptyStateBlock(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MaaEndDesignTokens.Spacing.xxl)
            .clip(RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .padding(MaaEndDesignTokens.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class PreviewPoint(
    val x: Int,
    val y: Int,
)

private fun buildCapabilitiesLabel(runtimeState: RuntimeStateSnapshot): String {
    return buildString {
        if (runtimeState.capabilities.hasBundledGoService) append("go")
        if (runtimeState.capabilities.hasBundledMaaFramework) {
            if (isNotEmpty()) append(" + ")
            append("maafw")
        }
        if (isEmpty()) append("基础能力")
    }
}

private fun canStopRun(runtimeState: RuntimeStateSnapshot): Boolean {
    return when (runtimeState.phase) {
        RunSessionPhase.Preparing,
        RunSessionPhase.Running,
        RunSessionPhase.Stopping -> true

        RunSessionPhase.Idle,
        RunSessionPhase.Completed,
        RunSessionPhase.Failed -> false
    }
}

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

private fun MaaEndTab.title(): String {
    return when (this) {
        MaaEndTab.HOME -> "主页"
        MaaEndTab.TASKS -> "后台任务"
        MaaEndTab.SETTINGS -> "设置"
        MaaEndTab.LOGS -> "日志"
    }
}

private fun MaaEndTab.subtitle(state: MainUiState): String {
    return when (this) {
        MaaEndTab.HOME -> if (state.rootConnected) {
            "首页现在会优先展示设备、服务和快捷操作，Root 已在后台静默接入。"
        } else {
            "启动后会静默尝试获取 Root 并自动连接 Runtime，只有失败时才需要手动重试。"
        }

        MaaEndTab.TASKS -> "预览、任务配置和运行控制都集中在这里。当前共 ${state.catalog.tasks.size} 个任务。"
        MaaEndTab.SETTINGS -> "当前仅开放基础日志级别配置。"
        MaaEndTab.LOGS -> "最近日志 ${state.runtimeState.recentLogs.size} 条。"
    }
}

private fun MaaEndTab.label(): String {
    return when (this) {
        MaaEndTab.HOME -> "主页"
        MaaEndTab.TASKS -> "任务"
        MaaEndTab.SETTINGS -> "设置"
        MaaEndTab.LOGS -> "日志"
    }
}

private fun MaaEndTab.icon() = when (this) {
    MaaEndTab.HOME -> Icons.Default.Home
    MaaEndTab.TASKS -> Icons.Default.ViewList
    MaaEndTab.SETTINGS -> Icons.Default.Settings
    MaaEndTab.LOGS -> Icons.Default.Article
}

private fun homeServiceStatusLabel(state: MainUiState): String {
    return when {
        state.rootConnected -> "已连接"
        state.busy -> "连接中"
        state.rootGranted -> "已授权，待握手"
        state.rootAvailable -> "等待授权"
        else -> "未检测到 Root"
    }
}

@Composable
private fun homeServiceStatusColor(state: MainUiState): Color {
    return when {
        state.rootConnected -> MaterialTheme.colorScheme.primary
        state.busy -> MaterialTheme.colorScheme.secondary
        state.rootGranted || state.rootAvailable -> Color(0xFFDD8A16)
        else -> MaterialTheme.colorScheme.error
    }
}

private fun homeRootHint(state: MainUiState): String {
    return when {
        state.rootConnected -> "Root 和 Runtime 已握手完成，后续准备运行时、打开游戏或执行任务都会直接复用这条连接。"
        state.rootAvailable -> "应用启动时会静默申请 Root 并自动握手。只有自动连接没完成时，才需要用下面的按钮手动重试。"
        else -> "当前没有检测到可用 Root，所以不会自动建立 Runtime 连接。"
    }
}
