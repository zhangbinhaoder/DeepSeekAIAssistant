package com.example.deepseekaiassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class DeepSeekApp : Application() {
    
    companion object {
        const val CHANNEL_ID_DEFAULT = "deepseek_default"
        const val CHANNEL_ID_REMINDER = "deepseek_reminder"
        private const val TAG = "DeepSeekApp"
        
        lateinit var instance: DeepSeekApp
            private set
    }
    
    // 全局未捕获异常处理器
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        try {
            // 设置全局异常捕获
            setupGlobalExceptionHandler()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup exception handler", e)
        }
        
        try {
            // 创建通知渠道
            createNotificationChannels()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channels", e)
        }
        
        try {
            // 初始化 SelfCheckManager 的 Context
            SelfCheckManager.setCheckContext(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SelfCheckManager", e)
        }
        
        Log.i(TAG, "DeepSeekApp initialized successfully")
    }
    
    /**
     * 设置全局异常捕获器
     * 防止应用闪退，记录崩溃日志
     */
    private fun setupGlobalExceptionHandler() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 记录崩溃日志
                logCrash(thread, throwable)
                
                // 尝试通知 DiagnosticManager
                try {
                    DiagnosticManager.error(
                        "CRASH",
                        "应用崩溃: ${throwable.javaClass.simpleName}",
                        throwable.message
                    )
                } catch (e: Exception) {
                    // 忽略
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in crash handler", e)
            } finally {
                // 调用默认处理器
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    /**
     * 记录崩溃日志到文件
     */
    private fun logCrash(thread: Thread, throwable: Throwable) {
        try {
            val logsDir = File(filesDir, "crash_logs")
            logsDir.mkdirs()
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val logFile = File(logsDir, "crash_$timestamp.log")
            
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            
            pw.println("=== DeepSeek AI Assistant Crash Report ===")
            pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            pw.println("Thread: ${thread.name}")
            pw.println("Device: ${Build.MODEL} (${Build.MANUFACTURER})")
            pw.println("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            pw.println()
            pw.println("=== Exception ===")
            throwable.printStackTrace(pw)
            pw.println()
            pw.println("=== Cause ===")
            throwable.cause?.printStackTrace(pw)
            
            logFile.writeText(sw.toString())
            
            Log.e(TAG, "Crash log saved to: ${logFile.absolutePath}")
            Log.e(TAG, sw.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        }
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // 默认通知渠道
            val defaultChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT,
                "默认通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "DeepSeek AI 助手的默认通知"
            }
            
            // 提醒通知渠道
            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDER,
                "提醒通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟和日程提醒"
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannels(
                listOf(defaultChannel, reminderChannel)
            )
        }
    }
}
