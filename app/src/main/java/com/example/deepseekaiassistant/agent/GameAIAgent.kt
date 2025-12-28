package com.example.deepseekaiassistant.agent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.deepseekaiassistant.root.RootManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * 游戏 AI 代理
 * 负责游戏场景下的 AI 自动操作
 * 支持 2D 休闲游戏和 3D 竞技游戏
 */
class GameAIAgent(private val context: Context) {
    
    companion object {
        private const val TAG = "GameAIAgent"
        
        // 操作循环间隔
        private const val LOOP_INTERVAL_MS = 100L
        
        // 最大连续错误次数
        private const val MAX_CONSECUTIVE_ERRORS = 5
    }
    
    // 核心模块
    private val screenVision = ScreenVision(context)
    private val actionExecutor = ActionExecutor(context)
    private val rootManager = RootManager.getInstance(context)
    
    // 执行线程
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 状态管理
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val currentStrategy = AtomicReference<SceneStrategy?>(null)
    
    // 游戏配置
    private var gamePackageName: String = ""
    private var gameType: GameType = GameType.UNKNOWN
    
    // 统计数据
    private var totalOperations = 0
    private var successfulOperations = 0
    private var errorCount = 0
    private var consecutiveErrors = 0
    private var startTime = 0L
    
    // 操作日志
    private val operationLog = CopyOnWriteArrayList<OperationLogEntry>()
    
    // 回调监听
    private var listener: GameAIListener? = null
    
    /**
     * 游戏类型
     */
    enum class GameType {
        ELIMINATE_2D,    // 2D 消消乐
        CARD_2D,         // 2D 卡牌（斗地主等）
        MOBA_3D,         // 3D MOBA（王者荣耀）
        FPS_3D,          // 3D 射击（和平精英）
        RPG_3D,          // 3D RPG（原神）
        UNKNOWN
    }
    
    /**
     * 操作日志条目
     */
    data class OperationLogEntry(
        val timestamp: Long,
        val operationType: OperationType,
        val description: String,
        val success: Boolean,
        val executionTime: Long
    )
    
    /**
     * 游戏 AI 监听器
     */
    interface GameAIListener {
        fun onStateChanged(isRunning: Boolean, isPaused: Boolean)
        fun onOperationExecuted(log: OperationLogEntry)
        fun onError(message: String)
        fun onStats(totalOps: Int, successOps: Int, runningTime: Long)
    }
    
    // ==================== 初始化和配置 ====================
    
    /**
     * 设置无障碍服务
     */
    fun setAccessibilityService(service: AccessibilityService) {
        screenVision.setAccessibilityService(service)
        actionExecutor.setAccessibilityService(service)
    }
    
    /**
     * 设置是否使用 Root 模式
     */
    fun setRootMode(enabled: Boolean) {
        actionExecutor.setRootMode(enabled)
    }
    
    /**
     * 设置监听器
     */
    fun setListener(listener: GameAIListener?) {
        this.listener = listener
    }
    
    /**
     * 配置游戏
     */
    fun configureGame(packageName: String, type: GameType) {
        this.gamePackageName = packageName
        this.gameType = type
        
        // 根据游戏类型选择策略
        val strategy = when (type) {
            GameType.ELIMINATE_2D -> EliminateGameStrategy()
            GameType.CARD_2D -> EliminateGameStrategy()  // 可以创建专用卡牌策略
            GameType.MOBA_3D -> MobaGameStrategy()
            GameType.FPS_3D -> MobaGameStrategy()  // 可以创建专用 FPS 策略
            GameType.RPG_3D -> MobaGameStrategy()  // 可以创建专用 RPG 策略
            GameType.UNKNOWN -> DefaultStrategy()
        }
        
        currentStrategy.set(strategy)
        Log.d(TAG, "游戏配置完成: $packageName, 类型: $type, 策略: ${strategy.strategyName}")
    }
    
    // ==================== 操作控制 ====================
    
