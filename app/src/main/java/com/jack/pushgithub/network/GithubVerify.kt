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



                val emailRequest =
                    Request.Builder()
                        .url(
                            "https://api.github.com/user/emails"
                        )
                        .header(
                            "Authorization",
                            "Bearer $token"
                        )
                        .build()



                val emailResponse =
                    client.newCall(
                        emailRequest
                    ).execute()



                if(emailMatched){

        log("邮箱验证通过")

    }else{

        log("未找到邮箱信息，跳过邮箱验证")

    }



                val emailsJson =
    emailResponse.body!!
        .string()


val emailArray =
    org.json.JSONArray(emailsJson)


var emailMatched = false


for(i in 0 until emailArray.length()){

    val item =
        emailArray.getJSONObject(i)


    if(item.optString("email") == email){

        emailMatched = true
        break

    }

}



if(!emailMatched){

    log("邮箱不一致")

    return@withContext false

}



                log("邮箱验证通过")


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
