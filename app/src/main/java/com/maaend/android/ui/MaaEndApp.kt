@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.maaend.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.maaend.android.BuildConfig
import com.maaframework.android.model.ResourceDescriptor
import com.maaframework.android.model.RuntimeStateSnapshot
import com.maaframework.android.model.RunSessionPhase
import com.maaframework.android.model.TaskDescriptor
import com.maaframework.android.model.TaskOptionDescriptor
import com.maaframework.android.model.TaskOptionType
import com.maaframework.android.preview.DefaultDisplayConfig
import com.maaframework.android.ui.MaaFullscreenPreviewOverlay as FrameworkFullscreenPreviewOverlay
import com.maaframework.android.ui.MaaHomeAction as FrameworkHomeAction
import com.maaframework.android.ui.MaaHomeActionRow as FrameworkHomeActionRow
import com.maaframework.android.ui.MaaHomeDivider
import com.maaframework.android.ui.MaaHomeInfo as FrameworkHomeInfo
import com.maaframework.android.ui.MaaHomeInfoRow as FrameworkHomeInfoRow
import com.maaframework.android.ui.MaaHomePanel as FrameworkHomePanel
import com.maaframework.android.ui.MaaHomeProgress as FrameworkHomeProgress
import com.maaframework.android.ui.MaaHomeProgressBlock as FrameworkHomeProgressBlock
import com.maaframework.android.ui.MaaHomeService as FrameworkHomeService
import com.maaframework.android.ui.MaaHomeStatus as FrameworkHomeStatus
import com.maaframework.android.ui.MaaHomeSupportText as FrameworkHomeSupportText
import com.maaframework.android.ui.MaaHomeTone as FrameworkHomeTone
import com.maaframework.android.ui.MaaLogMetric as FrameworkLogMetric
import com.maaframework.android.ui.MaaPreviewPanel as FrameworkPreviewPanel
import com.maaframework.android.ui.MaaPreviewSurfaceHost as FrameworkPreviewSurfaceHost
import com.maaframework.android.ui.MaaResourceRepositoryContent as FrameworkResourceRepositoryContent
import com.maaframework.android.ui.MaaRuntimeLogList as FrameworkRuntimeLogList
import com.maaframework.android.ui.MaaRuntimeLogsPanel as FrameworkRuntimeLogsPanel
import com.maaframework.android.ui.MaaSettingsChoice as FrameworkSettingsChoice
import com.maaframework.android.ui.MaaSettingsChoiceRow as FrameworkSettingsChoiceRow
import com.maaframework.android.ui.MaaSettingsPanel as FrameworkSettingsPanel
import com.maaframework.android.ui.MaaSettingsSection as FrameworkSettingsSection
import com.maaframework.android.ui.MaaTaskDetailPanel as FrameworkTaskDetailPanel
import com.maaframework.android.ui.MaaTaskListPanel as FrameworkTaskListPanel
import com.maaframework.android.ui.MaaTaskOptionsForm as FrameworkTaskOptionsForm
import com.maaend.android.runtime.PersistentResourceRepositoryManager
import com.maaend.android.runtime.PersistentResourceRepositorySyncProgress
import com.maaend.android.runtime.PersistentResourceRepositoryStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun MaaEndApp(viewModel: MainViewModel) {
    MaaEndTheme {
        val state by viewModel.uiState.collectAsState()
        var isFullscreenPreview by rememberSaveable { mutableStateOf(false) }

        val previewContent = remember {
            movableContentOf {
                FrameworkPreviewSurfaceHost(
                    modifier = Modifier.fillMaxSize(),
                    onPreviewSurfaceChanged = viewModel::setPreviewSurface,
                )
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
                            horizontal = MaaEndDesignTokens.Spacing.sm,
                            vertical = MaaEndDesignTokens.Spacing.sm,
                        ),
                    verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
                ) {
                    if (state.activeTab != MaaEndTab.TASKS) {
                        AppHeader(
                            title = state.activeTab.title(),
                            subtitle = if (state.activeTab == MaaEndTab.HOME) null else state.activeTab.subtitle(state),
                            connected = state.rootConnected,
                        )
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
                                onSelectResource = viewModel::selectResource,
                                onSwitchSharedOption = viewModel::updateSharedSwitchOption,
                                onToggleSharedCheckboxOption = viewModel::toggleSharedCheckboxOption,
                                onSharedInputValueChange = viewModel::updateSharedInputValue,
                            )

                            MaaEndTab.TASKS -> TaskScreen(
                                viewModel = viewModel,
                                state = state,
                                tasks = state.catalog.tasks,
                                selectedResourceId = state.selectedResourceId,
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
                                onToggleDisplayPower = viewModel::toggleDisplayPower,
                                onSwitchOption = viewModel::updateTaskSwitchOption,
                                onToggleCheckboxOption = viewModel::toggleTaskCheckboxOption,
                                onInputValueChange = viewModel::updateTaskInputValue,
                            )

                            MaaEndTab.SETTINGS -> SettingsScreen(
                                state = state,
                                viewModel = viewModel,
                            )

                            MaaEndTab.LOGS -> LogsScreen(
                                state = state,
                                currentTaskLabel = state.catalog.tasks
                                    .firstOrNull { it.id == state.runtimeState.currentTaskId }
                                    ?.label,
                            )
                        }
                    }
                }
            }

            if (isFullscreenPreview) {
                FrameworkFullscreenPreviewOverlay(
                    previewContent = previewContent,
                    onDismissRequest = { isFullscreenPreview = false },
                    onPreviewTouchDown = viewModel::onPreviewTouchDown,
                    onPreviewTouchMove = viewModel::onPreviewTouchMove,
                    onPreviewTouchUp = viewModel::onPreviewTouchUp,
                )
            }
            if (state.resourceRepositoryClearConfirmVisible) {
                ResourceRepositoryClearConfirmationDialog(
                    onDismissRequest = viewModel::dismissClearResourceRepositoryConfirmation,
                    onConfirm = viewModel::clearResourceRepository,
                )
            }
        }
    }
}

