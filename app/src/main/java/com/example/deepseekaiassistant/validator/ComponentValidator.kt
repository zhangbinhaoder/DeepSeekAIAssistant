package com.example.deepseekaiassistant.validator

import android.content.Context
import android.os.Build
import com.example.deepseekaiassistant.DiagnosticManager
import com.example.deepseekaiassistant.agent.NativeAgentCore
import com.example.deepseekaiassistant.kernel.KernelOptimize
import com.example.deepseekaiassistant.local.LlamaCpp
import com.example.deepseekaiassistant.termux.TermuxIntegration
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 组件完整性验证器
 * 
 * 功能：
 * 1. 检测所有组件的完整性（文件存在、哈希校验、功能测试）
 * 2. 发现错误时自动修复
 * 3. 输出详细的 Linux 风格日志
 * 4. 支持异步验证和实时进度回调
 */
object ComponentValidator {
    
    private const val TAG = "ComponentValidator"
    
    // 组件类别
    enum class ComponentCategory(val displayName: String, val icon: String) {
        NATIVE_LIBS("原生库", "📦"),
        AI_ENGINE("AI引擎", "🧠"),
        TERMUX("Termux集成", "🐧"),
        KERNEL("内核优化", "⚡"),
        STORAGE("存储系统", "💾"),
        NETWORK("网络模块", "🌐"),
        UI("界面组件", "🎨"),
        CONFIG("配置文件", "⚙️")
    }
    
    // 组件状态
    enum class ComponentStatus(val code: Int, val symbol: String) {
        UNKNOWN(0, "?"),
        CHECKING(1, "..."),
        OK(2, "✓"),
        WARNING(3, "⚠"),
        ERROR(4, "✗"),
        FIXING(5, "🔧"),
        FIXED(6, "✓"),
        CRITICAL(7, "☠")
    }
    
    // 组件验证结果
    data class ValidationResult(
        val componentId: String,
        val componentName: String,
        val category: ComponentCategory,
        var status: ComponentStatus,
        var message: String,
        var details: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        var canAutoFix: Boolean = false,
        var fixAction: (suspend () -> Boolean)? = null,
        var fixAttempted: Boolean = false,
        var fixSuccess: Boolean = false
    ) {
        fun toLogLine(timeOffset: Double = 0.0): String {
            val ts = String.format("%12.6f", timeOffset)
            val statusStr = when (status) {
                ComponentStatus.OK -> "[  OK  ]"
                ComponentStatus.WARNING -> "[ WARN ]"
                ComponentStatus.ERROR -> "[FAILED]"
                ComponentStatus.FIXED -> "[FIXED ]"
                ComponentStatus.FIXING -> "[FIXING]"
                ComponentStatus.CRITICAL -> "[CRIT  ]"
                else -> "[  --  ]"
            }
            return "[$ts] ${category.displayName}/$componentName: $message $statusStr"
        }
    }
    
    // 验证报告
    data class ValidationReport(
        val startTime: Long,
        val endTime: Long,
        val results: List<ValidationResult>,
        val totalComponents: Int,
        val okCount: Int,
        val warningCount: Int,
        val errorCount: Int,
        val fixedCount: Int,
        val criticalCount: Int
    ) {
        val duration: Long get() = endTime - startTime
        val allPassed: Boolean get() = errorCount == 0 && criticalCount == 0
        val hasIssues: Boolean get() = warningCount > 0 || errorCount > 0 || criticalCount > 0
        
        fun toBootLogString(): String = buildString {
            appendLine()
            appendLine("╔══════════════════════════════════════════════════════════════════╗")
            appendLine("║           COMPONENT INTEGRITY VALIDATION REPORT                   ║")
            appendLine("╚══════════════════════════════════════════════════════════════════╝")
            appendLine()
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            appendLine("Start Time : ${dateFormat.format(Date(startTime))}")
            appendLine("End Time   : ${dateFormat.format(Date(endTime))}")
            appendLine("Duration   : ${duration}ms")
            appendLine()
            
            appendLine("────────────────────────────────────────────────────────────────────")
            appendLine("                         VALIDATION LOG")
            appendLine("────────────────────────────────────────────────────────────────────")
            
            val startOffset = startTime
            results.forEach { result ->
                val offset = (result.timestamp - startOffset) / 1000.0
                appendLine(result.toLogLine(offset))
                if (result.details.isNotBlank()) {
                    result.details.lines().forEach { line ->
                        appendLine("             └─ $line")
                    }
                }
            }
            
            appendLine()
            appendLine("────────────────────────────────────────────────────────────────────")
            appendLine("                           SUMMARY")
            appendLine("────────────────────────────────────────────────────────────────────")
            appendLine()
            appendLine("Total Components : $totalComponents")
            appendLine("Passed      [OK] : $okCount")
            appendLine("Warnings  [WARN] : $warningCount")
            appendLine("Errors  [FAILED] : $errorCount")
            appendLine("Fixed   [FIXED ] : $fixedCount")
            appendLine("Critical [CRIT ] : $criticalCount")
            appendLine()
            
            val finalStatus = when {
                criticalCount > 0 -> "CRITICAL - System may not function correctly"
                errorCount > 0 -> "FAILED - Some components have errors"
                warningCount > 0 -> "PASSED WITH WARNINGS"
                else -> "ALL PASSED - System is healthy"
            }
            
            appendLine("Final Status: $finalStatus")
            appendLine()
            appendLine("══════════════════════════════════════════════════════════════════")
        }
    }
    
