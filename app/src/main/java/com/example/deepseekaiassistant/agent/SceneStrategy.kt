package com.example.deepseekaiassistant.agent

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log

/**
 * 场景策略接口
 * 定义 AI 在不同场景下的决策逻辑
 */
interface SceneStrategy {
    
    /**
     * 策略名称
     */
    val strategyName: String
    
    /**
     * 分析当前场景，生成操作决策
     */
    fun analyze(vision: ScreenVision, executor: ActionExecutor): List<OperationDecision>
    
    /**
     * 检查是否需要处理异常情况
     */
    fun handleException(vision: ScreenVision, executor: ActionExecutor): Boolean
    
    /**
     * 获取策略配置
     */
    fun getConfig(): StrategyConfig
}

/**
 * 操作决策
 */
data class OperationDecision(
    val type: OperationType,
    val target: OperationTarget?,
    val priority: Int,          // 优先级 1-10，越大越优先
    val description: String,
    val params: Map<String, Any> = emptyMap()
)

/**
 * 操作类型
 */
enum class OperationType {
    CLICK,           // 点击
    LONG_PRESS,      // 长按
    SWIPE,           // 滑动
    INPUT_TEXT,      // 输入文本
    WAIT,            // 等待
    BACK,            // 返回
    HOME,            // 回主页
    MULTI_TOUCH,     // 多指操作
    JOYSTICK,        // 摇杆操作
    RAPID_CLICK,     // 连点
    NONE             // 无操作
}

/**
 * 操作目标
 */
data class OperationTarget(
    val bounds: Rect,
    val label: String,
    val confidence: Float
)

/**
 * 策略配置
 */
data class StrategyConfig(
    val minOperationInterval: Long = 500,   // 最小操作间隔（毫秒）
    val maxOperationInterval: Long = 2000,  // 最大操作间隔（毫秒）
    val humanSimulation: Boolean = true,    // 是否模拟人类操作
    val maxRetryCount: Int = 3,             // 最大重试次数
    val timeoutMs: Long = 30000             // 超时时间
)

// ==================== 游戏策略 ====================

/**
 * 2D 消消乐游戏策略
 */
class EliminateGameStrategy : SceneStrategy {
    
    companion object {
        private const val TAG = "EliminateStrategy"
    }
    
    override val strategyName = "消消乐游戏策略"
    
    private var boardAnalyzed = false
    private var lastMoveTime = 0L
    
    override fun analyze(vision: ScreenVision, executor: ActionExecutor): List<OperationDecision> {
        val decisions = mutableListOf<OperationDecision>()
        
        // 截取游戏画面
        val screenshot = vision.getLastScreenshot() ?: vision.takeScreenshot()
        if (screenshot == null) {
            Log.w(TAG, "无法获取游戏截图")
            return decisions
        }
        
        // 分析可消除的棋子
        val eliminatePos = analyzeEliminatePositions(screenshot)
        
        if (eliminatePos != null) {
            decisions.add(
                OperationDecision(
                    type = OperationType.SWIPE,
                    target = OperationTarget(
                        bounds = eliminatePos.first,
                        label = "起始棋子",
                        confidence = 0.8f
                    ),
                    priority = 8,
                    description = "交换棋子消除",
                    params = mapOf(
                        "endX" to eliminatePos.second.centerX(),
                        "endY" to eliminatePos.second.centerY(),
                        "duration" to 400L
                    )
                )
            )
        } else {
            // 没有找到可消除的，等待一下
            decisions.add(
                OperationDecision(
                    type = OperationType.WAIT,
                    target = null,
                    priority = 1,
                    description = "等待动画完成",
                    params = mapOf("duration" to 1000L)
                )
            )
        }
        
        return decisions
    }
    
    override fun handleException(vision: ScreenVision, executor: ActionExecutor): Boolean {
        // 检查是否有弹窗
        val failText = vision.findTextOnScreen("失败")
        if (failText != null) {
            val retryButton = vision.findElementByText("重玩")
            if (retryButton != null) {
                executor.click(retryButton)
                Log.d(TAG, "检测到关卡失败，点击重玩")
                return true
            }
        }
        
        // 检查广告弹窗
        val closeButton = vision.findElementByText("关闭")
            ?: vision.findElementByText("跳过")
            ?: vision.findElementById("close")
        
        if (closeButton != null) {
            executor.click(closeButton)
            Log.d(TAG, "关闭广告弹窗")
            return true
        }
        
        return false
    }
    
