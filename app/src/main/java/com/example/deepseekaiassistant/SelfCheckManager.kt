package com.example.deepseekaiassistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.ScrollView
import android.widget.TextView
import com.example.deepseekaiassistant.local.LlamaCpp
import com.example.deepseekaiassistant.local.LocalAIManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * APP 自检管理器
 * 
 * 功能：
 * 1. 启动时自动检测各模块状态
 * 2. 自动修复常见问题
 * 3. 错误时弹出提示并记录日志
 * 
 * 检测项目：
 * - Native 库状态
 * - 模型文件完整性
 * - 配置文件有效性
 * - 存储空间
 * - 网络状态
 */
object SelfCheckManager {
    
    private const val TAG = "SelfCheck"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val isRunning = AtomicBoolean(false)
    
    // 检查结果
    data class CheckResult(
        val category: String,
        val item: String,
        val status: CheckStatus,
        val message: String,
        val canAutoFix: Boolean = false,
        val fixAction: (() -> Boolean)? = null
    )
    
    enum class CheckStatus {
        OK,         // 正常
        WARNING,    // 警告（不影响使用）
        ERROR,      // 错误（需要修复）
        FIXED       // 已自动修复
    }
    
    // 检查报告
    data class CheckReport(
        val timestamp: String,
        val results: List<CheckResult>,
        val hasErrors: Boolean,
        val hasWarnings: Boolean,
        val autoFixedCount: Int,
        val errorCount: Int,
        val warningCount: Int
    ) {
        fun toLogString(): String {
            return buildString {
                appendLine("════════════════════════════════════════")
                appendLine("     APP 自检报告 - $timestamp")
                appendLine("════════════════════════════════════════")
                appendLine()
                
                // 按类别分组
                val grouped = results.groupBy { it.category }
                grouped.forEach { (category, items) ->
                    appendLine("【$category】")
                    items.forEach { result ->
                        val icon = when (result.status) {
                            CheckStatus.OK -> "✓"
                            CheckStatus.WARNING -> "⚠"
                            CheckStatus.ERROR -> "✗"
                            CheckStatus.FIXED -> "🔧"
                        }
                        appendLine("  $icon ${result.item}: ${result.message}")
                    }
                    appendLine()
                }
                
                appendLine("────────────────────────────────────────")
                appendLine("统计: 错误 $errorCount | 警告 $warningCount | 自动修复 $autoFixedCount")
                appendLine("════════════════════════════════════════")
            }
        }
    }
    
