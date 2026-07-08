package com.jack.pushgithub.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
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
import java.io.PrintWriter
import java.io.StringWriter

data class MainUiState(
    val config: GitConfig = GitConfig(),
    val hasConfig: Boolean = false,
    val showConfigDialog: Boolean = false,
    val isFirstTime: Boolean = true,
    val repoUrl: String = "",
    val sourceDirDisplayName: String = "",
    val sourceDirUri: Uri? = null,
    val isWorking: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String = "",
    val hasStoragePermission: Boolean = false,
    val showStoragePermissionDialog: Boolean = false,
    val logMessages: List<String> = emptyList()   // 新增：日志列表
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

    // 日志记录
    fun addLog(message: String) {
        _uiState.update { state ->
            state.copy(logMessages = state.logMessages + message)
        }
    }

    fun clearLog() {
        _uiState.update { it.copy(logMessages = emptyList()) }
    }

    // ---------- 配置相关 ----------
    fun openConfigDialog(modify: Boolean = false) {
        _uiState.update {
            it.copy(
                showConfigDialog = true,
                isFirstTime = !modify
            )
        }
    }

    fun dismissConfigDialog() {
        _uiState.update { it.copy(showConfigDialog = false) }
    }

    fun saveConfig(email: String, username: String, token: String) {
        val newConfig = GitConfig(email, username, token)
        repository.saveConfig(newConfig)
        _uiState.update {
            it.copy(
                config = newConfig,
                hasConfig = true,
                showConfigDialog = false
            )
        }
    }

    fun updateRepoUrl(url: String) {
        _uiState.update { it.copy(repoUrl = url) }
    }

    fun tryGetDisplayPath(uri: Uri): String {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId.startsWith("primary:")) {
                val relativePath = docId.removePrefix("primary:")
                return "/storage/emulated/0/$relativePath"
            }
        }
        val docFile = DocumentFile.fromTreeUri(context, uri)
        return docFile?.name ?: uri.lastPathSegment ?: "已选文件夹"
    }

    fun updateSourceDir(uri: Uri, displayPath: String) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        _uiState.update {
            it.copy(sourceDirUri = uri, sourceDirDisplayName = displayPath)
        }
    }

    fun updateSourceDirManually(newPath: String) {
        _uiState.update {
            it.copy(sourceDirDisplayName = newPath, sourceDirUri = null)
        }
    }

    fun clearSourceDir() {
        _uiState.update { it.copy(sourceDirUri = null, sourceDirDisplayName = "") }
    }

    fun checkStoragePermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        _uiState.update { it.copy(hasStoragePermission = hasPermission) }
    }

    fun showStoragePermissionDialog() {
        _uiState.update { it.copy(showStoragePermissionDialog = true) }
    }

    fun dismissStoragePermissionDialog() {
        _uiState.update { it.copy(showStoragePermissionDialog = false) }
    }

    fun startPush() {
        val state = _uiState.value
        if (state.repoUrl.isBlank()) {
            addLog("错误：目标仓库地址为空")
            _uiState.update { it.copy(errorMessage = "请输入目标仓库地址") }
            return
        }
        if (state.sourceDirDisplayName.isBlank()) {
            addLog("错误：本地源码路径为空")
            _uiState.update { it.copy(errorMessage = "请选择或输入本地源码文件夹路径") }
            return
        }
        if (!state.hasStoragePermission && state.sourceDirUri == null) {
            addLog("错误：缺少存储权限，无法访问文件路径")
            _uiState.update { it.copy(errorMessage = "需要存储权限才能访问本地文件夹") }
            showStoragePermissionDialog()
            return
        }

        clearLog()
        addLog("开始推送流程...")
        addLog("目标仓库: ${state.repoUrl}")
        addLog("本地路径: ${state.sourceDirDisplayName}")
        addLog("Token 已配置: ${state.config.token.isNotEmpty()}")
        addLog("用户名: ${state.config.username}, 邮箱: ${state.config.email}")

        _uiState.update { it.copy(isWorking = true, errorMessage = "", statusMessage = "准备中...") }

        viewModelScope.launch {
            val result = GitHelper.pushSourceToRepo(
                context = getApplication(),
                config = state.config,
                repoUrl = state.repoUrl,
                sourcePath = state.sourceDirDisplayName,
                sourceUri = state.sourceDirUri,
                onProgress = { msg ->
                    addLog(msg)
                    _uiState.update { it.copy(statusMessage = msg) }
                }
            )
            result.onSuccess { msg ->
                addLog("✅ $msg")
                _uiState.update { it.copy(isWorking = false, statusMessage = msg, errorMessage = "") }
            }.onFailure { e ->
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                addLog("❌ 推送失败: ${e.message}")
                addLog("详细堆栈:\n$sw")
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        statusMessage = "",
                        errorMessage = e.message ?: "操作失败"
                    )
                }
            }
        }
    }
}
