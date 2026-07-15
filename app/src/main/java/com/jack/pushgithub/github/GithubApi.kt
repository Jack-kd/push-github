package com.jack.pushgithub.github

import com.jack.pushgithub.network.RetryInterceptor
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
            .addInterceptor(RetryInterceptor())
            .build()

    private val headers: Headers by lazy {
        Headers.Builder()
            .add("Authorization", "Bearer $token")
            .add("Accept", "application/vnd.github+json")
            .build()
    }

    /**
     * 获取仓库的默认分支名
     */
    suspend fun getDefaultBranch(owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo")
                .headers(headers)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("获取仓库信息失败 HTTP:${response.code}")
            }
            val body = response.body?.string()
                ?: throw Exception("GitHub API 返回空响应体")
            JSONObject(body).optString("default_branch", "main")
        } catch (e: Exception) {
            "main"
        }
    }

    /**
     * 获取仓库的提交历史
     */
    suspend fun getCommitHistory(
        owner: String,
        repo: String,
        branch: String,
        count: Int = 20
    ): List<CommitInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/commits?sha=$branch&per_page=$count")
            .headers(headers)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("获取提交历史失败 HTTP:${response.code}")
        }
        val body = response.body?.string()
            ?: throw Exception("GitHub API 返回空响应体")

        val jsonArray = org.json.JSONArray(body)
        val list = mutableListOf<CommitInfo>()

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val sha = item.getString("sha")
            val commit = item.getJSONObject("commit")
            val message = commit.getString("message")
            val author = commit.getJSONObject("author").getString("name")
            val date = commit.getJSONObject("author").getString("date")

            list.add(CommitInfo(sha, message, author, date))
        }

        list
    }

    suspend fun getRepositoryFiles(
        repo: String
    ): List<FileInfo> = withContext(Dispatchers.IO) {

        val parts = repo.split("/")
        val owner = parts.getOrNull(0) ?: ""
        val repoName = parts.getOrNull(1) ?: ""
        val defaultBranch = if (owner.isNotBlank() && repoName.isNotBlank()) {
            getDefaultBranch(owner, repoName)
        } else {
            "main"
        }

        val request =
            Request.Builder()
                .url("https://api.github.com/repos/$repo/git/trees/$defaultBranch")
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


    /**
     * 快速清空仓库（Git Tree API），会留下 .gitkeep 但速度快
     */
    suspend fun clearRepositoryFast(
        owner: String,
        repo: String,
        onProgress: suspend (String) -> Unit,
        onProgressPercent: suspend (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {

        onProgress("正在获取仓库信息...")
        onProgressPercent(5)

        val defaultBranch = getDefaultBranch(owner, repo)
        onProgress("默认分支: $defaultBranch")

        // 1. 获取最新 commit
        onProgress("正在获取最新提交...")
        onProgressPercent(15)

        val branchRequest = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/refs/heads/$defaultBranch")
            .headers(headers)
            .build()
        val branchResponse = client.newCall(branchRequest).execute()
        if (!branchResponse.isSuccessful) {
            val body = branchResponse.body?.string()
            throw Exception("获取分支引用失败 HTTP:${branchResponse.code}\n$body")
        }
        val branchJson = JSONObject(branchResponse.body?.string() ?: throw Exception("空响应"))
        val latestCommitSha = branchJson.getJSONObject("object").getString("sha")
        onProgress("最新提交 SHA: ${latestCommitSha.take(7)}")
        onProgressPercent(30)

        // 2. 创建空 blob
        onProgress("正在创建空仓库树...")
        val createBlobBody = JSONObject().apply {
            put("content", "")
            put("encoding", "utf-8")
        }
        val blobRequest = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/blobs")
            .headers(headers)
            .post(createBlobBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val blobResponse = client.newCall(blobRequest).execute()
        if (!blobResponse.isSuccessful) {
            val body = blobResponse.body?.string()
            throw Exception("创建 blob 失败 HTTP:${blobResponse.code}\n$body")
        }
        val blobSha = JSONObject(blobResponse.body?.string() ?: throw Exception("空响应")).getString("sha")
        onProgressPercent(50)

        // 3. 创建 tree（.gitkeep 占位）
        val treeEntry = JSONObject().apply {
            put("path", ".gitkeep")
            put("mode", "100644")
            put("type", "blob")
            put("sha", blobSha)
        }
        val createTreeBody = JSONObject().apply {
            put("tree", org.json.JSONArray().put(treeEntry))
        }
        val treeRequest = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/trees")
            .headers(headers)
            .post(createTreeBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val treeResponse = client.newCall(treeRequest).execute()
        if (!treeResponse.isSuccessful) {
            val body = treeResponse.body?.string()
            throw Exception("创建树失败 HTTP:${treeResponse.code}\n$body")
        }
        val newTreeSha = JSONObject(treeResponse.body?.string() ?: throw Exception("空响应")).getString("sha")
        onProgress("新树 SHA: ${newTreeSha.take(7)}")
        onProgressPercent(65)

        // 4. 创建 commit
        onProgress("正在创建清空提交...")
        val createCommitBody = JSONObject().apply {
            put("message", "清空仓库")
            put("tree", newTreeSha)
            put("parents", org.json.JSONArray().put(latestCommitSha))
        }
        val createCommitRequest = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/commits")
            .headers(headers)
            .post(createCommitBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val createCommitResponse = client.newCall(createCommitRequest).execute()
        if (!createCommitResponse.isSuccessful) {
            val body = createCommitResponse.body?.string()
            throw Exception("创建提交失败 HTTP:${createCommitResponse.code}\n$body")
        }
        val newCommitSha = JSONObject(createCommitResponse.body?.string() ?: throw Exception("空响应")).getString("sha")
        onProgress("新提交 SHA: ${newCommitSha.take(7)}")
        onProgressPercent(85)

        // 5. 更新分支引用
        onProgress("正在更新分支引用...")
        val updateRefBody = JSONObject().apply {
            put("sha", newCommitSha)
            put("force", false)
        }
        val updateRefRequest = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/refs/heads/$defaultBranch")
            .headers(headers)
            .patch(updateRefBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val updateRefResponse = client.newCall(updateRefRequest).execute()
        if (!updateRefResponse.isSuccessful) {
            val body = updateRefResponse.body?.string()
            throw Exception("更新分支引用失败 HTTP:${updateRefResponse.code}\n$body")
        }

        onProgress("GitHub仓库清空完成✅（保留 .gitkeep）")
        onProgressPercent(100)
    }

    /**
     * 获取仓库文件列表
     */
    suspend fun getRepositoryFiles(
        owner: String,
        repo: String,
        onProgress: suspend (String) -> Unit
    ): List<FileInfo> = withContext(Dispatchers.IO) {
        val defaultBranch = getDefaultBranch(owner, repo)
        onProgress("默认分支: $defaultBranch")

        val treeRequest = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/git/trees/$defaultBranch?recursive=1")
            .headers(headers)
            .build()

        val treeResponse = client.newCall(treeRequest).execute()
        if (!treeResponse.isSuccessful) {
            val body = treeResponse.body?.string()
            throw Exception("获取仓库树失败 HTTP:${treeResponse.code}\n$body")
        }

        val treeJson = JSONObject(treeResponse.body!!.string())
        val treeArray = treeJson.getJSONArray("tree")
        val list = mutableListOf<FileInfo>()
        for (i in 0 until treeArray.length()) {
            val item = treeArray.getJSONObject(i)
            if (item.getString("type") == "blob" || item.getString("type") == "tree") {
                list.add(FileInfo(
                    item.getString("path"),
                    item.getString("sha"),
                    item.getString("type")
                ))
            }
        }
        list
    }

    /**
     * 删除指定文件列表
     */
    suspend fun deleteFiles(
        owner: String,
        repo: String,
        files: List<FileInfo>,
        onProgress: suspend (String) -> Unit,
        onProgressPercent: suspend (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val defaultBranch = getDefaultBranch(owner, repo)
        onProgress("共 ${files.size} 个文件待删除")

        files.forEachIndexed { index, file ->
            val deleteBody = JSONObject().apply {
                put("message", "delete ${file.path}")
                put("sha", file.sha)
                put("branch", defaultBranch)
            }

            val deleteRequest = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents/${file.path}")
                .headers(headers)
                .delete(deleteBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val deleteResponse = client.newCall(deleteRequest).execute()
            if (!deleteResponse.isSuccessful) {
                val error = deleteResponse.body?.string()
                throw Exception("删除失败 ${file.path} HTTP:${deleteResponse.code}\n$error")
            }

            onProgress("✅ 已删除: ${file.path}")
            val percent = ((index + 1).toFloat() / files.size * 100).toInt()
            onProgressPercent(percent)
        }

        onProgress("删除完成✅")
        onProgressPercent(100)
    }
}


data class CommitInfo(
    val sha: String,
    val message: String,
    val author: String,
    val date: String
)

data class FileInfo(
    val path: String,
    val sha: String,
    val type: String
)