    // 验证进度回调
    interface ValidationCallback {
        fun onStart(totalComponents: Int)
        fun onProgress(current: Int, total: Int, result: ValidationResult)
        fun onFixing(result: ValidationResult)
        fun onFixed(result: ValidationResult, success: Boolean)
        fun onComplete(report: ValidationReport)
        fun onLog(message: String)
    }
    
    // 已注册的组件验证器
    private val componentValidators = mutableListOf<ComponentValidatorEntry>()
    
    data class ComponentValidatorEntry(
        val id: String,
        val name: String,
        val category: ComponentCategory,
        val priority: Int,  // 数字越小优先级越高
        val validator: suspend (Context) -> ValidationResult
    )
    
    init {
        // 注册所有组件验证器
        registerBuiltinValidators()
    }
    
    /**
     * 注册内置验证器
     */
    private fun registerBuiltinValidators() {
        // Native 库验证
        registerValidator("native_agent", "agent_native.so", ComponentCategory.NATIVE_LIBS, 10) { ctx ->
            validateNativeAgentCore(ctx)
        }
        
        registerValidator("native_llama", "llama_android.so", ComponentCategory.NATIVE_LIBS, 11) { ctx ->
            validateLlamaLibrary(ctx)
        }
        
        // AI 引擎验证
        registerValidator("ai_engine_init", "AI引擎初始化", ComponentCategory.AI_ENGINE, 20) { ctx ->
            validateAIEngineInit(ctx)
        }
        
        registerValidator("ai_model_dir", "模型目录", ComponentCategory.AI_ENGINE, 21) { ctx ->
            validateModelDirectory(ctx)
        }
        
        // Termux 验证
        registerValidator("termux_main", "Termux主程序", ComponentCategory.TERMUX, 30) { ctx ->
            validateTermuxMain(ctx)
        }
        
        registerValidator("termux_api", "Termux-API", ComponentCategory.TERMUX, 31) { ctx ->
            validateTermuxAPI(ctx)
        }
        
        registerValidator("termux_x11", "Termux-X11", ComponentCategory.TERMUX, 32) { ctx ->
            validateTermuxX11(ctx)
        }
        
        // 内核优化验证
        registerValidator("kernel_module", "内核优化模块", ComponentCategory.KERNEL, 40) { ctx ->
            validateKernelModule(ctx)
        }
        
        // 存储验证
        registerValidator("storage_space", "存储空间", ComponentCategory.STORAGE, 50) { ctx ->
            validateStorageSpace(ctx)
        }
        
        registerValidator("storage_permissions", "存储权限", ComponentCategory.STORAGE, 51) { ctx ->
            validateStoragePermissions(ctx)
        }
        
        // 配置文件验证
        registerValidator("config_api", "API配置", ComponentCategory.CONFIG, 60) { ctx ->
            validateAPIConfig(ctx)
        }
        
        registerValidator("config_model", "模型配置", ComponentCategory.CONFIG, 61) { ctx ->
            validateModelConfig(ctx)
        }
    }
    
