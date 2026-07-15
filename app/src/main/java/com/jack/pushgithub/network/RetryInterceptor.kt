package com.jack.pushgithub.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val retryDelayMs: Long = 1000
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var lastException: IOException? = null
        for (attempt in 0..maxRetries) {
            try {
                val response = chain.proceed(chain.request())
                if (response.isSuccessful || attempt == maxRetries) {
                    return response
                }
                // 服务端错误时重试
                if (response.code in 500..599) {
                    response.close()
                    Thread.sleep(retryDelayMs * (attempt + 1))
                    continue
                }
                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    Thread.sleep(retryDelayMs * (attempt + 1))
                }
            }
        }
        throw lastException ?: IOException("请求失败，已重试 $maxRetries 次")
    }
}