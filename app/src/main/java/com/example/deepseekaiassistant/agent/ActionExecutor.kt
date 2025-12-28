package com.example.deepseekaiassistant.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.deepseekaiassistant.root.RootManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 操作执行模块
 * 负责模拟用户操作：点击、滑动、输入、多指操作等
 * 支持 Root（精准无延迟）和非 Root（无障碍服务）两种模式
 */
class ActionExecutor(private val context: Context) {
    
    companion object {
        private const val TAG = "ActionExecutor"
        
        // 默认操作时长
        private const val DEFAULT_CLICK_DURATION = 100L      // 点击时长
        private const val DEFAULT_LONG_PRESS_DURATION = 500L // 长按时长
        private const val DEFAULT_SWIPE_DURATION = 300L      // 滑动时长
        
        // 人类模拟参数
        private const val HUMAN_DELAY_MIN = 50L   // 操作间隔最小值
        private const val HUMAN_DELAY_MAX = 200L  // 操作间隔最大值
        private const val POSITION_JITTER = 5     // 位置随机偏移像素
    }
    
    private val rootManager = RootManager.getInstance(context)
    private var accessibilityService: AccessibilityService? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 操作模式
    private var useRootMode: Boolean = false
    private var humanSimulationEnabled: Boolean = true  // 模拟人类操作
    
    /**
     * 操作结果
     */
    data class ActionResult(
        val success: Boolean,
        val message: String,
        val executionTime: Long
    )
    
    /**
     * 设置无障碍服务引用
     */
    fun setAccessibilityService(service: AccessibilityService) {
        this.accessibilityService = service
    }
    
    /**
     * 设置操作模式
     */
    fun setRootMode(enabled: Boolean) {
        this.useRootMode = enabled && rootManager.isDeviceRooted() && rootManager.isAppRootAuthorized()
    }
    
    /**
     * 设置是否模拟人类操作
     */
    fun setHumanSimulation(enabled: Boolean) {
        this.humanSimulationEnabled = enabled
    }
    
    // ==================== 点击操作 ====================
    
    /**
     * 点击指定坐标
     */
    fun click(x: Int, y: Int): ActionResult {
        val startTime = System.currentTimeMillis()
        val (adjustedX, adjustedY) = applyHumanJitter(x, y)
        
        val success = if (useRootMode) {
            clickWithRoot(adjustedX, adjustedY)
        } else {
            clickWithAccessibility(adjustedX, adjustedY, DEFAULT_CLICK_DURATION)
        }
        
        applyHumanDelay()
        
        return ActionResult(
            success = success,
            message = if (success) "点击成功 ($adjustedX, $adjustedY)" else "点击失败",
            executionTime = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * 点击 UI 元素
     */
    fun click(element: ScreenVision.UIElement): ActionResult {
        return click(element.bounds.centerX(), element.bounds.centerY())
    }
    
    /**
     * 点击矩形区域中心
     */
    fun click(bounds: Rect): ActionResult {
        return click(bounds.centerX(), bounds.centerY())
    }
    
    /**
     * 长按指定坐标
     */
    fun longPress(x: Int, y: Int, duration: Long = DEFAULT_LONG_PRESS_DURATION): ActionResult {
        val startTime = System.currentTimeMillis()
        val (adjustedX, adjustedY) = applyHumanJitter(x, y)
        
        val success = if (useRootMode) {
            longPressWithRoot(adjustedX, adjustedY, duration)
        } else {
            clickWithAccessibility(adjustedX, adjustedY, duration)
        }
        
        applyHumanDelay()
        
        return ActionResult(
            success = success,
            message = if (success) "长按成功 ($adjustedX, $adjustedY)" else "长按失败",
            executionTime = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * 双击
     */
    fun doubleClick(x: Int, y: Int): ActionResult {
        click(x, y)
        Thread.sleep(50)
        return click(x, y)
    }
    
    /**
     * Root 方式点击
     */
    private fun clickWithRoot(x: Int, y: Int): Boolean {
        val result = rootManager.executeRootCommand("input tap $x $y")
        return result.success
    }
    
    /**
     * Root 方式长按
     */
    private fun longPressWithRoot(x: Int, y: Int, duration: Long): Boolean {
        val result = rootManager.executeRootCommand("input swipe $x $y $x $y $duration")
        return result.success
    }
    
    /**
     * 无障碍服务方式点击
     */
    private fun clickWithAccessibility(x: Int, y: Int, duration: Long): Boolean {
        val service = accessibilityService ?: return false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            
            val latch = CountDownLatch(1)
            var success = false
            
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    latch.countDown()
                }
            }, mainHandler)
            
            latch.await(duration + 500, TimeUnit.MILLISECONDS)
            return success
        }
        
        return false
    }
    