    override fun getConfig(): StrategyConfig {
        return StrategyConfig(
            minOperationInterval = 1000,
            maxOperationInterval = 2000,
            humanSimulation = true
        )
    }
    
    /**
     * 分析可消除的棋子位置
     * 返回 Pair<起始位置, 目标位置>
     */
    private fun analyzeEliminatePositions(screenshot: Bitmap): Pair<Rect, Rect>? {
        val width = screenshot.width
        val height = screenshot.height
        
        // 假设游戏区域在屏幕中央
        val gameAreaTop = height / 4
        val gameAreaBottom = height * 3 / 4
        val gameAreaLeft = width / 8
        val gameAreaRight = width * 7 / 8
        
        // 将游戏区域分成 8x8 的网格
        val cellWidth = (gameAreaRight - gameAreaLeft) / 8
        val cellHeight = (gameAreaBottom - gameAreaTop) / 8
        
        // 获取每个格子的主要颜色
        val board = Array(8) { row ->
            Array(8) { col ->
                val centerX = gameAreaLeft + col * cellWidth + cellWidth / 2
                val centerY = gameAreaTop + row * cellHeight + cellHeight / 2
                if (centerX < width && centerY < height) {
                    screenshot.getPixel(centerX, centerY)
                } else {
                    0
                }
            }
        }
        
        // 查找可消除的组合（简化算法：相邻相同颜色）
        for (row in 0 until 8) {
            for (col in 0 until 7) {
                if (isSimilarColor(board[row][col], board[row][col + 1])) {
                    // 找到相邻相同颜色，返回交换位置
                    val rect1 = Rect(
                        gameAreaLeft + col * cellWidth,
                        gameAreaTop + row * cellHeight,
                        gameAreaLeft + (col + 1) * cellWidth,
                        gameAreaTop + (row + 1) * cellHeight
                    )
                    val rect2 = Rect(
                        gameAreaLeft + (col + 1) * cellWidth,
                        gameAreaTop + row * cellHeight,
                        gameAreaLeft + (col + 2) * cellWidth,
                        gameAreaTop + (row + 1) * cellHeight
                    )
                    return Pair(rect1, rect2)
                }
            }
        }
        
        return null
    }
    
    private fun isSimilarColor(color1: Int, color2: Int): Boolean {
        val r1 = android.graphics.Color.red(color1)
        val g1 = android.graphics.Color.green(color1)
        val b1 = android.graphics.Color.blue(color1)
        val r2 = android.graphics.Color.red(color2)
        val g2 = android.graphics.Color.green(color2)
        val b2 = android.graphics.Color.blue(color2)
        
        val diff = kotlin.math.abs(r1 - r2) + kotlin.math.abs(g1 - g2) + kotlin.math.abs(b1 - b2)
        return diff < 50
    }
}

/**
 * MOBA 游戏策略（王者荣耀/英雄联盟手游）
 */
class MobaGameStrategy : SceneStrategy {
    
    companion object {
        private const val TAG = "MobaStrategy"
    }
    
    override val strategyName = "MOBA游戏策略"
    
    // 策略状态
    private var currentPhase = GamePhase.LANING  // 当前阶段
    private var heroHp = 100
    private var heroMp = 100
    private var skillCooldowns = intArrayOf(0, 0, 0, 0)
    
    enum class GamePhase {
        LANING,      // 对线期
        TEAM_FIGHT,  // 团战期
        RETREAT      // 撤退期
    }
    
