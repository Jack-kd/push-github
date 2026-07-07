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
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jack.pushgithub.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val docFile = DocumentFile.fromTreeUri(context, it)
            val name = docFile?.name ?: it.lastPathSegment ?: "已选文件夹"
            viewModel.updateSourceDir(it, name)
        }
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
            // 修改配置按钮
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

            // 目标地址输入
            OutlinedTextField(
                value = state.repoUrl,
                onValueChange = { viewModel.updateRepoUrl(it) },
                label = { Text("目标地址 (如 https://github.com/Jack-kd/cs)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 本地源码地址 + 浏览按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.sourceDirDisplayName,
                    onValueChange = { },
                    label = { Text("本地源码地址") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    readOnly = true,
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