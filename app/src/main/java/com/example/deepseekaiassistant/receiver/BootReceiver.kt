package com.example.deepseekaiassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.deepseekaiassistant.service.NotificationService

/**
 * 开机启动接收器
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // 开机启动时可以执行一些初始化操作
            NotificationService.sendNotification(
                context,
                "DeepSeek AI 助手",
                "应用已就绪，随时为您服务"
            )
        }
    }
}