    /**
     * 执行完整自检
     * @param context 上下文
     * @param autoFix 是否自动修复
     * @param showDialog 是否显示结果对话框
     * @param onComplete 完成回调
     */
    fun runFullCheck(
        context: Context,
        autoFix: Boolean = true,
        showDialog: Boolean = true,
        onComplete: ((CheckReport) -> Unit)? = null
    ) {
        if (!isRunning.compareAndSet(false, true)) {
            DiagnosticManager.warning(TAG, "自检正在进行中，跳过重复调用")
            return
        }
        
        DiagnosticManager.info(TAG, "========== 开始 APP 自检 ==========")
        
        executor.execute {
            try {
                val results = mutableListOf<CheckResult>()
                
                // 1. 检查 Native 库
                results.addAll(checkNativeLibrary())
                
                // 2. 检查本地 AI 模块
                results.addAll(checkLocalAI(context))
                
                // 3. 检查存储空间
                results.addAll(checkStorage(context))
                
                // 4. 检查配置文件
                results.addAll(checkConfiguration(context))
                
                // 5. 检查模型文件
                results.addAll(checkModelFiles(context))
                
                // 6. 检查数学计算模块
                results.addAll(checkMathModule())
                
                // 自动修复
                var autoFixedCount = 0
                if (autoFix) {
                    results.forEachIndexed { index, result ->
                        if (result.status == CheckStatus.ERROR && result.canAutoFix && result.fixAction != null) {
                            DiagnosticManager.info(TAG, "尝试自动修复: ${result.item}")
                            try {
                                val fixed = result.fixAction.invoke()
                                if (fixed) {
                                    // 更新结果为已修复
                                    results[index] = result.copy(
                                        status = CheckStatus.FIXED,
                                        message = "${result.message} → 已自动修复"
                                    )
                                    autoFixedCount++
                                    DiagnosticManager.success(TAG, "✓ 自动修复成功: ${result.item}")
                                }
                            } catch (e: Exception) {
                                DiagnosticManager.error(TAG, "自动修复失败: ${result.item}", e.message)
                            }
                        }
                    }
                }
                
                // 生成报告
                val errorCount = results.count { it.status == CheckStatus.ERROR }
                val warningCount = results.count { it.status == CheckStatus.WARNING }
                
                val report = CheckReport(
                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    results = results,
                    hasErrors = errorCount > 0,
                    hasWarnings = warningCount > 0,
                    autoFixedCount = autoFixedCount,
                    errorCount = errorCount,
                    warningCount = warningCount
                )
                
                // 记录日志
                DiagnosticManager.info(TAG, report.toLogString())
                
                if (report.hasErrors) {
                    DiagnosticManager.error(TAG, "自检发现 ${report.errorCount} 个错误")
                } else if (report.hasWarnings) {
                    DiagnosticManager.warning(TAG, "自检发现 ${report.warningCount} 个警告")
                } else {
                    DiagnosticManager.success(TAG, "✓ 自检通过，所有模块正常")
                }
                
                DiagnosticManager.info(TAG, "========== 自检完成 ==========")
                
                // 主线程回调
                mainHandler.post {
                    // 显示对话框
                    if (showDialog && (report.hasErrors || report.hasWarnings)) {
                        showResultDialog(context, report)
                    }
                    
                    onComplete?.invoke(report)
                }
                
            } catch (e: Exception) {
                DiagnosticManager.error(TAG, "自检过程发生异常", e.message)
                mainHandler.post {
                    showErrorDialog(context, "自检失败", e.message ?: "未知错误")
                }
            } finally {
                isRunning.set(false)
            }
        }
    }
    
    /**
     * 快速检查（仅检查关键项，不显示对话框）
     */
    fun runQuickCheck(context: Context, onComplete: (Boolean) -> Unit) {
        executor.execute {
            try {
                val criticalErrors = mutableListOf<String>()
                
                // 检查 Native 库
                if (!LlamaCpp.isNativeAvailable()) {
                    // 这不是致命错误，有模拟模式
                    DiagnosticManager.info(TAG, "Native 库不可用，将使用模拟模式")
                }
                
                // 检查存储空间
                val filesDir = context.filesDir
                val freeSpace = filesDir.freeSpace / (1024 * 1024) // MB
                if (freeSpace < 100) {
                    criticalErrors.add("存储空间不足 (${freeSpace}MB)")
                }
                
                val hasErrors = criticalErrors.isNotEmpty()
                
                if (hasErrors) {
                    DiagnosticManager.error(TAG, "快速检查发现问题", criticalErrors.joinToString(", "))
                }
                
                mainHandler.post {
                    onComplete(!hasErrors)
                }
            } catch (e: Exception) {
                DiagnosticManager.error(TAG, "快速检查失败", e.message)
                mainHandler.post {
                    onComplete(false)
                }
            }
        }
    }
    
    // ==================== 检查项实现 ====================
    
    /**
     * 检查 Native 库状态
     */
    private fun checkNativeLibrary(): List<CheckResult> {
        val results = mutableListOf<CheckResult>()
        
        // 检查 llama_android 库
        val nativeAvailable = LlamaCpp.isNativeAvailable()
        val nativeError = LlamaCpp.getNativeLoadError()
        
        results.add(CheckResult(
            category = "Native 库",
            item = "llama_android.so",
            status = if (nativeAvailable) CheckStatus.OK else CheckStatus.WARNING,
            message = if (nativeAvailable) {
                "已加载"
            } else {
                "未加载 (${nativeError ?: "使用模拟模式"})"
            }
        ))
        
        // 检查真实推理支持
        if (nativeAvailable) {
            try {
                val llamaCpp = LlamaCpp()
                val realInference = llamaCpp.isRealInferenceSupported()
                results.add(CheckResult(
                    category = "Native 库",
                    item = "llama.cpp 推理",
                    status = if (realInference) CheckStatus.OK else CheckStatus.WARNING,
                    message = if (realInference) "已启用" else "未编译 llama.cpp"
                ))
            } catch (e: Exception) {
                results.add(CheckResult(
                    category = "Native 库",
                    item = "llama.cpp 推理",
                    status = CheckStatus.ERROR,
                    message = "检查失败: ${e.message}"
                ))
            }
        }
        
        return results
    }
    
