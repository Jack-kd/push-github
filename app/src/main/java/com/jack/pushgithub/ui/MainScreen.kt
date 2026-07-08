package com.jack.pushgithub.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestStoragePermission: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val displayPath = viewModel.tryGetDisplayPath(it)
            viewModel.updateSourceDir(it, displayPath)
        }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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

            // 日志输出区域
            if (state.logMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "操作日志",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        state.logMessages.forEach { log ->
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
        }
    }
}