    override fun analyze(vision: ScreenVision, executor: ActionExecutor): List<OperationDecision> {
        val decisions = mutableListOf<OperationDecision>()
        
        val screenshot = vision.getLastScreenshot() ?: vision.takeScreenshot()
        if (screenshot == null) return decisions
        
        // 识别血条
        val healthBars = vision.detectHealthBars(screenshot)
        val enemyBars = healthBars.filter { it.label == "enemy_health_bar" }
        val allyBars = healthBars.filter { it.label == "ally_health_bar" }
        
        // 识别技能按钮
        val skillButtons = vision.detectSkillButtons(screenshot)
        
        // 识别虚拟摇杆
        val joystick = vision.detectJoystick(screenshot)
        
        // 根据阶段制定策略
        when (currentPhase) {
            GamePhase.LANING -> {
                // 对线期策略：补兵 > 消耗 > 躲避
                if (enemyBars.isNotEmpty()) {
                    val nearestEnemy = enemyBars.minByOrNull { it.bounds.centerY() }
                    if (nearestEnemy != null) {
                        // 攻击最近的敌人
                        decisions.add(
                            OperationDecision(
                                type = OperationType.CLICK,
                                target = OperationTarget(
                                    bounds = nearestEnemy.bounds,
                                    label = "敌方单位",
                                    confidence = nearestEnemy.confidence
                                ),
                                priority = 7,
                                description = "攻击敌方单位"
                            )
                        )
                    }
                }
                
                // 如果有技能可用，释放技能
                if (skillButtons.isNotEmpty()) {
                    decisions.add(
                        OperationDecision(
                            type = OperationType.CLICK,
                            target = OperationTarget(
                                bounds = skillButtons.first().bounds,
                                label = "技能按钮",
                                confidence = skillButtons.first().confidence
                            ),
                            priority = 8,
                            description = "释放技能"
                        )
                    )
                }
            }
            
            GamePhase.TEAM_FIGHT -> {
                // 团战策略：优先击杀残血 > 保护C位 > 控场
                val lowHpEnemy = enemyBars.filter { 
                    // 假设血条宽度代表血量比例，较短的是残血
                    it.bounds.width() < 50 
                }.minByOrNull { it.bounds.width() }
                
                if (lowHpEnemy != null) {
                    decisions.add(
                        OperationDecision(
                            type = OperationType.CLICK,
                            target = OperationTarget(
                                bounds = lowHpEnemy.bounds,
                                label = "残血敌人",
                                confidence = lowHpEnemy.confidence
                            ),
                            priority = 10,
                            description = "击杀残血敌人"
                        )
                    )
                }
            }
            
            GamePhase.RETREAT -> {
                // 撤退策略：向己方防御塔方向移动
                if (joystick != null) {
                    decisions.add(
                        OperationDecision(
                            type = OperationType.JOYSTICK,
                            target = OperationTarget(
                                bounds = joystick.bounds,
                                label = "虚拟摇杆",
                                confidence = joystick.confidence
                            ),
                            priority = 10,
                            description = "向后撤退",
                            params = mapOf(
                                "directionX" to 0f,
                                "directionY" to 1f  // 向下移动（通常是己方阵营方向）
                            )
                        )
                    )
                }
            }
        }
        
        return decisions.sortedByDescending { it.priority }
    }
    
    override fun handleException(vision: ScreenVision, executor: ActionExecutor): Boolean {
        // 检查是否死亡
        val deathText = vision.findTextOnScreen("复活")
        if (deathText != null) {
            Log.d(TAG, "检测到死亡状态")
            // 等待复活
            return true
        }
        
        // 检查是否有系统弹窗
        val confirmButton = vision.findElementByText("确定")
            ?: vision.findElementByText("继续")
        
        if (confirmButton != null) {
            executor.click(confirmButton)
            return true
        }
        
        return false
    }
    
    override fun getConfig(): StrategyConfig {
        return StrategyConfig(
            minOperationInterval = 50,   // 游戏操作需要快速响应
            maxOperationInterval = 200,
            humanSimulation = false      // 游戏中不需要模拟人类延迟
        )
    }
    
    /**
     * 更新游戏阶段（可由 AI 或外部调用）
     */
    fun updatePhase(phase: GamePhase) {
        this.currentPhase = phase
        Log.d(TAG, "游戏阶段更新: $phase")
    }
}

// ==================== 生活场景策略 ====================

/**
 * 短视频策略（抖音/快手）
 */
class ShortVideoStrategy : SceneStrategy {
    
    companion object {
        private const val TAG = "ShortVideoStrategy"
    }
    
    override val strategyName = "短视频策略"
    
    private var watchedCount = 0
    private var likedCount = 0
    
    // 用户偏好关键词
    private val preferredKeywords = mutableListOf("宠物", "搞笑", "美食", "旅行")
    
