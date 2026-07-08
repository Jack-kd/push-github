package com.jack.pushgithub.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jack.pushgithub.data.ConfigRepository
import com.jack.pushgithub.data.GitConfig
import com.jack.pushgithub.git.GitHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val config: GitConfig = GitConfig(),
    val hasConfig: Boolean = false,
    val showConfigDialog: Boolean = false,
    val isFirstTime: Boolean = true,
    val repoUrl: String = "",
    val sourceDirDisplayName: String = "",   // 显示在输入框的路径（可能是文件路径或URI字符串）
    val sourceDirUri: Uri? = null,           // 原始 SAF URI，如果用户手动输入则为 null
    val isWorking: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String = "",
    val hasStoragePermission: Boolean = false,
    val showStoragePermissionDialog: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConfigRepository(application)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    init {
        val savedConfig = repository.loadConfig()
        val hasConfig = repository.hasConfig()
        _uiState.update {
            it.copy(
                config = savedConfig,
                hasConfig = hasConfig,
                showConfigDialog = !hasConfig,
                isFirstTime = !hasConfig
            )
        }
        checkStoragePermission()
    }

    fun checkStoragePermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // 低于 Android 11 默认有权限
        }
        _uiState.update { it.copy(hasStoragePermission = hasPermission) }
    }

    fun showStoragePermissionDialog() {
        _uiState.update { it.copy(showStoragePermissionDialog = true) }
    }

    fun dismissStoragePermissionDialog() {
        _uiState.update { it.copy(showStoragePermissionDialog = false) }
    }

    // 尝试将 SAF content URI 转换为用户可读的文件系统路径
    fun tryGetDisplayPath(uri: Uri): String {
        val context = getApplication<Application>()
        // 如果用户已授予所有文件访问权限，可以尝试解析出实际路径
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            // 利用 DocumentsContract 或简单的字符串处理
            val docId = DocumentsContract.getTreeDocumentId(uri)
            // docId 格式可能是 "primary:folder/subfolder"
            if (docId.startsWith("primary:")) {
                val relativePath = docId.removePrefix("primary:")
                return "/storage/emulated/0/$relativePath"
            }
            // 如果无法解析，回退到文件夹名
        }
        // 没有权限或无法解析时，返回文件夹名
        val docFile = DocumentFile.fromTreeUri(context, uri)
        return docFile?.name ?: uri.lastPathSegment ?: "已选文件夹"
    }

    fun updateSourceDir(uri: Uri, displayPath: String) {
        // 获取持久化读取权限
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        _uiState.update {
            it.copy(
                sourceDirUri = uri,
                sourceDirDisplayName = displayPath
            )
        }
    }

    // 手动输入路径时调用
    fun updateSourceDirManually(newPath: String) {
        _uiState.update {
            it.copy(
                sourceDirDisplayName = newPath,
                sourceDirUri = null  // 清除 URI，表示使用文件系统路径
            )
        }
    }

    fun clearSourceDir() {
        _uiState.update { it.copy(sourceDirUri = null, sourceDirDisplayName = "") }
    }

    // …… 其他函数（openConfigDialog, dismissConfigDialog, saveConfig, updateRepoUrl 等保持不变）
    // 下面只列出修改过的 startPush

    fun startPush() {
        val state = _uiState.value
        if (state.repoUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入目标仓库地址") }
            return
        }
        if (state.sourceDirDisplayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请选择或输入本地源码文件夹路径") }
            return
        }

        // 权限判断
        if (!state.hasStoragePermission && state.sourceDirUri == null) {
            // 用户手动输入了文件路径，但没有存储权限，无法访问
            _uiState.update { it.copy(errorMessage = "需要存储权限才能访问本地文件夹，请点击推送后授权") }
            showStoragePermissionDialog()
            return
        }

        _uiState.update { it.copy(isWorking = true, errorMessage = "", statusMessage = "准备中...") }

        viewModelScope.launch {
            // 传递给 GitHelper 的参数现在更灵活
            val result = GitHelper.pushSourceToRepo(
                context = getApplication(),
                config = state.config,
                repoUrl = state.repoUrl,
                sourcePath = state.sourceDirDisplayName,   // 可能是文件路径
                sourceUri = state.sourceDirUri,            // 可能是 SAF URI
                onProgress = { msg ->
                    _uiState.update { it.copy(statusMessage = msg) }
                }
            )
            result.onSuccess { msg ->
                _uiState.update { it.copy(isWorking = false, statusMessage = msg, errorMessage = "") }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isWorking = false, statusMessage = "", errorMessage = e.message ?: "操作失败")
                }
            }
        }
    }
    // ……
}
