package com.example.deepseekaiassistant.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.deepseekaiassistant.DeepSeekApp
import com.example.deepseekaiassistant.MainActivity
import com.example.deepseekaiassistant.R

/**
 * 通知服务 - 用于发送提醒通知
 */
class NotificationService : Service() {
    
    companion object {
        private var notificationId = 1000
        
        fun sendNotification(context: Context, title: String, content: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(context, DeepSeekApp.CHANNEL_ID_REMINDER)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            
            notificationManager.notify(notificationId++, notification)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