    override fun analyze(vision: ScreenVision, executor: ActionExecutor): List<OperationDecision> {
        val decisions = mutableListOf<OperationDecision>()
        
        // 获取屏幕上的文本
        val allText = vision.getAllVisibleText()
        
        // 检查视频内容是否符合偏好
        val videoContent = allText.map { it.text }.joinToString(" ")
        val isPreferred = preferredKeywords.any { keyword ->
            videoContent.contains(keyword, ignoreCase = true)
        }
        
        if (isPreferred) {
            // 找到点赞按钮
            val likeButton = vision.findElementByText("赞")
                ?: vision.findElementById("like")
            
            if (likeButton != null) {
                decisions.add(
                    OperationDecision(
                        type = OperationType.CLICK,
                        target = OperationTarget(
                            bounds = likeButton.bounds,
                            label = "点赞按钮",
                            confidence = 0.9f
                        ),
                        priority = 7,
                        description = "点赞喜欢的视频"
                    )
                )
                likedCount++
            }
        }
        
        // 下滑到下一个视频
        decisions.add(
            OperationDecision(
                type = OperationType.SWIPE,
                target = null,
                priority = 5,
                description = "下滑到下一个视频",
                params = mapOf(
                    "direction" to "up",
                    "distance" to 800,
                    "duration" to 300L
                )
            )
        )
        
        watchedCount++
        
        return decisions
    }
    
    override fun handleException(vision: ScreenVision, executor: ActionExecutor): Boolean {
        // 检查直播弹窗
        val closeButton = vision.findElementByText("关闭")
            ?: vision.findElementByText("跳过")
            ?: vision.findElementById("close")
        
        if (closeButton != null) {
            executor.click(closeButton)
            Log.d(TAG, "关闭弹窗")
            return true
        }
        
        // 检查广告
        val skipAd = vision.findElementByText("跳过广告")
        if (skipAd != null) {
            executor.click(skipAd)
            Log.d(TAG, "跳过广告")
            return true
        }
        
        return false
    }
    
    override fun getConfig(): StrategyConfig {
        return StrategyConfig(
            minOperationInterval = 3000,  // 每个视频至少看3秒
            maxOperationInterval = 10000, // 最多看10秒
            humanSimulation = true
        )
    }
    
    /**
     * 设置用户偏好
     */
    fun setPreferences(keywords: List<String>) {
        preferredKeywords.clear()
        preferredKeywords.addAll(keywords)
    }
    
    /**
     * 获取统计数据
     */
    fun getStats(): Pair<Int, Int> = Pair(watchedCount, likedCount)
}

/**
 * 电商抢购策略（淘宝/京东）
 */
class ECommerceStrategy : SceneStrategy {
    
    companion object {
        private const val TAG = "ECommerceStrategy"
    }
    
    override val strategyName = "电商抢购策略"
    
    private var targetTime: Long = 0  // 目标抢购时间
    private var isReady = false
    
    override fun analyze(vision: ScreenVision, executor: ActionExecutor): List<OperationDecision> {
        val decisions = mutableListOf<OperationDecision>()
        
        // 查找抢购/立即购买按钮
        val buyButton = vision.findElementByText("立即抢购")
            ?: vision.findElementByText("立即购买")
            ?: vision.findElementByText("马上抢")
            ?: vision.findElementByText("立即领取")
        
        if (buyButton != null && buyButton.isEnabled) {
            decisions.add(
                OperationDecision(
                    type = OperationType.CLICK,
                    target = OperationTarget(
                        bounds = buyButton.bounds,
                        label = "抢购按钮",
                        confidence = 1.0f
                    ),
                    priority = 10,
                    description = "点击抢购按钮"
                )
            )
        }
        
        // 检查倒计时
        val countdownText = vision.findNumbersOnScreen().firstOrNull { result ->
            result.text.contains(":") || result.text.contains("秒")
        }
        
        if (countdownText != null) {
            Log.d(TAG, "检测到倒计时: ${countdownText.text}")
            // 如果还有倒计时，等待
            if (!isReady) {
                decisions.add(
                    OperationDecision(
                        type = OperationType.WAIT,
                        target = null,
                        priority = 1,
                        description = "等待倒计时结束",
                        params = mapOf("duration" to 500L)
                    )
                )
            }
        }
        
        return decisions
    }
    