    /**
     * 启动游戏 AI
     */
    fun start() {
        if (isRunning.get()) {
            Log.w(TAG, "游戏 AI 已在运行")
            return
        }
        
        if (currentStrategy.get() == null) {
            listener?.onError("请先配置游戏类型")
            return
        }
        
        isRunning.set(true)
        isPaused.set(false)
        startTime = System.currentTimeMillis()
        totalOperations = 0
        successfulOperations = 0
        errorCount = 0
        consecutiveErrors = 0
        operationLog.clear()
        
        listener?.onStateChanged(true, false)
        Log.d(TAG, "游戏 AI 启动")
        
        // 启动主循环
        executor.execute { mainLoop() }
    }
    
    /**
     * 停止游戏 AI
     */
    fun stop() {
        isRunning.set(false)
        isPaused.set(false)
        listener?.onStateChanged(false, false)
        Log.d(TAG, "游戏 AI 停止")
    }
    
    /**
     * 暂停/恢复
     */
    fun togglePause() {
        val newPauseState = !isPaused.get()
        isPaused.set(newPauseState)
        listener?.onStateChanged(isRunning.get(), newPauseState)
        Log.d(TAG, if (newPauseState) "游戏 AI 暂停" else "游戏 AI 恢复")
    }
    
    /**
     * 主操作循环
     */
    private fun mainLoop() {
        Log.d(TAG, "主循环开始")
        
        while (isRunning.get()) {
            try {
                // 检查暂停状态
                if (isPaused.get()) {
                    Thread.sleep(500)
                    continue
                }
                
                // 获取当前策略
                val strategy = currentStrategy.get()
                if (strategy == null) {
                    Log.w(TAG, "策略为空，停止运行")
                    break
                }
                
                // 1. 检查异常情况（弹窗、死亡等）
                if (strategy.handleException(screenVision, actionExecutor)) {
                    Log.d(TAG, "处理了异常情况")
                    Thread.sleep(500)
                    continue
                }
                
                // 2. 分析场景，获取操作决策
                val decisions = strategy.analyze(screenVision, actionExecutor)
                
                if (decisions.isEmpty()) {
                    // 没有操作可执行，短暂等待
                    Thread.sleep(strategy.getConfig().minOperationInterval)
                    continue
                }
                
                // 3. 执行优先级最高的操作
                val topDecision = decisions.maxByOrNull { it.priority }
                if (topDecision != null) {
                    val result = executeDecision(topDecision)
                    
                    // 记录操作日志
                    val logEntry = OperationLogEntry(
                        timestamp = System.currentTimeMillis(),
                        operationType = topDecision.type,
                        description = topDecision.description,
                        success = result.success,
                        executionTime = result.executionTime
                    )
                    operationLog.add(logEntry)
                    
                    // 更新统计
                    totalOperations++
                    if (result.success) {
                        successfulOperations++
                        consecutiveErrors = 0
                    } else {
                        errorCount++
                        consecutiveErrors++
                        
                        // 连续错误过多，暂停运行
                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            Log.e(TAG, "连续错误过多，暂停运行")
                            isPaused.set(true)
                            mainHandler.post {
                                listener?.onError("连续 $MAX_CONSECUTIVE_ERRORS 次操作失败，已暂停")
                            }
                        }
                    }
                    
                    // 通知监听器
                    mainHandler.post {
                        listener?.onOperationExecuted(logEntry)
                        listener?.onStats(totalOperations, successfulOperations, 
                            System.currentTimeMillis() - startTime)
                    }
                }
                
                // 4. 操作间隔
                val config = strategy.getConfig()
                val interval = if (config.humanSimulation) {
                    Random.nextLong(config.minOperationInterval, config.maxOperationInterval)
                } else {
                    config.minOperationInterval
                }
                Thread.sleep(interval)
                
            } catch (e: InterruptedException) {
                Log.d(TAG, "主循环被中断")
                break
            } catch (e: Exception) {
                Log.e(TAG, "主循环异常: ${e.message}")
                errorCount++
                consecutiveErrors++
                
                mainHandler.post {
                    listener?.onError("操作异常: ${e.message}")
                }
                
                Thread.sleep(1000)
            }
        }
        
