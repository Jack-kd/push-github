package com.jack.pushgithub.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jack.pushgithub.viewmodel.MainViewModel
import kotlinx.coroutines.launch

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

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val displayPath = viewModel.tryGetDisplayPath(it)
            viewModel.updateSourceDir(it, displayPath)
        }
    }

    // 存储权限对话框
    if (state.showStoragePermissionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissStoragePermissionDialog() },
            title = { Text("需要存储权限") },
            text = { Text("为了直接访问文件夹路径，请授予“所有文件访问”权限。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissStoragePermissionDialog()
                    onRequestStoragePermission()
                }) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissStoragePermissionDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // 配置对话框
    if (state.showConfigDialog) {
        ConfigDialog(
            title = "请输入必要配置信息",
            negativeText = if (state.isFirstTime) "退出" else "取消",
            onNegative = {
                if (state.isFirstTime) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    viewModel.dismissConfigDialog()
                }
            },
            onPositive = { email, username, token ->
                viewModel.saveConfig(email, username, token)
            },
            initialEmail = state.config.email,
            initialUsername = state.config.username,
            initialToken = state.config.token
        )
    }

    // 下载源码对话框
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Push to GitHub") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 上半部分：表单区域
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedButton(
                    onClick = { viewModel.openConfigDialog(modify = true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("修改配置信息")
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = state.repoUrl,
                    onValueChange = { viewModel.updateRepoUrl(it) },
                    label = { Text("目标地址 (如 https://github.com/Jack-kd/cs)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.sourceDirDisplayName,
                        onValueChange = { viewModel.updateSourceDirManually(it) },
                        label = { Text("本地源码地址") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        readOnly = false,
                        trailingIcon = {
                            if (state.sourceDirUri != null) {
                                TextButton(onClick = { viewModel.clearSourceDir() }) {
                                    Text("清除")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "选择文件夹",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {


                    Button(
                        onClick = {
                            viewModel.clearGithubRepository()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("清空项目")
                    }


                    Button(
                        onClick = {
                            viewModel.showDownloadDialog()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("下载源码")
                    }


                    Button(
                        onClick = {

                            viewModel.checkStoragePermission()

                            if (state.hasStoragePermission) {

                                viewModel.startPush()

                            } else {

                                viewModel.showStoragePermissionDialog()

                            }

                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {

                        Text("开始推送")

                    }

                }

                Spacer(modifier = Modifier.height(8.dp))

                if (state.errorMessage.isNotEmpty()) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (state.statusMessage.isNotEmpty()) {
                    Text(
                        text = state.statusMessage,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 日志区域（占据剩余空间）
            if (state.logMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                if (state.progressVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp)
                            .height(26.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(state.progress / 100f)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary
                                )
                        )

                        Text(
                            text = "${state.progress}%",
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "操作日志",
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = { viewModel.clearLog() }) {
                        Text("清空")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 日志滚动状态定义在 Box 外部，方便底部按钮访问
                val lazyListState = rememberLazyListState()
                var autoScroll by remember { mutableStateOf(true) }

                // 判断当前是否在日志最底部
                LaunchedEffect(lazyListState) {
                    val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                    val totalItems = lazyListState.layoutInfo.totalItemsCount
                    if (totalItems > 0) {
                        autoScroll = lastVisibleItem?.index == totalItems - 1
                    }
                }

                // 新日志出现且允许自动滚动时，滚到底部
                LaunchedEffect(state.logMessages.size) {
                    if (
                        autoScroll &&
                        state.logMessages.isNotEmpty()
                    ) {
                        lazyListState.animateScrollToItem(
                            state.logMessages.takeLast(300).size - 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(350.dp)
                        ) {
                            items(
                                state.logMessages.takeLast(300)
                            ) { log ->
                                Text(
                                    text = log,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // 底部控制按钮：左“回到底部”，右“复制日志”
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!autoScroll) {

                        TextButton(
                            onClick = {

                                coroutineScope.launch {

                                    lazyListState.animateScrollToItem(
                                        state.logMessages.takeLast(300).size - 1
                                    )

                                }

                            }
                        ) {
                            Text("回到底部")
                        }

                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(onClick = {
                        val fullLog = state.logMessages.joinToString("\n")
                        clipboardManager.setText(AnnotatedString(fullLog))
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制日志")
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    onPathChange: (String) -> Unit
) {
    var path by remember { mutableStateOf(initialPath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "下载源码到本地",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = path,
                    onValueChange = {
                        path = it
                        onPathChange(it)
                    },
                    label = { Text("保存路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = { onDownload(path) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("下载")
                    }
                }
            }
        }
    }
}
