package com.example.deepseekaiassistant

import android.content.Context

/**
 * AI 服务提供商枚举
 */
enum class AIProvider(
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val models: List<String>,
    val description: String
) {
    DEEPSEEK(
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/",
        defaultModel = "deepseek-chat",
        models = listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner"),
        description = "深度求索，国产大模型"
    ),
    
    MOONSHOT(
        displayName = "Kimi (月之暗面)",
        baseUrl = "https://api.moonshot.cn/v1/",
        defaultModel = "moonshot-v1-8k",
        models = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
        description = "Kimi 智能助手"
    ),
    
    OPENAI(
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1/",
        defaultModel = "gpt-3.5-turbo",
        models = listOf("gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o", "gpt-4o-mini"),
        description = "ChatGPT 官方 API"
    ),
    
    ZHIPU(
        displayName = "智谱 AI (ChatGLM)",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
        defaultModel = "glm-4-flash",
        models = listOf("glm-4-flash", "glm-4", "glm-4-plus", "glm-4v"),
        description = "智谱清言，国产大模型"
    ),
    
    QWEN(
        displayName = "通义千问",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
        defaultModel = "qwen-turbo",
        models = listOf("qwen-turbo", "qwen-plus", "qwen-max", "qwen-long"),
        description = "阿里通义千问"
    ),
    
    BAICHUAN(
        displayName = "百川智能",
        baseUrl = "https://api.baichuan-ai.com/v1/",
        defaultModel = "Baichuan4",
        models = listOf("Baichuan4", "Baichuan3-Turbo", "Baichuan3-Turbo-128k"),
        description = "百川大模型"
    ),
    
    YI(
        displayName = "零一万物 (Yi)",
        baseUrl = "https://api.lingyiwanwu.com/v1/",
        defaultModel = "yi-lightning",
        models = listOf("yi-lightning", "yi-large", "yi-medium", "yi-spark"),
        description = "Yi 大模型"
    ),
    
    SILICONFLOW(
        displayName = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1/",
        defaultModel = "Qwen/Qwen2.5-7B-Instruct",
        models = listOf(
            "Qwen/Qwen2.5-7B-Instruct",
            "Qwen/Qwen2.5-72B-Instruct",
            "deepseek-ai/DeepSeek-V3",
            "Pro/deepseek-ai/DeepSeek-R1"
        ),
        description = "硅基流动，聚合多种模型"
    ),
    
    CUSTOM(
        displayName = "自定义 API",
        baseUrl = "",
        defaultModel = "",
        models = emptyList(),
        description = "自定义 OpenAI 兼容接口"
    );
    
    companion object {
        fun fromName(name: String): AIProvider {
            return values().find { it.name == name } ?: DEEPSEEK
        }
    }
}

/**
 * AI 配置管理器
 */
object AIConfigManager {
    private const val PREF_NAME = "ai_config"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_API_KEY_PREFIX = "api_key_"
    private const val KEY_MODEL_PREFIX = "model_"
    private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
    private const val KEY_CUSTOM_MODEL = "custom_model"
    
    /**
     * 获取当前选择的提供商
     */
    fun getCurrentProvider(context: Context): AIProvider {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val providerName = prefs.getString(KEY_PROVIDER, AIProvider.DEEPSEEK.name)
        return AIProvider.fromName(providerName ?: AIProvider.DEEPSEEK.name)
    }
    
    /**
     * 设置当前提供商
     */
    fun setCurrentProvider(context: Context, provider: AIProvider) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROVIDER, provider.name).apply()
    }
    
    /**
     * 获取指定提供商的 API Key
     */
    fun getApiKey(context: Context, provider: AIProvider = getCurrentProvider(context)): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY_PREFIX + provider.name, "") ?: ""
    }
    
    /**
     * 设置指定提供商的 API Key
     */
    fun setApiKey(context: Context, apiKey: String, provider: AIProvider = getCurrentProvider(context)) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY_PREFIX + provider.name, apiKey.trim()).apply()
    }
    
    /**
     * 获取指定提供商的模型
     */
    fun getModel(context: Context, provider: AIProvider = getCurrentProvider(context)): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MODEL_PREFIX + provider.name, provider.defaultModel) ?: provider.defaultModel
    }
    
    /**
     * 设置指定提供商的模型
     */
    fun setModel(context: Context, model: String, provider: AIProvider = getCurrentProvider(context)) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MODEL_PREFIX + provider.name, model).apply()
    }
    
    /**
     * 获取自定义 Base URL
     */
    fun getCustomBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_BASE_URL, "") ?: ""
    }
    
    /**
     * 设置自定义 Base URL
     */
    fun setCustomBaseUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_BASE_URL, url.trim()).apply()
    }
    
    /**
     * 获取当前提供商的 Base URL
     */
    fun getBaseUrl(context: Context): String {
        val provider = getCurrentProvider(context)
        return if (provider == AIProvider.CUSTOM) {
            getCustomBaseUrl(context)
        } else {
            provider.baseUrl
        }
    }
    
    /**
     * 检查当前提供商是否已配置 API Key
     */
    fun hasApiKey(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }
    
    /**
     * 获取当前配置的完整信息
     */
    fun getCurrentConfig(context: Context): AIConfig {
        val provider = getCurrentProvider(context)
        return AIConfig(
            provider = provider,
            apiKey = getApiKey(context, provider),
            model = getModel(context, provider),
            baseUrl = getBaseUrl(context)
        )
    }
}

/**
 * AI 配置数据类
 */
data class AIConfig(
    val provider: AIProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String
)