    // ==================== 滑动操作 ====================
    
    /**
     * 滑动操作
     */
    fun swipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        duration: Long = DEFAULT_SWIPE_DURATION
    ): ActionResult {
        val startTime = System.currentTimeMillis()
        val (adjStartX, adjStartY) = applyHumanJitter(startX, startY)
        val (adjEndX, adjEndY) = applyHumanJitter(endX, endY)
        
        val success = if (useRootMode) {
            swipeWithRoot(adjStartX, adjStartY, adjEndX, adjEndY, duration)
        } else {
            swipeWithAccessibility(adjStartX, adjStartY, adjEndX, adjEndY, duration)
        }
        
        applyHumanDelay()
        
        return ActionResult(
            success = success,
            message = if (success) "滑动成功" else "滑动失败",
            executionTime = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * 向上滑动
     */
    fun swipeUp(distance: Int = 500, duration: Long = DEFAULT_SWIPE_DURATION): ActionResult {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val centerX = screenWidth / 2
        val startY = screenHeight / 2 + distance / 2
        val endY = screenHeight / 2 - distance / 2
        return swipe(centerX, startY, centerX, endY, duration)
    }
    
    /**
     * 向下滑动
     */
    fun swipeDown(distance: Int = 500, duration: Long = DEFAULT_SWIPE_DURATION): ActionResult {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val centerX = screenWidth / 2
        val startY = screenHeight / 2 - distance / 2
        val endY = screenHeight / 2 + distance / 2
        return swipe(centerX, startY, centerX, endY, duration)
    }
    
    /**
     * 向左滑动
     */
    fun swipeLeft(distance: Int = 500, duration: Long = DEFAULT_SWIPE_DURATION): ActionResult {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val centerY = screenHeight / 2
        val startX = screenWidth / 2 + distance / 2
        val endX = screenWidth / 2 - distance / 2
        return swipe(startX, centerY, endX, centerY, duration)
    }
    
    /**
     * 向右滑动
     */
    fun swipeRight(distance: Int = 500, duration: Long = DEFAULT_SWIPE_DURATION): ActionResult {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val centerY = screenHeight / 2
        val startX = screenWidth / 2 - distance / 2
        val endX = screenWidth / 2 + distance / 2
        return swipe(startX, centerY, endX, centerY, duration)
    }
    
    /**
     * Root 方式滑动
     */
    private fun swipeWithRoot(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): Boolean {
        val result = rootManager.executeRootCommand("input swipe $startX $startY $endX $endY $duration")
        return result.success
    }
    
    /**
     * 无障碍服务方式滑动
     */
    private fun swipeWithAccessibility(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        duration: Long
    ): Boolean {
        val service = accessibilityService ?: return false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            
            val latch = CountDownLatch(1)
            var success = false
            
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    latch.countDown()
                }
            }, mainHandler)
            
            latch.await(duration + 500, TimeUnit.MILLISECONDS)
            return success
        }
        
        return false
    }
    
    // ==================== 多指操作（游戏专用）====================
    
    /**
     * 双指同时操作（如走位 + 技能释放）
     * 仅 Root 模式支持
     */
    fun multiTouch(
        touch1: TouchPoint,
        touch2: TouchPoint
    ): ActionResult {
        if (!useRootMode) {
            return ActionResult(false, "多指操作需要 Root 权限", 0)
        }
        
        val startTime = System.currentTimeMillis()
        
        // 构建多指触控命令
        val cmd = buildString {
            append("sendevent /dev/input/event1 3 57 0;")  // 第一个触点 ID
            append("sendevent /dev/input/event1 3 53 ${touch1.x};")
            append("sendevent /dev/input/event1 3 54 ${touch1.y};")
            append("sendevent /dev/input/event1 3 58 50;")  // 压力
            append("sendevent /dev/input/event1 0 0 0;")
            
            append("sendevent /dev/input/event1 3 57 1;")  // 第二个触点 ID
            append("sendevent /dev/input/event1 3 53 ${touch2.x};")
            append("sendevent /dev/input/event1 3 54 ${touch2.y};")
            append("sendevent /dev/input/event1 3 58 50;")
            append("sendevent /dev/input/event1 0 0 0;")
            
            // 保持一段时间
            append("sleep 0.1;")
            
            // 释放触点
            append("sendevent /dev/input/event1 3 57 -1;")
            append("sendevent /dev/input/event1 0 0 0;")
        }
        
        val result = rootManager.executeRootCommand(cmd)
        
        return ActionResult(
            success = result.success,
            message = if (result.success) "多指操作成功" else "多指操作失败: ${result.output}",
            executionTime = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * 触点数据类
     */
    data class TouchPoint(
        val x: Int,
        val y: Int,
        val action: TouchAction = TouchAction.TAP
    )
    
    enum class TouchAction {
        TAP, MOVE, RELEASE
    }
    
    /**
     * 虚拟摇杆操作（游戏专用）
     * 模拟左手区域的滑动走位
     */
    fun joystickMove(
        centerX: Int, centerY: Int,
        directionX: Float, directionY: Float,  // -1.0 到 1.0
        radius: Int = 100,
        duration: Long = 200
    ): ActionResult {
        val targetX = centerX + (directionX * radius).toInt()
        val targetY = centerY + (directionY * radius).toInt()
        
        return swipe(centerX, centerY, targetX, targetY, duration)
    }
    
    /**
     * 连续点击（游戏专用，如连点攻击）
     */
    fun rapidClick(x: Int, y: Int, count: Int, intervalMs: Long = 100): ActionResult {
        val startTime = System.currentTimeMillis()
        var successCount = 0
        
        repeat(count) {
            val result = click(x, y)
            if (result.success) successCount++
            if (it < count - 1) Thread.sleep(intervalMs)
        }
        
        return ActionResult(
            success = successCount == count,
            message = "连点 $successCount/$count 次成功",
            executionTime = System.currentTimeMillis() - startTime
        )
    }
    
    // ==================== 输入操作 ====================
    
    /**
     * 输入文本
     */
    fun inputText(text: String): ActionResult {
        val startTime = System.currentTimeMillis()
        
        val success = if (useRootMode) {
            inputTextWithRoot(text)
        } else {
            inputTextWithAccessibility(text)
        }
        
        return ActionResult(
            success = success,
            message = if (success) "输入成功: $text" else "输入失败",
            executionTime = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * Root 方式输入文本
     */
    private fun inputTextWithRoot(text: String): Boolean {
        // 转义特殊字符
        val escapedText = text.replace(" ", "%s")
            .replace("&", "\\&")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace("\"", "\\\"")
        
        val result = rootManager.executeRootCommand("input text '$escapedText'")
        return result.success
    }
    
    /**
     * 无障碍服务方式输入文本
     */
    private fun inputTextWithAccessibility(text: String): Boolean {
        val service = accessibilityService ?: return false
        
        try {
            val rootNode = service.rootInActiveWindow ?: return false
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            
            if (focusedNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val arguments = android.os.Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                focusedNode.recycle()
                rootNode.recycle()
                return result
            }
            
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "输入文本失败: ${e.message}")
        }
        
        return false
    }
    
    /**
     * 清除输入框内容
     */
    fun clearInput(): ActionResult {
        val service = accessibilityService ?: return ActionResult(false, "无障碍服务不可用", 0)
        
        try {
            val rootNode = service.rootInActiveWindow ?: return ActionResult(false, "无法获取根节点", 0)
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            
            if (focusedNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val arguments = android.os.Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    ""
                )
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                focusedNode.recycle()
            }
            
            rootNode.recycle()
            return ActionResult(true, "清除成功", 0)
        } catch (e: Exception) {
            return ActionResult(false, "清除失败: ${e.message}", 0)
        }
    }
    
    // ==================== 系统按键 ====================
    
    /**
     * 按下返回键
     */
    fun pressBack(): ActionResult {
        val success = if (useRootMode) {
            rootManager.executeRootCommand("input keyevent 4").success
        } else {
            accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
        }
        
        return ActionResult(success, if (success) "返回键成功" else "返回键失败", 0)
    }
    
    /**
     * 按下 Home 键
     */
    fun pressHome(): ActionResult {
        val success = if (useRootMode) {
            rootManager.executeRootCommand("input keyevent 3").success
        } else {
            accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
        }
        
        return ActionResult(success, if (success) "Home 键成功" else "Home 键失败", 0)
    }
    
    /**
     * 打开最近任务
     */
    fun pressRecents(): ActionResult {
        val success = if (useRootMode) {
            rootManager.executeRootCommand("input keyevent 187").success
        } else {
            accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
        }
        
        return ActionResult(success, if (success) "最近任务成功" else "最近任务失败", 0)
    }
    
    /**
     * 下拉通知栏
     */
    fun pullDownNotifications(): ActionResult {
        val success = if (useRootMode) {
            rootManager.executeRootCommand("cmd statusbar expand-notifications").success
        } else {
            accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false
        }
        
        return ActionResult(success, if (success) "下拉通知栏成功" else "下拉通知栏失败", 0)
    }
    
    /**
     * 下拉快速设置
     */
    fun pullDownQuickSettings(): ActionResult {
        val success = if (useRootMode) {
            rootManager.executeRootCommand("cmd statusbar expand-settings").success
        } else {
            accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) ?: false
        }
        
        return ActionResult(success, if (success) "下拉快速设置成功" else "下拉快速设置失败", 0)
    }
    
    /**
     * 锁屏
     */
    fun lockScreen(): ActionResult {
        val success = if (useRootMode) {
            rootManager.executeRootCommand("input keyevent 26").success
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
            } else {
                false
            }
        }
        
        return ActionResult(success, if (success) "锁屏成功" else "锁屏失败", 0)
    }
    
    /**
     * 截图
     */
    fun takeScreenshot(): ActionResult {
        val success = if (useRootMode) {
            rootManager.executeRootCommand("input keyevent 120").success
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
            } else {
                false
            }
        }
        
        return ActionResult(success, if (success) "截图成功" else "截图失败", 0)
    }
    
    // ==================== 人类行为模拟 ====================
    
    /**
     * 应用位置抖动（模拟人类点击不精准）
     */
    private fun applyHumanJitter(x: Int, y: Int): Pair<Int, Int> {
        if (!humanSimulationEnabled) return Pair(x, y)
        
        val jitterX = Random.nextInt(-POSITION_JITTER, POSITION_JITTER + 1)
        val jitterY = Random.nextInt(-POSITION_JITTER, POSITION_JITTER + 1)
        
        return Pair(x + jitterX, y + jitterY)
    }
    
    /**
     * 应用随机延迟（模拟人类操作间隔）
     */
    private fun applyHumanDelay() {
        if (!humanSimulationEnabled) return
        
        val delay = Random.nextLong(HUMAN_DELAY_MIN, HUMAN_DELAY_MAX)
        try {
            Thread.sleep(delay)
        } catch (e: InterruptedException) {
            // 忽略中断
        }
    }
    
    /**
     * 模拟人类思考停顿
     */
    fun humanPause(minMs: Long = 500, maxMs: Long = 2000) {
        val pause = Random.nextLong(minMs, maxMs)
        try {
            Thread.sleep(pause)
        } catch (e: InterruptedException) {
            // 忽略中断
        }
    }
    
    // ==================== APP 操作 ====================
    
    /**
     * 启动 APP
     */
    fun launchApp(packageName: String): ActionResult {
        val startTime = System.currentTimeMillis()
        
        val success = if (useRootMode) {
            val result = rootManager.executeRootCommand(
                "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
            )
            result.success
        } else {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动 APP 失败: ${e.message}")
                false
            }
        }
        
        return ActionResult(
            success = success,
            message = if (success) "启动 $packageName 成功" else "启动失败",
            executionTime = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * 强制停止 APP
     */
    fun forceStopApp(packageName: String): ActionResult {
        if (!useRootMode) {
            return ActionResult(false, "强制停止需要 Root 权限", 0)
        }
        
        val result = rootManager.executeRootCommand("am force-stop $packageName")
        return ActionResult(
            success = result.success,
            message = if (result.success) "已停止 $packageName" else "停止失败",
            executionTime = 0
        )
    }
    
    // ==================== 资源清理 ====================
    
    fun release() {
        accessibilityService = null
    }
}
