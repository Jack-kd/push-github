package com.jack.pushgithub.viewmodel

import android.app.Application
import android.net.Uri
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
    val errorMessage: String = ""
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
    }

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

    fun updateSourceDir(uri: Uri, displayName: String) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        _uiState.update {
            it.copy(sourceDirUri = uri, sourceDirDisplayName = displayName)
        }
    }

    fun clearSourceDir() {
        _uiState.update { it.copy(sourceDirUri = null, sourceDirDisplayName = "") }
    }

    fun startPush() {
        val state = _uiState.value
        if (state.repoUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入目标仓库地址") }
            return
        }
        if (state.sourceDirUri == null) {
            _uiState.update { it.copy(errorMessage = "请选择本地源码文件夹") }
            return
        }

        _uiState.update { it.copy(isWorking = true, errorMessage = "", statusMessage = "准备中...") }

        viewModelScope.launch {
            val result = GitHelper.pushSourceToRepo(
                context = getApplication(),
                config = state.config,
                repoUrl = state.repoUrl,
                sourceDirUri = state.sourceDirUri!!,
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

    fun clearStatus() {
        _uiState.update { it.copy(errorMessage = "", statusMessage = "") }
    }
}