    /**
     * 检查本地 AI 模块
     */
    private fun checkLocalAI(context: Context): List<CheckResult> {
        val results = mutableListOf<CheckResult>()
        
        try {
            val localAI = LocalAIManager.getInstance(context)
            val state = localAI.getState()
            
            results.add(CheckResult(
                category = "本地 AI",
                item = "模块状态",
                status = CheckStatus.OK,
                message = state.name
            ))
            
            // 检查是否有可用模型
            val downloadedModels = localAI.getDownloadedModels()
            results.add(CheckResult(
                category = "本地 AI",
                item = "已下载模型",
                status = if (downloadedModels.isNotEmpty()) CheckStatus.OK else CheckStatus.WARNING,
                message = if (downloadedModels.isNotEmpty()) {
                    "${downloadedModels.size} 个模型"
                } else {
                    "无模型，请下载"
                }
            ))
            
        } catch (e: Exception) {
            results.add(CheckResult(
                category = "本地 AI",
                item = "模块初始化",
                status = CheckStatus.ERROR,
                message = "初始化失败: ${e.message}"
            ))
        }
        
        return results
    }
    
    /**
     * 检查存储空间
     */
    private fun checkStorage(context: Context): List<CheckResult> {
        val results = mutableListOf<CheckResult>()
        
        try {
            val filesDir = context.filesDir
            val totalSpace = filesDir.totalSpace / (1024 * 1024 * 1024.0) // GB
            val freeSpace = filesDir.freeSpace / (1024 * 1024 * 1024.0) // GB
            val usedSpace = totalSpace - freeSpace
            
            val status = when {
                freeSpace < 0.1 -> CheckStatus.ERROR  // < 100MB
                freeSpace < 1.0 -> CheckStatus.WARNING // < 1GB
                else -> CheckStatus.OK
            }
            
            results.add(CheckResult(
                category = "存储",
                item = "可用空间",
                status = status,
                message = String.format("%.2f GB / %.2f GB", freeSpace, totalSpace)
            ))
            
            // 检查应用数据目录
            val modelDir = File(filesDir, "models")
            if (modelDir.exists()) {
                val modelSize = modelDir.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() } / (1024 * 1024.0) // MB
                
                results.add(CheckResult(
                    category = "存储",
                    item = "模型目录",
                    status = CheckStatus.OK,
                    message = String.format("%.2f MB", modelSize)
                ))
            }
            
        } catch (e: Exception) {
            results.add(CheckResult(
                category = "存储",
                item = "存储检查",
                status = CheckStatus.ERROR,
                message = "检查失败: ${e.message}"
            ))
        }
        
