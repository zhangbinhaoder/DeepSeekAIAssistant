package com.example.deepseekaiassistant.local

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.deepseekaiassistant.DiagnosticManager
import com.example.deepseekaiassistant.root.AIRootController
import com.example.deepseekaiassistant.root.AIControlCommand
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * 本地 AI 模型管理器
 * 
 * 职责：
 * 1. 管理模型生命周期（下载、加载、卸载、删除）
 * 2. 协调 LlamaCpp 进行推理
 * 3. 提供状态查询接口
 * 
 * 架构：
 * ┌─────────────────────────────────────────────────────────────┐
 * │                      LocalAIManager                         │
 * │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
 * │  │   模型管理       │  │    状态管理      │  │   推理调度   │ │
 * │  │ - 下载/删除     │  │ - 状态枚举      │  │ - 生成请求  │ │
 * │  │ - 文件管理     │  │ - 状态转换      │  │ - 回调处理  │ │
 * │  └─────────────────┘  └─────────────────┘  └─────────────┘ │
 * │                              │                              │
 * │                              ▼                              │
 * │                    ┌─────────────────┐                      │
 * │                    │    LlamaCpp     │                      │
 * │                    │   (JNI 绑定)     │                      │
 * │                    └─────────────────┘                      │
 * └─────────────────────────────────────────────────────────────┘
 */
class LocalAIManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var instance: LocalAIManager? = null
        
        // 模型文件目录
        private const val MODEL_DIR = "models"
        private const val DEFAULT_MODEL_NAME = "tinyllama-1.1b-q4.gguf"
        
        /**
         * 获取单例实例（线程安全的双重检查锁定）
         */
        fun getInstance(context: Context): LocalAIManager {
            return instance ?: synchronized(this) {
                instance ?: LocalAIManager(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
    
    // ==================== 状态定义 ====================
    
    /**
     * 模型状态枚举
     * 状态转换图：
     * NOT_DOWNLOADED ──▶ DOWNLOADING ──▶ DOWNLOADED ──▶ LOADING ──▶ READY
     *       ▲                │                │            │         │
     *       │                ▼                ▼            ▼         │
     *       └──────────── ERROR ◀─────────────────────────────────┘
     */
    enum class ModelState {
        NOT_DOWNLOADED,     // 未下载
        DOWNLOADING,        // 下载中
        DOWNLOADED,         // 已下载（未加载）
        LOADING,            // 加载中
        READY,              // 就绪（可推理）
        ERROR               // 错误状态
    }
    
    // 使用原子引用确保线程安全
    private val modelState = AtomicReference(ModelState.NOT_DOWNLOADED)
    private val currentModelPath = AtomicReference<String?>(null)
    private val lastError = AtomicReference<String?>(null)
    
    // LlamaCpp JNI 绑定实例
    private val llamaCpp = LlamaCpp()
    
    // AI Root 控制器
    private val aiRootController by lazy {
        AIRootController.getInstance(context)
    }
    
    // 执行器和 Handler
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LocalAI-Worker").apply { 
            isDaemon = true 
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // ==================== 模型配置 ====================
    
    /**
     * 模型配置数据类
     */
    data class ModelConfig(
        val name: String,
        val displayName: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val contextLength: Int = 2048,
        val description: String
    )
    
    /**
     * 可用的本地模型列表
     * 支持国内镜像源和原版地址
     */
    val availableModels = listOf(
        ModelConfig(
            name = "deepseek-coder-1.3b-q4.gguf",
            displayName = "DeepSeek Coder 1.3B (Q4量化)",
            // 使用国内镜像源（hf-mirror.com）
            downloadUrl = "https://hf-mirror.com/TheBloke/deepseek-coder-1.3b-instruct-GGUF/resolve/main/deepseek-coder-1.3b-instruct.Q4_K_M.gguf",
            sizeBytes = 800_000_000,
            contextLength = 2048,
            description = "轻量级代码模型，适合基础对话和代码问答"
        ),
        ModelConfig(
            name = "qwen2-0.5b-q8.gguf",
            displayName = "Qwen2 0.5B (Q8量化)",
            downloadUrl = "https://hf-mirror.com/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0_5b-instruct-q8_0.gguf",
            sizeBytes = 530_000_000,
            contextLength = 2048,
            description = "超轻量级模型，运行速度最快"
        ),
        ModelConfig(
            name = "phi-2-q4.gguf",
            displayName = "Microsoft Phi-2 (Q4量化)",
            downloadUrl = "https://hf-mirror.com/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
            sizeBytes = 1_600_000_000,
            contextLength = 2048,
            description = "微软小模型，质量较好"
        ),
        ModelConfig(
            name = "tinyllama-1.1b-q4.gguf",
            displayName = "TinyLlama 1.1B (Q4量化)",
            downloadUrl = "https://hf-mirror.com/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            sizeBytes = 670_000_000,
            contextLength = 2048,
            description = "小型聊天模型，平衡速度和质量"
        )
    )
    
    // 当前选择的模型配置
    private val currentModelConfig = AtomicReference<ModelConfig?>(null)
    
    // ==================== 初始化 ====================
    
    // SharedPreferences 用于持久化模型配置
    private val prefs by lazy {
        context.getSharedPreferences("local_ai_config", Context.MODE_PRIVATE)
    }
    
    init {
        DiagnosticManager.info("LocalAI", "LocalAIManager 初始化")
        initializeState()
    }
    
    /**
     * 初始化状态：检查已下载的模型并恢复上次的配置
     */
    private fun initializeState() {
        executor.execute {
            try {
                // 检查已下载的模型
                val downloadedModels = getDownloadedModels()
                if (downloadedModels.isNotEmpty()) {
                    modelState.set(ModelState.DOWNLOADED)
                    DiagnosticManager.info("LocalAI", "发现已下载的模型", 
                        downloadedModels.joinToString { it.name })
                    
                    // === 恢复上次使用的模型配置 ===
                    val lastModelPath = prefs.getString("last_loaded_model", null)
                    val autoLoad = prefs.getBoolean("auto_load_model", true)
                    
                    if (autoLoad && !lastModelPath.isNullOrEmpty()) {
                        val modelFile = File(lastModelPath)
                        if (modelFile.exists()) {
                            DiagnosticManager.info("LocalAI", "自动加载上次的模型", modelFile.name)
                            loadModelInternal(lastModelPath, {}, { success, _ ->
                                if (success) {
                                    DiagnosticManager.success("LocalAI", "自动加载模型成功")
                                }
                            })
                        }
                    }
                }
                
                // 尝试提取内置模型
                extractBundledModelIfNeeded()
            } catch (e: Exception) {
                DiagnosticManager.error("LocalAI", "初始化失败", e.message)
            }
        }
    }
    
    /**
     * 保存模型配置
     */
    private fun saveModelConfig(modelPath: String) {
        prefs.edit()
            .putString("last_loaded_model", modelPath)
            .putLong("last_load_time", System.currentTimeMillis())
            .apply()
        DiagnosticManager.info("LocalAI", "已保存模型配置", modelPath)
    }
    
    /**
     * 设置是否自动加载模型
     */
    fun setAutoLoadModel(enabled: Boolean) {
        prefs.edit().putBoolean("auto_load_model", enabled).apply()
    }
    
    /**
     * 获取是否自动加载模型
     */
    fun isAutoLoadEnabled(): Boolean = prefs.getBoolean("auto_load_model", true)
    
    /**
     * 检查并提取内置的模型文件（从 assets 目录）
     */
    private fun extractBundledModelIfNeeded() {
        val bundledModelName = "tinyllama-1.1b-q4.gguf"
        val targetFile = getModelFile(bundledModelName)
        
        if (targetFile.exists() && targetFile.length() > 1000) {
            return // 已存在，无需提取
        }
        
        try {
            val assetManager = context.assets
            val files = assetManager.list("models") ?: emptyArray()
            
            if (bundledModelName in files) {
                DiagnosticManager.info("LocalAI", "发现内置模型，正在提取...", bundledModelName)
                
                // 确保目录存在
                targetFile.parentFile?.mkdirs()
                
                // 提取模型文件
                assetManager.open("models/$bundledModelName").use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                
                DiagnosticManager.success("LocalAI", "内置模型提取完成", 
                    "大小: ${formatFileSize(targetFile.length())}")
                modelState.set(ModelState.DOWNLOADED)
            }
        } catch (e: Exception) {
            DiagnosticManager.error("LocalAI", "提取内置模型失败", e.message)
        }
    }
    
    // ==================== 状态查询 ====================
    
    /**
     * 获取当前模型状态
     */
    fun getState(): ModelState = modelState.get()
    
    /**
     * 获取最后一次错误信息
     */
    fun getLastError(): String? = lastError.get()
    
    /**
     * 检查是否支持真正的推理
     */
    fun isRealInferenceSupported(): Boolean {
        return try {
            llamaCpp.isRealInferenceSupported()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查模型是否就绪（已加载到内存，可以推理）
     */
    fun isReady(): Boolean {
        return try {
            llamaCpp.isModelLoaded() && modelState.get() == ModelState.READY
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取系统信息
     */
    fun getSystemInfo(): String {
        return try {
            buildString {
                appendLine(llamaCpp.getModeDescription())
                appendLine()
                append(llamaCpp.getSystemInfo())
            }
        } catch (e: Exception) {
            "llama.cpp: ${e.message}"
        }
    }
    
    /**
     * 获取详细状态信息
     */
    fun getDetailedStatus(): Map<String, Any> {
        return buildMap {
            put("managerState", modelState.get().name)
            put("currentModelPath", currentModelPath.get() ?: "none")
            put("lastError", lastError.get() ?: "none")
            putAll(llamaCpp.getDetailedStatus())
        }
    }
    
    // ==================== 模型文件管理 ====================
    
    /**
     * 获取模型文件对象
     */
    private fun getModelFile(modelName: String): File {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        return File(modelDir, modelName)
    }
    
    /**
     * 获取模型目录
     */
    fun getModelDirectory(): File {
        return File(context.filesDir, MODEL_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }
    
    /**
     * 获取已下载的模型列表
     */
    fun getDownloadedModels(): List<File> {
        val modelDir = getModelDirectory()
        return modelDir.listFiles { file ->
            file.isFile && (file.extension == "gguf" || file.extension == "bin")
        }?.toList() ?: emptyList()
    }
    
    /**
     * 检查模型是否已下载
     */
    fun isModelDownloaded(modelName: String = DEFAULT_MODEL_NAME): Boolean {
        val modelFile = getModelFile(modelName)
        return modelFile.exists() && modelFile.length() > 0
    }
    
    /**
     * 获取模型大小（格式化字符串）
     */
    fun getModelSize(modelName: String): String {
        val modelFile = getModelFile(modelName)
        return if (modelFile.exists()) {
            formatFileSize(modelFile.length())
        } else {
            "未下载"
        }
    }
    
    // ==================== 模型下载 ====================
    
    /**
     * 下载模型
     * @param modelConfig 模型配置
     * @param onProgress 进度回调 (0-100)
     * @param onComplete 完成回调 (success, errorMessage)
     */
    fun downloadModel(
        modelConfig: ModelConfig,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (modelState.get() == ModelState.DOWNLOADING) {
            onComplete(false, "已有模型正在下载中")
            return
        }
        
        modelState.set(ModelState.DOWNLOADING)
        lastError.set(null)
        DiagnosticManager.info("LocalAI", "开始下载模型", modelConfig.displayName)
        
        executor.execute {
            try {
                val modelFile = getModelFile(modelConfig.name)
                downloadFromUrl(modelConfig.downloadUrl, modelFile, modelConfig.sizeBytes, onProgress)
                
                modelState.set(ModelState.DOWNLOADED)
                currentModelConfig.set(modelConfig)
                DiagnosticManager.success("LocalAI", "模型下载完成", modelConfig.displayName)
                
                mainHandler.post { onComplete(true, null) }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "下载失败"
                modelState.set(ModelState.ERROR)
                lastError.set(errorMsg)
                DiagnosticManager.error("LocalAI", "模型下载失败", errorMsg)
                
                mainHandler.post { onComplete(false, errorMsg) }
            }
        }
    }
    
    /**
     * 从 URL 下载文件
     */
    private fun downloadFromUrl(
        url: String,
        targetFile: File,
        estimatedSize: Long,
        onProgress: (Int) -> Unit
    ) {
        var connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        
        // 处理重定向
        if (connection.responseCode == 301 || connection.responseCode == 302) {
            val redirectUrl = connection.getHeaderField("Location")
            connection.disconnect()
            connection = java.net.URL(redirectUrl).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        
        val totalSize = connection.contentLength.toLong().let { 
            if (it > 0) it else estimatedSize 
        }
        var downloadedSize = 0L
        
        connection.inputStream.use { input ->
            targetFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedSize += bytesRead
                    
                    val progress = if (totalSize > 0) {
                        ((downloadedSize * 100) / totalSize).toInt().coerceIn(0, 100)
                    } else {
                        -1
                    }
                    
                    mainHandler.post { onProgress(progress) }
                }
            }
        }
    }
    
    // ==================== 模型加载/卸载 ====================
    
    /**
     * 加载模型到内存
     * @param modelName 模型文件名（默认使用第一个可用模型）
     * @param onProgress 进度回调
     * @param onComplete 完成回调
     */
    fun loadModel(
        modelName: String = DEFAULT_MODEL_NAME,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        // 查找模型文件
        val modelFile = if (File(modelName).exists()) {
            File(modelName)
        } else {
            getModelFile(modelName)
        }
        
        if (!modelFile.exists()) {
            // 尝试使用第一个可用的模型
            val downloadedModels = getDownloadedModels()
            if (downloadedModels.isEmpty()) {
                onComplete(false, "没有找到模型文件，请先下载模型")
                return
            }
            loadModelInternal(downloadedModels.first().absolutePath, onProgress, onComplete)
            return
        }
        
        loadModelInternal(modelFile.absolutePath, onProgress, onComplete)
    }
    
    /**
     * 内部模型加载逻辑
     */
    private fun loadModelInternal(
        modelPath: String,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        modelState.set(ModelState.LOADING)
        lastError.set(null)
        DiagnosticManager.info("LocalAI", "开始加载模型", modelPath)
        
        executor.execute {
            try {
                mainHandler.post { onProgress(10) }
                
                // 获取模型配置
                val config = availableModels.find { modelPath.contains(it.name) }
                val contextLength = config?.contextLength ?: 2048
                
                mainHandler.post { onProgress(30) }
                
                // 使用 LlamaCpp 加载模型
                val success = llamaCpp.loadModel(
                    modelPath = modelPath,
                    nCtx = contextLength,
                    nGpuLayers = 0  // Android 不使用 GPU
                )
                
                mainHandler.post { onProgress(100) }
                
                if (success) {
                    modelState.set(ModelState.READY)
                    currentModelPath.set(modelPath)
                    currentModelConfig.set(config)
                    
                    // === 保存模型配置以便下次自动加载 ===
                    saveModelConfig(modelPath)
                    
                    DiagnosticManager.success("LocalAI", "模型加载完成")
                    mainHandler.post { onComplete(true, null) }
                } else {
                    val error = "模型加载失败"
                    modelState.set(ModelState.ERROR)
                    lastError.set(error)
                    DiagnosticManager.error("LocalAI", error)
                    mainHandler.post { onComplete(false, error) }
                }
                
            } catch (e: Exception) {
                val error = e.message ?: "加载异常"
                modelState.set(ModelState.ERROR)
                lastError.set(error)
                DiagnosticManager.error("LocalAI", "模型加载异常", error)
                mainHandler.post { onComplete(false, error) }
            }
        }
    }
    
    /**
     * 卸载模型，释放内存
     */
    fun unloadModel() {
        try {
            llamaCpp.unloadModel()
            modelState.set(if (getDownloadedModels().isNotEmpty()) {
                ModelState.DOWNLOADED
            } else {
                ModelState.NOT_DOWNLOADED
            })
            currentModelPath.set(null)
            DiagnosticManager.info("LocalAI", "模型已卸载")
        } catch (e: Exception) {
            DiagnosticManager.error("LocalAI", "卸载模型失败", e.message)
        }
    }
    
    /**
     * 删除模型文件
     */
    fun deleteModel(modelName: String): Boolean {
        val modelFile = getModelFile(modelName)
        if (!modelFile.exists()) return false
        
        // 如果是当前加载的模型，先卸载
        if (currentModelPath.get() == modelFile.absolutePath) {
            unloadModel()
        }
        
        return modelFile.delete().also { deleted ->
            if (deleted) {
                modelState.set(if (getDownloadedModels().isEmpty()) {
                    ModelState.NOT_DOWNLOADED
                } else {
                    ModelState.DOWNLOADED
                })
                DiagnosticManager.info("LocalAI", "模型已删除", modelName)
            }
        }
    }
    
    // ==================== 推理 ====================
    
    /**
     * 生成回复
     * 
     * 会自动选择推理模式：
     * - 如果模型已加载 → Native 推理
     * - 否则 → 模拟模式
     * 
     * @param prompt 用户输入的消息
     * @param maxTokens 最大生成 token 数
     * @param temperature 采样温度
     * @param onToken token 回调（流式输出）
     * @param onComplete 完成回调
     * @param onError 错误回调
     */
    fun generateResponse(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // 构建完整的 prompt（带系统提示）
        val fullPrompt = buildPrompt(prompt)
        
        DiagnosticManager.info("LocalAI", "开始本地推理", 
            "Prompt: ${prompt.take(50)}${if (prompt.length > 50) "..." else ""}")
        
        try {
            llamaCpp.generate(
                prompt = fullPrompt,
                maxTokens = maxTokens,
                temperature = temperature,
                callback = SimpleGenerationCallback(
                    onTokenReceived = { token ->
                        mainHandler.post { onToken(token) }
                    },
                    onGenerationComplete = { response ->
                        DiagnosticManager.success("LocalAI", "本地推理完成", 
                            "长度: ${response.length}")
                        
                        // === AI 控制指令处理 ===
                        processAIControlCommand(response, true)
                        
                        mainHandler.post { onComplete(response) }
                    },
                    onGenerationError = { error ->
                        DiagnosticManager.error("LocalAI", "本地推理失败", error)
                        mainHandler.post { onError(error) }
                    }
                )
            )
        } catch (e: Exception) {
            val error = e.message ?: "未知错误"
            DiagnosticManager.error("LocalAI", "本地推理异常", error)
            mainHandler.post { onError(error) }
        }
    }
    
    /**
     * 处理 AI 输出中的控制指令
     */
    private fun processAIControlCommand(aiOutput: String, isLocalAI: Boolean) {
        try {
            val result = aiRootController.processAIOutput(aiOutput, isLocalAI)
            if (result != null) {
                DiagnosticManager.info("LocalAI", "AI 控制指令已执行",
                    "${result.action}: ${result.detail}")
            }
        } catch (e: Exception) {
            DiagnosticManager.error("LocalAI", "AI 控制指令处理失败", e.message)
        }
    }
    
    /**
     * 获取 AI 控制系统提示词
     */
    fun getAIControlSystemPrompt(): String {
        return aiRootController.generateSystemPrompt()
    }
    
    /**
     * 构建完整的 prompt（ChatML 格式）
     * 如果 AI 控制权限开启，会添加控制指令的系统提示词
     */
    private fun buildPrompt(userMessage: String): String {
        val baseSystemPrompt = "你是一个运行在Android设备上的AI助手。请用中文简洁地回答问题。"
        
        // 检查是否开启了 AI 控制权限
        val hasControlPermission = aiRootController.isLocalAIControlEnabled()
        
        val systemPrompt = if (hasControlPermission) {
            """$baseSystemPrompt

你拥有 Root 权限，可以执行手机控制操作。当用户请求执行手机操作时，输出 JSON 格式指令：
{"action": "操作类型", "params": {"参数"}, "timestamp": 时间戳, "need_root": true}

可用操作：root_bluetooth_toggle(蓝牙), root_wifi_toggle(WiFi), root_adjust_system_volume(音量), root_set_brightness(亮度), root_reboot_device(重启), root_force_stop_app(停止应用), root_drop_caches(清理内存), root_input_tap(点击), root_input_swipe(滑动), root_take_screenshot(截图)"""
        } else {
            baseSystemPrompt
        }
        
        return """<|im_start|>system
$systemPrompt
<|im_end|>
<|im_start|>user
$userMessage
<|im_end|>
<|im_start|>assistant
"""
    }
    
    /**
     * 停止当前生成
     */
    fun stopGeneration() {
        try {
            llamaCpp.stopGeneration()
            DiagnosticManager.info("LocalAI", "已停止生成")
        } catch (e: Exception) {
            DiagnosticManager.error("LocalAI", "停止生成失败", e.message)
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