    override fun handleException(vision: ScreenVision, executor: ActionExecutor): Boolean {
        // 检查验证码弹窗
        val captchaText = vision.findTextOnScreen("验证")
        if (captchaText != null) {
            Log.w(TAG, "检测到验证码，需要人工处理")
            // 通知用户处理验证码
            return true
        }
        
        // 检查库存不足
        val outOfStock = vision.findTextOnScreen("已售罄")
            ?: vision.findTextOnScreen("库存不足")
        
        if (outOfStock != null) {
            Log.w(TAG, "商品已售罄")
            return true
        }
        
        return false
    }
    
    override fun getConfig(): StrategyConfig {
        return StrategyConfig(
            minOperationInterval = 100,  // 抢购需要快速响应
            maxOperationInterval = 200,
            humanSimulation = false      // 抢购不需要模拟人类延迟
        )
    }
    
    /**
     * 设置目标抢购时间
     */
    fun setTargetTime(timeMillis: Long) {
        this.targetTime = timeMillis
    }
}

/**
 * 外卖点餐策略（美团/饿了么）
 */
class FoodDeliveryStrategy : SceneStrategy {
    
    companion object {
        private const val TAG = "FoodDeliveryStrategy"
    }
    
    override val strategyName = "外卖点餐策略"
    
    // 订单配置
    private var restaurantName: String = ""
    private var dishNames: List<String> = emptyList()
    private var address: String = ""
    
    enum class OrderStep {
        SEARCH_RESTAURANT,  // 搜索店铺
        SELECT_DISHES,      // 选择菜品
        CHECKOUT,           // 结算
        CONFIRM_ORDER,      // 确认订单
        PAYMENT,            // 支付
        COMPLETE            // 完成
    }
    
    private var currentStep = OrderStep.SEARCH_RESTAURANT
    
    override fun analyze(vision: ScreenVision, executor: ActionExecutor): List<OperationDecision> {
        val decisions = mutableListOf<OperationDecision>()
        
        when (currentStep) {
            OrderStep.SEARCH_RESTAURANT -> {
                // 查找搜索框
                val searchBox = vision.findElementById("search")
                    ?: vision.findElementByText("搜索")
                
                if (searchBox != null) {
                    decisions.add(
                        OperationDecision(
                            type = OperationType.CLICK,
                            target = OperationTarget(
                                bounds = searchBox.bounds,
                                label = "搜索框",
                                confidence = 0.9f
                            ),
                            priority = 8,
                            description = "点击搜索框"
                        )
                    )
                    
                    decisions.add(
                        OperationDecision(
                            type = OperationType.INPUT_TEXT,
                            target = null,
                            priority = 7,
                            description = "输入店铺名",
                            params = mapOf("text" to restaurantName)
                        )
                    )
                }
            }
            
            OrderStep.SELECT_DISHES -> {
                // 查找要添加的菜品
                for (dishName in dishNames) {
                    val dish = vision.findElementByText(dishName)
                    if (dish != null) {
                        val addButton = vision.findElementByText("+")
                            ?: vision.findElementById("add")
                        
                        if (addButton != null) {
                            decisions.add(
                                OperationDecision(
                                    type = OperationType.CLICK,
                                    target = OperationTarget(
                                        bounds = addButton.bounds,
                                        label = "添加按钮",
                                        confidence = 0.9f
                                    ),
                                    priority = 8,
                                    description = "添加菜品: $dishName"
                                )
                            )
                        }
                    }
                }
            }
            
            OrderStep.CHECKOUT -> {
                val checkoutButton = vision.findElementByText("去结算")
                    ?: vision.findElementByText("结算")
                
                if (checkoutButton != null) {
                    decisions.add(
                        OperationDecision(
                            type = OperationType.CLICK,
                            target = OperationTarget(
                                bounds = checkoutButton.bounds,
                                label = "结算按钮",
                                confidence = 0.9f
                            ),
                            priority = 9,
                            description = "点击结算"
                        )
                    )
                    currentStep = OrderStep.CONFIRM_ORDER
                }
            }
            
            OrderStep.CONFIRM_ORDER -> {
                val submitButton = vision.findElementByText("提交订单")
                
                if (submitButton != null) {
                    decisions.add(
                        OperationDecision(
                            type = OperationType.CLICK,
                            target = OperationTarget(
                                bounds = submitButton.bounds,
                                label = "提交订单",
                                confidence = 0.9f
                            ),
                            priority = 9,
                            description = "提交订单"
                        )
                    )
                    currentStep = OrderStep.PAYMENT
                }
            }
            
            OrderStep.PAYMENT -> {
                // 支付环节暂停，提醒用户确认
                Log.d(TAG, "到达支付环节，暂停自动操作")
                decisions.add(
                    OperationDecision(
                        type = OperationType.NONE,
                        target = null,
                        priority = 10,
                        description = "支付环节，需要用户确认"
                    )
                )
            }
            
            OrderStep.COMPLETE -> {
                Log.d(TAG, "订单已完成")
            }
        }
        
        return decisions
    }
    
