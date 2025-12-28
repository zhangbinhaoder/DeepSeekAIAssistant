package com.example.deepseekaiassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务 - 用于高级设备控制（自动点击、滑动等）
 */
class AIAccessibilityService : AccessibilityService() {
    
    companion object {
        var instance: AIAccessibilityService? = null
            private set
        
        fun isServiceEnabled(): Boolean = instance != null
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 暂不处理事件
    }
    
    override fun onInterrupt() {
        // 服务中断
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
    
    /**
     * 执行点击操作
     */
    fun performClick(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback?.invoke(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback?.invoke(false)
                }
            }, null)
        }
    }
    
    /**
     * 执行滑动操作
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300, callback: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback?.invoke(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback?.invoke(false)
                }
            }, null)
        }
    }
    
    /**
     * 返回上一页
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
    
    /**
     * 回到桌面
     */
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    /**
     * 打开最近任务
     */
    fun performRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
    
    /**
     * 打开通知栏
     */
    fun performNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
    
    /**
     * 截图 (Android 9+)
     */
    fun performScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }
}