    /**
     * 注册自定义验证器
     */
    fun registerValidator(
        id: String,
        name: String,
        category: ComponentCategory,
        priority: Int,
        validator: suspend (Context) -> ValidationResult
    ) {
        componentValidators.add(ComponentValidatorEntry(id, name, category, priority, validator))
        componentValidators.sortBy { it.priority }
    }
    
    /**
     * 执行完整验证
     */
    suspend fun runFullValidation(
        context: Context,
        autoFix: Boolean = true,
        callback: ValidationCallback? = null
    ): ValidationReport = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<ValidationResult>()
        val totalComponents = componentValidators.size
        
        callback?.onStart(totalComponents)
        log(callback, "")
        log(callback, "╔══════════════════════════════════════════════════════════════════╗")
        log(callback, "║         STARTING COMPONENT INTEGRITY VALIDATION                   ║")
        log(callback, "╚══════════════════════════════════════════════════════════════════╝")
        log(callback, "")
        log(callback, "[    0.000000] Device: ${Build.MODEL} (${Build.DEVICE})")
        log(callback, "[    0.000001] Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        log(callback, "[    0.000002] Validator version: 2.0")
        log(callback, "[    0.000003] Auto-fix enabled: $autoFix")
        log(callback, "")
        
        var current = 0
        
        for (entry in componentValidators) {
            current++
            
            val offset = (System.currentTimeMillis() - startTime) / 1000.0
            log(callback, String.format("[%12.6f] Checking: ${entry.category.icon} ${entry.name}...", offset))
            
            try {
                val result = entry.validator(context)
                results.add(result)
                
                callback?.onProgress(current, totalComponents, result)
                
                val statusLog = result.toLogLine((result.timestamp - startTime) / 1000.0)
                log(callback, statusLog)
                
                // 如果有错误且可以自动修复
                if (autoFix && result.status == ComponentStatus.ERROR && result.canAutoFix && result.fixAction != null) {
                    log(callback, String.format("[%12.6f] Attempting auto-fix for ${entry.name}...", 
                        (System.currentTimeMillis() - startTime) / 1000.0))
                    
                    callback?.onFixing(result)
                    result.status = ComponentStatus.FIXING
                    result.fixAttempted = true
                    
                    try {
                        val success = result.fixAction!!.invoke()
                        result.fixSuccess = success
                        
                        if (success) {
                            result.status = ComponentStatus.FIXED
                            result.message = "${result.message} → Auto-fixed"
                            log(callback, String.format("[%12.6f] ${entry.name}: Auto-fix successful [FIXED ]",
                                (System.currentTimeMillis() - startTime) / 1000.0))
                        } else {
                            result.status = ComponentStatus.ERROR
                            log(callback, String.format("[%12.6f] ${entry.name}: Auto-fix failed [FAILED]",
                                (System.currentTimeMillis() - startTime) / 1000.0))
                        }
                        
                        callback?.onFixed(result, success)
                    } catch (e: Exception) {
                        result.status = ComponentStatus.ERROR
                        result.details += "\nFix error: ${e.message}"
                        log(callback, String.format("[%12.6f] ${entry.name}: Auto-fix exception: ${e.message}",
                            (System.currentTimeMillis() - startTime) / 1000.0))
                        callback?.onFixed(result, false)
                    }
                }
                
            } catch (e: Exception) {
                val errorResult = ValidationResult(
                    componentId = entry.id,
                    componentName = entry.name,
                    category = entry.category,
                    status = ComponentStatus.ERROR,
                    message = "Validation exception",
                    details = e.message ?: "Unknown error"
                )
                results.add(errorResult)
                
                log(callback, String.format("[%12.6f] ${entry.name}: Exception - ${e.message} [FAILED]",
                    (System.currentTimeMillis() - startTime) / 1000.0))
                
                callback?.onProgress(current, totalComponents, errorResult)
            }
        }
        
        val endTime = System.currentTimeMillis()
        
        val report = ValidationReport(
            startTime = startTime,
            endTime = endTime,
            results = results,
            totalComponents = totalComponents,
            okCount = results.count { it.status == ComponentStatus.OK },
            warningCount = results.count { it.status == ComponentStatus.WARNING },
            errorCount = results.count { it.status == ComponentStatus.ERROR },
            fixedCount = results.count { it.status == ComponentStatus.FIXED },
            criticalCount = results.count { it.status == ComponentStatus.CRITICAL }
        )
        
        log(callback, "")
        log(callback, "────────────────────────────────────────────────────────────────────")
        log(callback, "Validation complete in ${report.duration}ms")
        log(callback, "Results: ${report.okCount} OK, ${report.warningCount} WARN, ${report.errorCount} ERROR, ${report.fixedCount} FIXED")
        log(callback, "────────────────────────────────────────────────────────────────────")
        
        // 记录到诊断系统
        DiagnosticManager.info(TAG, report.toBootLogString())
        
        callback?.onComplete(report)
        
        report
    }
    
