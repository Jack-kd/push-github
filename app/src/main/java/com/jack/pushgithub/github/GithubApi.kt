package com.jack.pushgithub.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GithubApi(
    private val token: String
) {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private val headers: Headers by lazy {
        Headers.Builder()
            .add("Authorization", "Bearer $token")
            .add("Accept", "application/vnd.github+json")
            .build()
    }

    suspend fun getRepositoryFiles(
        repo: String
    ): List<FileInfo> = withContext(Dispatchers.IO) {

        val request =
            Request.Builder()
                .url("https://api.github.com/repos/$repo/git/trees/main")
                .headers(headers)
                .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            throw Exception(
                "GitHub API错误\n" +
                "HTTP状态: ${response.code}\n" +
                "返回内容: ${errorBody ?: "空"}"
            )
        }

        val body = response.body?.string()
            ?: throw Exception("GitHub API 返回空响应体")

        val json = JSONObject(body)
        val array = json.getJSONArray("tree")
        val list = mutableListOf<FileInfo>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            if (
                item.getString("type") == "blob" ||
                item.getString("type") == "tree"
            ) {
                list.add(
                    FileInfo(
                        item.getString("path"),
                        item.getString("sha"),
                        item.getString("type")
                    )
                )
            }
        }

        list
    }


    suspend fun clearRepository(
        owner: String,
        repo: String,
        onProgress: suspend (String) -> Unit,
        onProgressPercent: suspend (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {

        // 获取当前仓库文件树（递归）
        val repoInfoRequest = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1")
            .headers(headers)
            .build()

        val repoInfoResponse = client.newCall(repoInfoRequest).execute()

        if (!repoInfoResponse.isSuccessful) {
            throw Exception("获取仓库树失败 HTTP:${repoInfoResponse.code}")
        }

        val treeJson = JSONObject(repoInfoResponse.body!!.string())
        val treeArray = treeJson.getJSONArray("tree")

        if (treeArray.length() == 0) {
            onProgress("仓库已经为空")
            return@withContext
        }

        // 分类：blob 和 tree（按路径深度排序，叶子节点在前）
        val blobs = mutableListOf<JSONObject>()
        val trees = mutableListOf<JSONObject>()

        for (i in 0 until treeArray.length()) {
            val item = treeArray.getJSONObject(i)
            when (item.getString("type")) {
                "blob" -> blobs.add(item)
                "tree" -> trees.add(item)
            }
        }

        // 按路径深度降序排列 trees（深层子目录先删除）
        trees.sortByDescending { it.getString("path").count { c -> c == '/' } }

        val totalItems = blobs.size + trees.size
        var completed = 0

        // 先删除所有 blob
        blobs.forEach { item ->
            val path = item.getString("path")
            val sha = item.getString("sha")

            val deleteRequestBody = JSONObject().apply {
                put("message", "delete $path")
                put("sha", sha)
                put("branch", "main")
            }

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents/$path")
                .headers(headers)
                .delete(
                    deleteRequestBody.toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                onProgress("✅ 删除文件: $path")
            } else {
                val error = response.body?.string()
                throw Exception("删除失败 $path HTTP:${response.code}\n$error")
            }

            completed++
            onProgressPercent((completed.toFloat() / totalItems * 100).toInt())
        }

        // 再删除所有 tree（空目录）
        trees.forEach { item ->
            val path = item.getString("path")
            val sha = item.getString("sha")

            val deleteRequestBody = JSONObject().apply {
                put("message", "delete directory $path")
                put("sha", sha)
                put("branch", "main")
            }

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents/$path")
                .headers(headers)
                .delete(
                    deleteRequestBody.toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                onProgress("✅ 删除目录: $path")
            } else {
                onProgress("⚠️ 删除目录失败（可能非空）: $path")
            }

            completed++
            onProgressPercent((completed.toFloat() / totalItems * 100).toInt())
        }

        onProgress("GitHub仓库清空完成✅")
    }
}


data class FileInfo(
    val path: String,
    val sha: String,
    val type: String
)