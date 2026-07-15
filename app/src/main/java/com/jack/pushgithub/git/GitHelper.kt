package com.jack.pushgithub.git

import android.content.Context
import android.net.Uri
import com.jack.pushgithub.data.GitConfig
import com.jack.pushgithub.platform.GitPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.RemoteRefUpdate
import java.io.File

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

            // 总是全新克隆，确保与远程完全同步
            if (repoDir.exists()) {
                repoDir.deleteRecursively()
                onProgress("删除旧的临时目录")
            }
            repoDir.mkdirs()

            // 尝试克隆远程仓库，空仓库会失败（没有分支）
            var cloneGit: Git? = null
            onProgress("正在克隆远程仓库...")
            try {
                cloneGit = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(repoDir)
                    .setBranch(branch)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                    .call()
                onProgress("仓库克隆成功")
            } catch (e: Exception) {
                if (e.message?.contains("not found in upstream") == true ||
                    e.message?.contains("Remote branch") == true) {
                    onProgress("远程仓库为空（无分支），初始化本地仓库...")
                    cloneGit = Git.init().setDirectory(repoDir).call()
                    // 设置远程地址
                    cloneGit!!.remoteAdd()
                        .setName("origin")
                        .setUri(URIish(cloneUrl))
                        .call()
                } else {
                    throw e
                }
            }

            cloneGit!!.use { cloneGit ->
                onProgress("正在复制文件...")
                try {
                    if (sourceUri != null && sourceUri.scheme == "content") {
                        onProgress("通过 SAF URI 复制文件")
                        DocumentFileCopy.copyFromUri(context, sourceUri, repoDir)
                        onFileProgress(1, 1)
                    } else {
                        val srcFolder = File(sourcePath)
                        if (!srcFolder.exists() || !srcFolder.isDirectory) {
                            throw Exception("本地文件夹不存在或无法访问: $sourcePath")
                        }
                        onProgress("从本地路径复制文件: $sourcePath")

                        // 加载 .gitignore 规则
                        val ignoreRules = loadIgnoreRules(srcFolder)
                        val totalFiles = countFiles(srcFolder, ignoreRules)
                        val copied = copyDirectory(srcFolder, repoDir, ignoreRules) { current ->
                            onFileProgress(current, totalFiles)
                        }
                        onProgress("已复制 $copied 个文件")
                    }
                    onProgress("文件复制完成")
                } catch (e: Exception) {
                    onProgress("文件复制失败: ${e.message}")
                    throw e
                }

                onProgress("添加所有文件到暂存区...")
                cloneGit.add().addFilepattern(".").call()

                onProgress("提交更改...")
                cloneGit.commit()
                    .setAuthor(config.username, config.email)
                    .setMessage("自动更新于 ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                    .call()

                onProgress("正在推送到远程仓库...")
                val pushResult = cloneGit.push()
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
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