@Composable
private fun AppHeader(
    title: String,
    subtitle: String?,
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
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    onSelectResource: (String) -> Unit,
    onSwitchSharedOption: (String, String, String) -> Unit,
    onToggleSharedCheckboxOption: (String, String, String) -> Unit,
    onSharedInputValueChange: (String, String, String, String) -> Unit,
) {
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val screenSizeLabel = "${displayMetrics.widthPixels} × ${displayMetrics.heightPixels}"
    val selectedResource = state.catalog.resources.firstOrNull { it.id == state.selectedResourceId }
        ?: state.catalog.resources.firstOrNull()
    val visibleGlobalOptions = remember(state.catalog.globalOptions, state.selectedResourceId) {
        ProjectInterfaceSupport.filterOptionsForResource(state.catalog.globalOptions, state.selectedResourceId)
    }
    val resourceOptions = remember(selectedResource, state.selectedResourceId) {
        selectedResource?.let {
            ProjectInterfaceSupport.filterOptionsForResource(it.options, state.selectedResourceId)
        }.orEmpty()
    }
    val globalScopeId = ProjectInterfaceSupport.GLOBAL_SCOPE_ID
    val resourceScopeId = selectedResource?.id?.let(ProjectInterfaceSupport::resourceScopeId)
    val globalInputErrors = remember(
        visibleGlobalOptions,
        state.sharedOptionSelectionsByScope,
        state.sharedInputValuesByScope,
    ) {
        ProjectInterfaceSupport.collectInputValidationErrors(
            options = visibleGlobalOptions,
            selectedByOption = state.sharedOptionSelectionsByScope[globalScopeId].orEmpty(),
            inputValuesByOption = state.sharedInputValuesByScope[globalScopeId].orEmpty(),
        )
    }
    val resourceInputErrors = remember(
        resourceOptions,
        resourceScopeId,
        state.sharedOptionSelectionsByScope,
        state.sharedInputValuesByScope,
    ) {
        ProjectInterfaceSupport.collectInputValidationErrors(
            options = resourceOptions,
            selectedByOption = resourceScopeId?.let { state.sharedOptionSelectionsByScope[it].orEmpty() }.orEmpty(),
            inputValuesByOption = resourceScopeId?.let { state.sharedInputValuesByScope[it].orEmpty() }.orEmpty(),
        )
    }
    val resourceSummary = if (state.catalog.tasks.isEmpty()) {
        "接口资源未加载"
    } else {
        "${state.catalog.tasks.size} 个任务 / ${state.catalog.presets.size} 个预设"
    }

    FrameworkHomePanel(
        overview = buildList {
            add(FrameworkHomeInfo("屏幕分辨率", screenSizeLabel))
            add(
                FrameworkHomeInfo(
                    label = "接口资源",
                    value = resourceSummary,
                    tone = if (state.catalog.tasks.isEmpty()) FrameworkHomeTone.Error else FrameworkHomeTone.Neutral,
                ),
            )
            add(FrameworkHomeInfo("运行阶段", state.runtimeState.phase.displayName()))
            state.runtimeState.currentTaskId?.takeIf { it.isNotBlank() }?.let { currentTaskId ->
                add(FrameworkHomeInfo("当前任务", currentTaskId))
            }
        },
        service = FrameworkHomeService(
            label = "Runtime 服务",
            value = homeServiceStatusLabel(state),
            tone = homeServiceTone(state),
            loading = state.busy && !state.rootConnected,
        ),
        statuses = listOf(
            FrameworkHomeStatus("Root 可用", state.rootAvailable),
            FrameworkHomeStatus("授权通过", state.rootGranted),
            FrameworkHomeStatus("服务在线", state.rootConnected),
        ),
        actions = listOf(
            FrameworkHomeAction(
                title = "准备运行时",
                actionLabel = if (state.runtimeState.runtimePrepared) "已就绪" else "执行",
                enabled = !state.busy,
                onClick = viewModel::prepareRuntime,
            ),
            FrameworkHomeAction(
                title = "打开游戏",
                actionLabel = "打开",
                enabled = !state.busy,
                onClick = viewModel::startWindowedGame,
            ),
            FrameworkHomeAction(
                title = if (state.rootConnected) "重新连接 Runtime" else "连接 Root / Runtime",
                actionLabel = if (state.busy && !state.rootConnected) {
                    "连接中"
                } else if (state.rootConnected) {
                    "重连"
                } else {
                    "连接"
                },
                enabled = !state.busy,
                onClick = viewModel::requestRootAndConnect,
            ),
            FrameworkHomeAction(
                title = "导出诊断包",
                actionLabel = if (state.runtimeState.lastDiagnosticsPath.isNullOrBlank()) "导出" else "最新",
                onClick = viewModel::exportDiagnostics,
            ),
        ),
        resourceContent = {
            ResourceConfigPanel(
                resources = state.catalog.resources,
                selectedResource = selectedResource,
                resourceRepository = state.resourceRepository,
                resourceRepositoryUpdating = state.resourceRepositoryUpdating,
                resourceRepositoryProgress = state.resourceRepositoryProgress,
                globalOptions = visibleGlobalOptions,
                globalSelections = state.sharedOptionSelectionsByScope[globalScopeId].orEmpty(),
                globalInputs = state.sharedInputValuesByScope[globalScopeId].orEmpty(),
                globalInputErrors = globalInputErrors,
                resourceOptions = resourceOptions,
                resourceSelections = resourceScopeId?.let { state.sharedOptionSelectionsByScope[it].orEmpty() }.orEmpty(),
                resourceInputs = resourceScopeId?.let { state.sharedInputValuesByScope[it].orEmpty() }.orEmpty(),
                resourceInputErrors = resourceInputErrors,
                onSelectResource = onSelectResource,
                onSwitchSharedOption = onSwitchSharedOption,
                onToggleSharedCheckboxOption = onToggleSharedCheckboxOption,
                onSharedInputValueChange = onSharedInputValueChange,
                onRefreshResourceRepository = viewModel::refreshResourceRepository,
                onClearResourceRepository = viewModel::requestClearResourceRepositoryConfirmation,
                hideDescriptions = true,
            )
        },
    )
}

