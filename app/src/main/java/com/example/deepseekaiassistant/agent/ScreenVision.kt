package com.example.deepseekaiassistant.agent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.example.deepseekaiassistant.root.RootManager
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 视觉感知模块
 * 负责截图、OCR文字识别、图像识别、UI元素定位
 * 支持 Root 和非 Root 两种模式
 */
class ScreenVision(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenVision"
        
        // 截图缓存目录
        private const val SCREENSHOT_DIR = "screenshots"
        
        // 颜色识别阈值
        private const val COLOR_SIMILARITY_THRESHOLD = 30
    }
    
    private val rootManager = RootManager.getInstance(context)
    private var accessibilityService: AccessibilityService? = null
    private var lastScreenshot: Bitmap? = null
    private var lastScreenshotTime: Long = 0
    
    // 识别结果缓存
    private val recognizedElements = CopyOnWriteArrayList<UIElement>()
    
    /**
     * UI 元素数据类
     */
    data class UIElement(
        val id: String,
        val text: String,
        val contentDescription: String,
        val className: String,
        val bounds: Rect,
        val isClickable: Boolean,
        val isScrollable: Boolean,
        val isEnabled: Boolean,
        val confidence: Float = 1.0f  // 识别置信度
    )
    
    /**
     * 图像识别结果
     */
    data class ImageRecognitionResult(
        val label: String,           // 识别标签（如 "敌人"、"血条"、"按钮"）
        val bounds: Rect,            // 位置边界
        val confidence: Float,       // 置信度 0-1
        val color: Int? = null,      // 主要颜色
        val additionalInfo: Map<String, Any> = emptyMap()
    )
    
    /**
     * OCR 识别结果
     */
    data class OCRResult(
        val text: String,
        val bounds: Rect,
        val confidence: Float
    )
    
    // ==================== 截图功能 ====================
    
    /**
     * 设置无障碍服务引用
     */
    fun setAccessibilityService(service: AccessibilityService) {
        this.accessibilityService = service
    }
    
    /**
     * 截取屏幕（自动选择 Root 或无障碍方式）
     */
    fun takeScreenshot(): Bitmap? {
        return if (rootManager.isDeviceRooted() && rootManager.isAppRootAuthorized()) {
            takeScreenshotWithRoot()
        } else {
            takeScreenshotWithAccessibility()
        }
    }
    
    /**
     * Root 方式截图（无延迟、无弹窗）
     */
    private fun takeScreenshotWithRoot(): Bitmap? {
        try {
            val screenshotDir = File(context.cacheDir, SCREENSHOT_DIR)
            if (!screenshotDir.exists()) screenshotDir.mkdirs()
            
            val screenshotFile = File(screenshotDir, "screen_${System.currentTimeMillis()}.png")
            val screenshotPath = screenshotFile.absolutePath
            
            // 使用 screencap 命令截图
            val result = rootManager.executeRootCommand("screencap -p $screenshotPath")
            
            if (result.success && screenshotFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(screenshotPath)
                lastScreenshot = bitmap
                lastScreenshotTime = System.currentTimeMillis()
                
                // 清理临时文件
                screenshotFile.delete()
                
                Log.d(TAG, "Root 截图成功: ${bitmap?.width}x${bitmap?.height}")
                return bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Root 截图失败: ${e.message}")
        }
        return null
    }
    
    /**
     * 无障碍服务方式截图
     */
    private fun takeScreenshotWithAccessibility(): Bitmap? {
        val service = accessibilityService ?: return null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // Android 11+ 使用无障碍服务截图
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    context.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            lastScreenshot = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            lastScreenshotTime = System.currentTimeMillis()
                            screenshot.hardwareBuffer.close()
                            Log.d(TAG, "无障碍截图成功")
                        }
                        
                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "无障碍截图失败: $errorCode")
                        }
                    }
                )
                
                // 等待截图完成
                Thread.sleep(200)
                return lastScreenshot
            } catch (e: Exception) {
                Log.e(TAG, "无障碍截图异常: ${e.message}")
            }
        }
        return null
    }
    
    /**
     * 获取上次截图（带缓存检查）
     */
    fun getLastScreenshot(maxAge: Long = 500): Bitmap? {
        return if (System.currentTimeMillis() - lastScreenshotTime < maxAge) {
            lastScreenshot
        } else {
            takeScreenshot()
        }
    }
    
    // ==================== UI 元素识别 ====================
    
    /**
     * 通过无障碍服务获取当前界面所有 UI 元素
     */
    fun getAllUIElements(): List<UIElement> {
        val elements = mutableListOf<UIElement>()
        val service = accessibilityService ?: return elements
        
        try {
            val rootNode = service.rootInActiveWindow ?: return elements
            traverseNode(rootNode, elements)
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "获取 UI 元素失败: ${e.message}")
        }
        
        recognizedElements.clear()
        recognizedElements.addAll(elements)
        return elements
    }
    
    private fun traverseNode(node: AccessibilityNodeInfo, elements: MutableList<UIElement>) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        // 只记录有效元素
        if (bounds.width() > 0 && bounds.height() > 0) {
            elements.add(
                UIElement(
                    id = node.viewIdResourceName ?: "",
                    text = node.text?.toString() ?: "",
                    contentDescription = node.contentDescription?.toString() ?: "",
                    className = node.className?.toString() ?: "",
                    bounds = bounds,
                    isClickable = node.isClickable,
                    isScrollable = node.isScrollable,
                    isEnabled = node.isEnabled
                )
            )
        }
        
        // 递归遍历子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, elements)
                child.recycle()
            }
        }
    }
    
    /**
     * 根据文本查找 UI 元素
     */
    fun findElementByText(text: String, exactMatch: Boolean = false): UIElement? {
        val elements = if (recognizedElements.isEmpty()) getAllUIElements() else recognizedElements
        
        return elements.find { element ->
            if (exactMatch) {
                element.text == text || element.contentDescription == text
            } else {
                element.text.contains(text) || element.contentDescription.contains(text)
            }
        }
    }
    
    /**
     * 根据 ID 查找 UI 元素
     */
    fun findElementById(id: String): UIElement? {
        val elements = if (recognizedElements.isEmpty()) getAllUIElements() else recognizedElements
        return elements.find { it.id.contains(id) }
    }
    
    /**
     * 根据类名查找 UI 元素
     */
    fun findElementsByClassName(className: String): List<UIElement> {
        val elements = if (recognizedElements.isEmpty()) getAllUIElements() else recognizedElements
        return elements.filter { it.className.contains(className) }
    }
    
    /**
     * 查找可点击的元素
     */
    fun findClickableElements(): List<UIElement> {
        val elements = if (recognizedElements.isEmpty()) getAllUIElements() else recognizedElements
        return elements.filter { it.isClickable && it.isEnabled }
    }
    
    // ==================== 图像识别 ====================
    
    /**
     * 在截图中查找指定颜色区域
     */
    fun findColorRegions(
        bitmap: Bitmap,
        targetColor: Int,
        minArea: Int = 100
    ): List<ImageRecognitionResult> {
        val results = mutableListOf<ImageRecognitionResult>()
        val width = bitmap.width
        val height = bitmap.height
        val visited = Array(height) { BooleanArray(width) }
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!visited[y][x]) {
                    val pixelColor = bitmap.getPixel(x, y)
                    if (isColorSimilar(pixelColor, targetColor)) {
                        // 使用洪水填充找到连通区域
                        val region = floodFill(bitmap, x, y, targetColor, visited)
                        if (region.size >= minArea) {
                            val bounds = calculateBounds(region)
                            results.add(
                                ImageRecognitionResult(
                                    label = "color_region",
                                    bounds = bounds,
                                    confidence = 0.9f,
                                    color = targetColor
                                )
                            )
                        }
                    }
                }
            }
        }
        
        return results
    }
    
    private fun floodFill(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        targetColor: Int,
        visited: Array<BooleanArray>
    ): List<Pair<Int, Int>> {
        val region = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))
        
        while (queue.isNotEmpty() && region.size < 10000) {  // 限制区域大小
            val (x, y) = queue.removeFirst()
            
            if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) continue
            if (visited[y][x]) continue
            
            val pixelColor = bitmap.getPixel(x, y)
            if (!isColorSimilar(pixelColor, targetColor)) continue
            
            visited[y][x] = true
            region.add(Pair(x, y))
            
            queue.add(Pair(x + 1, y))
            queue.add(Pair(x - 1, y))
            queue.add(Pair(x, y + 1))
            queue.add(Pair(x, y - 1))
        }
        
        return region
    }
    
    private fun calculateBounds(region: List<Pair<Int, Int>>): Rect {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        
        for ((x, y) in region) {
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
        }
        
        return Rect(minX, minY, maxX, maxY)
    }
    
    private fun isColorSimilar(color1: Int, color2: Int): Boolean {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        
        val diff = kotlin.math.abs(r1 - r2) + kotlin.math.abs(g1 - g2) + kotlin.math.abs(b1 - b2)
        return diff < COLOR_SIMILARITY_THRESHOLD * 3
    }
    
    /**
     * 识别血条（游戏专用）
     * 红色 = 敌方，蓝色/绿色 = 己方
     */
    fun detectHealthBars(bitmap: Bitmap): List<ImageRecognitionResult> {
        val results = mutableListOf<ImageRecognitionResult>()
        
        // 红色血条（敌方）
        val redRegions = findColorRegions(bitmap, Color.RED, 50)
        redRegions.forEach { region ->
            if (region.bounds.width() > region.bounds.height() * 2) {  // 血条是长条形
                results.add(region.copy(
                    label = "enemy_health_bar",
                    additionalInfo = mapOf("team" to "enemy")
                ))
            }
        }
        
        // 绿色血条（己方）
        val greenRegions = findColorRegions(bitmap, Color.GREEN, 50)
        greenRegions.forEach { region ->
            if (region.bounds.width() > region.bounds.height() * 2) {
                results.add(region.copy(
                    label = "ally_health_bar",
                    additionalInfo = mapOf("team" to "ally")
                ))
            }
        }
        
        return results
    }
    
    /**
     * 识别技能按钮（游戏专用）
     * 通常在屏幕右下角，圆形或方形
     */
    fun detectSkillButtons(bitmap: Bitmap): List<ImageRecognitionResult> {
        val results = mutableListOf<ImageRecognitionResult>()
        val width = bitmap.width
        val height = bitmap.height
        
        // 技能按钮通常在右下角区域
        val skillAreaLeft = width * 0.5
        val skillAreaTop = height * 0.5
        
        // 查找圆形高亮区域
        for (y in skillAreaTop.toInt() until height step 50) {
            for (x in skillAreaLeft.toInt() until width step 50) {
                val pixelColor = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixelColor) + Color.green(pixelColor) + Color.blue(pixelColor)) / 3
                
                if (brightness > 150) {  // 高亮区域可能是技能按钮
                    results.add(
                        ImageRecognitionResult(
                            label = "skill_button",
                            bounds = Rect(x - 40, y - 40, x + 40, y + 40),
                            confidence = 0.7f
                        )
                    )
                }
            }
        }
        
        return results
    }
    
    /**
     * 检测游戏虚拟摇杆位置
     */
    fun detectJoystick(bitmap: Bitmap): ImageRecognitionResult? {
        val width = bitmap.width
        val height = bitmap.height
        
        // 虚拟摇杆通常在左下角
        val joystickArea = Rect(0, (height * 0.5).toInt(), (width * 0.4).toInt(), height)
        
        return ImageRecognitionResult(
            label = "joystick",
            bounds = joystickArea,
            confidence = 0.8f,
            additionalInfo = mapOf(
                "centerX" to joystickArea.centerX(),
                "centerY" to joystickArea.centerY()
            )
        )
    }
    
    // ==================== OCR 文字识别 ====================
    
    /**
     * 简易 OCR（基于 AccessibilityNodeInfo）
     * 获取屏幕上所有可见文本
     */
    fun getAllVisibleText(): List<OCRResult> {
        val results = mutableListOf<OCRResult>()
        val elements = getAllUIElements()
        
        elements.forEach { element ->
            if (element.text.isNotEmpty()) {
                results.add(
                    OCRResult(
                        text = element.text,
                        bounds = element.bounds,
                        confidence = 1.0f
                    )
                )
            }
            if (element.contentDescription.isNotEmpty() && element.contentDescription != element.text) {
                results.add(
                    OCRResult(
                        text = element.contentDescription,
                        bounds = element.bounds,
                        confidence = 0.9f
                    )
                )
            }
        }
        
        return results
    }
    
    /**
     * 查找包含指定文本的区域
     */
    fun findTextOnScreen(searchText: String): OCRResult? {
        return getAllVisibleText().find { it.text.contains(searchText, ignoreCase = true) }
    }
    
    /**
     * 识别数字（用于识别倒计时、分数等）
     */
    fun findNumbersOnScreen(): List<OCRResult> {
        return getAllVisibleText().filter { result ->
            result.text.any { it.isDigit() }
        }
    }
    
    // ==================== 场景识别 ====================
    
    /**
     * 识别当前 APP
     */
    fun getCurrentPackageName(): String {
        val service = accessibilityService ?: return ""
        return try {
            service.rootInActiveWindow?.packageName?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 识别当前场景类型
     */
    fun detectSceneType(): SceneType {
        val packageName = getCurrentPackageName()
        val elements = getAllUIElements()
        
        return when {
            // 游戏检测
            isGameScene(packageName, elements) -> SceneType.GAME
            // 短视频检测
            isShortVideoScene(packageName, elements) -> SceneType.SHORT_VIDEO
            // 电商检测
            isECommerceScene(packageName, elements) -> SceneType.E_COMMERCE
            // 外卖检测
            isFoodDeliveryScene(packageName, elements) -> SceneType.FOOD_DELIVERY
            // 系统设置检测
            isSystemSettings(packageName) -> SceneType.SYSTEM_SETTINGS
            // 其他
            else -> SceneType.UNKNOWN
        }
    }
    
    private fun isGameScene(packageName: String, elements: List<UIElement>): Boolean {
        val gamePackages = listOf(
            "com.tencent.tmgp", "com.netease", "com.mihoyo",
            "com.supercell", "com.king", "com.garena"
        )
        return gamePackages.any { packageName.contains(it) }
    }
    
    private fun isShortVideoScene(packageName: String, elements: List<UIElement>): Boolean {
        val videoPackages = listOf(
            "com.ss.android.ugc.aweme",  // 抖音
            "com.smile.gifmaker",         // 快手
            "com.kuaishou.nebula",
            "com.ss.android.ugc.trill"    // TikTok
        )
        return videoPackages.any { packageName.contains(it) }
    }
    
    private fun isECommerceScene(packageName: String, elements: List<UIElement>): Boolean {
        val ecomPackages = listOf(
            "com.taobao.taobao",
            "com.jingdong.app.mall",
            "com.xingin.xhs",             // 小红书
            "com.achievo.vipshop"         // 唯品会
        )
        return ecomPackages.any { packageName.contains(it) }
    }
    
    private fun isFoodDeliveryScene(packageName: String, elements: List<UIElement>): Boolean {
        val foodPackages = listOf(
            "com.sankuai.meituan",
            "me.ele",                      // 饿了么
            "com.dianping.v1"
        )
        return foodPackages.any { packageName.contains(it) }
    }
    
    private fun isSystemSettings(packageName: String): Boolean {
        return packageName.contains("settings") || packageName.contains("systemui")
    }
    
    /**
     * 场景类型枚举
     */
    enum class SceneType {
        GAME,           // 游戏
        SHORT_VIDEO,    // 短视频
        E_COMMERCE,     // 电商
        FOOD_DELIVERY,  // 外卖
        SYSTEM_SETTINGS,// 系统设置
        UNKNOWN         // 未知
    }
    
    // ==================== 资源清理 ====================
    
    fun release() {
        lastScreenshot?.recycle()
        lastScreenshot = null
        recognizedElements.clear()
        accessibilityService = null
    }
}