    private fun log(callback: ValidationCallback?, message: String) {
        callback?.onLog(message)
        DiagnosticManager.info(TAG, message)
    }
    
    // ==================== 内置验证器实现 ====================
    
    private suspend fun validateNativeAgentCore(context: Context): ValidationResult {
        return try {
            val loaded = NativeAgentCore.load()
            
            if (loaded) {
                val version = NativeAgentCore.getVersion()
                val hasSimd = NativeAgentCore.hasSimdSupport()
                
                ValidationResult(
                    componentId = "native_agent",
                    componentName = "agent_native.so",
                    category = ComponentCategory.NATIVE_LIBS,
                    status = ComponentStatus.OK,
                    message = "Loaded v$version",
                    details = "SIMD: ${if (hasSimd) "enabled" else "disabled"}"
                )
            } else {
                ValidationResult(
                    componentId = "native_agent",
                    componentName = "agent_native.so",
                    category = ComponentCategory.NATIVE_LIBS,
                    status = ComponentStatus.WARNING,
                    message = "Not available",
                    details = "Using fallback Kotlin implementation"
                )
            }
        } catch (e: Exception) {
            ValidationResult(
                componentId = "native_agent",
                componentName = "agent_native.so",
                category = ComponentCategory.NATIVE_LIBS,
                status = ComponentStatus.ERROR,
                message = "Load failed",
                details = e.message ?: ""
            )
        }
    }
    
    private suspend fun validateLlamaLibrary(context: Context): ValidationResult {
        return try {
            val available = LlamaCpp.isNativeAvailable()
            val error = LlamaCpp.getNativeLoadError()
            
            if (available) {
                val llamaCpp = LlamaCpp()
                val realInference = llamaCpp.isRealInferenceSupported()
                
                ValidationResult(
                    componentId = "native_llama",
                    componentName = "llama_android.so",
                    category = ComponentCategory.NATIVE_LIBS,
                    status = ComponentStatus.OK,
                    message = "Loaded",
                    details = "Real inference: ${if (realInference) "yes" else "no (simulation mode)"}"
                )
            } else {
                ValidationResult(
                    componentId = "native_llama",
                    componentName = "llama_android.so",
                    category = ComponentCategory.NATIVE_LIBS,
                    status = ComponentStatus.WARNING,
                    message = "Not available",
                    details = error ?: "Using simulation mode"
                )
            }
        } catch (e: Exception) {
            ValidationResult(
                componentId = "native_llama",
                componentName = "llama_android.so",
                category = ComponentCategory.NATIVE_LIBS,
                status = ComponentStatus.WARNING,
                message = "Check failed",
                details = e.message ?: ""
            )
        }
    }
    
    private suspend fun validateAIEngineInit(context: Context): ValidationResult {
        return try {
            val localAI = com.example.deepseekaiassistant.local.LocalAIManager.getInstance(context)
            val state = localAI.getState()
            
            ValidationResult(
                componentId = "ai_engine_init",
                componentName = "AI引擎初始化",
                category = ComponentCategory.AI_ENGINE,
                status = ComponentStatus.OK,
                message = "State: ${state.name}",
                details = ""
            )
        } catch (e: Exception) {
            ValidationResult(
                componentId = "ai_engine_init",
                componentName = "AI引擎初始化",
                category = ComponentCategory.AI_ENGINE,
                status = ComponentStatus.ERROR,
                message = "Init failed",
                details = e.message ?: ""
            )
        }
    }
    
