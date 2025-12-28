package com.example.deepseekaiassistant

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DeepSeekApiService {
    // 发送聊天请求
    @POST("chat/completions")
    fun sendChatRequest(
        @Header("Authorization") token: String, // 身份验证头
        @Body request: DeepSeekRequest // 请求体
    ): Call<DeepSeekResponse>
}