        Log.d(TAG, "主循环结束")
    }
    
    /**
     * 执行操作决策
     */
    private fun executeDecision(decision: OperationDecision): ActionExecutor.ActionResult {
        Log.d(TAG, "执行操作: ${decision.description}")
        
        return when (decision.type) {
            OperationType.CLICK -> {
                val target = decision.target
                if (target != null) {
                    actionExecutor.click(target.bounds)
                } else {
                    ActionExecutor.ActionResult(false, "无目标", 0)
                }
            }
            
            OperationType.LONG_PRESS -> {
                val target = decision.target
                val duration = decision.params["duration"] as? Long ?: 500L
                if (target != null) {
                    actionExecutor.longPress(target.bounds.centerX(), target.bounds.centerY(), duration)
                } else {
                    ActionExecutor.ActionResult(false, "无目标", 0)
                }
            }
            
            OperationType.SWIPE -> {
                val target = decision.target
                val endX = decision.params["endX"] as? Int
                val endY = decision.params["endY"] as? Int
                val duration = decision.params["duration"] as? Long ?: 300L
                val direction = decision.params["direction"] as? String
                
                when {
                    direction == "up" -> actionExecutor.swipeUp(
                        decision.params["distance"] as? Int ?: 500,
                        duration
                    )
                    direction == "down" -> actionExecutor.swipeDown(
                        decision.params["distance"] as? Int ?: 500,
                        duration
                    )
                    direction == "left" -> actionExecutor.swipeLeft(
                        decision.params["distance"] as? Int ?: 500,
                        duration
                    )
                    direction == "right" -> actionExecutor.swipeRight(
                        decision.params["distance"] as? Int ?: 500,
                        duration
                    )
                    target != null && endX != null && endY != null -> {
                        actionExecutor.swipe(
                            target.bounds.centerX(), target.bounds.centerY(),
                            endX, endY, duration
                        )
                    }
                    else -> ActionExecutor.ActionResult(false, "滑动参数不完整", 0)
                }
            }
            
            OperationType.INPUT_TEXT -> {
                val text = decision.params["text"] as? String ?: ""
                actionExecutor.inputText(text)
            }
            
            OperationType.WAIT -> {
                val duration = decision.params["duration"] as? Long ?: 1000L
                try {
                    Thread.sleep(duration)
                    ActionExecutor.ActionResult(true, "等待 ${duration}ms", duration)
                } catch (e: InterruptedException) {
                    ActionExecutor.ActionResult(false, "等待被中断", 0)
                }
            }
            
            OperationType.BACK -> actionExecutor.pressBack()
            
            OperationType.HOME -> actionExecutor.pressHome()
            
            OperationType.MULTI_TOUCH -> {
                val touch1X = decision.params["touch1X"] as? Int ?: 0
                val touch1Y = decision.params["touch1Y"] as? Int ?: 0
                val touch2X = decision.params["touch2X"] as? Int ?: 0
                val touch2Y = decision.params["touch2Y"] as? Int ?: 0
                
                actionExecutor.multiTouch(
                    ActionExecutor.TouchPoint(touch1X, touch1Y),
                    ActionExecutor.TouchPoint(touch2X, touch2Y)
                )
            }
            
            OperationType.JOYSTICK -> {
                val target = decision.target
                val directionX = decision.params["directionX"] as? Float ?: 0f
                val directionY = decision.params["directionY"] as? Float ?: 0f
                
                if (target != null) {
                    actionExecutor.joystickMove(
                        target.bounds.centerX(), target.bounds.centerY(),
                        directionX, directionY
                    )
                } else {
                    ActionExecutor.ActionResult(false, "无摇杆目标", 0)
                }
            }
            
            OperationType.RAPID_CLICK -> {
                val target = decision.target
                val count = decision.params["count"] as? Int ?: 5
                val interval = decision.params["interval"] as? Long ?: 100L
                
                if (target != null) {
                    actionExecutor.rapidClick(
                        target.bounds.centerX(), target.bounds.centerY(),
                        count, interval
                    )
                } else {
                    ActionExecutor.ActionResult(false, "无目标", 0)
                }
            }
            
            OperationType.NONE -> {
                ActionExecutor.ActionResult(true, "无操作", 0)
            }
        }
    }
    
    // ==================== 游戏专属功能 ====================
    
    /**
     * 启动游戏
     */
    fun launchGame(): ActionExecutor.ActionResult {
        if (gamePackageName.isEmpty()) {
            return ActionExecutor.ActionResult(false, "未配置游戏包名", 0)
        }
        return actionExecutor.launchApp(gamePackageName)
    }
    
    /**
     * 更新游戏策略（用于动态调整）
     */
    fun updateStrategy(strategy: SceneStrategy) {
        currentStrategy.set(strategy)
        Log.d(TAG, "策略更新: ${strategy.strategyName}")
    }
    
    /**
     * 获取 MOBA 策略并更新阶段
     */
    fun setMobaPhase(phase: MobaGameStrategy.GamePhase) {
        val strategy = currentStrategy.get()
        if (strategy is MobaGameStrategy) {
            strategy.updatePhase(phase)
        }
    }
    
    // ==================== 统计和日志 ====================
    
    /**
     * 获取运行统计
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "isRunning" to isRunning.get(),
            "isPaused" to isPaused.get(),
            "totalOperations" to totalOperations,
            "successfulOperations" to successfulOperations,
            "errorCount" to errorCount,
            "successRate" to if (totalOperations > 0) 
                (successfulOperations * 100 / totalOperations) else 0,
            "runningTime" to if (startTime > 0) 
                (System.currentTimeMillis() - startTime) else 0,
            "currentStrategy" to (currentStrategy.get()?.strategyName ?: "无")
        )
    }
    
    /**
     * 获取操作日志
     */
    fun getOperationLog(limit: Int = 50): List<OperationLogEntry> {
        return if (operationLog.size > limit) {
            operationLog.takeLast(limit)
        } else {
            operationLog.toList()
        }
    }
    
    /**
     * 清空日志
     */
    fun clearLog() {
        operationLog.clear()
    }
    
    // ==================== 资源清理 ====================
    
    fun release() {
        stop()
        screenVision.release()
        actionExecutor.release()
        operationLog.clear()
        listener = null
    }
}

