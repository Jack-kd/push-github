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

    // ---------- 目标地址 ----------
    fun updateRepoUrl(url: String) {
        _uiState.update { it.copy(repoUrl = url) }
    }

    // ---------- 本地源码路径 ----------
    fun tryGetDisplayPath(uri: Uri): String {
        val context = getApplication<Application>()
        // 如果有存储权限，尝试将 SAF URI 转为文件系统路径
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

    // ---------- 权限 ----------
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

    // ---------- 推送 ----------
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

        if (!state.hasStoragePermission && state.sourceDirUri == null) {
            _uiState.update { it.copy(errorMessage = "需要存储权限才能访问本地文件夹") }
            showStoragePermissionDialog()
            return
        }

        _uiState.update { it.copy(isWorking = true, errorMessage = "", statusMessage = "准备中...") }

        viewModelScope.launch {
            val result = GitHelper.pushSourceToRepo(
                context = getApplication(),
                config = state.config,
                repoUrl = state.repoUrl,
                sourcePath = state.sourceDirDisplayName,
                sourceUri = state.sourceDirUri,
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
}
