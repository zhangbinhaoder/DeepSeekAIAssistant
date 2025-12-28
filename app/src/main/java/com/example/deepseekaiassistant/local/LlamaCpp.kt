package com.example.deepseekaiassistant.local

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * LlamaCpp - llama.cpp 的 JNI 绑定类
 * 
 * 提供与 native 层的稳定接口，用于加载模型和生成回复
 * 
 * 架构设计：
 * ┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
 * │  LocalAIManager │ ──▶ │     LlamaCpp     │ ──▶ │ llama_android.cpp│
 * │   (状态管理)     │     │   (JNI 绑定层)    │     │   (Native 实现)  │
 * └─────────────────┘     └──────────────────┘     └─────────────────┘
 * 
 * 支持两种模式：
 * 1. Native 模式：真正的 llama.cpp 推理（需要编译 native 库 + 加载模型）
 * 2. 模拟模式：当 native 库不可用或模型未加载时，提供基础对话能力
 */
class LlamaCpp {
    
    companion object {
        private const val TAG = "LlamaCpp"
        
        // Native 库加载状态（全局单例）
        private val nativeLibLoaded = AtomicBoolean(false)
        private val nativeLoadError = AtomicReference<String?>(null)
        
        init {
            loadNativeLibrary()
        }
        
        /**
         * 加载 native 库（线程安全）
         */
        private fun loadNativeLibrary() {
            try {
                System.loadLibrary("llama_android")
                nativeLibLoaded.set(true)
                nativeLoadError.set(null)
                Log.i(TAG, "✓ Native library 'llama_android' loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                nativeLibLoaded.set(false)
                nativeLoadError.set(e.message)
                Log.w(TAG, "✗ Native library not available: ${e.message}")
                Log.i(TAG, "Falling back to Kotlin simulation mode")
            } catch (e: Exception) {
                nativeLibLoaded.set(false)
                nativeLoadError.set(e.message)
                Log.e(TAG, "✗ Unexpected error loading native library: ${e.message}")
            }
        }
        
        /**
         * 检查 native 库是否已加载
         */
        fun isNativeAvailable(): Boolean = nativeLibLoaded.get()
        
        /**
         * 获取 native 库加载错误信息
         */
        fun getNativeLoadError(): String? = nativeLoadError.get()
    }
    
    // ==================== Native 方法声明 ====================
    // 这些方法在 llama_android.cpp 中实现，使用 JNI 绑定
    // 方法签名必须与 C++ 端完全匹配
    
    private external fun nativeIsRealInferenceSupported(): Boolean
    private external fun nativeGetSystemInfo(): String
    private external fun nativeLoadModel(modelPath: String, nCtx: Int, nGpuLayers: Int): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float, callback: GenerationCallback)
    private external fun nativeStopGeneration()
    private external fun nativeIsGenerating(): Boolean
    
    // ==================== 模拟模式状态 ====================
    private val simulatedModelLoaded = AtomicBoolean(false)
    private val isCurrentlyGenerating = AtomicBoolean(false)
    private val shouldStop = AtomicBoolean(false)
    
    /**
     * 回调接口 - 用于接收生成过程中的事件
     * Native 层和 Kotlin 层都会调用这些方法
     */
    interface GenerationCallback {
        /** 收到一个 token（流式输出） */
        fun onToken(token: String)
        /** 生成完成 */
        fun onComplete(response: String)
        /** 发生错误 */
        fun onError(error: String)
    }
    
    /**
     * 检查是否支持真正的推理（llama.cpp 是否已编译并可用）
     */
    fun isRealInferenceSupported(): Boolean {
        if (!nativeLibLoaded.get()) {
            return false
        }
        return try {
            nativeIsRealInferenceSupported()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking real inference support: ${e.message}")
            false
        }
    }
    