    override fun handleException(vision: ScreenVision, executor: ActionExecutor): Boolean {
        // 检查店铺休息
        val closedText = vision.findTextOnScreen("休息中")
            ?: vision.findTextOnScreen("已打烊")
        
        if (closedText != null) {
            Log.w(TAG, "店铺已休息")
            return true
        }
        
        // 检查配送费提示
        val deliveryFeeText = vision.findTextOnScreen("配送费")
        if (deliveryFeeText != null) {
            // 继续流程
            return false
        }
        
        return false
    }
    
    override fun getConfig(): StrategyConfig {
        return StrategyConfig(
            minOperationInterval = 1000,
            maxOperationInterval = 3000,
            humanSimulation = true
        )
    }
    
    /**
     * 设置订单配置
     */
    fun setOrder(restaurant: String, dishes: List<String>, deliveryAddress: String) {
        this.restaurantName = restaurant
        this.dishNames = dishes
        this.address = deliveryAddress
        this.currentStep = OrderStep.SEARCH_RESTAURANT
    }
}

/**
 * 策略工厂
 */
object StrategyFactory {
    
    fun createStrategy(sceneType: ScreenVision.SceneType): SceneStrategy {
        return when (sceneType) {
            ScreenVision.SceneType.GAME -> MobaGameStrategy()  // 默认使用 MOBA 策略
            ScreenVision.SceneType.SHORT_VIDEO -> ShortVideoStrategy()
            ScreenVision.SceneType.E_COMMERCE -> ECommerceStrategy()
            ScreenVision.SceneType.FOOD_DELIVERY -> FoodDeliveryStrategy()
            ScreenVision.SceneType.SYSTEM_SETTINGS -> SystemSettingsStrategy()
            ScreenVision.SceneType.UNKNOWN -> DefaultStrategy()
        }
    }
    
    fun createGameStrategy(gameType: String): SceneStrategy {
        return when {
            gameType.contains("消消乐") || gameType.contains("match") -> EliminateGameStrategy()
            gameType.contains("王者") || gameType.contains("moba") -> MobaGameStrategy()
            else -> MobaGameStrategy()
        }
    }
}

/**
 * 系统设置策略
 */
class SystemSettingsStrategy : SceneStrategy {
    
    override val strategyName = "系统设置策略"
    
    override fun analyze(vision: ScreenVision, executor: ActionExecutor): List<OperationDecision> {
        return emptyList()  // 系统设置主要通过 Root 命令控制
    }
    
    override fun handleException(vision: ScreenVision, executor: ActionExecutor): Boolean {
        return false
    }
    
    override fun getConfig(): StrategyConfig {
        return StrategyConfig()
    }
}

/**
 * 默认策略
 */
class DefaultStrategy : SceneStrategy {
    
    override val strategyName = "默认策略"
    
    override fun analyze(vision: ScreenVision, executor: ActionExecutor): List<OperationDecision> {
        return emptyList()
    }
    
    override fun handleException(vision: ScreenVision, executor: ActionExecutor): Boolean {
        // 通用异常处理：关闭弹窗
        val closeButton = vision.findElementByText("关闭")
            ?: vision.findElementByText("取消")
            ?: vision.findElementById("close")
        
        if (closeButton != null) {
            executor.click(closeButton)
            return true
        }
        
        return false
    }
    
    override fun getConfig(): StrategyConfig {
        return StrategyConfig()
    }
}
