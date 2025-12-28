package com.example.deepseekaiassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DeepSeekApp : Application() {
    
    companion object {
        const val CHANNEL_ID_DEFAULT = "deepseek_default"
        const val CHANNEL_ID_REMINDER = "deepseek_reminder"
        
        lateinit var instance: DeepSeekApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
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
