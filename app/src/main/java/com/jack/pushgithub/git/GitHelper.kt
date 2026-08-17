package com.jack.pushgithub.git

import android.content.Context
import android.net.Uri
import com.jack.pushgithub.data.GitConfig
import com.jack.pushgithub.platform.GitPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.RefSpec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GitHelper {

    // 常见的应被忽略的文件和目录
    private val DEFAULT_IGNORE_PATTERNS = setOf(
        ".git", ".gitignore", ".gitattributes",
        "build", ".gradle", ".idea", "*.iml",
        "node_modules", ".DS_Store", "Thumbs.db",
        "__pycache__", "*.pyc", ".env", "local.properties"
    )

    suspend fun pushSourceToRepo(
        context: Context,
        config: GitConfig,
        repoUrl: String,
        sourcePath: String,
        sourceUri: Uri?,
        branch: String = "main",
        onProgress: (String) -> Unit,
        onFileProgress: (current: Int, total: Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val repoDir = File(context.filesDir, "temp_git_upload")

            // 使用 GitPlatform 构建克隆 URL
            val platform = GitPlatform.detect(repoUrl)
            val cloneUrl = GitPlatform.buildCloneUrl(platform, repoUrl)
            onProgress("目标地址: $cloneUrl")
            onProgress("目标分支: $branch")

            // 总是全新浅克隆（depth=1），确保与远程同步且速度快
            if (repoDir.exists()) {
                repoDir.deleteRecursively()
                onProgress("删除旧的临时目录")
            }
            repoDir.mkdirs()

            // 尝试浅克隆远程仓库，空仓库会失败（没有分支）
            var cloneGit: Git? = null
            onProgress("正在浅克隆远程仓库（depth=1）...")
            try {
                cloneGit = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(repoDir)
                    .setBranch(branch)
                    .setDepth(1)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                    .call()
                onProgress("仓库克隆成功")
            } catch (e: Exception) {
                if (e.message?.contains("not found in upstream") == true ||
                    e.message?.contains("Remote branch") == true) {
                    onProgress("远程仓库为空（无分支），初始化本地仓库...")
                    cloneGit = Git.init().setDirectory(repoDir).call()
                    cloneGit!!.remoteAdd()
                        .setName("origin")
                        .setUri(URIish(cloneUrl))
                        .call()
                } else {
                    throw e
                }
            }

            // 关闭克隆阶段的 Git 对象，释放 HTTP 连接
            onProgress("释放克隆连接...")
            cloneGit!!.close()

            // 重新打开仓库，push 时将创建全新的 HTTP 连接
            Git.open(repoDir).use { git ->
                configureRepoForPush(git.repository)

                // 清空工作目录（保留 .git），确保删除的文件能被追踪
                onProgress("清理工作目录...")
                val workTree = git.repository.workTree
                workTree.listFiles()?.forEach { file ->
                    if (file.name != ".git") file.deleteRecursively()
                }

                // 复制源文件到仓库目录
                onProgress("正在复制文件...")
                try {
                    if (sourceUri != null && sourceUri.scheme == "content") {
                        onProgress("通过 SAF URI 复制文件")
                        DocumentFileCopy.copyFromUri(context, sourceUri, workTree)
                        onFileProgress(1, 1)
                    } else {
                        val srcFolder = File(sourcePath)
                        if (!srcFolder.exists() || !srcFolder.isDirectory) {
                            throw Exception("本地文件夹不存在或无法访问: $sourcePath")
                        }
                        onProgress("从本地路径复制文件: $sourcePath")

                        val ignoreRules = loadIgnoreRules(srcFolder)
                        val totalFiles = countFiles(srcFolder, ignoreRules)
                        val copied = copyDirectory(srcFolder, workTree, ignoreRules) { current ->
                            onFileProgress(current, totalFiles)
                        }
                        onProgress("已复制 $copied 个文件")
                    }
                    onProgress("文件复制完成")
                } catch (e: Exception) {
                    onProgress("文件复制失败: ${e.message}")
                    throw e
                }

                // 检测是否有变更，无变更则跳过 push
                onProgress("检测文件变更...")
                git.add().addFilepattern(".").call()
                val status = git.status().call()
                val hasChanges = status.added.isNotEmpty() ||
                        status.changed.isNotEmpty() ||
                        status.removed.isNotEmpty() ||
                        status.modified.isNotEmpty() ||
                        status.missing.isNotEmpty()

                if (!hasChanges) {
                    onProgress("文件无变化，跳过推送")
                    repoDir.deleteRecursively()
                    return@withContext Result.success("文件无变化，无需推送")
                }

                onProgress("变更文件: 新增 ${status.added.size} / 修改 ${status.changed.size + status.modified.size} / 删除 ${status.removed.size + status.missing.size}")

                onProgress("提交更改...")
                git.commit()
                    .setAuthor(config.username, config.email)
                    .setMessage("自动更新于 ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                    .call()

                onProgress("正在推送到远程仓库...")
                var lastPushException: Exception? = null
                val maxPushRetries = 3
                for (attempt in 0 until maxPushRetries) {
                    try {
                        if (attempt > 0) {
                            val waitMs = (attempt * 3000).toLong()
                            onProgress("等待 ${waitMs / 1000}s 后重试推送 (${attempt + 1}/$maxPushRetries)...")
                            kotlinx.coroutines.delay(waitMs)
                        }
                        val pushResult = git.push()
                            .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                            .setTimeout(600)
                            .setRefSpecs(listOf(org.eclipse.jgit.transport.RefSpec("HEAD:refs/heads/$branch")))
                            .call()

                        for (pushInfo in pushResult) {
                            val remoteUpdates = pushInfo.remoteUpdates
                            for (update in remoteUpdates) {
                                when (update.status) {
                                    RemoteRefUpdate.Status.OK -> onProgress("推送成功: ${update.remoteName}")
                                    RemoteRefUpdate.Status.UP_TO_DATE -> onProgress("已是最新，无需推送")
                                    RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD ->
                                        throw Exception("推送被拒绝：远程仓库有新的提交，请先同步后再推送")
                                    else -> throw Exception("推送失败: ${update.status} - ${update.message}")
                                }
                            }
                        }
                        lastPushException = null
                        break
                    } catch (e: Exception) {
                        lastPushException = e
                        val is500 = e.message?.contains("500") == true ||
                                e.message?.contains("Internal Server Error") == true
                        if (attempt < maxPushRetries - 1 && is500) {
                            onProgress("推送失败 (HTTP 500)，准备重试...")
                            continue
                        }
                        throw e
                    }
                }
                if (lastPushException != null) throw lastPushException
            }

            onProgress("清理临时目录...")
            repoDir.deleteRecursively()
            Result.success("推送成功！")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 推送单个文件：先创建新分支，提交后推送该分支（不直接改主分支），
     * 由上层调用方通过平台 API 创建 PR。
     *
     * @param sourceUri     通过文件选择器选择的文件（优先使用）
     * @param sourcePath    直接路径（sourceUri 为 null 时使用）
     * @param targetPath    文件在仓库内的目标路径，如 "docs/note.md"；
     *                      为空或目录形式（以 "/" 结尾）时自动拼上文件名
     * @param baseBranch    基准分支（PR 的目标分支）
     */
    suspend fun pushSingleFileViaPr(
        context: Context,
        config: GitConfig,
        repoUrl: String,
        sourceUri: Uri?,
        sourcePath: String,
        sourceDisplayName: String,
        targetPath: String,
        baseBranch: String,
        onProgress: (String) -> Unit
    ): Result<FilePushResult> = withContext(Dispatchers.IO) {
        try {
            val repoDir = File(context.filesDir, "temp_git_upload_file")
            if (repoDir.exists()) {
                repoDir.deleteRecursively()
                onProgress("删除旧的临时目录")
            }
            repoDir.mkdirs()

            val platform = GitPlatform.detect(repoUrl)
            val cloneUrl = GitPlatform.buildCloneUrl(platform, repoUrl)
            onProgress("目标地址: $cloneUrl")
            onProgress("基准分支: $baseBranch")

            // 目标路径规范化：空/目录 → 拼上文件名
            val fileName = sourceDisplayName.substringAfterLast('/').ifBlank { "file" }
            val finalTargetPath = normalizeTargetPath(targetPath, fileName)
            onProgress("仓库内路径: $finalTargetPath")

            // 生成新分支名 push/<文件名>-<时间戳>
            val safeName = fileName
                .lowercase()
                .replace(Regex("[^a-z0-9._-]"), "-")
                .trim('-')
                .ifBlank { "file" }
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val branchName = "push/$safeName-$timestamp"
            onProgress("新分支: $branchName")

            // 浅克隆（空仓库会失败，改为本地初始化）
            var cloneGit: Git? = null
            var repoEmpty = false
            onProgress("正在浅克隆远程仓库（depth=1）...")
            try {
                cloneGit = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(repoDir)
                    .setBranch(baseBranch)
                    .setDepth(1)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                    .call()
                onProgress("仓库克隆成功")
            } catch (e: Exception) {
                if (e.message?.contains("not found in upstream") == true ||
                    e.message?.contains("Remote branch") == true
                ) {
                    onProgress("远程仓库为空（无分支），将直接初始化并推送（无法创建 PR）")
                    repoEmpty = true
                    cloneGit = Git.init().setDirectory(repoDir).call()
                    cloneGit!!.remoteAdd()
                        .setName("origin")
                        .setUri(URIish(cloneUrl))
                        .call()
                } else {
                    throw e
                }
            }
            cloneGit!!.close()

            Git.open(repoDir).use { git ->
                configureRepoForPush(git.repository)

                // 非空仓库：从基准分支创建新分支
                if (!repoEmpty) {
                    onProgress("创建新分支 $branchName ...")
                    git.branchCreate().setName(branchName).call()
                    git.checkout().setName(branchName).call()
                }

                // 写入单个文件
                onProgress("写入文件: $finalTargetPath")
                val destFile = File(git.repository.workTree, finalTargetPath)
                destFile.parentFile?.mkdirs()
                if (sourceUri != null) {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw Exception("无法读取所选文件")
                } else {
                    val srcFile = File(sourcePath)
                    if (!srcFile.exists() || !srcFile.isFile) {
                        throw Exception("本地文件不存在或无法访问: $sourcePath")
                    }
                    srcFile.copyTo(destFile, overwrite = true)
                }
                onProgress("文件写入完成")

                // 变更检测：内容一致则跳过，避免空提交
                onProgress("检测文件变更...")
                git.add().addFilepattern(finalTargetPath).call()
                val status = git.status().call()
                val hasChanges = status.added.isNotEmpty() ||
                        status.changed.isNotEmpty() ||
                        status.modified.isNotEmpty()
                if (!hasChanges) {
                    onProgress("文件内容与仓库一致，跳过推送")
                    repoDir.deleteRecursively()
                    return@withContext Result.success(FilePushResult(branch = branchName, skipped = true))
                }

                // 提交
                onProgress("提交更改...")
                git.commit()
                    .setAuthor(config.username, config.email)
                    .setMessage("上传文件 $finalTargetPath")
                    .call()

                // 推送新分支（空仓库时推送到基准分支）
                val pushBranch = if (repoEmpty) baseBranch else branchName
                onProgress("正在推送分支 $pushBranch ...")
                git.push()
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                    .setTimeout(600)
                    .setRefSpecs(listOf(RefSpec("HEAD:refs/heads/$pushBranch")))
                    .call()
                onProgress("分支推送成功: $pushBranch")
            }

            onProgress("清理临时目录...")
            repoDir.deleteRecursively()
            Result.success(FilePushResult(branch = if (repoEmpty) baseBranch else branchName, repoEmpty = repoEmpty))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 规范化仓库内目标路径：空路径或仅目录（以 "/" 结尾）时补上文件名
     */
    private fun normalizeTargetPath(targetPath: String, fileName: String): String {
        var p = targetPath.trim().trimStart('/')
        if (p.isEmpty()) return fileName
        // 先判断是否为目录形式，再去掉结尾斜杠拼文件名
        val isDirForm = p.endsWith("/")
        p = p.removeSuffix("/").trimEnd('/')
        return if (isDirForm) "$p/$fileName" else p
    }

    /**
     * 加载 .gitignore 规则，返回 (negatePatterns, normalPatterns) 两个集合
     */
    private data class IgnoreRules(
        val patterns: List<IgnorePattern>,
    )

    private data class IgnorePattern(
        val raw: String,
        val isNegation: Boolean,
        val isDirectoryOnly: Boolean,
        val regex: Regex
    )

    private fun loadIgnoreRules(sourceDir: File): IgnoreRules {
        val patterns = mutableListOf<IgnorePattern>()

        // 先添加默认规则
        DEFAULT_IGNORE_PATTERNS.forEach { raw ->
            patterns.add(compilePattern(raw))
        }

        // 读取源目录的 .gitignore
        val gitignoreFile = File(sourceDir, ".gitignore")
        if (gitignoreFile.exists()) {
            try {
                gitignoreFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        patterns.add(compilePattern(trimmed))
                    }
                }
            } catch (_: Exception) {
                // 读取失败时忽略，使用默认规则
            }
        }

        return IgnoreRules(patterns)
    }

    private fun compilePattern(raw: String): IgnorePattern {
        val isNegation = raw.startsWith("!")
        val cleaned = if (isNegation) raw.substring(1).trimStart('/') else raw.trimStart('/')
        val isDirectoryOnly = cleaned.endsWith("/")
        val patternBody = if (isDirectoryOnly) cleaned.dropLast(1) else cleaned

        // 将 gitignore 通配符转换为正则表达式
        val regexStr = buildString {
            append("^")
            // 如果模式不含 /，则匹配任意层级
            if (!patternBody.contains("/") || patternBody.startsWith("**/")) {
                append("(.*/)?")
            }
            var i = 0
            val body = patternBody.removePrefix("**/")
            while (i < body.length) {
                when {
                    body.startsWith("**", i) -> {
                        // ** 匹配任意层级
                        if (i + 2 < body.length && body[i + 2] == '/') {
                            append("(.*/)?")
                            i += 3
                        } else {
                            append(".*")
                            i += 2
                        }
                    }
                    body[i] == '*' -> {
                        append("[^/]*")
                        i++
                    }
                    body[i] == '?' -> {
                        append("[^/]")
                        i++
                    }
                    body[i] == '.' || body[i] == '+' || body[i] == '(' ||
                    body[i] == ')' || body[i] == '[' || body[i] == ']' ||
                    body[i] == '{' || body[i] == '}' || body[i] == '^' ||
                    body[i] == '$' || body[i] == '|' || body[i] == '\\' -> {
                        append("\\").append(body[i])
                        i++
                    }
                    else -> {
                        append(body[i])
                        i++
                    }
                }
            }
            if (isDirectoryOnly) append("(/.*)?")
            append("$")
        }

        return IgnorePattern(
            raw = raw,
            isNegation = isNegation,
            isDirectoryOnly = isDirectoryOnly,
            regex = Regex(regexStr)
        )
    }

    private fun shouldIgnore(file: File, sourceDir: File, rules: IgnoreRules): Boolean {
        val relativePath = file.relativeTo(sourceDir).path.replace(File.separatorChar, '/')
        val pathToCheck = if (file.isDirectory) "$relativePath/" else relativePath

        var ignored = false
        for (pattern in rules.patterns) {
            if (pattern.regex.matches(pathToCheck) || pattern.regex.matches(relativePath)) {
                ignored = !pattern.isNegation
            }
        }
        return ignored
    }

    /**
     * 配置仓库参数，限制内存占用 + 大文件推送可靠性。
     * JGit 默认的 delta 压缩会加载大文件到内存中，在 Android 受限堆上容易 OOM。
     */
    private fun configureRepoForPush(repo: Repository) {
        val config = repo.config
        // pack 参数 — 限制 delta 压缩内存
        config.setString("pack", null, "window", "2")
        config.setString("pack", null, "depth", "10")
        config.setString("pack", null, "windowMemory", "8m")
        config.setString("pack", null, "deltaCacheSize", "2m")
        config.setString("pack", null, "deltaCacheLimit", "30")
        config.setString("pack", null, "bigFileThreshold", "3m")
        config.setString("pack", null, "threads", "1")
        config.setString("pack", null, "indexVersion", "2")
        // HTTP 参数 — 大文件推送 500 错误修复
        config.setString("http", null, "postBuffer", "524288000")  // 500MB 缓冲区
        config.setString("http", null, "lowSpeedLimit", "0")       // 不限速
        config.setString("http", null, "lowSpeedTime", "0")        // 不限速时间
        config.save()
    }

    suspend fun cloneDownload(
        repoUrl: String,
        destPath: String,
        onProgress: (String) -> Unit,
        onProgressPercent: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destPath)

            // 使用 GitPlatform 构建克隆 URL
            val platform = GitPlatform.detect(repoUrl)
            val cloneUrl = GitPlatform.buildCloneUrl(platform, repoUrl)

            onProgress("目标地址: $cloneUrl")
            onProgress("下载目录: $destPath")
            onProgressPercent(5)

            if (destDir.exists()) {
                onProgress("目标目录已存在，尝试删除...")
                destDir.deleteRecursively()
                onProgress("已删除旧目录")
            }

            destDir.mkdirs()
            onProgress("开始克隆仓库...")
            onProgressPercent(10)

            var totalWork = 0
            val git = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(destDir)
                .setProgressMonitor(object : org.eclipse.jgit.lib.ProgressMonitor {
                     override fun start(totalTasks: Int) {}
                    override fun beginTask(title: String?, work: Int) {
                        totalWork = work
                        onProgress("下载中: $title")
                    }
                    override fun update(completed: Int) {
                        val percent = if (totalWork > 0) {
                            (10 + (completed.toDouble() / totalWork * 80).toInt()).coerceIn(10, 90)
                        } else {
                            50
                        }
                        onProgressPercent(percent)
                    }
                    override fun endTask() {}
                    override fun isCancelled(): Boolean = false
                    override fun showDuration(enabled: Boolean) {}

                })
                .call()

            git.close()
            onProgress("克隆完成，文件数: ${destDir.listFiles()?.size ?: 0}")
            onProgressPercent(100)
            Result.success("下载成功！源码已保存到: $destPath")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun countFiles(sourceDir: File, ignoreRules: IgnoreRules): Int {
        return sourceDir.walkTopDown().count { file ->
            file != sourceDir && !shouldIgnore(file, sourceDir, ignoreRules) && file.isFile
        }
    }

    private fun copyDirectory(
        sourceDir: File,
        destDir: File,
        ignoreRules: IgnoreRules,
        onFileCopied: (Int) -> Unit
    ): Int {
        var copied = 0
        sourceDir.walkTopDown().forEach { file ->
            // 跳过源目录本身
            if (file == sourceDir) return@forEach

            if (shouldIgnore(file, sourceDir, ignoreRules)) {
                return@forEach
            }

            val relativePath = file.relativeTo(sourceDir)
            val target = File(destDir, relativePath.path)
            if (file.isDirectory) {
                target.mkdirs()
            } else {
                file.copyTo(target, overwrite = true)
                copied++
                onFileCopied(copied)
            }
        }
        return copied
    }
}

/**
 * 单文件推送结果
 */
data class FilePushResult(
    val branch: String,
    val repoEmpty: Boolean = false,
    val skipped: Boolean = false
)
