package com.jack.pushgithub.git

import android.content.Context
import android.net.Uri
import com.jack.pushgithub.data.GitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
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
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val repoDir = File(context.filesDir, "temp_git_upload")

            // 智能拼接克隆URL，防止重复 https://github.com
            val cleanUrl = repoUrl.trimEnd('/')
            val cloneUrl = if (cleanUrl.startsWith("https://github.com/")) {
                if (cleanUrl.endsWith(".git")) cleanUrl else "$cleanUrl.git"
            } else {
                val base = cleanUrl.removeSuffix(".git")
                "https://github.com/${base}.git"
            }
            onProgress("目标地址: $cloneUrl")

            // 增量更新：如果本地已有仓库，执行 fetch + reset 而非全量克隆
            var git: Git
            if (repoDir.exists() && File(repoDir, ".git").exists()) {
                onProgress("检测到已有本地仓库，执行增量更新...")
                git = Git.open(repoDir)
                try {
                    // Fetch 最新
                    git.fetch()
                        .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                        .call()
                    // Reset 到远程最新（覆盖本地变更）
                    git.reset()
                        .setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
                        .setRef("origin/main")
                        .call()
                    onProgress("更新成功")
                } catch (e: Exception) {
                    // 增量更新失败，回退到全量克隆
                    onProgress("增量更新失败: ${e.message}，尝试全量克隆...")
                    git.close()
                    repoDir.deleteRecursively()
                    repoDir.mkdirs()
                    git = Git.cloneRepository()
                        .setURI(cloneUrl)
                        .setDirectory(repoDir)
                        .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                        .call()
                    onProgress("仓库克隆成功")
                }
            } else {
                if (repoDir.exists()) {
                    repoDir.deleteRecursively()
                    onProgress("删除旧的临时目录")
                }
                repoDir.mkdirs()
                onProgress("正在克隆远程仓库...")
                git = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(repoDir)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                    .call()
                onProgress("仓库克隆成功")
            }

            git.use { cloneGit ->
                onProgress("正在复制文件...")
                try {
                    if (sourceUri != null && sourceUri.scheme == "content") {
                        onProgress("通过 SAF URI 复制文件")
                        DocumentFileCopy.copyFromUri(context, sourceUri, repoDir)
                    } else {
                        val srcFolder = File(sourcePath)
                        if (!srcFolder.exists() || !srcFolder.isDirectory) {
                            throw Exception("本地文件夹不存在或无法访问: $sourcePath")
                        }
                        onProgress("从本地路径复制文件: $sourcePath")

                        // 加载 .gitignore 规则
                        val ignorePatterns = loadIgnorePatterns(srcFolder)
                        copyDirectory(srcFolder, repoDir, ignorePatterns)
                    }
                    onProgress("文件复制完成，文件数量: ${repoDir.listFiles()?.size ?: 0}")
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
                    .setForce(true)


                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                    .call()

                for (pushInfo in pushResult) {
                    val remoteUpdates = pushInfo.remoteUpdates
                    for (update in remoteUpdates) {
                        when (update.status) {
                            RemoteRefUpdate.Status.OK -> onProgress("推送成功: ${update.remoteName}")
                            RemoteRefUpdate.Status.UP_TO_DATE -> onProgress("已是最新，无需推送")
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

    private fun loadIgnorePatterns(sourceDir: File): Set<String> {
        val patterns = mutableSetOf<String>()
        patterns.addAll(DEFAULT_IGNORE_PATTERNS)

        // 读取源目录的 .gitignore
        val gitignoreFile = File(sourceDir, ".gitignore")
        if (gitignoreFile.exists()) {
            try {
                gitignoreFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        // 去掉前导斜杠和尾部斜杠，保留通配符
                        patterns.add(trimmed.trimStart('/').trimEnd('/'))
                    }
                }
            } catch (_: Exception) {
                // 读取失败时忽略，使用默认规则
            }
        }

        return patterns
    }

    private fun shouldIgnore(file: File, sourceDir: File, patterns: Set<String>): Boolean {
        val relativePath = file.relativeTo(sourceDir).path.replace(File.separatorChar, '/')
        val name = file.name

        for (pattern in patterns) {
            // 精确匹配文件名
            if (name == pattern) return true
            // 匹配路径
            if (relativePath == pattern || relativePath.startsWith("$pattern/")) return true
            // 简单通配符匹配（*.ext）
            if (pattern.startsWith("*.") && name.endsWith(pattern.removePrefix("*"))) return true
        }

        return false
    }

    suspend fun cloneDownload(
        repoUrl: String,
        destPath: String,
        onProgress: (String) -> Unit,
        onProgressPercent: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destPath)

            // 清理URL，构造克隆地址
            val cleanUrl = repoUrl.trimEnd('/')
            val cloneUrl = if (cleanUrl.startsWith("https://github.com/")) {
                if (cleanUrl.endsWith(".git")) cleanUrl else "$cleanUrl.git"
            } else {
                val base = cleanUrl.removeSuffix(".git")
                "https://github.com/${base}.git"
            }

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

    private fun copyDirectory(sourceDir: File, destDir: File, ignorePatterns: Set<String> = emptySet()) {
        sourceDir.walkTopDown().forEach { file ->
            // 跳过源目录本身
            if (file == sourceDir) return@forEach

            if (shouldIgnore(file, sourceDir, ignorePatterns)) {
                return@forEach
            }

            val relativePath = file.relativeTo(sourceDir)
            val target = File(destDir, relativePath.path)
            if (file.isDirectory) {
                target.mkdirs()
            } else {
                file.copyTo(target, overwrite = true)
            }
        }
    }
}
