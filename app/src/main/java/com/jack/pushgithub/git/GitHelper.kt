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
            onProgress("准备临时目录: ${repoDir.absolutePath}")
            if (repoDir.exists()) {
                repoDir.deleteRecursively()
                onProgress("删除旧的临时目录")
            }
            repoDir.mkdirs()

            // 智能拼接克隆URL，防止重复 https://github.com
            val cleanUrl = repoUrl.trimEnd('/')
            val cloneUrl = if (cleanUrl.startsWith("https://github.com/")) {
                // 已经包含完整URL，只需确保以 .git 结尾
                if (cleanUrl.endsWith(".git")) cleanUrl else "$cleanUrl.git"
            } else {
                // 只提供了 owner/repo 的形式，需要补全
                val base = cleanUrl.removeSuffix(".git")
                "https://github.com/${base}.git"
            }
            onProgress("克隆地址: $cloneUrl")

            onProgress("正在克隆远程仓库...")
            val git = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(repoDir)
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                .call()

            git.use { cloneGit ->
                onProgress("仓库克隆成功，正在复制文件...")
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
                        copyDirectory(srcFolder, repoDir)
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

    private fun copyDirectory(sourceDir: File, destDir: File) {
        sourceDir.walkTopDown().forEach { file ->
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
