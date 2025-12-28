package com.example.deepseekaiassistant

import android.content.Context
import android.content.SharedPreferences

object DeepSeekConfig {
    // DeepSeek API 基础URL
    const val BASE_URL = "https://api.deepseek.com/"
    
    // 默认 API Key（用户可在设置中替换）
    private const val DEFAULT_API_KEY = ""
    
    // 模型名称
    const val MODEL_NAME = "deepseek-chat"
    
    private const val PREF_NAME = "deepseek_config"
    private const val KEY_API_KEY = "api_key"
    
    /**
     * 获取 API Key（优先使用用户配置的）
     */
    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }
    
    /**
     * 保存 API Key
     */
    fun setApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }
    
    /**
     * 检查是否已配置 API Key
     */
    fun hasApiKey(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }
    
    // 兼容旧代码的属性（建议使用 getApiKey(context) 替代）
    @Deprecated("Use getApiKey(context) instead")
    val API_KEY: String
        get() = DEFAULT_API_KEY
}