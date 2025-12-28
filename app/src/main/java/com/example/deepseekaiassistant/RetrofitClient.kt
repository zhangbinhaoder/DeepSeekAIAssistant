package com.example.deepseekaiassistant

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String = ""
    
    // OkHttp 客户端（共享）
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 获取指定 Base URL 的 Retrofit 实例
     */
    private fun getRetrofitInstance(baseUrl: String): Retrofit {
        // 如果 Base URL 改变了，重新创建 Retrofit
        if (retrofit == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    /**
     * 获取 AI API 服务（使用当前配置的提供商）
     */
    fun getApiService(context: Context): DeepSeekApiService {
        val baseUrl = AIConfigManager.getBaseUrl(context)
        return getRetrofitInstance(baseUrl).create(DeepSeekApiService::class.java)
    }
    
    /**
     * 获取指定 Base URL 的 API 服务
     */
    fun getApiService(baseUrl: String): DeepSeekApiService {
        return getRetrofitInstance(baseUrl).create(DeepSeekApiService::class.java)
    }
    
    /**
     * 旧版兼容方法
     */
    @Deprecated("Use getApiService(context) instead")
    fun getDeepSeekApiService(): DeepSeekApiService {
        return getRetrofitInstance(DeepSeekConfig.BASE_URL).create(DeepSeekApiService::class.java)
    }
    
    /**
     * 清除缓存，强制重新创建 Retrofit 实例
     */
    fun clearCache() {
        retrofit = null
        currentBaseUrl = ""
    }
}