        return results
    }
    
    /**
     * 检查配置文件
     */
    private fun checkConfiguration(context: Context): List<CheckResult> {
        val results = mutableListOf<CheckResult>()
        
        try {
            val config = AIConfigManager.getCurrentConfig(context)
            
            // 检查 API Key
            results.add(CheckResult(
                category = "配置",
                item = "API Key",
                status = if (config.apiKey.isNotEmpty()) CheckStatus.OK else CheckStatus.WARNING,
                message = if (config.apiKey.isNotEmpty()) {
                    "已配置 (${config.apiKey.take(4)}...)"
                } else {
                    "未配置（联网模式不可用）"
                }
            ))
            
            // 检查 Base URL
            results.add(CheckResult(
                category = "配置",
                item = "Base URL",
                status = if (config.baseUrl.isNotEmpty()) CheckStatus.OK else CheckStatus.WARNING,
                message = if (config.baseUrl.isNotEmpty()) config.baseUrl else "未配置"
            ))
            
            // 检查模型
            results.add(CheckResult(
                category = "配置",
                item = "AI 模型",
                status = CheckStatus.OK,
                message = config.model
            ))
            
        } catch (e: Exception) {
            results.add(CheckResult(
                category = "配置",
                item = "配置检查",
                status = CheckStatus.ERROR,
                message = "检查失败: ${e.message}"
            ))
        }
        
        return results
    }
    
    /**
     * 检查模型文件完整性
     */
    private fun checkModelFiles(context: Context): List<CheckResult> {
        val results = mutableListOf<CheckResult>()
        
        try {
            val modelDir = File(context.filesDir, "models")
            if (!modelDir.exists()) {
                results.add(CheckResult(
                    category = "模型文件",
                    item = "模型目录",
                    status = CheckStatus.OK,
                    message = "目录不存在（正常，首次使用）",
                    canAutoFix = true,
                    fixAction = {
                        modelDir.mkdirs()
                        true
                    }
                ))
                return results
            }
            
            val modelFiles = modelDir.listFiles { file ->
                file.isFile && (file.extension == "gguf" || file.extension == "bin")
            } ?: emptyArray()
            
            if (modelFiles.isEmpty()) {
                results.add(CheckResult(
                    category = "模型文件",
                    item = "模型数量",
                    status = CheckStatus.WARNING,
                    message = "无模型文件"
                ))
            } else {
                modelFiles.forEach { file ->
                    val sizeGB = file.length() / (1024 * 1024 * 1024.0)
                    val sizeMB = file.length() / (1024 * 1024.0)
                    
                    // 检查文件是否过小（可能损坏）
                    val status = when {
                        file.length() < 1024 * 1024 -> CheckStatus.ERROR // < 1MB
                        file.length() < 100 * 1024 * 1024 -> CheckStatus.WARNING // < 100MB
                        else -> CheckStatus.OK
                    }
                    
                    val sizeStr = if (sizeGB >= 1) {
                        String.format("%.2f GB", sizeGB)
                    } else {
                        String.format("%.2f MB", sizeMB)
                    }
                    
                    results.add(CheckResult(
                        category = "模型文件",
                        item = file.name,
                        status = status,
                        message = sizeStr + when (status) {
                            CheckStatus.ERROR -> " (文件可能损坏)"
                            CheckStatus.WARNING -> " (文件较小)"
                            else -> ""
                        },
                        canAutoFix = status == CheckStatus.ERROR,
                        fixAction = if (status == CheckStatus.ERROR) {
                            {
                                file.delete()
                                DiagnosticManager.info(TAG, "已删除损坏的模型文件: ${file.name}")
                                true
                            }
                        } else null
                    ))
                }
            }
            
        } catch (e: Exception) {
            results.add(CheckResult(
                category = "模型文件",
                item = "文件检查",
                status = CheckStatus.ERROR,
                message = "检查失败: ${e.message}"
            ))
        }
        
        return results
    }
    
    // ==================== 对话框显示 ====================
    
    /**
     * 显示自检结果对话框
     */
    private fun showResultDialog(context: Context, report: CheckReport) {
        if (context !is Activity || context.isFinishing) return
        
        val dialogContent = buildString {
            if (report.hasErrors) {
                appendLine("⚠️ 发现 ${report.errorCount} 个错误")
                appendLine()
            }
            
            if (report.autoFixedCount > 0) {
                appendLine("🔧 已自动修复 ${report.autoFixedCount} 个问题")
                appendLine()
            }
            
            // 只显示错误和警告
            val problemResults = report.results.filter { 
                it.status == CheckStatus.ERROR || it.status == CheckStatus.WARNING || it.status == CheckStatus.FIXED
            }
            
            if (problemResults.isNotEmpty()) {
                appendLine("详细信息：")
                problemResults.forEach { result ->
                    val icon = when (result.status) {
                        CheckStatus.ERROR -> "✗"
                        CheckStatus.WARNING -> "⚠"
                        CheckStatus.FIXED -> "✓"
                        else -> ""
                    }
                    appendLine("$icon [${result.category}] ${result.item}")
                    appendLine("   ${result.message}")
                }
            }
        }
        
        val title = when {
            report.hasErrors && report.autoFixedCount > 0 -> "自检完成 - 部分问题已修复"
            report.hasErrors -> "自检发现问题"
            report.hasWarnings -> "自检完成 - 有警告"
            else -> "自检完成"
        }
        
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(dialogContent)
            .setPositiveButton("确定", null)
            .setNeutralButton("查看日志") { _, _ ->
                showLogDialog(context, report)
            }
            .show()
    }
    
    /**
     * 显示详细日志对话框
     */
    private fun showLogDialog(context: Context, report: CheckReport) {
        if (context !is Activity || context.isFinishing) return
        
        val scrollView = ScrollView(context).apply {
            setPadding(32, 32, 32, 32)
        }
        
        val textView = TextView(context).apply {
            text = report.toLogString()
            textSize = 12f
            setTextIsSelectable(true)
        }
        
        scrollView.addView(textView)
        
        AlertDialog.Builder(context)
            .setTitle("自检日志")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .setNeutralButton("复制日志") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("自检日志", report.toLogString())
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "日志已复制", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    /**
     * 显示错误对话框
     */
    private fun showErrorDialog(context: Context, title: String, message: String) {
        if (context !is Activity || context.isFinishing) return
        
        AlertDialog.Builder(context)
            .setTitle("⚠️ $title")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 获取最近的错误日志
     */
    fun getRecentErrors(): List<DiagnosticManager.LogEntry> {
        return DiagnosticManager.getAllLogs().filter { 
            it.level == DiagnosticManager.LogLevel.ERROR 
        }.takeLast(20)
    }
    
    /**
     * 导出日志到文件
     */
    fun exportLogsToFile(context: Context): File? {
        return try {
            val logsDir = File(context.filesDir, "logs")
            logsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val logFile = File(logsDir, "selfcheck_$timestamp.log")
            
            val logs = DiagnosticManager.getAllLogs()
            logFile.writeText(logs.joinToString("\n") { it.toString() })
            
            DiagnosticManager.success(TAG, "日志已导出", logFile.absolutePath)
            logFile
        } catch (e: Exception) {
            DiagnosticManager.error(TAG, "日志导出失败", e.message)
            null
        }
    }
    
    /**
     * 检查数学计算模块
     */
    private fun checkMathModule(): List<CheckResult> {
        val results = mutableListOf<CheckResult>()
        
        try {
            // 检查 LaTeX 解析器
            val parser = com.example.deepseekaiassistant.math.LatexParser()
            val testResult = parser.parse("x^2 + 1")
            
            results.add(CheckResult(
                category = "数学计算",
                item = "LaTeX 解析器",
                status = if (testResult.success) CheckStatus.OK else CheckStatus.WARNING,
                message = if (testResult.success) "正常" else "解析测试失败"
            ))
            
            // 检查数学运算引擎
            val engine = com.example.deepseekaiassistant.math.MathEngine()
            val evalResult = engine.evaluate("2 + 2")
            
            val evalOk = evalResult is com.example.deepseekaiassistant.math.CalculationResult.NumericResult &&
                         (evalResult as com.example.deepseekaiassistant.math.CalculationResult.NumericResult).value == 4.0
            
            results.add(CheckResult(
                category = "数学计算",
                item = "运算引擎",
                status = if (evalOk) CheckStatus.OK else CheckStatus.WARNING,
                message = if (evalOk) "正常" else "计算测试异常"
            ))
            
            // 检查函数图像生成
            val plotData = engine.generatePlotData("sin(x)")
            results.add(CheckResult(
                category = "数学计算",
                item = "图像生成",
                status = if (plotData.points.isNotEmpty()) CheckStatus.OK else CheckStatus.WARNING,
                message = if (plotData.points.isNotEmpty()) "正常 (${plotData.points.size} 点)" else "图像生成异常"
            ))
            
            // 检查公式模板
            val templates = com.example.deepseekaiassistant.math.FormulaTemplates.getAllTemplates()
            results.add(CheckResult(
                category = "数学计算",
                item = "公式模板库",
                status = CheckStatus.OK,
                message = "${templates.size} 个模板可用"
            ))
            
        } catch (e: Exception) {
            results.add(CheckResult(
                category = "数学计算",
                item = "模块初始化",
                status = CheckStatus.ERROR,
                message = "初始化失败: ${e.message}"
            ))
        }
        
        return results
    }
}