/**
 * 多场景 AI 代理
 * 统一管理各种生活场景的 AI 自动操作
 */
class MultiSceneAIAgent(private val context: Context) {
    
    companion object {
        private const val TAG = "MultiSceneAgent"
    }
    
    // 核心模块
    private val screenVision = ScreenVision(context)
    private val actionExecutor = ActionExecutor(context)
    
    // 执行线程
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 状态
    private val isRunning = AtomicBoolean(false)
    private val currentStrategy = AtomicReference<SceneStrategy?>(null)
    
    // 回调
    private var listener: MultiSceneListener? = null
    
    /**
     * 场景监听器
     */
    interface MultiSceneListener {
        fun onStateChanged(isRunning: Boolean)
        fun onSceneDetected(sceneType: ScreenVision.SceneType)
        fun onOperationComplete(description: String, success: Boolean)
        fun onError(message: String)
    }
    
    /**
     * 设置无障碍服务
     */
    fun setAccessibilityService(service: AccessibilityService) {
        screenVision.setAccessibilityService(service)
        actionExecutor.setAccessibilityService(service)
    }
    
    /**
     * 设置 Root 模式
     */
    fun setRootMode(enabled: Boolean) {
        actionExecutor.setRootMode(enabled)
    }
    
    /**
     * 设置监听器
     */
    fun setListener(listener: MultiSceneListener?) {
        this.listener = listener
    }
    
    // ==================== 场景检测与自动适配 ====================
    
    /**
     * 自动检测并适配场景
     */
    fun autoDetectScene(): ScreenVision.SceneType {
        val sceneType = screenVision.detectSceneType()
        val strategy = StrategyFactory.createStrategy(sceneType)
        currentStrategy.set(strategy)
        
        mainHandler.post {
            listener?.onSceneDetected(sceneType)
        }
        
        Log.d(TAG, "检测到场景: $sceneType, 策略: ${strategy.strategyName}")
        return sceneType
    }
    
    /**
     * 手动设置场景策略
     */
    fun setStrategy(strategy: SceneStrategy) {
        currentStrategy.set(strategy)
        Log.d(TAG, "设置策略: ${strategy.strategyName}")
    }
    
