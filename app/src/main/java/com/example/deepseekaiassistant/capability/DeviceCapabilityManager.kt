package com.example.deepseekaiassistant.capability

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.deepseekaiassistant.DeepSeekApp
import java.util.Calendar

/**
 * 设备能力管理器 - 类似 Termux API，提供 AI 操作手机的能力
 */
class DeviceCapabilityManager(private val context: Context) {
    
    private val cameraManager by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val locationManager by lazy { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val batteryManager by lazy { context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager }
    
    private var isFlashlightOn = false
    
    // ==================== 电话功能 ====================
    
    /**
     * 拨打电话
     */
    fun makePhoneCall(phoneNumber: String): CapabilityResult {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            return CapabilityResult.PermissionDenied("需要电话权限才能拨打电话")
        }
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CapabilityResult.Success("正在拨打电话给 $phoneNumber")
        } catch (e: Exception) {
            CapabilityResult.Error("拨打电话失败: ${e.message}")
        }
    }
    
    /**
     * 打开拨号界面（不需要权限）
     */
    fun openDialer(phoneNumber: String): CapabilityResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CapabilityResult.Success("已打开拨号界面: $phoneNumber")
        } catch (e: Exception) {
            CapabilityResult.Error("打开拨号界面失败: ${e.message}")
        }
    }
    
    // ==================== 短信功能 ====================
    
    /**
     * 发送短信
     */
    fun sendSms(phoneNumber: String, message: String): CapabilityResult {
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            return CapabilityResult.PermissionDenied("需要短信权限才能发送短信")
        }
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            CapabilityResult.Success("短信已发送给 $phoneNumber")
        } catch (e: Exception) {
            CapabilityResult.Error("发送短信失败: ${e.message}")
        }
    }
    
    /**
     * 打开短信编辑界面
     */
    fun openSmsApp(phoneNumber: String, message: String = ""): CapabilityResult {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CapabilityResult.Success("已打开短信编辑界面")
        } catch (e: Exception) {
            CapabilityResult.Error("打开短信应用失败: ${e.message}")
        }
    }
    
    // ==================== 闹钟功能 ====================
    
    /**
     * 设置闹钟
     */
    fun setAlarm(hour: Int, minute: Int, message: String = "AI助手提醒"): CapabilityResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CapabilityResult.Success("已设置闹钟: ${hour}:${String.format("%02d", minute)} - $message")
        } catch (e: Exception) {
            CapabilityResult.Error("设置闹钟失败: ${e.message}")
        }
    }
    
    /**
     * 设置定时器
     */
    fun setTimer(seconds: Int, message: String = "计时结束"): CapabilityResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CapabilityResult.Success("已设置${seconds}秒定时器")
        } catch (e: Exception) {
            CapabilityResult.Error("设置定时器失败: ${e.message}")
        }
    }
    
    // ==================== 日历功能 ====================
    
    /**
     * 添加日历事件
     */
    fun addCalendarEvent(
        title: String,
        description: String = "",
        startTimeMillis: Long,
        endTimeMillis: Long,
        location: String = ""
    ): CapabilityResult {
        if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            return CapabilityResult.PermissionDenied("需要日历权限才能添加事件")
        }
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CapabilityResult.Success("已打开日历添加事件: $title")
        } catch (e: Exception) {
            CapabilityResult.Error("添加日历事件失败: ${e.message}")
        }
    }
    
    // ==================== 位置功能 ====================
    
    /**
     * 获取当前位置
     */
    fun getCurrentLocation(): CapabilityResult {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return CapabilityResult.PermissionDenied("需要位置权限才能获取位置")
        }
        return try {
            val location = getLastKnownLocation()
            if (location != null) {
                CapabilityResult.Success(
                    "当前位置: 纬度 ${location.latitude}, 经度 ${location.longitude}",
                    mapOf("latitude" to location.latitude, "longitude" to location.longitude)
                )
            } else {
                CapabilityResult.Error("无法获取位置信息，请确保已开启定位服务")
            }
        } catch (e: Exception) {
            CapabilityResult.Error("获取位置失败: ${e.message}")
        }
    }
    
    private fun getLastKnownLocation(): Location? {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return null
        
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        }
    }
    
    /**
     * 打开地图应用
     */
    fun openMap(latitude: Double, longitude: Double, label: String = ""): CapabilityResult {
        return try {
            val uri = if (label.isNotEmpty()) {
                Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
            } else {
                Uri.parse("geo:$latitude,$longitude")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CapabilityResult.Success("已打开地图")
        } catch (e: Exception) {
            CapabilityResult.Error("打开地图失败: ${e.message}")
        }
    }
    
    // ==================== 手电筒功能 ====================
    
    /**
     * 开关手电筒
     */
    fun toggleFlashlight(turnOn: Boolean): CapabilityResult {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            return CapabilityResult.PermissionDenied("需要相机权限才能控制手电筒")
        }
        return try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, turnOn)
            isFlashlightOn = turnOn
            CapabilityResult.Success(if (turnOn) "手电筒已开启" else "手电筒已关闭")
        } catch (e: Exception) {
            CapabilityResult.Error("控制手电筒失败: ${e.message}")
        }
    }
    
    // ==================== 振动功能 ====================
    
    /**
     * 振动
     */
    fun vibrate(durationMs: Long = 500): CapabilityResult {
        if (!hasPermission(Manifest.permission.VIBRATE)) {
            return CapabilityResult.PermissionDenied("需要振动权限")
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
            CapabilityResult.Success("设备已振动")
        } catch (e: Exception) {
            CapabilityResult.Error("振动失败: ${e.message}")
        }
    }
    
    // ==================== 电池信息 ====================
    
    /**
     * 获取电池状态
     */
    fun getBatteryStatus(): CapabilityResult {
        return try {
            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = batteryManager.isCharging
            val status = if (isCharging) "正在充电" else "未充电"
            CapabilityResult.Success(
                "电池电量: $level%, 状态: $status",
                mapOf("level" to level, "isCharging" to isCharging)
            )
        } catch (e: Exception) {
            CapabilityResult.Error("获取电池状态失败: ${e.message}")
        }
    }
    
    // ==================== 音量控制 ====================
    
    /**
     * 设置媒体音量
     */
    fun setVolume(volumePercent: Int): CapabilityResult {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (maxVolume * volumePercent / 100).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            CapabilityResult.Success("媒体音量已设置为 $volumePercent%")
        } catch (e: Exception) {
            CapabilityResult.Error("设置音量失败: ${e.message}")
        }
    }
    
    /**
     * 获取当前音量
     */
    fun getVolume(): CapabilityResult {
        return try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val percent = (current * 100 / max)
            CapabilityResult.Success(
                "当前媒体音量: $percent%",
                mapOf("volume" to percent, "current" to current, "max" to max)
            )
        } catch (e: Exception) {
            CapabilityResult.Error("获取音量失败: ${e.message}")
        }
    }
    
    // ==================== 联系人功能 ====================
    
    /**
     * 查找联系人电话号码
     */
    fun findContactPhone(name: String): CapabilityResult {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return CapabilityResult.PermissionDenied("需要联系人权限才能查找联系人")
        }
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            
            val contacts = mutableListOf<Map<String, String>>()
            cursor?.use {
                while (it.moveToNext()) {
                    val contactName = it.getString(0)
                    val phone = it.getString(1)
                    contacts.add(mapOf("name" to contactName, "phone" to phone))
                }
            }
            
            if (contacts.isNotEmpty()) {
                val result = contacts.joinToString("\n") { "${it["name"]}: ${it["phone"]}" }
                CapabilityResult.Success("找到以下联系人:\n$result", mapOf("contacts" to contacts))
            } else {
                CapabilityResult.Success("未找到名为 '$name' 的联系人")
            }
        } catch (e: Exception) {
            CapabilityResult.Error("查找联系人失败: ${e.message}")
        }
    }
    
    // ==================== 打开应用/网页 ====================
    
    /**
     * 打开网页
     */
    fun openUrl(url: String): CapabilityResult {
        return try {
            val uri = if (url.startsWith("http://") || url.startsWith("https://")) {
                Uri.parse(url)
            } else {
                Uri.parse("https://$url")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CapabilityResult.Success("已打开网页: $url")
        } catch (e: Exception) {
            CapabilityResult.Error("打开网页失败: ${e.message}")
        }
    }
    
    /**
     * 打开应用
     */
    fun openApp(packageName: String): CapabilityResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                CapabilityResult.Success("已打开应用: $packageName")
            } else {
                CapabilityResult.Error("未找到应用: $packageName")
            }
        } catch (e: Exception) {
            CapabilityResult.Error("打开应用失败: ${e.message}")
        }
    }
    
    /**
     * 打开系统设置
     */
    fun openSettings(settingsAction: String = Settings.ACTION_SETTINGS): CapabilityResult {
        return try {
            val intent = Intent(settingsAction).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CapabilityResult.Success("已打开设置")
        } catch (e: Exception) {
            CapabilityResult.Error("打开设置失败: ${e.message}")
        }
    }
    
    // ==================== 剪贴板功能 ====================
    
    /**
     * 复制文本到剪贴板
     */
    fun copyToClipboard(text: String, label: String = "AI助手"): CapabilityResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            CapabilityResult.Success("已复制到剪贴板")
        } catch (e: Exception) {
            CapabilityResult.Error("复制失败: ${e.message}")
        }
    }
    
    /**
     * 获取剪贴板内容
     */
    fun getClipboardContent(): CapabilityResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                CapabilityResult.Success("剪贴板内容: $text", mapOf("text" to text))
            } else {
                CapabilityResult.Success("剪贴板为空")
            }
        } catch (e: Exception) {
            CapabilityResult.Error("获取剪贴板内容失败: ${e.message}")
        }
    }
    
    // ==================== 工具方法 ====================
    
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取所有可用的能力列表
     */
    fun getAvailableCapabilities(): List<DeviceCapability> {
        return listOf(
            DeviceCapability("phone", "拨打电话", "call [电话号码]", hasPermission(Manifest.permission.CALL_PHONE)),
            DeviceCapability("sms", "发送短信", "sms [电话号码] [内容]", hasPermission(Manifest.permission.SEND_SMS)),
            DeviceCapability("alarm", "设置闹钟", "alarm [时:分] [备注]", true),
            DeviceCapability("timer", "设置定时器", "timer [秒数] [备注]", true),
            DeviceCapability("calendar", "添加日历", "calendar [标题] [开始时间] [结束时间]", hasPermission(Manifest.permission.WRITE_CALENDAR)),
            DeviceCapability("location", "获取位置", "location", hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)),
            DeviceCapability("flashlight", "手电筒", "flashlight on/off", hasPermission(Manifest.permission.CAMERA)),
            DeviceCapability("vibrate", "振动", "vibrate [毫秒]", hasPermission(Manifest.permission.VIBRATE)),
            DeviceCapability("battery", "电池状态", "battery", true),
            DeviceCapability("volume", "音量控制", "volume [0-100]", true),
            DeviceCapability("contacts", "查找联系人", "contacts [姓名]", hasPermission(Manifest.permission.READ_CONTACTS)),
            DeviceCapability("url", "打开网页", "url [网址]", true),
            DeviceCapability("app", "打开应用", "app [包名]", true),
            DeviceCapability("clipboard", "剪贴板", "clipboard copy/paste [内容]", true)
        )
    }
}

/**
 * 设备能力描述
 */
data class DeviceCapability(
    val id: String,
    val name: String,
    val usage: String,
    val isAvailable: Boolean
)

/**
 * 能力执行结果
 */
sealed class CapabilityResult {
    data class Success(val message: String, val data: Map<String, Any?> = emptyMap()) : CapabilityResult()
    data class Error(val message: String) : CapabilityResult()
    data class PermissionDenied(val message: String) : CapabilityResult()
}
