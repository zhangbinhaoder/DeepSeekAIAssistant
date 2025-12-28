package com.example.deepseekaiassistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * API 诊断管理器
 * 用于测试 API 连接、验证配置、显示实时日志
 */
object DiagnosticManager {
    
    // 日志列表
    private val logList = mutableListOf<LogEntry>()
    private val logListeners = mutableListOf<(LogEntry) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * 日志条目
     */
    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val details: String? = null
    ) {
        override fun toString(): String {
            val detailStr = if (details != null) "\n  $details" else ""
            return "[$timestamp] [$level] [$tag] $message$detailStr"
        }
    }
    
    enum class LogLevel {
        INFO, SUCCESS, WARNING, ERROR, DEBUG
    }
    
    /**
     * 添加日志监听器
     */
    fun addLogListener(listener: (LogEntry) -> Unit) {
        logListeners.add(listener)
    }
    
    /**
     * 移除日志监听器
     */
    fun removeLogListener(listener: (LogEntry) -> Unit) {
        logListeners.remove(listener)
    }
    
    /**
     * 记录日志
     */
    fun log(level: LogLevel, tag: String, message: String, details: String? = null) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level,
            tag = tag,
            message = message,
            details = details
        )
        
        synchronized(logList) {
            logList.add(entry)
            // 保持最近 200 条日志
            if (logList.size > 200) {
                logList.removeAt(0)
            }
        }
        
        // 在主线程通知监听器
        mainHandler.post {
            logListeners.forEach { it(entry) }
        }
    }
    
    fun info(tag: String, message: String, details: String? = null) = log(LogLevel.INFO, tag, message, details)
    fun success(tag: String, message: String, details: String? = null) = log(LogLevel.SUCCESS, tag, message, details)
    fun warning(tag: String, message: String, details: String? = null) = log(LogLevel.WARNING, tag, message, details)
    fun error(tag: String, message: String, details: String? = null) = log(LogLevel.ERROR, tag, message, details)
    fun debug(tag: String, message: String, details: String? = null) = log(LogLevel.DEBUG, tag, message, details)
    
    /**
     * 获取所有日志
     */
    fun getAllLogs(): List<LogEntry> {
        synchronized(logList) {
            return logList.toList()
        }
    }
    
    /**
     * 清空日志
     */
    fun clearLogs() {
        synchronized(logList) {
            logList.clear()
        }
    }
    
    /**
     * 诊断结果
     */
    data class DiagnosticResult(
        val success: Boolean,
        val providerName: String,
        val baseUrl: String,
        val model: String,
        val responseTime: Long,
        val statusCode: Int?,
        val errorMessage: String?,
        val responseBody: String?,
        val details: Map<String, String> = emptyMap()
    )
    
    /**
     * 执行 API 诊断测试
     */
    fun runDiagnostic(
        context: Context,
        onProgress: (String) -> Unit,
        onComplete: (DiagnosticResult) -> Unit
    ) {
        val config = AIConfigManager.getCurrentConfig(context)
        
        info("Diagnostic", "========== 开始 API 诊断 ==========")
        info("Diagnostic", "提供商: ${config.provider.displayName}")
        info("Diagnostic", "Base URL: ${config.baseUrl}")
        info("Diagnostic", "模型: ${config.model}")
        info("Diagnostic", "API Key: ${if (config.apiKey.isNotEmpty()) "${config.apiKey.take(8)}...${config.apiKey.takeLast(4)}" else "未配置"}")
        
        onProgress("正在检查配置...")
        
        // 检查配置
        if (config.apiKey.isEmpty()) {
            error("Diagnostic", "API Key 未配置")
            onComplete(DiagnosticResult(
                success = false,
                providerName = config.provider.displayName,
                baseUrl = config.baseUrl,
                model = config.model,
                responseTime = 0,
                statusCode = null,
                errorMessage = "API Key 未配置，请在设置中输入 API Key",
                responseBody = null
            ))
            return
        }
        
        if (config.baseUrl.isEmpty()) {
            error("Diagnostic", "Base URL 未配置")
            onComplete(DiagnosticResult(
                success = false,
                providerName = config.provider.displayName,
                baseUrl = config.baseUrl,
                model = config.model,
                responseTime = 0,
                statusCode = null,
                errorMessage = "Base URL 未配置",
                responseBody = null
            ))
            return
        }
        
        onProgress("正在连接服务器...")
        info("Diagnostic", "发送测试请求...")
        
        // 构建测试请求
        val testMessage = JSONObject().apply {
            put("model", config.model)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Hi")
                })
            })
            put("max_tokens", 10)
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = testMessage.toString().toRequestBody(mediaType)
        
        val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
        debug("Diagnostic", "请求 URL: $url")
        debug("Diagnostic", "请求体: ${testMessage.toString(2)}")
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        val startTime = System.currentTimeMillis()
        
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                val responseTime = System.currentTimeMillis() - startTime
                error("Diagnostic", "网络请求失败: ${e.message}")
                error("Diagnostic", "异常类型: ${e.javaClass.simpleName}")
                
                val errorMsg = when {
                    e.message?.contains("Unable to resolve host") == true -> 
                        "无法解析域名，请检查网络连接或 Base URL 是否正确"
                    e.message?.contains("Connection refused") == true ->
                        "连接被拒绝，服务器可能不可用"
                    e.message?.contains("timeout") == true ->
                        "连接超时，请检查网络状况"
                    e.message?.contains("SSL") == true || e.message?.contains("Certificate") == true ->
                        "SSL 证书错误，请检查网络环境"
                    else -> "网络错误: ${e.message}"
                }
                
                mainHandler.post {
                    onComplete(DiagnosticResult(
                        success = false,
                        providerName = config.provider.displayName,
                        baseUrl = config.baseUrl,
                        model = config.model,
                        responseTime = responseTime,
                        statusCode = null,
                        errorMessage = errorMsg,
                        responseBody = null,
                        details = mapOf("exception" to (e.message ?: "Unknown"))
                    ))
                }
            }
            
            override fun onResponse(call: Call, response: okhttp3.Response) {
                val responseTime = System.currentTimeMillis() - startTime
                val statusCode = response.code
                val responseBody = response.body?.string()
                
                info("Diagnostic", "响应状态码: $statusCode")
                info("Diagnostic", "响应时间: ${responseTime}ms")
                debug("Diagnostic", "响应体: ${responseBody?.take(500) ?: "null"}")
                
                val isSuccess = response.isSuccessful
                val errorMessage = if (!isSuccess) {
                    parseErrorMessage(statusCode, responseBody, config.provider.displayName)
                } else {
                    null
                }
                
                if (isSuccess) {
                    success("Diagnostic", "✓ API 连接成功!")
                } else {
                    error("Diagnostic", "✗ API 请求失败: $errorMessage")
                }
                
                info("Diagnostic", "========== 诊断完成 ==========")
                
                mainHandler.post {
                    onComplete(DiagnosticResult(
                        success = isSuccess,
                        providerName = config.provider.displayName,
                        baseUrl = config.baseUrl,
                        model = config.model,
                        responseTime = responseTime,
                        statusCode = statusCode,
                        errorMessage = errorMessage,
                        responseBody = responseBody
                    ))
                }
            }
        })
    }
    
    /**
     * 解析错误信息
     */
    private fun parseErrorMessage(statusCode: Int, responseBody: String?, providerName: String): String {
        // 尝试从响应体解析错误信息
        val apiError = try {
            responseBody?.let {
                val json = JSONObject(it)
                json.optJSONObject("error")?.optString("message")
                    ?: json.optString("message")
                    ?: json.optString("msg")
            }
        } catch (e: Exception) {
            null
        }
        
        return when (statusCode) {
            400 -> "请求格式错误: ${apiError ?: "检查请求参数"}"
            401 -> "API Key 无效或已过期\n请检查 $providerName 的 API Key 是否正确"
            402 -> "账户余额不足\n请前往 $providerName 控制台充值"
            403 -> "访问被禁止: ${apiError ?: "API Key 可能没有访问权限"}"
            404 -> "API 端点不存在: ${apiError ?: "检查 Base URL 或模型名称"}"
            429 -> "请求频率超限\n${apiError ?: "请稍后重试或检查账户配额"}"
            500 -> "$providerName 服务器内部错误"
            502 -> "$providerName 网关错误，服务可能暂时不可用"
            503 -> "$providerName 服务暂时不可用，请稍后重试"
            else -> "HTTP $statusCode: ${apiError ?: responseBody?.take(100) ?: "未知错误"}"
        }
    }
    
    /**
     * 快速连通性测试（只测试网络，不发 API 请求）
     */
    fun testConnectivity(
        context: Context,
        onComplete: (Boolean, String) -> Unit
    ) {
        val config = AIConfigManager.getCurrentConfig(context)
        
        if (config.baseUrl.isEmpty()) {
            onComplete(false, "Base URL 未配置")
            return
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
        
        // 只做 HEAD 请求测试连通性
        val request = Request.Builder()
            .url(config.baseUrl)
            .head()
            .build()
        
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    onComplete(false, "无法连接: ${e.message}")
                }
            }
            
            override fun onResponse(call: Call, response: okhttp3.Response) {
                mainHandler.post {
                    onComplete(true, "服务器可达 (${response.code})")
                }
            }
        })
    }
}