    /**
     * 获取系统信息（用于诊断）
     */
    fun getSystemInfo(): String {
        if (!nativeLibLoaded.get()) {
            return buildSimulationModeInfo()
        }
        
        return try {
            val nativeInfo = nativeGetSystemInfo()
            buildString {
                appendLine("✓ Native 库已加载")
                appendLine()
                append(nativeInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system info: ${e.message}")
            "llama.cpp (native) - Error: ${e.message}"
        }
    }
    
    /**
     * 构建模拟模式信息
     */
    private fun buildSimulationModeInfo(): String {
        return buildString {
            appendLine("llama.cpp: Kotlin 模拟模式")
            appendLine()
            appendLine("当前使用模拟 AI，回复能力有限。")
            appendLine()
            appendLine("Native 库加载状态: ${if (nativeLibLoaded.get()) "已加载" else "未加载"}")
            nativeLoadError.get()?.let {
                appendLine("加载错误: $it")
            }
            appendLine()
            appendLine("要启用真正的离线 AI 推理，需要：")
            appendLine("1. 运行 setup_llama.bat 下载 llama.cpp")
            appendLine("2. 将项目移动到纯英文路径")
            appendLine("3. 重新构建项目")
            appendLine("4. 下载并加载 GGUF 模型文件")
        }
    }
    
    /**
     * 加载模型
     * 
     * @param modelPath 模型文件的绝对路径
     * @param nCtx 上下文长度（默认 2048）
     * @param nGpuLayers GPU 加速层数（Android 默认 0）
     * @return 是否加载成功
     */
    fun loadModel(modelPath: String, nCtx: Int = 2048, nGpuLayers: Int = 0): Boolean {
        Log.i(TAG, "Loading model: $modelPath (nCtx=$nCtx, nGpuLayers=$nGpuLayers)")
        
        if (!nativeLibLoaded.get()) {
            // 模拟模式：标记为已加载
            Log.i(TAG, "Native not available, using simulation mode")
            simulatedModelLoaded.set(true)
            return true
        }
        
        return try {
            val result = nativeLoadModel(modelPath, nCtx, nGpuLayers)
            if (result) {
                simulatedModelLoaded.set(true)
                Log.i(TAG, "✓ Model loaded successfully via native")
            } else {
                Log.e(TAG, "✗ Native loadModel returned false")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "✗ Exception during model loading: ${e.message}")
            // 回退到模拟模式
            simulatedModelLoaded.set(true)
            Log.i(TAG, "Falling back to simulation mode after load failure")
            true
        }
    }
    
    /**
     * 卸载模型，释放内存
     */
    fun unloadModel() {
        Log.i(TAG, "Unloading model...")
        
        if (nativeLibLoaded.get()) {
            try {
                nativeUnloadModel()
                Log.i(TAG, "✓ Model unloaded via native")
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading model: ${e.message}")
            }
        }
        
        simulatedModelLoaded.set(false)
    }
    
    /**
     * 检查模型是否已加载（可以进行推理）
     */
    fun isModelLoaded(): Boolean {
        if (!nativeLibLoaded.get()) {
            return simulatedModelLoaded.get()
        }
        
        return try {
            nativeIsModelLoaded()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model loaded state: ${e.message}")
            simulatedModelLoaded.get()
        }
    }
    
    /**
     * 生成回复（异步操作）
     * 
     * 自动选择推理模式：
     * - 如果 Native 可用且模型已加载 → 使用 Native 推理
     * - 否则 → 使用模拟模式
     * 
     * @param prompt 输入提示词（通常是 ChatML 格式）
     * @param maxTokens 最大生成 token 数
     * @param temperature 采样温度（0-2，越高越随机）
     * @param callback 回调接口
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        callback: GenerationCallback
    ) {
        // 决定使用哪种模式
        val nativeAvailable = nativeLibLoaded.get()
        val realInferenceSupported = nativeAvailable && isRealInferenceSupported()
        val modelLoaded = isModelLoaded()
        
        val useNative = nativeAvailable && realInferenceSupported && modelLoaded
        
        Log.i(TAG, buildString {
            append("Generate request - ")
            append("Native: $nativeAvailable, ")
            append("RealInference: $realInferenceSupported, ")
            append("ModelLoaded: $modelLoaded, ")
            append("UseNative: $useNative")
        })
        
        if (useNative) {
            try {
                Log.i(TAG, "→ Using native llama.cpp inference")
                nativeGenerate(prompt, maxTokens, temperature, callback)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Native generate failed: ${e.message}, falling back to simulation")
            }
        }
        
        // 使用模拟模式
        Log.i(TAG, "→ Using Kotlin simulation mode")
        generateSimulated(prompt, callback)
    }
    
    /**
     * 模拟模式生成（在后台线程执行）
     */
    private fun generateSimulated(prompt: String, callback: GenerationCallback) {
        // 防止重入
        if (!isCurrentlyGenerating.compareAndSet(false, true)) {
            callback.onError("已有生成任务在进行中")
            return
        }
        
        shouldStop.set(false)
        
        Thread {
            try {
                val response = generateSimulatedResponse(prompt)
                streamOutput(response, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Simulation error: ${e.message}")
                callback.onError(e.message ?: "未知错误")
            } finally {
                isCurrentlyGenerating.set(false)
            }
        }.apply {
            name = "LlamaCpp-SimulationThread"
            start()
        }
    }
    
    /**
     * 模拟流式输出
     */
    private fun streamOutput(response: String, callback: GenerationCallback) {
        val fullResponse = StringBuilder()
        var pos = 0
        
        while (pos < response.length && !shouldStop.get()) {
            // 每次输出 2-4 个字符，模拟真实推理
            val chunkSize = (2..4).random()
            val end = minOf(pos + chunkSize, response.length)
            val token = response.substring(pos, end)
            
            fullResponse.append(token)
            callback.onToken(token)
            pos = end
            
            // 模拟推理延迟
            Thread.sleep((20L..40L).random())
        }
        
        callback.onComplete(fullResponse.toString())
    }
    
    /**
     * 生成模拟回复
     * 根据用户输入返回预设的回复
     */
    private fun generateSimulatedResponse(prompt: String): String {
        // 从 ChatML 格式中提取用户原始消息
        val userMessage = extractUserMessage(prompt)
        val lowerPrompt = userMessage.lowercase()
        
        return when {
            // 问候语
            containsAny(lowerPrompt, "你好", "hello", "hi", "嗨", "您好") ->
                buildGreetingResponse()
            
            // 自我介绍
            containsAny(lowerPrompt, "你是谁", "介绍", "什么是ai", "你叫什么") ->
                buildIntroductionResponse()
            
            // 天气查询
            lowerPrompt.contains("天气") ->
                buildWeatherResponse()
            
            // 时间查询
            containsAny(lowerPrompt, "时间", "几点", "现在") ->
                "🕐 当前时间是 ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
            
            // 日期查询
            containsAny(lowerPrompt, "日期", "今天", "星期", "周几") ->
                "📅 今天是 ${java.text.SimpleDateFormat("yyyy年MM月dd日 EEEE", java.util.Locale.CHINESE).format(java.util.Date())}"
            
            // 帮助/功能
            containsAny(lowerPrompt, "帮助", "功能", "能做什么", "你会") ->
                buildHelpResponse()
            
            // 计算类问题
            containsAny(lowerPrompt, "+", "-", "*", "/", "等于", "计算", "加", "减", "乘", "除") ->
                tryCalculate(userMessage)
            
            // 编程相关
            containsAny(lowerPrompt, "代码", "code", "编程", "java", "kotlin", "python", "程序") ->
                buildProgrammingResponse()
            
            // 感谢
            containsAny(lowerPrompt, "谢谢", "感谢", "thank") ->
                "不客气！😊 很高兴能帮到你。有任何问题随时问我！"
            
            // 默认回复
            else -> buildDefaultResponse(userMessage)
        }
    }
    
    /**
     * 辅助方法：检查是否包含任意关键词
     */
    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }
    
    /**
     * 构建问候回复
     */
    private fun buildGreetingResponse(): String {
        return buildString {
            appendLine("你好！我是运行在你设备上的本地 AI 助手。")
            appendLine()
            appendLine("🧠 当前模式：本地模拟 AI")
            appendLine("📱 不需要网络连接")
            appendLine("🔒 隐私数据不上传")
            appendLine()
            append("如需更强大的 AI 功能，请开启联网模式。")
        }
    }
    
    /**
     * 构建自我介绍回复
     */
    private fun buildIntroductionResponse(): String {
        return buildString {
            appendLine("我是 DeepSeek AI 助手，运行在你的 Android 设备上。")
            appendLine()
            appendLine("📱 **本地 AI 模式**（当前）：")
            appendLine("• 不需要网络连接")
            appendLine("• 基础对话能力")
            appendLine("• 隐私数据不上传")
            appendLine()
            appendLine("🌐 **联网模式**：")
            appendLine("• 调用云端 AI 接口")
            appendLine("• 更强大的理解能力")
            append("• 需要网络和 API Key")
        }
    }
    
    /**
     * 构建天气查询回复
     */
    private fun buildWeatherResponse(): String {
        return buildString {
            appendLine("抱歉，我是离线运行的本地 AI，无法获取实时天气信息。")
            appendLine()
            appendLine("🌤️ 建议你：")
            appendLine("• 开启联网模式查询天气")
            append("• 或使用天气应用查询")
        }
    }
    
    /**
     * 构建帮助回复
     */
    private fun buildHelpResponse(): String {
        return buildString {
            appendLine("我是本地 AI 助手，可以帮你：")
            appendLine()
            appendLine("💬 **基础对话**")
            appendLine("• 回答简单问题")
            appendLine("• 日常聊天交流")
            appendLine()
            appendLine("🕐 **时间日期**")
            appendLine("• 查询当前时间")
            appendLine("• 查询今天日期")
            appendLine()
            appendLine("🧮 **简单计算**")
            appendLine("• 基础四则运算")
            appendLine()
            appendLine("💡 **小提示**")
            append("开启联网模式可获得更强大的 AI 能力！")
        }
    }
    
    /**
     * 构建编程相关回复
     */
    private fun buildProgrammingResponse(): String {
        return buildString {
            appendLine("💻 编程问题需要更强大的 AI 能力。")
            appendLine()
            appendLine("建议开启联网模式，连接云端 AI 来获取：")
            appendLine("• 代码生成")
            appendLine("• 代码解释")
            append("• Bug 调试建议")
        }
    }
    
    /**
     * 构建默认回复
     */
    private fun buildDefaultResponse(userMessage: String): String {
        val shortQuestion = if (userMessage.length > 30) {
            userMessage.take(30) + "..."
        } else {
            userMessage
        }
        
        return buildString {
            appendLine("我收到了你的消息：「$shortQuestion」")
            appendLine()
            appendLine("🤔 当前我处于本地模拟模式，回复能力有限。")
            appendLine()
            appendLine("你可以：")
            appendLine("• 询问当前时间/日期")
            appendLine("• 进行简单对话")
            append("• 开启联网模式获得完整 AI 能力")
        }
    }
    
    /**
     * 从 ChatML 格式的 prompt 中提取用户原始消息
     */
    private fun extractUserMessage(prompt: String): String {
        // 尝试从 ChatML 格式提取
        val userStartTag = "<|im_start|>user"
        val userEndTag = "<|im_end|>"
        
        val startIndex = prompt.indexOf(userStartTag)
        if (startIndex != -1) {
            val messageStart = startIndex + userStartTag.length
            val endIndex = prompt.indexOf(userEndTag, messageStart)
            if (endIndex != -1) {
                return prompt.substring(messageStart, endIndex).trim()
            }
        }
        
        // 如果不是 ChatML 格式，直接返回原始 prompt
        return prompt.trim()
    }
    
    /**
     * 尝试计算简单的数学表达式
     */
    private fun tryCalculate(expression: String): String {
        return try {
            // 预处理表达式
            val cleaned = expression.replace(" ", "")
                .replace("加", "+")
                .replace("减", "-")
                .replace("乘", "*")
                .replace("乘以", "*")
                .replace("除", "/")
                .replace("除以", "/")
                .replace("等于", "=")
                .replace("×", "*")
                .replace("÷", "/")
            
            // 简单的两个数运算
            val pattern = Regex("""(\d+\.?\d*)\s*([+\-*/])\s*(\d+\.?\d*)""")
            val match = pattern.find(cleaned)
            
            if (match != null) {
                val (num1Str, op, num2Str) = match.destructured
                val num1 = num1Str.toDouble()
                val num2 = num2Str.toDouble()
                
                val result = when (op) {
                    "+" -> num1 + num2
                    "-" -> num1 - num2
                    "*" -> num1 * num2
                    "/" -> if (num2 != 0.0) num1 / num2 else Double.NaN
                    else -> Double.NaN
                }
                
                if (result.isNaN()) {
                    "无法计算（除数不能为0）"
                } else {
                    val formatted = if (result == result.toLong().toDouble()) {
                        result.toLong().toString()
                    } else {
                        String.format("%.2f", result)
                    }
                    "🧮 计算结果：$num1Str $op $num2Str = $formatted"
                }
            } else {
                "请输入简单的数学表达式，例如：3+5 或 10*2"
            }
        } catch (e: Exception) {
            "抱歉，无法计算这个表达式。请尝试简单的格式，如：5+3"
        }
    }
    
    /**
     * 停止当前生成任务
     */
    fun stopGeneration() {
        Log.i(TAG, "Stop generation requested")
        shouldStop.set(true)
        
        if (nativeLibLoaded.get()) {
            try {
                nativeStopGeneration()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping native generation: ${e.message}")
            }
        }
    }
    
    /**
     * 检查是否正在生成
     */
    fun isGenerating(): Boolean {
        if (!nativeLibLoaded.get()) {
            return isCurrentlyGenerating.get()
        }
        
        return try {
            nativeIsGenerating()
        } catch (e: Exception) {
            isCurrentlyGenerating.get()
        }
    }
    
    /**
     * 获取当前模式描述（用于 UI 显示）
     */
    fun getModeDescription(): String {
        return if (nativeLibLoaded.get() && isRealInferenceSupported()) {
            if (isModelLoaded()) {
                "🧠 真实 AI 推理 (llama.cpp native)"
            } else {
                "🧠 Native 就绪 (模型未加载)"
            }
        } else if (nativeLibLoaded.get()) {
            "⚙️ Native 库已加载 (llama.cpp 未编译)"
        } else {
            "💡 模拟 AI 模式 (回复能力有限)"
        }
    }
    
    /**
     * 获取详细状态信息
     */
    fun getDetailedStatus(): Map<String, Any> {
        return mapOf(
            "nativeLoaded" to nativeLibLoaded.get(),
            "nativeLoadError" to (nativeLoadError.get() ?: "none"),
            "realInferenceSupported" to isRealInferenceSupported(),
            "modelLoaded" to isModelLoaded(),
            "isGenerating" to isGenerating(),
            "mode" to getModeDescription()
        )
    }
}

/**
 * 简单的生成回调实现
 * 方便创建回调实例
 */
open class SimpleGenerationCallback(
    private val onTokenReceived: (String) -> Unit = {},
    private val onGenerationComplete: (String) -> Unit = {},
    private val onGenerationError: (String) -> Unit = {}
) : LlamaCpp.GenerationCallback {
    
    override fun onToken(token: String) {
        onTokenReceived(token)
    }
    
    override fun onComplete(response: String) {
        onGenerationComplete(response)
    }
    
    override fun onError(error: String) {
        onGenerationError(error)
    }
}
