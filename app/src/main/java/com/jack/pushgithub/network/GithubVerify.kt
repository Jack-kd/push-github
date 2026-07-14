package com.jack.pushgithub.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


object GithubVerify {


    suspend fun verify(
        username:String,
        email:String,
        token:String,
        log:(String)->Unit
    ):Boolean {


        return withContext(Dispatchers.IO){


            try {


                log("正在连接 GitHub...")


                val client = OkHttpClient()


                val request = Request.Builder()
                    .url("https://api.github.com/user")
                    .header(
                        "Authorization",
                        "Bearer $token"
                    )
                    .build()



                val response =
                    client.newCall(request).execute()



                if(!response.isSuccessful){

                    log("Token验证失败")
                    return@withContext false
                }



                log("Token有效")



                val body: String =
             response.body?.string()
            ?: return@withContext false


                val json =
                JSONObject(body)



                val githubName =
                    json.optString("login")



                log(
                    "GitHub用户名: $githubName"
                )



                if(githubName != username){

                    log("用户名不一致")

                    return@withContext false
                }



                log("用户名验证通过")



                log("邮箱信息检查跳过")
                log("GitHub 不强制验证邮箱")




                log("========== 验证完成 ==========")


                true


            }catch(e:Exception){


                log(
                    "错误:${e.message}"
                )


                false
            }

        }

    }

}
