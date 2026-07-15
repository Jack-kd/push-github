package com.jack.pushgithub.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jack.pushgithub.data.ConfigRepository
import com.jack.pushgithub.data.PushHistoryEntity
import com.jack.pushgithub.git.GitHelper
import com.jack.pushgithub.notification.PushNotificationHelper
import com.jack.pushgithub.platform.GitPlatform
import androidx.room.Room
import com.jack.pushgithub.data.AppDatabase

class ScheduledPushWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_REPO_URL = "repo_url"
        const val KEY_SOURCE_PATH = "source_path"
        const val KEY_BRANCH = "branch"
        const val KEY_PLATFORM = "platform"
    }

    override suspend fun doWork(): Result {
        val repoUrl = inputData.getString(KEY_REPO_URL) ?: return Result.failure()
        val sourcePath = inputData.getString(KEY_SOURCE_PATH) ?: return Result.failure()
        val branch = inputData.getString(KEY_BRANCH) ?: "main"
        val platformName = inputData.getString(KEY_PLATFORM) ?: "GitHub"

        val configRepo = ConfigRepository(applicationContext)
        val config = configRepo.loadConfig()
        if (!configRepo.hasConfig()) {
            PushNotificationHelper.notify(applicationContext, "定时推送失败", "未配置 Git 凭据", false)
            return Result.failure()
        }

        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "push_github_db").build()
        val historyDao = db.pushHistoryDao()

        val result = GitHelper.pushSourceToRepo(
            context = applicationContext,
            config = config,
            repoUrl = repoUrl,
            sourcePath = sourcePath,
            sourceUri = null,
            branch = branch,
            onProgress = {},
            onFileProgress = { _, _ -> }
        )

        result.onSuccess { msg ->
            historyDao.insert(
                PushHistoryEntity(
                    repoUrl = repoUrl,
                    branch = branch,
                    sourcePath = sourcePath,
                    success = true,
                    timestamp = System.currentTimeMillis(),
                    message = msg
                )
            )
            PushNotificationHelper.notify(applicationContext, "定时推送成功", "已推送到 $repoUrl")
        }.onFailure { e ->
            historyDao.insert(
                PushHistoryEntity(
                    repoUrl = repoUrl,
                    branch = branch,
                    sourcePath = sourcePath,
                    success = false,
                    timestamp = System.currentTimeMillis(),
                    message = e.message ?: "未知错误"
                )
            )
            PushNotificationHelper.notify(applicationContext, "定时推送失败", e.message ?: "未知错误", false)
        }

        db.close()
        return if (result.isSuccess) Result.success() else Result.failure()
    }
}