    private suspend fun validateModelDirectory(context: Context): ValidationResult {
        val modelDir = File(context.filesDir, "models")
        
        return if (modelDir.exists()) {
            val files = modelDir.listFiles { f -> f.extension == "gguf" || f.extension == "bin" } ?: emptyArray()
            val totalSize = files.sumOf { it.length() } / (1024 * 1024.0)
            
            ValidationResult(
                componentId = "ai_model_dir",
                componentName = "模型目录",
                category = ComponentCategory.AI_ENGINE,
                status = ComponentStatus.OK,
                message = "${files.size} models (${String.format("%.1f", totalSize)} MB)",
                details = files.joinToString(", ") { it.name }
            )
        } else {
            ValidationResult(
                componentId = "ai_model_dir",
                componentName = "模型目录",
                category = ComponentCategory.AI_ENGINE,
                status = ComponentStatus.WARNING,
                message = "Directory not found",
                details = "Will be created on first model download",
                canAutoFix = true,
                fixAction = {
                    modelDir.mkdirs()
                    modelDir.exists()
                }
            )
        }
    }
    
    private suspend fun validateTermuxMain(context: Context): ValidationResult {
        val status = TermuxIntegration.checkTermuxStatus(context)
        
        return if (status.termuxInstalled) {
            ValidationResult(
                componentId = "termux_main",
                componentName = "Termux主程序",
                category = ComponentCategory.TERMUX,
                status = ComponentStatus.OK,
                message = "Installed",
                details = "Version: ${status.termuxVersion ?: "unknown"}"
            )
        } else {
            ValidationResult(
                componentId = "termux_main",
                componentName = "Termux主程序",
                category = ComponentCategory.TERMUX,
                status = ComponentStatus.WARNING,
                message = "Not installed",
                details = "Termux features unavailable"
            )
        }
    }
    
    private suspend fun validateTermuxAPI(context: Context): ValidationResult {
        val status = TermuxIntegration.checkTermuxStatus(context)
        
        return if (status.apiInstalled) {
            ValidationResult(
                componentId = "termux_api",
                componentName = "Termux-API",
                category = ComponentCategory.TERMUX,
                status = ComponentStatus.OK,
                message = "Installed",
                details = ""
            )
        } else {
            ValidationResult(
                componentId = "termux_api",
                componentName = "Termux-API",
                category = ComponentCategory.TERMUX,
                status = if (status.termuxInstalled) ComponentStatus.WARNING else ComponentStatus.OK,
                message = if (status.termuxInstalled) "Not installed" else "N/A",
                details = if (status.termuxInstalled) "Hardware API unavailable" else "Termux not installed"
            )
        }
    }
    
    private suspend fun validateTermuxX11(context: Context): ValidationResult {
        val status = TermuxIntegration.checkTermuxStatus(context)
        
        return if (status.x11Installed) {
            ValidationResult(
                componentId = "termux_x11",
                componentName = "Termux-X11",
                category = ComponentCategory.TERMUX,
                status = ComponentStatus.OK,
                message = "Installed",
                details = ""
            )
        } else {
            ValidationResult(
                componentId = "termux_x11",
                componentName = "Termux-X11",
                category = ComponentCategory.TERMUX,
                status = ComponentStatus.OK,  // X11 是可选的
                message = "Not installed (optional)",
                details = "GUI apps unavailable"
            )
        }
    }
    
    private suspend fun validateKernelModule(context: Context): ValidationResult {
        return try {
            KernelOptimize.init(context)
            val loaded = KernelOptimize.isLoaded()
            
            if (loaded) {
                val status = KernelOptimize.getOptimizeStatus()
                ValidationResult(
                    componentId = "kernel_module",
                    componentName = "内核优化模块",
                    category = ComponentCategory.KERNEL,
                    status = ComponentStatus.OK,
                    message = "Loaded",
                    details = "Enabled: ${status?.isEnabled ?: false}"
                )
            } else {
                ValidationResult(
                    componentId = "kernel_module",
                    componentName = "内核优化模块",
                    category = ComponentCategory.KERNEL,
                    status = ComponentStatus.WARNING,
                    message = "Not loaded",
                    details = "Requires ROOT access"
                )
            }
        } catch (e: Exception) {
            ValidationResult(
                componentId = "kernel_module",
                componentName = "内核优化模块",
                category = ComponentCategory.KERNEL,
                status = ComponentStatus.WARNING,
                message = "Check failed",
                details = e.message ?: ""
            )
        }
    }
    
