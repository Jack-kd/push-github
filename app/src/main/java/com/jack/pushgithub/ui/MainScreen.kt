package com.jack.pushgithub.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 主内容区域可滚动
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
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
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !state.isWorking
                ) {
                    if (state.isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("推送中...")
                    } else {
                        Text("开始推送", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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

            // 日志区域：固定高度 + 自动滚动 + 可选可复制
            if (state.logMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "操作日志",
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = { viewModel.clearLog() }) {
                        Icon(
                            Icons.Default.ClearAll,
                            contentDescription = "清空日志",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val listState = rememberLazyListState()
                var autoScroll by remember { mutableStateOf(true) }

                // 监听手动滚动：只要不是程序触发的滚动，就停止自动滚动
                LaunchedEffect(listState.isScrollInProgress) {
                    if (listState.isScrollInProgress) {
                        autoScroll = false
                    }
                }

                // 新日志出现时自动滚动到底部（如果 autoScroll 为 true）
                LaunchedEffect(state.logMessages.size) {
                    if (autoScroll && state.logMessages.isNotEmpty()) {
                        listState.animateScrollToItem(state.logMessages.size - 1)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 250.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        items(state.logMessages) { log ->
                            SelectionContainer {
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

                // 如果自动滚动被关闭，显示一个“回到底部”的按钮
                if (!autoScroll) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            autoScroll = true
                            coroutineScope.launch {
                                listState.animateScrollToItem(state.logMessages.size - 1)
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("回到底部")
                    }
                }
            }
        }
    }
}
