package com.jack.pushgithub.github

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
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                30,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                30,
                TimeUnit.SECONDS
            )
            .build()

    fun getRepositoryFiles(
        repo: String
    ): List<FileInfo> {

        val request =
            Request.Builder()
                .url(
                    "https://api.github.com/repos/$repo/git/trees/main"
                )
                .addHeader(
                    "Authorization",
                    "Bearer $token"
                )
                .build()



        val response =
            client.newCall(request)
                .execute()

        if (!response.isSuccessful) {

            val errorBody =
                response.body?.string()


            throw Exception(
                "GitHub API错误\n" +
                "HTTP状态: ${response.code}\n" +
                "返回内容: ${errorBody ?: "空"}"
            )

        }


        val body =
            response
                .body!!
                .string()



        val json =
            JSONObject(body)




        val array =
            json.getJSONArray("tree")




        val list =
            mutableListOf<FileInfo>()




        for (i in 0 until array.length()) {


            val item =
                array.getJSONObject(i)



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


        return list

    }


    suspend fun clearRepository(
    owner: String,
    repo: String,
    token: String,
    onProgress: suspend (String) -> Unit,
    onProgressPercent: suspend (Int) -> Unit = {}
) {

    val client = OkHttpClient()

    val headers = Headers.Builder()
        .add("Authorization", "Bearer $token")
        .add("Accept", "application/vnd.github+json")
        .build()


    // 获取当前仓库文件树
    val repoInfoRequest = Request.Builder()
        .url("https://api.github.com/repos/$owner/$repo/git/trees/main?recursive=1")
        .headers(headers)
        .build()

    val repoInfoResponse = client.newCall(repoInfoRequest)
        .execute()

    if (!repoInfoResponse.isSuccessful) {
        throw Exception("获取仓库树失败 HTTP:${repoInfoResponse.code}")
    }


    val treeJson = JSONObject(repoInfoResponse.body!!.string())

    val treeArray = treeJson.getJSONArray("tree")


    if (treeArray.length() == 0) {
        onProgress("仓库已经为空")
        return
    }


    // 逐个删除文件，只删除 blob，跳过目录 tree
    val blobFiles = mutableListOf<JSONObject>()
    for (i in 0 until treeArray.length()) {
        val item = treeArray.getJSONObject(i)
        if (item.getString("type") == "blob") {
            blobFiles.add(item)
        }
    }

    blobFiles.forEachIndexed { index, item ->
        val path = item.getString("path")
        val sha = item.getString("sha")


        val deleteRequestBody = JSONObject().apply {
            put("message", "delete $path")
            put("sha", sha)
            put("branch", "main")
        }


        val request = Request.Builder()
            .url(
                "https://api.github.com/repos/$owner/$repo/contents/$path"
            )
            .headers(headers)
            .delete(
                deleteRequestBody.toString()
                    .toRequestBody(
                        "application/json".toMediaType()
                    )
            )
            .build()


        val response = client.newCall(request).execute()


        if (response.isSuccessful) {

            onProgress("✅删除成功: $path")

        } else {

            val error = response.body?.string()

            throw Exception(
                "删除失败 $path HTTP:${response.code}\n$error"
            )
        }

        val percent = ((index + 1).toFloat() / blobFiles.size * 100).toInt()
        onProgressPercent(percent)
    }


    onProgress("GitHub仓库清空完成✅")
}

}


data class FileInfo(
    val path: String,
    val sha: String,
    val type: String
)