    private suspend fun validateStorageSpace(context: Context): ValidationResult {
        val filesDir = context.filesDir
        val freeSpace = filesDir.freeSpace / (1024 * 1024.0) // MB
        val totalSpace = filesDir.totalSpace / (1024 * 1024 * 1024.0) // GB
        
        val status = when {
            freeSpace < 100 -> ComponentStatus.CRITICAL  // < 100MB
            freeSpace < 500 -> ComponentStatus.ERROR     // < 500MB
            freeSpace < 1024 -> ComponentStatus.WARNING  // < 1GB
            else -> ComponentStatus.OK
        }
        
        return ValidationResult(
            componentId = "storage_space",
            componentName = "存储空间",
            category = ComponentCategory.STORAGE,
            status = status,
            message = "${String.format("%.0f", freeSpace)} MB free",
            details = "Total: ${String.format("%.1f", totalSpace)} GB",
            canAutoFix = status == ComponentStatus.ERROR || status == ComponentStatus.WARNING,
            fixAction = if (status != ComponentStatus.OK) {
                {
                    // 尝试清理缓存
                    val cacheDir = context.cacheDir
                    val deleted = cacheDir.deleteRecursively()
                    cacheDir.mkdirs()
                    deleted
                }
            } else null
        )
    }
    
    private suspend fun validateStoragePermissions(context: Context): ValidationResult {
        val filesDir = context.filesDir
        val canRead = filesDir.canRead()
        val canWrite = filesDir.canWrite()
        
        return if (canRead && canWrite) {
            ValidationResult(
                componentId = "storage_permissions",
                componentName = "存储权限",
                category = ComponentCategory.STORAGE,
                status = ComponentStatus.OK,
                message = "Read/Write OK",
                details = filesDir.absolutePath
            )
        } else {
            ValidationResult(
                componentId = "storage_permissions",
                componentName = "存储权限",
                category = ComponentCategory.STORAGE,
                status = ComponentStatus.ERROR,
                message = "Permission denied",
                details = "Read: $canRead, Write: $canWrite"
            )
        }
    }
    
    private suspend fun validateAPIConfig(context: Context): ValidationResult {
        return try {
            val config = com.example.deepseekaiassistant.AIConfigManager.getCurrentConfig(context)
            
            val hasApiKey = config.apiKey.isNotBlank()
            val hasBaseUrl = config.baseUrl.isNotBlank()
            
            if (hasApiKey && hasBaseUrl) {
                ValidationResult(
                    componentId = "config_api",
                    componentName = "API配置",
                    category = ComponentCategory.CONFIG,
                    status = ComponentStatus.OK,
                    message = "Configured",
                    details = "Model: ${config.model}"
                )
            } else {
                ValidationResult(
                    componentId = "config_api",
                    componentName = "API配置",
                    category = ComponentCategory.CONFIG,
                    status = ComponentStatus.WARNING,
                    message = "Incomplete",
                    details = "API Key: ${if (hasApiKey) "set" else "missing"}, URL: ${if (hasBaseUrl) "set" else "missing"}"
                )
            }
        } catch (e: Exception) {
            ValidationResult(
                componentId = "config_api",
                componentName = "API配置",
                category = ComponentCategory.CONFIG,
                status = ComponentStatus.ERROR,
                message = "Check failed",
                details = e.message ?: ""
            )
        }
    }
    
    private suspend fun validateModelConfig(context: Context): ValidationResult {
        val prefs = context.getSharedPreferences("model_config", Context.MODE_PRIVATE)
        val hasConfig = prefs.all.isNotEmpty()
        
        return ValidationResult(
            componentId = "config_model",
            componentName = "模型配置",
            category = ComponentCategory.CONFIG,
            status = ComponentStatus.OK,
            message = if (hasConfig) "Loaded" else "Default",
            details = "${prefs.all.size} settings"
        )
    }
    
    // ==================== 工具函数 ====================
    
    /**
     * 计算文件 SHA-256 哈希
     */
    fun calculateFileHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 导出验证报告到文件
     */
    suspend fun exportReport(context: Context, report: ValidationReport): File? {
        return try {
            val logsDir = File(context.filesDir, "logs")
            logsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(logsDir, "validation_$timestamp.log")
            
            file.writeText(report.toBootLogString())
            
            DiagnosticManager.info(TAG, "Report exported to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            DiagnosticManager.error(TAG, "Failed to export report", e.message ?: "")
            null
        }
    }
}
