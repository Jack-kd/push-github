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
        sourcePath: String,       // 用户输入的路径或 content URI 字符串
        sourceUri: Uri?,          // 如果有 SAF URI，优先使用
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val repoDir = File(context.filesDir, "temp_git_upload")
            if (repoDir.exists()) repoDir.deleteRecursively()
            repoDir.mkdirs()

            onProgress("正在克隆远程仓库...")

            val cloneUrl = repoUrl.let { url ->
                val base = url.removeSuffix("/").removeSuffix(".git")
                "https://github.com/$base"
            } + ".git"

            Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(repoDir)
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(config.token, ""))
                .call()
                .use { cloneGit ->
                    onProgress("仓库克隆成功，正在复制文件...")

                    // 判断来源
                    if (sourceUri != null && sourceUri.scheme == "content") {
                        // 使用 SAF 复制
                        DocumentFileCopy.copyFromUri(context, sourceUri, repoDir)
                    } else {
                        // 使用文件系统路径复制（需要存储权限）
                        val srcFolder = File(sourcePath)
                        if (!srcFolder.exists() || !srcFolder.isDirectory) {
                            throw Exception("本地文件夹不存在或无法访问: $sourcePath")
                        }
                        copyDirectory(srcFolder, repoDir)
                    }

                    onProgress("文件复制完成，正在提交...")

                    cloneGit.add().addFilepattern(".").call()
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
                            if (update.status != RemoteRefUpdate.Status.OK && update.status != RemoteRefUpdate.Status.UP_TO_DATE) {
                                throw Exception("推送失败: ${update.status}")
                            }
                        }
                    }
                }

            repoDir.deleteRecursively()
            Result.success("推送成功！")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 简单的文件目录复制
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