@Composable
private fun HomeStatusCard(
    screenSizeLabel: String,
    resourceSummary: String,
    state: MainUiState,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaaEndDesignTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
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
        subtitle = null,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            StatusPill("Root 可用", state.rootAvailable)
            StatusPill("授权通过", state.rootGranted)
            StatusPill("服务在线", state.rootConnected)
        }

        if (state.lastMessage.isNotBlank()) {
            Text(
                text = state.lastMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        OutlinedButton(
            onClick = onReconnect,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
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
                style = MaterialTheme.typography.bodySmall,
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
        subtitle = null,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        HomeActionButton(
            title = "准备运行时",
            active = state.runtimeState.runtimePrepared,
            activeLabel = "已就绪",
            idleLabel = "准备",
            onClick = viewModel::prepareRuntime,
        )
        HomeActionButton(
            title = "窗口打开游戏",
            active = false,
            activeLabel = "已开",
            idleLabel = "打开",
            onClick = viewModel::startWindowedGame,
        )
        HomeActionButton(
            title = "导出诊断包",
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
    selectedResourceId: String?,
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
    onToggleDisplayPower: () -> Unit,
    onSwitchOption: (String, String, String) -> Unit,
    onToggleCheckboxOption: (String, String, String) -> Unit,
    onInputValueChange: (String, String, String, String) -> Unit,
) {
    val visibleTasks = remember(tasks, selectedResourceId) {
        tasks.filter { ProjectInterfaceSupport.taskSupportsResource(it, selectedResourceId) }
    }
    val selectedTask = visibleTasks.firstOrNull { it.id == selectedTaskId }
    val visibleTaskOptions = remember(selectedTask, selectedResourceId) {
        selectedTask?.let { ProjectInterfaceSupport.filterOptionsForResource(it.options, selectedResourceId) }.orEmpty()
    }
    val taskInputErrors = remember(
        visibleTaskOptions,
        selectedTask,
        taskOptionSelectionsByTask,
        taskInputValuesByTask,
    ) {
        if (selectedTask == null) {
            emptyMap()
        } else {
            ProjectInterfaceSupport.collectInputValidationErrors(
                options = visibleTaskOptions,
                selectedByOption = taskOptionSelectionsByTask[selectedTask.id].orEmpty(),
                inputValuesByOption = taskInputValuesByTask[selectedTask.id].orEmpty(),
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
    ) {
        FrameworkPreviewPanel(
            isFullscreenPreview = isFullscreenPreview,
            onExpandPreview = onExpandPreview,
            previewContent = previewContent,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            FrameworkTaskListPanel(
                tasks = visibleTasks,
                selectedTaskId = selectedTaskId,
                checkedTaskIds = checkedTaskIds,
                onSelectTask = onSelect,
                onToggleTaskChecked = onToggleChecked,
                modifier = Modifier
                    .fillMaxHeight(),
                runningTaskId = state.runtimeState.currentTaskId,
                pinnedTaskIds = ProjectInterfaceSupport.PINNED_TASK_IDS,
                taskLabel = ::compactTaskLabel,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            ) {
                FrameworkTaskDetailPanel(
                    task = selectedTask,
                    options = visibleTaskOptions,
                    selectedCaseNamesByOption = selectedTask?.let { taskOptionSelectionsByTask[it.id].orEmpty() }.orEmpty(),
                    inputValuesByOption = selectedTask?.let { taskInputValuesByTask[it.id].orEmpty() }.orEmpty(),
                    inputErrorsByOption = taskInputErrors,
                    onSwitchOption = onSwitchOption,
                    onToggleCheckboxOption = onToggleCheckboxOption,
                    onInputValueChange = onInputValueChange,
                    modifier = Modifier.weight(1f, fill = true),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
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
                    text = "开始任务",
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
            OutlinedButton(
                onClick = onToggleDisplayPower,
                modifier = Modifier
                    .widthIn(min = 56.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
                enabled = state.runtimeState.phase in setOf(
                    RunSessionPhase.Preparing,
                    RunSessionPhase.Running,
                    RunSessionPhase.Stopping,
                ) || state.runtimeState.displayPowerOffActive,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (state.runtimeState.displayPowerOffActive) "恢复\n亮屏" else "息屏\n挂机",
                    style = MaterialTheme.typography.labelMedium.copy(lineHeight = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
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
    val (pinnedTasks, otherTasks) = tasks.partition {
        it.id in ProjectInterfaceSupport.PINNED_TASK_IDS
    }
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = 96.dp, max = 112.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (tasks.isEmpty()) {
            EmptyStateBlock(
                title = "暂无任务",
                description = "目录加载完成后，任务会显示在这里。",
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pinnedTasks.forEach { task ->
                        TaskCard(
                            task = task,
                            selected = task.id == selectedTaskId,
                            checked = task.id in checkedTaskIds,
                            onSelect = { onSelect(task.id) },
                            onToggleChecked = { checked -> onToggleChecked(task.id, checked) },
                        )
                    }
                    if (otherTasks.isNotEmpty()) {
                        TaskSectionDivider()
                        otherTasks.forEach { task ->
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
    }
}

@Composable
private fun TaskSectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun ResourceConfigPanel(
    resources: List<ResourceDescriptor>,
    selectedResource: ResourceDescriptor?,
    resourceRepository: PersistentResourceRepositoryStatus,
    resourceRepositoryUpdating: Boolean,
    resourceRepositoryProgress: PersistentResourceRepositorySyncProgress?,
    globalOptions: List<TaskOptionDescriptor>,
    globalSelections: Map<String, Set<String>>,
    globalInputs: Map<String, Map<String, String>>,
    globalInputErrors: Map<String, Map<String, String>>,
    resourceOptions: List<TaskOptionDescriptor>,
    resourceSelections: Map<String, Set<String>>,
    resourceInputs: Map<String, Map<String, String>>,
    resourceInputErrors: Map<String, Map<String, String>>,
    onSelectResource: (String) -> Unit,
    onSwitchSharedOption: (String, String, String) -> Unit,
    onToggleSharedCheckboxOption: (String, String, String) -> Unit,
    onSharedInputValueChange: (String, String, String, String) -> Unit,
    onRefreshResourceRepository: () -> Unit,
    onClearResourceRepository: () -> Unit,
    hideDescriptions: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showGlobalConfig by rememberSaveable(globalOptions.size) { mutableStateOf(false) }
    var showResourceConfig by rememberSaveable(selectedResource?.id, resourceOptions.size) { mutableStateOf(false) }

    SettingsGroupCard {
        SettingsInfoRow(
            label = "资源仓库",
            value = resourceRepositorySummary(resourceRepository),
        )
        resourceRepository.rootPath?.takeIf { it.isNotBlank() }?.let { rootPath ->
            SettingsDivider()
            SettingsSupportText(
                text = rootPath,
                tone = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        resourceRepository.lastError?.takeIf { it.isNotBlank() }?.let { error ->
            SettingsDivider()
            SettingsSupportText(
                text = error,
                tone = MaterialTheme.colorScheme.error,
            )
        }
        SettingsDivider()
        SettingsActionRow(
            title = if (resourceRepository.available) "更新 GitHub 资源" else "下载 GitHub 资源",
            description = if (hideDescriptions) {
                null
            } else if (resourceRepositoryUpdating) {
                "正在处理 GitHub 资源缓存"
            } else {
                "首次下载后会缓存在本地，后续按需手动刷新。"
            },
            actionLabel = if (resourceRepositoryUpdating) "处理中" else "执行",
            enabled = !resourceRepositoryUpdating,
            onClick = onRefreshResourceRepository,
        )
        SettingsDivider()
        SettingsActionRow(
            title = "清空 GitHub 资源",
            description = if (hideDescriptions) {
                null
            } else {
                "删除当前缓存和历史目录，下次更新会重新下载，适合排除旧数据干扰。"
            },
            actionLabel = if (resourceRepositoryUpdating) "处理中" else "清空",
            enabled = !resourceRepositoryUpdating,
            onClick = onClearResourceRepository,
        )
        if (resourceRepositoryUpdating && resourceRepositoryProgress != null) {
            SettingsDivider()
            ResourceRepositoryProgressBlock(progress = resourceRepositoryProgress)
        }

        if (resources.isNotEmpty()) {
            SettingsDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "资源包",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    resources.forEach { resource ->
                        FilterChip(
                            selected = resource.id == selectedResource?.id,
                            onClick = { onSelectResource(resource.id) },
                            label = {
                                Text(
                                    text = resource.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = resource.iconPath.takeIf { it.isNotBlank() }?.let {
                                {
                                    AssetIcon(
                                        assetPath = it,
                                        contentDescription = resource.label,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (resource.id == selectedResource?.id) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            null
                                        },
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
                selectedResource?.description?.takeIf { !hideDescriptions && it.isNotBlank() }?.let { description ->
                    RichDescriptionText(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (globalOptions.isNotEmpty()) {
            SettingsDivider()
            SettingsActionRow(
                title = "全局配置",
                description = if (hideDescriptions) null else "共 ${globalOptions.size} 项，会合并到所有任务。",
                actionLabel = if (showGlobalConfig) "收起" else "展开",
                onClick = { showGlobalConfig = !showGlobalConfig },
            )
            if (showGlobalConfig) {
                FrameworkTaskOptionsForm(
                    ownerId = ProjectInterfaceSupport.GLOBAL_SCOPE_ID,
                    title = "全局配置",
                    description = "",
                    options = globalOptions,
                    selectedCaseNamesByOption = globalSelections,
                    inputValuesByOption = globalInputs,
                    inputErrorsByOption = globalInputErrors,
                    onSwitchOption = onSwitchSharedOption,
                    onToggleCheckboxOption = onToggleSharedCheckboxOption,
                    onInputValueChange = onSharedInputValueChange,
                    showHeader = false,
                    hideDescriptions = hideDescriptions,
                )
            }
        }

        if (resourceOptions.isNotEmpty() && selectedResource != null) {
            SettingsDivider()
            SettingsActionRow(
                title = "${selectedResource.label} 配置",
                description = if (hideDescriptions) null else "共 ${resourceOptions.size} 项，只在当前资源包生效。",
                actionLabel = if (showResourceConfig) "收起" else "展开",
                onClick = { showResourceConfig = !showResourceConfig },
            )
            if (showResourceConfig) {
                FrameworkTaskOptionsForm(
                    ownerId = ProjectInterfaceSupport.resourceScopeId(selectedResource.id),
                    title = "${selectedResource.label} 配置",
                    description = "",
                    options = resourceOptions,
                    selectedCaseNamesByOption = resourceSelections,
                    inputValuesByOption = resourceInputs,
                    inputErrorsByOption = resourceInputErrors,
                    onSwitchOption = onSwitchSharedOption,
                    onToggleCheckboxOption = onToggleSharedCheckboxOption,
                    onInputValueChange = onSharedInputValueChange,
                    showHeader = false,
                    hideDescriptions = hideDescriptions,
                )
            }
        }
    }
}

@Composable
private fun TaskConfigPanel(
    task: TaskDescriptor?,
    options: List<TaskOptionDescriptor>,
    selectedCaseNamesByOption: Map<String, Set<String>>,
    inputValuesByOption: Map<String, Map<String, String>>,
    inputErrorsByOption: Map<String, Map<String, String>>,
    onSwitchOption: (String, String, String) -> Unit,
    onToggleCheckboxOption: (String, String, String) -> Unit,
    onInputValueChange: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "任务配置",
        subtitle = null,
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 2.dp,
            top = MaaEndDesignTokens.Spacing.sm,
            end = 4.dp,
            bottom = MaaEndDesignTokens.Spacing.sm,
        ),
    ) {
        if (task == null) {
            EmptyStateBlock(
                title = "还没有选中任务",
                description = "先在左侧点一个任务，再到这里调整执行参数。",
            )
        } else if (options.isEmpty()) {
            EmptyStateBlock(
                title = "这个任务暂无额外参数",
                description = "可以直接勾选并开始执行。",
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    OptionConfigCard(
                        ownerId = task.id,
                        title = task.label,
                        description = task.description,
                        iconPath = task.iconPath,
                        options = options,
                        selectedCaseNamesByOption = selectedCaseNamesByOption,
                        inputValuesByOption = inputValuesByOption,
                        inputErrorsByOption = inputErrorsByOption,
                        onSwitchOption = onSwitchOption,
                        onToggleCheckboxOption = onToggleCheckboxOption,
                        onInputValueChange = onInputValueChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionConfigCard(
    ownerId: String,
    title: String,
    description: String,
    options: List<TaskOptionDescriptor>,
    selectedCaseNamesByOption: Map<String, Set<String>>,
    inputValuesByOption: Map<String, Map<String, String>>,
    inputErrorsByOption: Map<String, Map<String, String>>,
    onSwitchOption: (String, String, String) -> Unit,
    onToggleCheckboxOption: (String, String, String) -> Unit,
    onInputValueChange: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    iconPath: String = "",
    showHeader: Boolean = true,
    hideDescriptions: Boolean = false,
) {
    val descriptionStyle = taskConfigDescriptionStyle()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
        ) {
            if (showHeader) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
                ) {
                    if (iconPath.isNotBlank()) {
                        AssetIcon(
                            assetPath = iconPath,
                            contentDescription = title,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (showHeader && !hideDescriptions && description.isNotBlank()) {
                RichDescriptionText(
                    text = description,
                    style = descriptionStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.xs),
            ) {
                options.forEach { option ->
                    TaskOptionBlock(
                        ownerId = ownerId,
                        option = option,
                        selectedCaseNamesByOption = selectedCaseNamesByOption,
                        inputValuesByOption = inputValuesByOption,
                        inputErrorsByOption = inputErrorsByOption,
                        onSwitchOption = onSwitchOption,
                        onToggleCheckboxOption = onToggleCheckboxOption,
                        onInputValueChange = onInputValueChange,
                        hideDescriptions = hideDescriptions,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskOptionBlock(
    ownerId: String,
    option: TaskOptionDescriptor,
    selectedCaseNamesByOption: Map<String, Set<String>>,
    inputValuesByOption: Map<String, Map<String, String>>,
    inputErrorsByOption: Map<String, Map<String, String>>,
    onSwitchOption: (String, String, String) -> Unit,
    onToggleCheckboxOption: (String, String, String) -> Unit,
    onInputValueChange: (String, String, String, String) -> Unit,
    compact: Boolean = false,
    nested: Boolean = false,
    hideDescriptions: Boolean = false,
) {
    val selectedCaseNames = selectedCaseNamesByOption[option.id].takeUnless { it.isNullOrEmpty() }
        ?: ProjectInterfaceSupport.defaultSelectionForOption(option)
    val inputValues = inputValuesByOption[option.id].orEmpty()
    val descriptionStyle = taskConfigDescriptionStyle()
    val controlTextStyle = taskConfigControlTextStyle()
    val contentPadding = if (compact) 6.dp else MaaEndDesignTokens.Spacing.sm
    val blockSpacing = if (compact) 4.dp else MaaEndDesignTokens.Spacing.xs
    val headerSpacing = if (compact) 4.dp else MaaEndDesignTokens.Spacing.xs
    val titleStyle = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall
    val iconSize = if (compact) 14.dp else 18.dp
    val inputMinHeight = if (compact) 38.dp else 44.dp

    Surface(
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
        color = if (nested) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = if (nested) {
            null
        } else {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(blockSpacing),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(headerSpacing),
            ) {
                if (option.iconPath.isNotBlank()) {
                    AssetIcon(
                        assetPath = option.iconPath,
                        contentDescription = option.label,
                        modifier = Modifier.size(iconSize),
                    )
                }
                Text(
                    text = option.label,
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (!hideDescriptions && option.description.isNotBlank()) {
                RichDescriptionText(
                    text = option.description,
                    style = descriptionStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (option.type) {
                TaskOptionType.Switch,
                TaskOptionType.Select -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        option.cases.forEach { optionCase ->
                            val caseLabel = optionCase.label.ifBlank { optionCase.name }.trim()
                            if (caseLabel.isBlank()) {
                                return@forEach
                            }
                            val selected = optionCase.name in selectedCaseNames
                            OptionChip(
                                label = caseLabel,
                                selected = selected,
                                onClick = { onSwitchOption(ownerId, option.id, optionCase.name) },
                            )
                        }
                    }
                    option.cases.forEach { optionCase ->
                        if (optionCase.name in selectedCaseNames && optionCase.nestedOptions.isNotEmpty()) {
                            NestedTaskOptions(
                                ownerId = ownerId,
                                options = optionCase.nestedOptions,
                                selectedCaseNamesByOption = selectedCaseNamesByOption,
                                inputValuesByOption = inputValuesByOption,
                                inputErrorsByOption = inputErrorsByOption,
                                onSwitchOption = onSwitchOption,
                                onToggleCheckboxOption = onToggleCheckboxOption,
                                onInputValueChange = onInputValueChange,
                                hideDescriptions = hideDescriptions,
                            )
                        }
                    }
                }

                TaskOptionType.Checkbox -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.xs),
                    ) {
                        option.cases.forEach { optionCase ->
                            val caseLabel = optionCase.label.ifBlank { optionCase.name }.trim()
                            if (caseLabel.isBlank()) {
                                return@forEach
                            }
                            val selected = optionCase.name in selectedCaseNames
                            CheckboxOptionRow(
                                label = caseLabel,
                                checked = selected,
                                onCheckedChange = { onToggleCheckboxOption(ownerId, option.id, optionCase.name) },
                            )
                        }
                    }
                    option.cases.forEach { optionCase ->
                        if (optionCase.name in selectedCaseNames && optionCase.nestedOptions.isNotEmpty()) {
                            NestedTaskOptions(
                                ownerId = ownerId,
                                options = optionCase.nestedOptions,
                                selectedCaseNamesByOption = selectedCaseNamesByOption,
                                inputValuesByOption = inputValuesByOption,
                                inputErrorsByOption = inputErrorsByOption,
                                onSwitchOption = onSwitchOption,
                                onToggleCheckboxOption = onToggleCheckboxOption,
                                onInputValueChange = onInputValueChange,
                                hideDescriptions = hideDescriptions,
                            )
                        }
                    }
                }

                TaskOptionType.Input -> {
                    option.inputs.forEach { input ->
                        val error = inputErrorsByOption[option.id]?.get(input.name)
                        OutlinedTextField(
                            value = inputValues[input.name] ?: input.defaultValue,
                            onValueChange = { value ->
                                onInputValueChange(ownerId, option.id, input.name, value)
                            },
                            label = { Text(input.label, style = controlTextStyle) },
                            isError = error != null,
                            textStyle = MaterialTheme.typography.bodySmall,
                            supportingText = if (error != null) {
                                { Text(error, style = descriptionStyle) }
                            } else if (!hideDescriptions && input.description.isNotBlank()) {
                                {
                                    RichDescriptionText(
                                        text = input.description,
                                        style = descriptionStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = inputMinHeight),
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
    ownerId: String,
    options: List<TaskOptionDescriptor>,
    selectedCaseNamesByOption: Map<String, Set<String>>,
    inputValuesByOption: Map<String, Map<String, String>>,
    inputErrorsByOption: Map<String, Map<String, String>>,
    onSwitchOption: (String, String, String) -> Unit,
    onToggleCheckboxOption: (String, String, String) -> Unit,
    onInputValueChange: (String, String, String, String) -> Unit,
    hideDescriptions: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.pill),
                ),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
                )
                .padding(start = 4.dp, top = 1.dp, end = 0.dp, bottom = 1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            options.forEach { nestedOption ->
                TaskOptionBlock(
                    ownerId = ownerId,
                    option = nestedOption,
                    selectedCaseNamesByOption = selectedCaseNamesByOption,
                    inputValuesByOption = inputValuesByOption,
                    inputErrorsByOption = inputErrorsByOption,
                    onSwitchOption = onSwitchOption,
                    onToggleCheckboxOption = onToggleCheckboxOption,
                    onInputValueChange = onInputValueChange,
                    compact = true,
                    nested = true,
                    hideDescriptions = hideDescriptions,
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
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onToggleChecked,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer(
                            scaleX = 0.72f,
                            scaleY = 0.72f,
                        ),
                )
            }
            Text(
                text = compactTaskLabel(task),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
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
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer(
                        scaleX = 0.72f,
                        scaleY = 0.72f,
                    ),
            )
        }
        Text(
            text = label,
            style = taskConfigControlTextStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PreviewCard(
    isFullscreenPreview: Boolean,
    onExpandPreview: () -> Unit,
    previewContent: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(DefaultDisplayConfig.ASPECT_RATIO)
            .clip(RoundedCornerShape(MaaEndDesignTokens.CornerRadius.card))
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
                                Color.Black.copy(alpha = 0.35f),
                            ),
                        ),
                    )
                    .clickable(onClick = onExpandPreview),
            )
        } else {
            Spacer(modifier = Modifier.fillMaxSize())
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
    val activeTouches = remember { mutableMapOf<Int, PreviewPoint>() }
    val contactIdsByPointer = remember { mutableMapOf<Long, Int>() }
    val nextContactId = remember { intArrayOf(0) }

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

    DisposableEffect(viewModel) {
        onDispose {
            activeTouches.toMap().forEach { (contactId, point) ->
                viewModel.onPreviewTouchUp(contactId, point.x, point.y)
            }
            activeTouches.clear()
            contactIdsByPointer.clear()
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
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        if (changes.isEmpty()) {
                            continue
                        }

                        changes
                            .filter { !it.previousPressed && it.pressed }
                            .forEach { change ->
                                val point = mapViewToVirtualDisplay(
                                    viewX = change.position.x,
                                    viewY = change.position.y,
                                    viewWidth = size.width,
                                    viewHeight = size.height,
                                    bufferWidth = DefaultDisplayConfig.WIDTH,
                                    bufferHeight = DefaultDisplayConfig.HEIGHT,
                                    clampToBounds = false,
                                ) ?: return@forEach
                                val pointerToken = change.id.value
                                val contactId = contactIdsByPointer.getOrPut(pointerToken) { nextContactId[0]++ }
                                if (viewModel.onPreviewTouchDown(contactId, point.x, point.y)) {
                                    activeTouches[contactId] = point
                                } else {
                                    contactIdsByPointer.remove(pointerToken)
                                }
                            }

                        changes
                            .filter { it.previousPressed && it.pressed && it.position != it.previousPosition }
                            .forEach { change ->
                                val pointerToken = change.id.value
                                val contactId = contactIdsByPointer[pointerToken] ?: return@forEach
                                val point = mapViewToVirtualDisplay(
                                    viewX = change.position.x,
                                    viewY = change.position.y,
                                    viewWidth = size.width,
                                    viewHeight = size.height,
                                    bufferWidth = DefaultDisplayConfig.WIDTH,
                                    bufferHeight = DefaultDisplayConfig.HEIGHT,
                                    clampToBounds = true,
                                ) ?: return@forEach
                                activeTouches[contactId] = point
                                viewModel.onPreviewTouchMove(contactId, point.x, point.y)
                            }

                        changes
                            .filter { it.previousPressed && !it.pressed }
                            .forEach { change ->
                                val pointerToken = change.id.value
                                val contactId = contactIdsByPointer[pointerToken] ?: return@forEach
                                val point = mapViewToVirtualDisplay(
                                    viewX = change.position.x,
                                    viewY = change.position.y,
                                    viewWidth = size.width,
                                    viewHeight = size.height,
                                    bufferWidth = DefaultDisplayConfig.WIDTH,
                                    bufferHeight = DefaultDisplayConfig.HEIGHT,
                                    clampToBounds = true,
                                ) ?: activeTouches[contactId]
                                if (point != null) {
                                    viewModel.onPreviewTouchUp(contactId, point.x, point.y)
                                }
                                activeTouches.remove(contactId)
                                contactIdsByPointer.remove(pointerToken)
                            }

                        changes.forEach { it.consume() }
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
    state: MainUiState,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.let(viewModel::exportConfig)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.let(viewModel::importConfig)
    }

    FrameworkSettingsPanel {
        FrameworkSettingsSection(title = "资源") {
            FrameworkResourceRepositoryContent(
                summary = resourceRepositorySummary(state.resourceRepository),
                rootPath = state.resourceRepository.rootPath,
                error = state.resourceRepository.lastError,
                progress = if (state.resourceRepositoryUpdating && state.resourceRepositoryProgress != null) {
                    FrameworkHomeProgress(
                        fraction = state.resourceRepositoryProgress.fraction,
                        label = state.resourceRepositoryProgress.label,
                    )
                } else {
                    null
                },
                action = FrameworkHomeAction(
                    title = if (state.resourceRepository.available) "更新 GitHub 资源" else "下载 GitHub 资源",
                    description = if (state.resourceRepositoryUpdating) {
                        "正在处理 GitHub 资源缓存"
                    } else {
                        "首次下载后会缓存在本地，之后可以手动刷新"
                    },
                    actionLabel = if (state.resourceRepositoryUpdating) "处理中" else "执行",
                    enabled = !state.resourceRepositoryUpdating,
                    onClick = viewModel::refreshResourceRepository,
                ),
                clearAction = FrameworkHomeAction(
                    title = "清空 GitHub 资源",
                    description = "删除当前缓存和历史目录，下次更新会重新下载，适合排除旧数据干扰",
                    actionLabel = if (state.resourceRepositoryUpdating) "处理中" else "清空",
                    enabled = !state.resourceRepositoryUpdating,
                    onClick = viewModel::requestClearResourceRepositoryConfirmation,
                ),
            )
            if (!state.resourceRepositoryUpdating && state.lastMessage.isNotBlank()) {
                MaaHomeDivider()
                FrameworkHomeSupportText(
                    text = state.lastMessage,
                    tone = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FrameworkSettingsSection(title = "日志") {
            FrameworkSettingsChoiceRow(
                title = "日志级别",
                description = "控制 root runtime 与日志页展示的详细程度",
                options = listOf(
                    FrameworkSettingsChoice("error", "错误"),
                    FrameworkSettingsChoice("warn", "警告"),
                    FrameworkSettingsChoice("info", "信息"),
                    FrameworkSettingsChoice("debug", "调试"),
                ),
                selected = state.settings.logLevel,
                onSelected = viewModel::updateLogLevel,
            )
        }

        FrameworkSettingsSection(title = "数据") {
            FrameworkHomeActionRow(
                action = FrameworkHomeAction(
                    title = "导出配置",
                    description = "导出任务勾选、资源选择和全部参数配置",
                    actionLabel = "导出",
                    onClick = { exportLauncher.launch("maaend_android_config.json") },
                ),
            )
            MaaHomeDivider()
            FrameworkHomeActionRow(
                action = FrameworkHomeAction(
                    title = "导入配置",
                    description = "导入后会覆盖当前本地配置并立即刷新界面",
                    actionLabel = "导入",
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                ),
            )
        }

        FrameworkSettingsSection(title = "关于") {
            FrameworkHomeInfoRow(
                label = "版本",
                value = BuildConfig.VERSION_NAME,
            )
            MaaHomeDivider()
            FrameworkHomeInfoRow(
                label = "资源分支",
                value = state.resourceRepository.branch,
            )
            state.resourceRepository.modelRevision?.takeIf { it.isNotBlank() }?.let { revision ->
                MaaHomeDivider()
                FrameworkHomeInfoRow(
                    label = "模型版本",
                    value = revision.take(7),
                )
            }
            MaaHomeDivider()
            FrameworkHomeActionRow(
                action = FrameworkHomeAction(
                    title = "打开项目主页",
                    description = "查看 MaaEnd-Android 仓库和最新提交",
                    actionLabel = "打开",
                    onClick = { uriHandler.openUri("https://github.com/MaaEnd/MaaEnd-Android") },
                ),
            )
        }
    }
}

@Composable
private fun ResourceRepositoryProgressBlock(
    progress: PersistentResourceRepositorySyncProgress,
) {
    val percentText = "${(progress.fraction * 100).roundToInt().coerceIn(0, 100)}%"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = progress.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = percentText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50)),
        )
    }
}

@Composable
private fun ResourceRepositoryClearConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaaEndDesignTokens.Spacing.lg)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.card),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaaEndDesignTokens.Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.md),
            ) {
                Surface(
                    shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.pill),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                ) {
                    Text(
                        text = "缓存重置",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.xs),
                ) {
                    Text(
                        text = "清空 GitHub 资源",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "会删除当前缓存的 GitHub 资源和历史目录，下次更新会重新下载。任务配置不会被清空。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                ) {
                    Text(
                        text = "适合在资源切换、缓存残留或历史数据干扰时使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaaEndDesignTokens.Spacing.md,
                            vertical = MaaEndDesignTokens.Spacing.sm,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
                    ) {
                        Text(
                            text = "取消",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.inner),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(
                            text = "确认清空",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String? = null,
    actionLabel: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.6f),
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.8f else 0.45f),
                )
            }
        }
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsGroupCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
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
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    description: String? = null,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelected(value) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == value,
                        onClick = null,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSupportText(
    text: String,
    tone: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = tone,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SettingsDivider() {
    Divider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 12.dp),
    )
}

@Composable
private fun LogsScreen(
    state: MainUiState,
    currentTaskLabel: String?,
) {
    val lines = state.displayLogs
    val taskStatusLines = remember(
        state.runtimeState.phase,
        state.runtimeState.currentTaskId,
        state.runtimeState.lastMessage,
        state.lastMessage,
        currentTaskLabel,
    ) {
        buildTaskStatusLogLines(
            runtimeState = state.runtimeState,
            uiMessage = state.lastMessage,
            currentTaskLabel = currentTaskLabel,
        )
    }
    val visibleRuntimeLines = remember(lines, state.settings.logLevel) {
        buildVisibleRuntimeLogLines(lines, state.settings.logLevel)
    }
    val mergedLines = remember(taskStatusLines, visibleRuntimeLines) {
        buildList {
            addAll(taskStatusLines)
            addAll(visibleRuntimeLines)
        }
    }
    val displayLines = remember(mergedLines) {
        mergedLines.map { line ->
            buildString {
                append(line.time ?: "--:--:--")
                append(" [")
                append(line.level.displayName)
                append("] ")
                append(line.content)
            }
        }
    }

    FrameworkRuntimeLogsPanel(
        lines = displayLines,
        subtitle = state.runtimeState.lastMessage.ifBlank { state.lastMessage },
        modifier = Modifier.fillMaxSize(),
        metrics = listOf(
            FrameworkLogMetric(label = "阶段", value = state.runtimeState.phase.displayName()),
            FrameworkLogMetric(label = "当前任务", value = currentTaskLabel ?: state.runtimeState.currentTaskId ?: "-"),
            FrameworkLogMetric(label = "日志级别", value = state.settings.logLevel),
        ),
        emptyTitle = "暂无日志",
        emptyDescription = "开始一次任务后，这里会显示当前任务动态和原始运行日志。",
    )
}

@Composable
private fun StatusLogLine(
    time: String?,
    levelLabel: String,
    levelColor: Color,
    content: String,
    monospace: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = time ?: "--:--:--",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                modifier = Modifier.width(58.dp),
            )

            Surface(
                shape = RoundedCornerShape(3.dp),
                color = levelColor.copy(alpha = 0.14f),
            ) {
                Text(
                    text = levelLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = levelColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                ),
                color = if (monospace) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class UiLogLevel(
    val displayName: String,
    val color: Color,
    val priority: Int,
) {
    Error("错误", Color(0xFFEF4444), priority = 0),
    Warning("提醒", Color(0xFFF59E0B), priority = 1),
    Info("进度", Color(0xFF3B82F6), priority = 2),
    Success("完成", Color(0xFF16A34A), priority = 2),
    Debug("调试", Color(0xFF6B7280), priority = 3),
}

private data class UiLogLine(
    val time: String?,
    val level: UiLogLevel,
    val content: String,
)

private fun buildTaskStatusLogLines(
    runtimeState: RuntimeStateSnapshot,
    uiMessage: String,
    currentTaskLabel: String?,
): List<UiLogLine> {
    val entries = mutableListOf<UiLogLine>()
    val currentTaskName = currentTaskLabel ?: runtimeState.currentTaskId
    if (!currentTaskName.isNullOrBlank()) {
        entries += UiLogLine(
            time = null,
            level = UiLogLevel.Info,
            content = "当前任务：$currentTaskName",
        )
    }
    entries += UiLogLine(
        time = null,
        level = UiLogLevel.Info,
        content = "运行阶段：${runtimeState.phase.displayName()}",
    )

    val runtimeMessage = translateRuntimeStateMessage(
        runtimeState.lastMessage,
        runtimeState.currentTaskId,
        currentTaskLabel,
    )
    if (runtimeMessage.isNotBlank()) {
        entries += UiLogLine(
            time = null,
            level = UiLogLevel.Info,
            content = runtimeMessage,
        )
    }

    if (uiMessage.isNotBlank() && uiMessage != runtimeMessage) {
        entries += UiLogLine(
            time = null,
            level = UiLogLevel.Info,
            content = uiMessage,
        )
    }
    return entries.distinctBy { "${it.level.displayName}:${it.content}" }
}

private fun parseRawRuntimeLogLine(line: String): UiLogLine {
    val trimmed = line.trim()
    val firstSpace = trimmed.indexOf(' ')
    val timestamp = if (firstSpace > 0) {
        trimmed.substring(0, firstSpace).takeIf { value ->
            value.all(Char::isDigit)
        }
    } else {
        null
    }
    val content = if (timestamp != null) {
        trimmed.substring(firstSpace + 1).trim()
    } else {
        trimmed
    }
    val time = timestamp?.toLongOrNull()?.let { millis ->
        DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(millis))
    }
    return UiLogLine(
        time = time,
        level = classifyUiLogLevel(content),
        content = content,
    )
}

private fun classifyUiLogLevel(message: String): UiLogLevel {
    val lower = message.lowercase()
    return when {
        "failed" in lower || "error" in lower || "exception" in lower || "fatal" in lower || "失败" in message -> UiLogLevel.Error
        "warning" in lower || "warn" in lower || "timeout" in lower || "提醒" in message -> UiLogLevel.Warning
        "success" in lower || "completed" in lower || "succeeded" in lower || "完成" in message || "成功" in message -> UiLogLevel.Success
        "running" in lower || "preparing" in lower || "starting" in lower || "started" in lower ||
            "准备" in message || "运行" in message || "同步" in message -> UiLogLevel.Info
        "debug" in lower || "trace" in lower || "verbose" in lower -> UiLogLevel.Debug
        else -> UiLogLevel.Debug
    }
}

private fun RunSessionPhase.displayName(): String = when (this) {
    RunSessionPhase.Idle -> "待命"
    RunSessionPhase.Preparing -> "准备中"
    RunSessionPhase.Running -> "运行中"
    RunSessionPhase.Stopping -> "停止中"
    RunSessionPhase.Completed -> "已完成"
    RunSessionPhase.Failed -> "已失败"
}

private fun translateRuntimeStateMessage(
    message: String,
    currentTaskId: String?,
    currentTaskLabel: String?,
): String {
    if (message.isBlank()) {
        return ""
    }
    return when {
        message == "Preparing runtime" -> "正在准备运行时"
        message == "Stop requested" -> "已请求停止任务"
        message == "Run stopped" -> "任务已停止"
        message == "Root runtime bootstrapped" -> "Root 运行环境已启动"
        message == "task completed" -> "任务执行完成"
        message.startsWith("Running ") -> {
            val taskId = message.removePrefix("Running ").trim()
            val label = when (taskId) {
                currentTaskId -> currentTaskLabel
                else -> null
            }
            if (label.isNullOrBlank()) "正在执行：$taskId" else "正在执行：$label"
        }
        else -> message
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
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(MaaEndDesignTokens.Spacing.sm),
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
                .padding(contentPadding),
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
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .heightIn(min = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(MaaEndDesignTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
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
    Surface(
        shape = RoundedCornerShape(MaaEndDesignTokens.CornerRadius.pill),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            },
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(MaaEndDesignTokens.CornerRadius.pill))
            .widthIn(min = 32.dp, max = 160.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = taskConfigControlTextStyle(),
            maxLines = 2,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
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

@Composable
private fun taskConfigDescriptionStyle(): TextStyle {
    return MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp,
        lineHeight = 11.sp,
    )
}

@Composable
private fun taskConfigControlTextStyle(): TextStyle {
    return MaterialTheme.typography.labelSmall
}

private fun resourceRepositorySummary(status: PersistentResourceRepositoryStatus): String {
    return when {
        status.available -> buildString {
            append("GitHub / ")
            append(status.branch)
            status.modelRevision?.takeIf { it.isNotBlank() }?.let {
                append(" / AI ")
                append(it.take(7))
            }
            if (status.updatedAt > 0L) {
                append(" / ")
                append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(status.updatedAt)))
            }
        }
        status.lastError.isNullOrBlank() -> "尚未下载，首次启动会同步 MaaEnd 资源"
        else -> "GitHub 更新失败，请重新同步 MaaEnd 资源"
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
        MaaEndTab.TASKS -> "任务"
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

        MaaEndTab.TASKS -> "当前共 ${state.catalog.tasks.size} 个任务。"
        MaaEndTab.SETTINGS -> "资源更新、日志级别和配置导入导出都集中在这里。"
        MaaEndTab.LOGS -> "当前日志 ${visibleRuntimeLogCount(state.displayLogs, state.settings.logLevel)} 条。"
    }
}

internal fun visibleRuntimeLogCount(
    lines: List<String>,
    selectedLogLevel: String,
): Int = buildVisibleRuntimeLogLines(lines, selectedLogLevel).size

internal fun visibleRuntimeLogContents(
    lines: List<String>,
    selectedLogLevel: String,
): List<String> = buildVisibleRuntimeLogLines(lines, selectedLogLevel).map { it.content }

private fun buildVisibleRuntimeLogLines(
    lines: List<String>,
    selectedLogLevel: String,
): List<UiLogLine> {
    return lines
        .map(::parseRawRuntimeLogLine)
        .filter { it.level.isVisibleAt(selectedLogLevel) }
}

private fun UiLogLevel.isVisibleAt(selectedLogLevel: String): Boolean {
    return priority <= maxVisibleUiLogPriority(selectedLogLevel)
}

private fun maxVisibleUiLogPriority(selectedLogLevel: String): Int {
    return when (normalizeUiSelectedLogLevel(selectedLogLevel)) {
        "error" -> UiLogLevel.Error.priority
        "warn" -> UiLogLevel.Warning.priority
        "debug" -> UiLogLevel.Debug.priority
        else -> UiLogLevel.Info.priority
    }
}

private fun normalizeUiSelectedLogLevel(selectedLogLevel: String?): String {
    val normalized = selectedLogLevel?.trim()?.lowercase()
    return when (normalized) {
        "error", "warn", "info", "debug" -> normalized
        else -> "info"
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

private fun homeServiceTone(state: MainUiState): FrameworkHomeTone {
    return when {
        state.rootConnected -> FrameworkHomeTone.Positive
        state.busy -> FrameworkHomeTone.Warning
        state.rootGranted || state.rootAvailable -> FrameworkHomeTone.Warning
        else -> FrameworkHomeTone.Error
    }
}

private fun homeRootHint(state: MainUiState): String {
    return when {
        state.rootConnected -> "Root 和 Runtime 已握手完成，后续准备运行时、打开游戏或执行任务都会直接复用这条连接。"
        state.rootAvailable -> "应用启动时会静默申请 Root 并自动握手。只有自动连接没完成时，才需要用下面的按钮手动重试。"
        else -> "当前没有检测到可用 Root，所以不会自动建立 Runtime 连接。"
    }
}

@Composable
private fun AssetIcon(
    assetPath: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val context = LocalContext.current
    val bitmap = remember(assetPath) {
        runCatching {
            loadCatalogAssetBytes(context, assetPath)?.inputStream()
                ?.use(BitmapFactory::decodeStream)
                ?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            colorFilter = tint?.let { ColorFilter.tint(it) },
        )
    }
}

@Composable
private fun RichDescriptionText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val resolvedText = remember(text) {
        loadRichTextContent(context, text)
    }.trim()

    if (resolvedText.isBlank()) {
        return
    }

    if (resolvedText.startsWith("http://") || resolvedText.startsWith("https://")) {
        Text(
            text = resolvedText,
            style = style,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.clickable { uriHandler.openUri(resolvedText) },
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        resolvedText.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                line.startsWith("### ") -> Text(
                    text = line.removePrefix("### ").trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("## ") -> Text(
                    text = line.removePrefix("## ").trim(),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("# ") -> Text(
                    text = line.removePrefix("# ").trim(),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "•",
                        style = style,
                        color = color,
                    )
                    Text(
                        text = line.drop(2).trim(),
                        style = style,
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> Text(
                    text = line,
                    style = style,
                    color = color,
                )
            }
        }
    }
}

private fun loadRichTextContent(context: Context, rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.isBlank()) {
        return ""
    }
    val looksLikeAssetPath = '/' in trimmed || trimmed.endsWith(".md") || trimmed.endsWith(".txt")
    if (!looksLikeAssetPath) {
        return trimmed
    }
    return loadCatalogAssetBytes(context, trimmed)
        ?.toString(Charsets.UTF_8)
        ?: trimmed
}

private fun loadCatalogAssetBytes(context: Context, rawPath: String): ByteArray? {
    val normalizedPath = rawPath.trim().removePrefix("./").trimStart('/')
    if (normalizedPath.isBlank()) {
        return null
    }

    val repoRoot = PersistentResourceRepositoryManager.loadStatus(context).rootPath
    if (!repoRoot.isNullOrBlank()) {
        val repoFile = File(repoRoot, normalizedPath)
        if (repoFile.isFile) {
            return runCatching { repoFile.readBytes() }.getOrNull()
        }
    }

    return runCatching {
        context.assets.open(normalizedPath).use { it.readBytes() }
    }.getOrNull()
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
        "Crafting" -> "工艺制造"
        "WeaponUpgrade" -> "武器强化"
        "AutoUseSpMedication" -> "体力用药"
        "SimpleProductionBatchStart" -> "批量生产"
        "ReceiveProdManual" -> "领取手册"
        "BakerEntry" -> "会话嘴替"
        "ReadAllWiki" -> "阅读图鉴"
        "DeliveryJobs" -> "转交委托"
        "GearAssembly" -> "装备制造"
        else -> task.label
            .replace(Regex("[^\\p{L}\\p{N}\\p{IsHan}]"), "")
            .take(4)
            .ifBlank { task.id.take(4) }
    }
}
