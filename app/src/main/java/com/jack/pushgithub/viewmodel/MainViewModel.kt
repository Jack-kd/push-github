package com.jack.pushgithub.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jack.pushgithub.BuildConfig
import com.jack.pushgithub.data.AppDatabase
import com.jack.pushgithub.data.ConfigRepository
import com.jack.pushgithub.data.GitConfig
import com.jack.pushgithub.data.PushHistoryEntity
import com.jack.pushgithub.data.RepoConfigEntity
import com.jack.pushgithub.github.CommitInfo
import com.jack.pushgithub.github.FileInfo
import com.jack.pushgithub.github.GithubApi
import com.jack.pushgithub.git.GitHelper
import com.jack.pushgithub.network.NetworkUtils
import com.jack.pushgithub.notification.PushNotificationHelper
import com.jack.pushgithub.platform.GitPlatform
import com.jack.pushgithub.worker.ScheduledPushWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

data class MainUiState(
    val config: GitConfig = GitConfig(),
    val hasConfig: Boolean = false,
    val showConfigDialog: Boolean = false,
    val isFirstTime: Boolean = true,
    val repoUrl: String = "",
    val branch: String = "main",
    val sourceDirDisplayName: String = "",
    val sourceDirUri: Uri? = null,
    val isWorking: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String = "",
    val hasStoragePermission: Boolean = false,
    val showStoragePermissionDialog: Boolean = false,
    val logMessages: List<String> = emptyList(),
    val logFilter: String = "",

    val progressVisible: Boolean = false,
    val progress: Int = 0,

    val showDownloadDialog: Boolean = false,
    val downloadPath: String = "",

    // Multi-repo config
    val repoConfigs: List<RepoConfigEntity> = emptyList(),

    // Push history
    val pushHistory: List<PushHistoryEntity> = emptyList(),
    val showHistory: Boolean = false,

    // File preview
    val showFilePreview: Boolean = false,
    val previewFiles: List<String> = emptyList(),

    // Commit history
    val showCommitHistory: Boolean = false,
    val commitHistory: List<CommitInfo> = emptyList(),
    val isLoadingCommits: Boolean = false,

    // Scheduled push
    val showScheduleDialog: Boolean = false,
    val scheduleIntervalHours: Int = 24,

    // Clear repository
    val showClearOptionsDialog: Boolean = false,
    val showFileSelector: Boolean = false,
    val repoFiles: List<FileInfo> = emptyList(),
    val selectedFilePaths: Set<String> = emptySet(),

    // Source dir dialog
    val showSourceDirDialog: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConfigRepository(application)
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(
            getApplication(),
            AppDatabase::class.java,
            "push_github_db"
        ).build()
    }
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    companion object {
        private const val MAX_LOG_ENTRIES = 500
    }

    init {
        viewModelScope.launch {
            loadConfig()
            loadRepoConfigs()
            loadHistory()
        }
        checkStoragePermission()
    }

    private suspend fun loadConfig() {
        val config = repository.loadConfig()
        val hasConfig = repository.hasConfig()
        _uiState.value = _uiState.value.copy(
            config = config,
            hasConfig = hasConfig,
            showConfigDialog = !hasConfig,
            isFirstTime = !hasConfig
        )
    }

    fun addLog(message: String) {
        _uiState.update { state ->
            val currentLogs = state.logMessages
            // 超过上限时直接截断，避免每次都复制整个列表
            val newLogs = if (currentLogs.size >= MAX_LOG_ENTRIES) {
                (currentLogs + message).takeLast(MAX_LOG_ENTRIES)
            } else {
                currentLogs + message
            }
            state.copy(logMessages = newLogs)
        }
    }

    fun clearLog() {
        _uiState.update { it.copy(logMessages = emptyList()) }
    }

    fun updateLogFilter(filter: String) {
        _uiState.update { it.copy(logFilter = filter) }
    }

    val filteredLogs: List<String>
        get() {
            val filter = _uiState.value.logFilter
            val logs = _uiState.value.logMessages
            return if (filter.isBlank()) {
                logs
            } else {
                logs.filter { it.contains(filter, ignoreCase = true) }
            }
        }

    fun showClearOptionsDialog() {
        _uiState.value = _uiState.value.copy(showClearOptionsDialog = true)
    }

    fun hideClearOptionsDialog() {
        _uiState.value = _uiState.value.copy(showClearOptionsDialog = false)
    }

    fun clearRepositoryFast() {
        viewModelScope.launch {
            hideClearOptionsDialog()
            _uiState.value = _uiState.value.copy(
                progressVisible = true,
                progress = 0
            )

            try {
                addLog("开始快速清空GitHub仓库...")
                val (owner, repo) = parseRepoUrl() ?: run {
                    addLog("错误：无效的仓库地址")
                    return@launch
                }
                val token = loadToken() ?: return@launch

                withContext(Dispatchers.IO) {
                    GithubApi(token).clearRepositoryFast(
                        owner, repo,
                        { message -> addLog(message) },
                        { percent -> _uiState.value = _uiState.value.copy(progress = percent) }
                    )
                    _uiState.value = _uiState.value.copy(progress = 100)
                    delay(800)
                    _uiState.value = _uiState.value.copy(
                        progressVisible = false,
                        progress = 0
                    )
                }
            } catch (e: Exception) {
                addLog("========== 清空失败 ==========")
                addLog("错误信息: ${e.message ?: "无错误信息"}")
                logExceptionDetails(e)
                addLog("==============================")
            }
        }
    }

    fun showFileSelector() {
        viewModelScope.launch {
            hideClearOptionsDialog()
            _uiState.value = _uiState.value.copy(
                showFileSelector = true,
                repoFiles = emptyList(),
                selectedFilePaths = emptySet()
            )

            val (owner, repo) = parseRepoUrl() ?: run {
                addLog("错误：无效的仓库地址")
                _uiState.value = _uiState.value.copy(showFileSelector = false)
                return@launch
            }
            val token = loadToken() ?: run {
                _uiState.value = _uiState.value.copy(showFileSelector = false)
                return@launch
            }

            try {
                addLog("正在获取仓库文件列表...")
                val files = withContext(Dispatchers.IO) {
                    GithubApi(token).getRepositoryFiles(owner, repo) { addLog(it) }
                }
                addLog("获取到 ${files.size} 个文件/目录")
                _uiState.value = _uiState.value.copy(repoFiles = files)
            } catch (e: Exception) {
                addLog("获取文件列表失败: ${e.message}")
                _uiState.value = _uiState.value.copy(showFileSelector = false)
            }
        }
    }

    fun hideFileSelector() {
        _uiState.value = _uiState.value.copy(showFileSelector = false)
    }

    fun toggleFileSelection(path: String) {
        _uiState.value = _uiState.value.copy(
            selectedFilePaths = if (path in _uiState.value.selectedFilePaths) {
                _uiState.value.selectedFilePaths - path
            } else {
                _uiState.value.selectedFilePaths + path
            }
        )
    }

    fun selectAllFiles() {
        _uiState.value = _uiState.value.copy(
            selectedFilePaths = _uiState.value.repoFiles
                .filter { it.type == "blob" }
                .map { it.path }
                .toSet()
        )
    }

    fun deselectAllFiles() {
        _uiState.value = _uiState.value.copy(selectedFilePaths = emptySet())
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            val selectedPaths = _uiState.value.selectedFilePaths
            if (selectedPaths.isEmpty()) {
                addLog("请先选择文件")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                showFileSelector = false,
                progressVisible = true,
                progress = 0
            )

            try {
                addLog("开始删除 ${selectedPaths.size} 个文件...")
                val (owner, repo) = parseRepoUrl() ?: run {
                    addLog("错误：无效的仓库地址")
                    return@launch
                }
                val token = loadToken() ?: return@launch

                val filesToDelete = _uiState.value.repoFiles.filter { it.path in selectedPaths }
                withContext(Dispatchers.IO) {
                    GithubApi(token).deleteFiles(
                        owner, repo, filesToDelete,
                        { message -> addLog(message) },
                        { percent -> _uiState.value = _uiState.value.copy(progress = percent) }
                    )
                    _uiState.value = _uiState.value.copy(progress = 100)
                    delay(800)
                    _uiState.value = _uiState.value.copy(
                        progressVisible = false,
                        progress = 0
                    )
                }
            } catch (e: Exception) {
                addLog("========== 删除失败 ==========")
                addLog("错误信息: ${e.message ?: "无错误信息"}")
                logExceptionDetails(e)
                addLog("==============================")
            }
        }
    }

    private fun parseRepoUrl(): Pair<String, String>? {
        val repoUrl = _uiState.value.repoUrl
            .replace("https://github.com/", "")
            .removeSuffix(".git")
        val parts = repoUrl.split("/")
        val owner = parts.getOrNull(0) ?: ""
        val repo = parts.getOrNull(1) ?: ""
        if (owner.isBlank() || repo.isBlank()) return null
        return Pair(owner, repo)
    }

    private suspend fun loadToken(): String? {
        val token = repository.loadConfig().token
        if (token.isBlank()) {
            addLog("错误：没有GitHub Token")
            return null
        }
        return token
    }

    @Deprecated("Use showClearOptionsDialog instead")
    fun clearGithubRepository() {
        showClearOptionsDialog()
    }

    // 配置相关方法
    fun openConfigDialog(modify: Boolean = false) {
        _uiState.update { it.copy(showConfigDialog = true, isFirstTime = !modify) }
    }

    fun dismissConfigDialog() {
        _uiState.update { it.copy(showConfigDialog = false) }
    }

    fun saveConfig(email: String, username: String, token: String) {
        val newConfig = GitConfig(email, username, token)
        viewModelScope.launch {
            repository.saveConfig(newConfig)
            _uiState.update { it.copy(config = newConfig, hasConfig = true, showConfigDialog = false) }
        }
    }

    fun updateRepoUrl(url: String) {
        _uiState.update { it.copy(repoUrl = url) }
    }

    fun updateBranch(branch: String) {
        _uiState.update { it.copy(branch = branch) }
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
            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        _uiState.update { it.copy(sourceDirUri = uri, sourceDirDisplayName = displayPath) }
    }

    fun updateSourceDirManually(newPath: String) {
        _uiState.update { it.copy(sourceDirDisplayName = newPath, sourceDirUri = null) }
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

    // ========== Source dir dialog ==========

    fun showSourceDirDialog() {
        _uiState.update { it.copy(showSourceDirDialog = true) }
    }

    fun hideSourceDirDialog() {
        _uiState.update { it.copy(showSourceDirDialog = false) }
    }

    fun confirmPush() {
        checkStoragePermission()
        val state = _uiState.value
        if (state.sourceDirDisplayName.isBlank()) {
            addLog("错误：请选择或输入源码路径")
            return
        }
        if (!state.hasStoragePermission && state.sourceDirUri == null) {
            showStoragePermissionDialog()
            return
        }
        hideSourceDirDialog()
        startPush()
    }

    // ========== Multi-repo config ==========

    fun loadRepoConfigs() {
        viewModelScope.launch {
            db.repoConfigDao().getAllConfigs().collect { configs ->
                _uiState.update { it.copy(repoConfigs = configs) }
            }
        }
    }

    fun addRepoConfig(name: String, url: String, branch: String) {
        viewModelScope.launch {
            val config = RepoConfigEntity(
                name = name,
                repoUrl = url,
                branch = branch,
                createTime = System.currentTimeMillis()
            )
            db.repoConfigDao().insert(config)
            addLog("已添加仓库配置: $name")
        }
    }

    fun deleteRepoConfig(id: Long) {
        viewModelScope.launch {
            val config = db.repoConfigDao().getById(id)
            config?.let {
                db.repoConfigDao().delete(it)
                addLog("已删除仓库配置: ${it.name}")
            }
        }
    }

    fun selectRepoConfig(id: Long) {
        viewModelScope.launch {
            val config = db.repoConfigDao().getById(id)
            config?.let {
                _uiState.update { state ->
                    state.copy(
                        repoUrl = it.repoUrl,
                        branch = it.branch
                    )
                }
                addLog("已选择仓库: ${it.name}")
            }
        }
    }

    // ========== Push history ==========

    fun loadHistory() {
        viewModelScope.launch {
            db.pushHistoryDao().getAllHistory().collect { history ->
                _uiState.update { it.copy(pushHistory = history) }
            }
        }
    }

    fun showHistoryScreen() {
        _uiState.update { it.copy(showHistory = true) }
    }

    fun dismissHistoryScreen() {
        _uiState.update { it.copy(showHistory = false) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            db.pushHistoryDao().clearAll()
            addLog("已清空推送历史")
        }
    }

    private suspend fun insertPushHistory(repoUrl: String, branch: String, sourcePath: String, success: Boolean, message: String) {
        val history = PushHistoryEntity(
            repoUrl = repoUrl,
            branch = branch,
            sourcePath = sourcePath,
            success = success,
            timestamp = System.currentTimeMillis(),
            message = message
        )
        db.pushHistoryDao().insert(history)
    }

    // ========== File preview ==========

    fun previewFilesBeforePush() {
        val state = _uiState.value
        val sourcePath = state.sourceDirDisplayName
        if (sourcePath.isBlank()) {
            addLog("错误：请先选择源码路径")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = mutableListOf<String>()
                val baseDir = File(sourcePath)
                if (baseDir.exists() && baseDir.isDirectory) {
                    baseDir.walkTopDown().filter { it.isFile && !it.path.contains(".git") }.forEach { file ->
                        val relativePath = file.relativeTo(baseDir).path
                        files.add(relativePath)
                    }
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(showFilePreview = true, previewFiles = files) }
                        addLog("找到 ${files.size} 个文件")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        addLog("错误：无法访问目录 $sourcePath")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog("扫描文件失败: ${e.message}")
                }
            }
        }
    }

    fun dismissFilePreview() {
        _uiState.update { it.copy(showFilePreview = false, previewFiles = emptyList()) }
    }

    // ========== Commit history ==========

    fun loadCommitHistory() {
        val state = _uiState.value
        val repoUrl = state.repoUrl
        if (repoUrl.isBlank()) {
            addLog("错误：仓库地址为空")
            return
        }

        _uiState.update { it.copy(isLoadingCommits = true, showCommitHistory = true) }

        viewModelScope.launch {
            try {
                val token = repository.loadConfig().token
                if (token.isBlank()) {
                    addLog("错误：没有GitHub Token")
                    _uiState.update { it.copy(isLoadingCommits = false) }
                    return@launch
                }

                val repoUrlStr = repoUrl
                    .replace("https://github.com/", "")
                    .removeSuffix(".git")
                val parts = repoUrlStr.split("/")
                val owner = parts.getOrNull(0) ?: ""
                val repo = parts.getOrNull(1) ?: ""

                if (owner.isBlank() || repo.isBlank()) {
                    addLog("错误：无效的仓库地址")
                    _uiState.update { it.copy(isLoadingCommits = false) }
                    return@launch
                }

                val history = withContext(Dispatchers.IO) {
                    GithubApi(token).getCommitHistory(owner, repo, state.branch)
                }

                _uiState.update { it.copy(commitHistory = history, isLoadingCommits = false) }
                addLog("加载了 ${history.size} 条提交记录")
            } catch (e: Exception) {
                addLog("加载提交历史失败: ${e.message}")
                _uiState.update { it.copy(isLoadingCommits = false) }
            }
        }
    }

    fun dismissCommitHistory() {
        _uiState.update { it.copy(showCommitHistory = false, commitHistory = emptyList()) }
    }

    // ========== Scheduled push ==========

    fun showScheduleDialog() {
        _uiState.update { it.copy(showScheduleDialog = true) }
    }

    fun dismissScheduleDialog() {
        _uiState.update { it.copy(showScheduleDialog = false) }
    }

    fun updateScheduleInterval(hours: Int) {
        _uiState.update { it.copy(scheduleIntervalHours = hours) }
    }

    fun schedulePush() {
        val state = _uiState.value
        val repoUrl = state.repoUrl
        val sourcePath = state.sourceDirDisplayName
        val branch = state.branch

        if (repoUrl.isBlank() || sourcePath.isBlank()) {
            addLog("错误：仓库地址或源码路径为空")
            return
        }

        val platform = GitPlatform.detect(repoUrl)
        val workRequest = PeriodicWorkRequestBuilder<ScheduledPushWorker>(
            state.scheduleIntervalHours.toLong(), TimeUnit.HOURS
        ).setInputData(
            androidx.work.Data.Builder()
                .putString(ScheduledPushWorker.KEY_REPO_URL, repoUrl)
                .putString(ScheduledPushWorker.KEY_SOURCE_PATH, sourcePath)
                .putString(ScheduledPushWorker.KEY_BRANCH, branch)
                .putString(ScheduledPushWorker.KEY_PLATFORM, platform.name)
                .build()
        ).build()

        WorkManager.getInstance(getApplication()).enqueue(workRequest)
        addLog("已设置定时推送，间隔 ${state.scheduleIntervalHours} 小时")
        dismissScheduleDialog()
    }

    fun cancelScheduledPush() {
        WorkManager.getInstance(getApplication()).cancelAllWork()
        addLog("已取消所有定时推送")
    }

    // ========== Main push method ==========

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

        // Network check
        if (!NetworkUtils.isNetworkAvailable(getApplication())) {
            addLog("错误：网络不可用，请检查网络连接")
            _uiState.update { it.copy(errorMessage = "网络不可用，请检查网络连接") }
            return
        }

        clearLog()
        addLog("开始推送流程...")
        addLog("目标仓库: ${state.repoUrl}")
        addLog("目标分支: ${state.branch}")
        addLog("本地路径: ${state.sourceDirDisplayName}")
        addLog("Token: 已配置")
        addLog("用户名: 已配置")

        // Platform detection
        val platform = GitPlatform.detect(state.repoUrl)
        addLog("检测到平台: ${platform.name}")

        _uiState.update { it.copy(isWorking = true, errorMessage = "", statusMessage = "准备中...") }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                progressVisible = true,
                progress = 0
            )
            var success = false
            var resultMessage = ""

            try {
                val result = GitHelper.pushSourceToRepo(
                    context = getApplication(),
                    config = state.config,
                    repoUrl = state.repoUrl,
                    sourcePath = state.sourceDirDisplayName,
                    sourceUri = state.sourceDirUri,
                    branch = state.branch,
                    onProgress = { msg ->
                        addLog(msg)
                        _uiState.update { it.copy(statusMessage = msg) }
                    },
                    // Real progress via onFileProgress callback (20-50% range)
                    onFileProgress = { current, total ->
                        if (total > 0) {
                            val fileProgress = (20 + (current.toDouble() / total * 30).toInt()).coerceIn(20, 50)
                            _uiState.value = _uiState.value.copy(progress = fileProgress)
                        }
                    }
                )

                result.onSuccess { msg ->
                    success = true
                    resultMessage = msg
                    addLog("✅ $msg")
                    _uiState.update { it.copy(isWorking = false, statusMessage = msg, errorMessage = "") }
                    kotlinx.coroutines.delay(800)
                    _uiState.value = _uiState.value.copy(
                        progressVisible = false,
                        progress = 0
                    )
                }.onFailure { e ->
                    success = false
                    resultMessage = e.message ?: "未知错误"
                    addLog("❌ 推送失败: ${e.message}")
                    logExceptionDetails(e)
                    _uiState.update {
                        it.copy(isWorking = false, statusMessage = "", errorMessage = e.message ?: "操作失败")
                    }
                }
            } catch (e: Exception) {
                success = false
                resultMessage = e.message ?: "未知错误"
                addLog("❌ 协程内异常: ${e.message}")
                logExceptionDetails(e)
                _uiState.update {
                    it.copy(isWorking = false, statusMessage = "", errorMessage = e.message ?: "未知错误")
                }
            } finally {
                // Save to push history
                withContext(Dispatchers.IO) {
                    insertPushHistory(
                        repoUrl = state.repoUrl,
                        branch = state.branch,
                        sourcePath = state.sourceDirDisplayName,
                        success = success,
                        message = resultMessage
                    )
                }
                // Send notification
                val title = if (success) "推送完成" else "推送失败"
                PushNotificationHelper.notify(
                    getApplication(),
                    title,
                    resultMessage,
                    success
                )
            }
        }
    }

    private fun logExceptionDetails(e: Throwable) {
        if (BuildConfig.DEBUG) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            addLog("详细堆栈:\n$sw")
        }
    }

    // ========== 下载源码相关 ==========

    fun showDownloadDialog() {
        val repoUrl = _uiState.value.repoUrl
        val repoName = extractRepoName(repoUrl)
        val defaultPath = if (repoName.isNotBlank()) {
            "/storage/emulated/0/github下载源码/$repoName/"
        } else {
            "/storage/emulated/0/github下载源码/"
        }
        _uiState.update { it.copy(showDownloadDialog = true, downloadPath = defaultPath) }
    }

    fun dismissDownloadDialog() {
        _uiState.update { it.copy(showDownloadDialog = false) }
    }

    fun updateDownloadPath(path: String) {
        _uiState.update { it.copy(downloadPath = path) }
    }

    private fun extractRepoName(repoUrl: String): String {
        val clean = repoUrl.trimEnd('/').removeSuffix(".git")
        return clean.substringAfterLast("/")
    }

    fun startDownload() {
        val state = _uiState.value
        if (state.repoUrl.isBlank()) {
            addLog("错误：仓库地址为空")
            _uiState.update { it.copy(errorMessage = "请输入仓库地址") }
            return
        }
        if (state.downloadPath.isBlank()) {
            addLog("错误：下载路径为空")
            _uiState.update { it.copy(errorMessage = "请输入下载路径") }
            return
        }

        // Network check
        if (!NetworkUtils.isNetworkAvailable(getApplication())) {
            addLog("错误：网络不可用，请检查网络连接")
            _uiState.update { it.copy(errorMessage = "网络不可用，请检查网络连接") }
            return
        }

        _uiState.update { it.copy(showDownloadDialog = false) }

        clearLog()
        addLog("开始下载源码...")
        addLog("仓库地址: ${state.repoUrl}")
        addLog("下载路径: ${state.downloadPath}")

        // Platform detection
        val platform = GitPlatform.detect(state.repoUrl)
        addLog("检测到平台: ${platform.name}")

        _uiState.update { it.copy(isWorking = true, errorMessage = "", statusMessage = "下载中...") }

        var success = false
        var resultMessage = ""

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                progressVisible = true,
                progress = 0
            )
            try {
                val result = GitHelper.cloneDownload(
                    repoUrl = state.repoUrl,
                    destPath = state.downloadPath,
                    onProgress = { msg ->
                        addLog(msg)
                        _uiState.update { it.copy(statusMessage = msg) }
                    },
                    onProgressPercent = { percent ->
                        _uiState.value = _uiState.value.copy(progress = percent)
                    }
                )
                result.onSuccess { msg ->
                    success = true
                    resultMessage = msg
                    addLog("✅ $msg")
                    _uiState.update { it.copy(isWorking = false, statusMessage = msg, errorMessage = "") }
                    delay(800)
                    _uiState.value = _uiState.value.copy(
                        progressVisible = false,
                        progress = 0
                    )
                }.onFailure { e ->
                    success = false
                    resultMessage = e.message ?: "下载失败"
                    addLog("❌ 下载失败: ${e.message}")
                    logExceptionDetails(e)
                    _uiState.update {
                        it.copy(isWorking = false, statusMessage = "", errorMessage = e.message ?: "下载失败")
                    }
                }
            } catch (e: Exception) {
                success = false
                resultMessage = e.message ?: "未知错误"
                addLog("❌ 下载异常: ${e.message}")
                logExceptionDetails(e)
                _uiState.update {
                    it.copy(isWorking = false, statusMessage = "", errorMessage = e.message ?: "未知错误")
                }
            } finally {
                // Send notification
                val title = if (success) "下载完成" else "下载失败"
                PushNotificationHelper.notify(
                    getApplication(),
                    title,
                    resultMessage,
                    success
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        db.close()
    }
}