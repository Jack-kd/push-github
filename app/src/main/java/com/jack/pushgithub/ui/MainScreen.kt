package com.jack.pushgithub.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jack.pushgithub.data.PushHistoryEntity
import com.jack.pushgithub.data.RepoConfigEntity
import com.jack.pushgithub.github.CommitInfo
import com.jack.pushgithub.network.NetworkUtils
import com.jack.pushgithub.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// 主界面
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestStoragePermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // 本地 UI 状态
    var isOnline by remember { mutableStateOf(true) }
    var configDropdownExpanded by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scheduleHours by remember { mutableStateOf("") }
    var isScheduleRunning by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        NetworkUtils.observeNetworkState(context).collect { isOnline = it }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.updateSourceDir(it, viewModel.tryGetDisplayPath(it))
        }
    }

    // ============================================================
    // 对话框
    // ============================================================

    if (state.showStoragePermissionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissStoragePermissionDialog() },
            title = { Text("需要存储权限") },
            text = { Text("为了直接访问文件夹路径，请授予「所有文件访问」权限。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissStoragePermissionDialog()
                    onRequestStoragePermission()
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissStoragePermissionDialog() }) { Text("取消") }
            }
        )
    }

    if (state.showConfigDialog) {
        ConfigDialog(
            title = "请输入必要配置信息",
            negativeText = if (state.isFirstTime) "退出" else "取消",
            onNegative = {
                if (state.isFirstTime) (context as? android.app.Activity)?.finish()
                else viewModel.dismissConfigDialog()
            },
            onPositive = { email, username, token ->
                viewModel.saveConfig(email, username, token)
            },
            initialEmail = state.config.email,
            initialUsername = state.config.username,
            initialToken = state.config.token
        )
    }

    if (state.showDownloadDialog) {
        DownloadDialog(
            initialPath = state.downloadPath,
            onDismiss = { viewModel.dismissDownloadDialog() },
            onDownload = { path ->
                viewModel.updateDownloadPath(path)
                viewModel.startDownload()
            },
            onPathChange = { viewModel.updateDownloadPath(it) }
        )
    }

    if (state.showFilePreview) {
        FilePreviewDialog(
            files = state.previewFiles,
            selectedFiles = selectedFiles,
            onSelectionChange = { selectedFiles = it },
            onDismiss = {
                selectedFiles = emptySet()
                viewModel.dismissFilePreview()
            },
            onConfirmPush = {
                viewModel.dismissFilePreview()
                viewModel.addLog("已选择 ${selectedFiles.size} 个文件进行推送")
                viewModel.startPush()
            }
        )
    }

    if (state.showCommitHistory) {
        CommitHistoryDialog(
            commits = state.commitHistory,
            isLoading = state.isLoadingCommits,
            onDismiss = { viewModel.dismissCommitHistory() }
        )
    }

    if (state.showHistory) {
        HistoryDialog(
            history = state.pushHistory,
            onDismiss = { viewModel.dismissHistoryScreen() },
            onClear = { viewModel.clearHistory() }
        )
    }

    if (state.showScheduleDialog) {
        ScheduleDialog(
            hours = scheduleHours,
            isRunning = isScheduleRunning,
            onHoursChange = { scheduleHours = it },
            onStart = {
                val h = scheduleHours.toIntOrNull() ?: 24
                viewModel.updateScheduleInterval(h)
                viewModel.schedulePush()
                isScheduleRunning = true
            },
            onCancel = {
                viewModel.cancelScheduledPush()
                isScheduleRunning = false
                scheduleHours = ""
            },
            onDismiss = {
                viewModel.dismissScheduleDialog()
                if (!isScheduleRunning) scheduleHours = ""
            }
        )
    }

    if (state.showClearOptionsDialog) {
        ClearOptionsDialog(
            onDismiss = { viewModel.hideClearOptionsDialog() },
            onFastClear = { viewModel.clearRepositoryFast() },
            onSelectFiles = { viewModel.showFileSelector() }
        )
    }

    if (state.showFileSelector) {
        FileSelectorDialog(
            files = state.repoFiles,
            selectedPaths = state.selectedFilePaths,
            onToggleSelection = { viewModel.toggleFileSelection(it) },
            onSelectAll = { viewModel.selectAllFiles() },
            onDeselectAll = { viewModel.deselectAllFiles() },
            onDelete = { viewModel.deleteSelectedFiles() },
            onDismiss = { viewModel.hideFileSelector() }
        )
    }

    if (state.showSourceDirDialog) {
        SourceDirDialog(
            sourcePath = state.sourceDirDisplayName,
            onPathChange = { viewModel.updateSourceDirManually(it) },
            onBrowse = { folderPicker.launch(null) },
            onConfirm = { viewModel.confirmPush() },
            onDismiss = { viewModel.hideSourceDirDialog() }
        )
    }

    // ============================================================
    // 主界面
    // ============================================================

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null,
                            modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Push to GitHub", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // 在线状态
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (isOnline) " 在线 " else " 离线 ",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    // 菜单
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("修改配置") },
                                onClick = { showMenu = false; viewModel.openConfigDialog(modify = true) },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("推送历史") },
                                onClick = { showMenu = false; viewModel.showHistoryScreen() },
                                leadingIcon = { Icon(Icons.Default.History, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("定时推送") },
                                onClick = {
                                    showMenu = false
                                    scheduleHours = ""
                                    isScheduleRunning = false
                                    viewModel.showScheduleDialog()
                                },
                                leadingIcon = { Icon(Icons.Default.Schedule, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ============================================================
            // 卡片 1: 目标仓库
            // ============================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("目标仓库", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))

                    // 已保存配置
                    if (state.repoConfigs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = configDropdownExpanded,
                                onExpandedChange = { configDropdownExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = "",
                                    onValueChange = {},
                                    readOnly = true,
                                    placeholder = { Text("已保存的仓库配置") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(configDropdownExpanded)
                                    },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                ExposedDropdownMenu(
                                    expanded = configDropdownExpanded,
                                    onDismissRequest = { configDropdownExpanded = false }
                                ) {
                                    state.repoConfigs.forEach { config ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(config.name, fontWeight = FontWeight.Medium)
                                                    Text(
                                                        "${config.repoUrl} · ${config.branch}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.selectRepoConfig(config.id)
                                                configDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = {
                                viewModel.addRepoConfig(
                                    "配置 ${state.repoConfigs.size + 1}",
                                    state.repoUrl,
                                    state.branch
                                )
                            }) {
                                Icon(Icons.Default.BookmarkAdd, "保存", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // URL
                    OutlinedTextField(
                        value = state.repoUrl,
                        onValueChange = { viewModel.updateRepoUrl(it) },
                        label = { Text("仓库地址") },
                        placeholder = { Text("如 https://github.com/user/repo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Link, null) }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.branch,
                        onValueChange = { viewModel.updateBranch(it) },
                        label = { Text("分支") },
                        placeholder = { Text("main") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.AccountTree, null) }
                    )
                    }
            }

            // ============================================================
            // 操作按钮
            // ============================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.clearGithubRepository() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("清空仓库", fontSize = 12.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { viewModel.showDownloadDialog() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("下载源码", fontSize = 12.sp, maxLines = 1)
                }

                Button(
                    onClick = {
                        viewModel.showSourceDirDialog()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !state.isWorking
                ) {
                    if (state.isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(2.dp))
                    Text("上传源码", fontSize = 12.sp, maxLines = 1)
                }
            }

            // 进度条
            AnimatedVisibility(state.progressVisible) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Text(
                        text = "进度: ${state.progress}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // 错误信息
            if (state.errorMessage.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // ============================================================
            // 卡片 3: 运行日志
            // ============================================================
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // 日志头部（标题 + 条数 + 操作按钮合并到一行）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("运行日志", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "${state.logMessages.size} 条",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            val text = state.logMessages.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(text))
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, "复制", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.clearLog() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.DeleteSweep, "清空", modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(8.dp))

                    // 日志列表（智能自动滚动）
                    val lazyListState = rememberLazyListState()
                    var autoScroll by remember { mutableStateOf(true) }

                    // 检测用户手动滚动后是否在底部
                    LaunchedEffect(lazyListState.isScrollInProgress) {
                        if (!lazyListState.isScrollInProgress) {
                            val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                            val totalItems = lazyListState.layoutInfo.totalItemsCount
                            autoScroll = lastVisible != null && lastVisible.index >= totalItems - 1
                        }
                    }

                    // 新日志到达时自动滚动（仅当 autoScroll 为 true）
                    LaunchedEffect(state.logMessages.size) {
                        if (autoScroll && state.logMessages.isNotEmpty()) {
                            lazyListState.animateScrollToItem(state.logMessages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E2E))
                            .padding(8.dp)
                    ) {
                        if (state.logMessages.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "暂无日志",
                                        color = Color(0xFF6C7086),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        items(state.logMessages) { log ->
                            SelectionContainer {
                                Text(
                                    text = log,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = when {
                                        log.contains("❌") || log.contains("失败") || log.contains("错误") -> Color(0xFFFF6B6B)
                                        log.contains("✅") || log.contains("成功") || log.contains("完成") -> Color(0xFF69F0AE)
                                        log.contains("⏳") || log.contains("正在") || log.contains("开始") -> Color(0xFFFFD93D)
                                        log.contains("---") -> Color(0xFF6C7086)
                                        else -> Color(0xFFCDD6F4)
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 文件预览对话框
// ============================================================

@Composable
private fun FilePreviewDialog(
    files: List<String>,
    selectedFiles: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onConfirmPush: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("文件预览 (${files.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row {
                        TextButton(onClick = {
                            onSelectionChange(if (selectedFiles.size == files.size) emptySet() else files.toSet())
                        }) {
                            Text(if (selectedFiles.size == files.size) "取消全选" else "全选")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(files) { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = file in selectedFiles,
                                onCheckedChange = { checked ->
                                    onSelectionChange(
                                        if (checked) selectedFiles + file
                                        else selectedFiles - file
                                    )
                                }
                            )
                            Text(
                                file,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirmPush,
                        enabled = selectedFiles.isNotEmpty()
                    ) { Text("推送 ${selectedFiles.size} 个文件") }
                }
            }
        }
    }
}

// ============================================================
// 提交历史对话框
// ============================================================

@Composable
private fun CommitHistoryDialog(
    commits: List<CommitInfo>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.7f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("提交历史", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (commits.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无提交记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(commits) { commit ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            commit.sha.take(7),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            commit.date.take(10),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(commit.message, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        commit.author,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

// ============================================================
// 推送历史对话框
// ============================================================

@Composable
private fun HistoryDialog(
    history: List<PushHistoryEntity>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.7f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("推送历史", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (history.isNotEmpty()) {
                        TextButton(onClick = onClear) {
                            Text("清空", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (history.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无推送记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(history) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (item.success) Color(0xFF4CAF50) else Color(0xFFF44336))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.repoUrl,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${item.branch} · ${formatTimestamp(item.timestamp)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (item.message.isNotBlank()) {
                                            Text(
                                                item.message,
                                                fontSize = 11.sp,
                                                color = if (item.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

// ============================================================
// 定时推送对话框
// ============================================================

@Composable
private fun ScheduleDialog(
    hours: String,
    isRunning: Boolean,
    onHoursChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("定时推送", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text("每隔指定小时自动推送一次", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = hours,
                    onValueChange = { newVal ->
                        if (newVal.all { it.isDigit() } || newVal.isEmpty()) {
                            onHoursChange(newVal)
                        }
                    },
                    label = { Text("间隔小时数") },
                    placeholder = { Text("24") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isRunning,
                    supportingText = {
                        if (hours.isNotBlank()) {
                            Text("每 ${hours} 小时自动推送一次")
                        }
                    }
                )
                Spacer(Modifier.height(20.dp))

                if (isRunning) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("取消定时") }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = hours.isNotBlank() && (hours.toIntOrNull() ?: 0) > 0
                    ) { Text("开始定时") }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("关闭") }
            }
        }
    }
}

// ============================================================
// 下载源码对话框
// ============================================================

@Composable
private fun DownloadDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    onPathChange: (String) -> Unit
) {
    var path by remember { mutableStateOf(initialPath) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("下载源码", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = {
                        path = it
                        onPathChange(it)
                    },
                    label = { Text("保存路径") },
                    placeholder = { Text("/storage/emulated/0/Download") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onDownload(path) },
                        shape = RoundedCornerShape(12.dp),
                        enabled = path.isNotBlank()
                    ) { Text("开始下载") }
                }
            }
        }
    }
}

// ============================================================
// 本地源码对话框
// ============================================================

@Composable
private fun SourceDirDialog(
    sourcePath: String,
    onPathChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    Icons.Default.Folder, null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text("本地源码", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text("选择或输入要推送的本地文件夹", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sourcePath,
                        onValueChange = onPathChange,
                        label = { Text("源码路径") },
                        placeholder = { Text("选择或输入本地文件夹") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Description, null) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onBrowse,
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, "浏览")
                    }
                }
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        enabled = sourcePath.isNotBlank()
                    ) {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("确定")
                    }
                }
            }
        }
    }
}

// ============================================================
// 清空方式选择对话框
// ============================================================

@Composable
private fun ClearOptionsDialog(
    onDismiss: () -> Unit,
    onFastClear: () -> Unit,
    onSelectFiles: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    Icons.Default.DeleteForever, null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                Text("清空仓库", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "请选择删除方式",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onFastClear,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("快速清空（保留 .gitkeep）")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "使用 Git Tree API 快速清空，速度最快",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onSelectFiles,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ListAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("选择文件删除")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "手动选择要删除的文件，灵活控制",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取消") }
            }
        }
    }
}

// ============================================================
// 文件选择器对话框
// ============================================================

@Composable
private fun FileSelectorDialog(
    files: List<com.jack.pushgithub.github.FileInfo>,
    selectedPaths: Set<String>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val blobFiles = files.filter { it.type == "blob" }
    val allSelected = blobFiles.isNotEmpty() && blobFiles.all { it.path in selectedPaths }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "选择要删除的文件",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "已选 ${selectedPaths.size}/${blobFiles.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))

                // 全选/取消全选
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onSelectAll) {
                        Icon(Icons.Default.SelectAll, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("全选", fontSize = 13.sp)
                    }
                    TextButton(onClick = onDeselectAll) {
                        Icon(Icons.Default.Deselect, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("取消全选", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(4.dp))

                // 文件列表
                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("正在加载文件列表...", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (blobFiles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("仓库为空，没有可删除的文件", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(blobFiles) { file ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (file.path in selectedPaths)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleSelection(file.path) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = file.path in selectedPaths,
                                        onCheckedChange = { onToggleSelection(file.path) }
                                    )
                                    Icon(
                                        Icons.Default.Description,
                                        null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        file.path,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))

                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onDelete,
                        enabled = selectedPaths.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除 ${selectedPaths.size} 个文件")
                    }
                }
            }
        }
    }
}

// ============================================================
// 工具函数
// ============================================================

private fun formatTimestamp(timestamp: Long): String {
    return try {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        timestamp.toString()
    }
}