    // ==================== 快捷操作 ====================
    
    /**
     * 短视频自动刷取
     */
    fun startShortVideoMode(preferences: List<String> = emptyList()) {
        val strategy = ShortVideoStrategy()
        if (preferences.isNotEmpty()) {
            strategy.setPreferences(preferences)
        }
        currentStrategy.set(strategy)
        start()
    }
    
    /**
     * 电商抢购模式
     */
    fun startECommerceMode(targetTimeMillis: Long = 0) {
        val strategy = ECommerceStrategy()
        if (targetTimeMillis > 0) {
            strategy.setTargetTime(targetTimeMillis)
        }
        currentStrategy.set(strategy)
        start()
    }
    
    /**
     * 外卖点餐模式
     */
    fun startFoodDeliveryMode(
        restaurant: String,
        dishes: List<String>,
        address: String
    ) {
        val strategy = FoodDeliveryStrategy()
        strategy.setOrder(restaurant, dishes, address)
        currentStrategy.set(strategy)
        start()
    }
    
    // ==================== 控制 ====================
    
    /**
     * 启动
     */
    fun start() {
        if (isRunning.get()) return
        
        val strategy = currentStrategy.get()
        if (strategy == null) {
            listener?.onError("请先选择或检测场景")
            return
        }
        
        isRunning.set(true)
        listener?.onStateChanged(true)
        
        executor.execute { runLoop() }
    }
    
    /**
     * 停止
     */
    fun stop() {
        isRunning.set(false)
        listener?.onStateChanged(false)
    }
    
    /**
     * 主循环
     */
    private fun runLoop() {
        while (isRunning.get()) {
            try {
                val strategy = currentStrategy.get() ?: break
                
                // 处理异常
                if (strategy.handleException(screenVision, actionExecutor)) {
                    Thread.sleep(500)
                    continue
                }
                
                // 分析并执行
                val decisions = strategy.analyze(screenVision, actionExecutor)
                val topDecision = decisions.maxByOrNull { it.priority }
                
                if (topDecision != null && topDecision.type != OperationType.NONE) {
                    val result = executeDecision(topDecision)
                    
                    mainHandler.post {
                        listener?.onOperationComplete(topDecision.description, result.success)
                    }
                }
                
                // 间隔
                val config = strategy.getConfig()
                val interval = Random.nextLong(config.minOperationInterval, config.maxOperationInterval)
                Thread.sleep(interval)
                
            } catch (e: Exception) {
                Log.e(TAG, "运行异常: ${e.message}")
                mainHandler.post {
                    listener?.onError("操作异常: ${e.message}")
                }
                Thread.sleep(1000)
            }
        }
    }
    
    private fun executeDecision(decision: OperationDecision): ActionExecutor.ActionResult {
        return when (decision.type) {
            OperationType.CLICK -> {
                decision.target?.let { actionExecutor.click(it.bounds) }
                    ?: ActionExecutor.ActionResult(false, "无目标", 0)
            }
            OperationType.SWIPE -> {
                val direction = decision.params["direction"] as? String
                val distance = decision.params["distance"] as? Int ?: 500
                val duration = decision.params["duration"] as? Long ?: 300L
                
                when (direction) {
                    "up" -> actionExecutor.swipeUp(distance, duration)
                    "down" -> actionExecutor.swipeDown(distance, duration)
                    "left" -> actionExecutor.swipeLeft(distance, duration)
                    "right" -> actionExecutor.swipeRight(distance, duration)
                    else -> ActionExecutor.ActionResult(false, "未知方向", 0)
                }
            }
            OperationType.INPUT_TEXT -> {
                val text = decision.params["text"] as? String ?: ""
                actionExecutor.inputText(text)
            }
            OperationType.WAIT -> {
                val duration = decision.params["duration"] as? Long ?: 1000L
                Thread.sleep(duration)
                ActionExecutor.ActionResult(true, "等待完成", duration)
            }
            OperationType.BACK -> actionExecutor.pressBack()
            else -> ActionExecutor.ActionResult(true, "跳过", 0)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
        screenVision.release()
        actionExecutor.release()
        listener = null
    }
}
