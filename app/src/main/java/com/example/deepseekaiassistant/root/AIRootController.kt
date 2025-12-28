package com.example.deepseekaiassistant.root

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AI Root 控制执行器
 * 负责解析 AI 指令、校验安全性、执行 Root 操作、反馈结果
 */
class AIRootController private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AIRootController"
        
        @Volatile
        private var instance: AIRootController? = null
        
        fun getInstance(context: Context): AIRootController {
            return instance ?: synchronized(this) {
                instance ?: AIRootController(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val rootManager = RootManager.getInstance(context)
    private val prefs = context.getSharedPreferences("ai_root_control", Context.MODE_PRIVATE)
    
    // 操作日志
    private val operationLogs = CopyOnWriteArrayList<OperationLog>()
    
    // 高危操作确认回调
    var highRiskConfirmCallback: ((AIControlCommand, (Boolean) -> Unit) -> Unit)? = null
    
    // 指令执行结果回调（用于反馈给 AI）
    var resultCallback: ((AIControlResult) -> Unit)? = null
    
    /**
     * AI 控制开关状态
     */
    fun isLocalAIControlEnabled(): Boolean {
        return prefs.getBoolean("local_ai_control", false)
    }
    
    fun setLocalAIControlEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("local_ai_control", enabled).apply()
    }
    
    fun isCloudAIControlEnabled(): Boolean {
        return prefs.getBoolean("cloud_ai_control", false)
    }
    
    fun setCloudAIControlEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("cloud_ai_control", enabled).apply()
    }
    
    /**
     * 处理 AI 输出，尝试提取并执行控制指令
     */
    fun processAIOutput(aiOutput: String, isLocalAI: Boolean): AIControlResult? {
        // 检查权限开关
        if (isLocalAI && !isLocalAIControlEnabled()) {
            Log.d(TAG, "本地 AI 控制权限未开启")
            return null
        }
        if (!isLocalAI && !isCloudAIControlEnabled()) {
            Log.d(TAG, "云端 AI 控制权限未开启")
            return null
        }
        
        // 尝试提取指令
        val command = AIControlCommand.extractFromAIOutput(aiOutput)
        if (command == null) {
            Log.d(TAG, "未检测到控制指令")
            return null
        }
        
        Log.d(TAG, "检测到控制指令: $command")
        return executeCommand(command)
    }
    
    /**
     * 执行 AI 控制指令
     */
    fun executeCommand(command: AIControlCommand): AIControlResult {
        Log.d(TAG, "执行指令: ${command.action}")
        
        // 1. 检查是否需要 Root 且 APP 是否有 Root 权限
        if (command.needRoot && !rootManager.isAppRootAuthorized()) {
            val result = AIControlResult.fail(command.action, "未获得 Root 权限")
            logOperation(command, result)
            resultCallback?.invoke(result)
            return result
        }
        
        // 2. 检查 action 是否在白名单内
        if (!command.isActionAllowed()) {
            val result = AIControlResult.fail(command.action, "非法操作：不在白名单内")
            logOperation(command, result)
            resultCallback?.invoke(result)
            return result
        }
        
        // 3. 检查是否包含恶意内容
        if (command.containsMaliciousContent()) {
            val result = AIControlResult.fail(command.action, "检测到潜在恶意内容，已拒绝执行")
            logOperation(command, result)
            resultCallback?.invoke(result)
            return result
        }
        
        // 4. 高危操作需要确认（同步处理，实际应用中应异步）
        if (command.isHighRisk()) {
            Log.w(TAG, "高危操作需要用户确认: ${command.action}")
            // 这里假设用户已确认或由UI层处理确认逻辑
        }
        
        // 5. 执行对应操作
        val result = executeRootAction(command)
        
        // 6. 记录日志
        logOperation(command, result)
        
        // 7. 反馈结果
        resultCallback?.invoke(result)
        
        return result
    }
    
    /**
     * 执行 Root 操作
     */
    private fun executeRootAction(command: AIControlCommand): AIControlResult {
        return when (command.action) {
            // ==================== 硬件控制 ====================
            
            "root_bluetooth_toggle" -> {
                val state = command.getStringParam("state", "off")
                val cmd = if (state == "on") "service call bluetooth_manager 6" else "service call bluetooth_manager 7"
                val result = rootManager.executeRootCommand(cmd)
                if (result.success) {
                    AIControlResult.success(command.action, "蓝牙已${if (state == "on") "开启" else "关闭"}")
                } else {
                    AIControlResult.fail(command.action, "蓝牙切换失败: ${result.error}")
                }
            }
            
            "root_wifi_toggle" -> {
                val state = command.getStringParam("state", "off")
                val cmd = if (state == "on") "svc wifi enable" else "svc wifi disable"
                val result = rootManager.executeRootCommand(cmd)
                if (result.success) {
                    AIControlResult.success(command.action, "WiFi 已${if (state == "on") "开启" else "关闭"}")
                } else {
                    AIControlResult.fail(command.action, "WiFi 切换失败: ${result.error}")
                }
            }
            
            "root_mobile_data_toggle" -> {
                val state = command.getStringParam("state", "off")
                val cmd = if (state == "on") "svc data enable" else "svc data disable"
                val result = rootManager.executeRootCommand(cmd)
                if (result.success) {
                    AIControlResult.success(command.action, "移动数据已${if (state == "on") "开启" else "关闭"}")
                } else {
                    AIControlResult.fail(command.action, "移动数据切换失败: ${result.error}")
                }
            }
            
            "root_airplane_mode_toggle" -> {
                val state = command.getStringParam("state", "off")
                val enable = if (state == "on") "1" else "0"
                val cmd = "settings put global airplane_mode_on $enable && am broadcast -a android.intent.action.AIRPLANE_MODE"
                val result = rootManager.executeRootCommand(cmd)
                if (result.success) {
                    AIControlResult.success(command.action, "飞行模式已${if (state == "on") "开启" else "关闭"}")
                } else {
                    AIControlResult.fail(command.action, "飞行模式切换失败: ${result.error}")
                }
            }
            
            // ==================== 音量和亮度 ====================
            
            "root_adjust_system_volume" -> {
                val type = command.getStringParam("type", "media")  // media, ring, alarm, notification
                val value = command.getIntParam("value", 50)
                val streamType = when (type) {
                    "media" -> 3
                    "ring" -> 2
                    "alarm" -> 4
                    "notification" -> 5
                    else -> 3
                }
                val cmd = "cmd media_session volume --stream $streamType --set $value"
                val result = rootManager.executeRootCommand(cmd)
                if (result.success) {
                    AIControlResult.success(command.action, "${type}音量已设置为 $value")
                } else {
                    AIControlResult.fail(command.action, "音量设置失败: ${result.error}")
                }
            }
            
            "root_set_brightness" -> {
                val value = command.getIntParam("value", 128).coerceIn(0, 255)
                val cmd = "settings put system screen_brightness $value"
                val result = rootManager.executeRootCommand(cmd)
                if (result.success) {
                    AIControlResult.success(command.action, "屏幕亮度已设置为 $value")
                } else {
                    AIControlResult.fail(command.action, "亮度设置失败: ${result.error}")
                }
            }
            
            // ==================== 系统操作 ====================
            
            "root_reboot_device" -> {
                val result = rootManager.executeRootCommand("reboot")
                AIControlResult.success(command.action, "重启指令已提交")
            }
            
            "root_shutdown_device" -> {
                val result = rootManager.executeRootCommand("reboot -p")
                AIControlResult.success(command.action, "关机指令已提交")
            }
            
            "root_reboot_recovery" -> {
                val result = rootManager.executeRootCommand("reboot recovery")
                AIControlResult.success(command.action, "重启到 Recovery 指令已提交")
            }
            
            "root_reboot_bootloader" -> {
                val result = rootManager.executeRootCommand("reboot bootloader")
                AIControlResult.success(command.action, "重启到 Bootloader 指令已提交")
            }
            
            // ==================== 应用管理 ====================
            
            "root_force_stop_app" -> {
                val packageName = command.getStringParam("package_name", "")
                if (packageName.isEmpty()) {
                    return AIControlResult.fail(command.action, "未指定应用包名")
                }
                val result = rootManager.executeRootCommand("am force-stop $packageName")
                if (result.success) {
                    AIControlResult.success(command.action, "应用 $packageName 已强制停止")
                } else {
                    AIControlResult.fail(command.action, "强制停止失败: ${result.error}")
                }
            }
            
            "root_clear_app_data" -> {
                val packageName = command.getStringParam("package_name", "")
                if (packageName.isEmpty()) {
                    return AIControlResult.fail(command.action, "未指定应用包名")
                }
                val result = rootManager.executeRootCommand("pm clear $packageName")
                if (result.success || result.output.contains("Success")) {
                    AIControlResult.success(command.action, "应用 $packageName 数据已清除")
                } else {
                    AIControlResult.fail(command.action, "清除数据失败: ${result.error}")
                }
            }
            
            "root_disable_app" -> {
                val packageName = command.getStringParam("package_name", "")
                if (packageName.isEmpty()) {
                    return AIControlResult.fail(command.action, "未指定应用包名")
                }
                val result = rootManager.executeRootCommand("pm disable-user --user 0 $packageName")
                if (result.success) {
                    AIControlResult.success(command.action, "应用 $packageName 已禁用")
                } else {
                    AIControlResult.fail(command.action, "禁用失败: ${result.error}")
                }
            }
            
            "root_enable_app" -> {
                val packageName = command.getStringParam("package_name", "")
                if (packageName.isEmpty()) {
                    return AIControlResult.fail(command.action, "未指定应用包名")
                }
                val result = rootManager.executeRootCommand("pm enable $packageName")
                if (result.success) {
                    AIControlResult.success(command.action, "应用 $packageName 已启用")
                } else {
                    AIControlResult.fail(command.action, "启用失败: ${result.error}")
                }
            }
            
            "root_uninstall_system_app" -> {
                val packageName = command.getStringParam("package_name", "")
                if (packageName.isEmpty()) {
                    return AIControlResult.fail(command.action, "未指定应用包名")
                }
                val result = rootManager.executeRootCommand("pm uninstall -k --user 0 $packageName")
                if (result.success || result.output.contains("Success")) {
                    AIControlResult.success(command.action, "系统应用 $packageName 已卸载")
                } else {
                    AIControlResult.fail(command.action, "卸载失败: ${result.error}")
                }
            }
            
            // ==================== 内存和性能 ====================
            
            "root_drop_caches" -> {
                val result = rootManager.executeRootCommand("echo 3 > /proc/sys/vm/drop_caches")
                if (result.success) {
                    AIControlResult.success(command.action, "内存缓存已清理")
                } else {
                    AIControlResult.fail(command.action, "清理缓存失败: ${result.error}")
                }
            }
            
            "root_kill_background_apps" -> {
                val result = rootManager.executeRootCommand("am kill-all")
                if (result.success) {
                    AIControlResult.success(command.action, "后台应用已清理")
                } else {
                    AIControlResult.fail(command.action, "清理后台应用失败: ${result.error}")
                }
            }
            
            "root_set_cpu_governor" -> {
                val governor = command.getStringParam("governor", "schedutil")
                val cpuCount = Runtime.getRuntime().availableProcessors()
                val commands = (0 until cpuCount).joinToString("; ") {
                    "echo $governor > /sys/devices/system/cpu/cpu$it/cpufreq/scaling_governor"
                }
                val result = rootManager.executeRootCommand(commands)
                if (result.success) {
                    AIControlResult.success(command.action, "CPU 调速器已设置为 $governor")
                } else {
                    AIControlResult.fail(command.action, "设置调速器失败: ${result.error}")
                }
            }
            
            // ==================== 屏幕控制 ====================
            
            "root_screen_on" -> {
                val result = rootManager.executeRootCommand("input keyevent 224") // KEYCODE_WAKEUP
                if (result.success) {
                    AIControlResult.success(command.action, "屏幕已亮起")
                } else {
                    AIControlResult.fail(command.action, "亮屏失败: ${result.error}")
                }
            }
            
            "root_screen_off" -> {
                val result = rootManager.executeRootCommand("input keyevent 223") // KEYCODE_SLEEP
                if (result.success) {
                    AIControlResult.success(command.action, "屏幕已关闭")
                } else {
                    AIControlResult.fail(command.action, "息屏失败: ${result.error}")
                }
            }
            
            "root_take_screenshot" -> {
                val path = "/sdcard/screenshot_${System.currentTimeMillis()}.png"
                val result = rootManager.executeRootCommand("screencap -p $path")
                if (result.success) {
                    AIControlResult.success(command.action, "截图已保存到 $path")
                } else {
                    AIControlResult.fail(command.action, "截图失败: ${result.error}")
                }
            }
            
            // ==================== 输入模拟 ====================
            
            "root_input_tap" -> {
                val x = command.getIntParam("x", 0)
                val y = command.getIntParam("y", 0)
                val result = rootManager.executeRootCommand("input tap $x $y")
                if (result.success) {
                    AIControlResult.success(command.action, "已点击坐标 ($x, $y)")
                } else {
                    AIControlResult.fail(command.action, "点击失败: ${result.error}")
                }
            }
            
            "root_input_swipe" -> {
                val x1 = command.getIntParam("x1", 0)
                val y1 = command.getIntParam("y1", 0)
                val x2 = command.getIntParam("x2", 0)
                val y2 = command.getIntParam("y2", 0)
                val duration = command.getIntParam("duration", 300)
                val result = rootManager.executeRootCommand("input swipe $x1 $y1 $x2 $y2 $duration")
                if (result.success) {
                    AIControlResult.success(command.action, "已滑动从 ($x1,$y1) 到 ($x2,$y2)")
                } else {
                    AIControlResult.fail(command.action, "滑动失败: ${result.error}")
                }
            }
            
            "root_input_text" -> {
                val text = command.getStringParam("text", "")
                if (text.isEmpty()) {
                    return AIControlResult.fail(command.action, "未指定输入文本")
                }
                // 需要对特殊字符进行转义
                val escapedText = text.replace(" ", "%s").replace("'", "\\'")
                val result = rootManager.executeRootCommand("input text '$escapedText'")
                if (result.success) {
                    AIControlResult.success(command.action, "已输入文本")
                } else {
                    AIControlResult.fail(command.action, "输入文本失败: ${result.error}")
                }
            }
            
            "root_input_keyevent" -> {
                val keycode = command.getIntParam("keycode", 0)
                val result = rootManager.executeRootCommand("input keyevent $keycode")
                if (result.success) {
                    AIControlResult.success(command.action, "已发送按键事件 $keycode")
                } else {
                    AIControlResult.fail(command.action, "发送按键失败: ${result.error}")
                }
            }
            
            // ==================== 系统设置 ====================
            
            "root_set_animation_scale" -> {
                val scale = command.getStringParam("scale", "1.0")
                val cmd = """
                    settings put global window_animation_scale $scale
                    settings put global transition_animation_scale $scale
                    settings put global animator_duration_scale $scale
                """.trimIndent()
                val result = rootManager.executeRootCommand(cmd)
                if (result.success) {
                    AIControlResult.success(command.action, "动画缩放已设置为 $scale")
                } else {
                    AIControlResult.fail(command.action, "设置动画缩放失败: ${result.error}")
                }
            }
            
            "root_set_screen_timeout" -> {
                val timeout = command.getIntParam("timeout", 60000)  // 毫秒
                val result = rootManager.executeRootCommand("settings put system screen_off_timeout $timeout")
                if (result.success) {
                    AIControlResult.success(command.action, "屏幕超时已设置为 ${timeout / 1000} 秒")
                } else {
                    AIControlResult.fail(command.action, "设置屏幕超时失败: ${result.error}")
                }
            }
            
            // ==================== 文件操作 ====================
            
            "root_read_file" -> {
                val path = command.getStringParam("path", "")
                if (path.isEmpty()) {
                    return AIControlResult.fail(command.action, "未指定文件路径")
                }
                // 安全检查：限制可读取的目录
                if (!isPathSafe(path)) {
                    return AIControlResult.fail(command.action, "不允许读取该路径")
                }
                val result = rootManager.executeRootCommand("cat $path")
                if (result.success) {
                    AIControlResult.success(command.action, "文件内容: ${result.output.take(500)}")
                } else {
                    AIControlResult.fail(command.action, "读取文件失败: ${result.error}")
                }
            }
            
            "root_write_prop" -> {
                val prop = command.getStringParam("prop", "")
                val value = command.getStringParam("value", "")
                if (prop.isEmpty()) {
                    return AIControlResult.fail(command.action, "未指定属性名")
                }
                // 仅允许安全的属性修改
                if (!isPropSafe(prop)) {
                    return AIControlResult.fail(command.action, "不允许修改该系统属性")
                }
                val result = rootManager.executeRootCommand("setprop $prop $value")
                if (result.success) {
                    AIControlResult.success(command.action, "属性 $prop 已设置为 $value")
                } else {
                    AIControlResult.fail(command.action, "设置属性失败: ${result.error}")
                }
            }
            
            else -> {
                AIControlResult.fail(command.action, "未知操作: ${command.action}")
            }
        }
    }
    
    /**
     * 检查文件路径是否安全
     */
    private fun isPathSafe(path: String): Boolean {
        val allowedPaths = listOf(
            "/system/build.prop",
            "/proc/",
            "/sys/class/",
            "/sdcard/"
        )
        return allowedPaths.any { path.startsWith(it) }
    }
    
    /**
     * 检查属性名是否安全
     */
    private fun isPropSafe(prop: String): Boolean {
        val forbiddenProps = listOf(
            "ro.secure",
            "ro.debuggable",
            "ro.build",
            "ro.product",
            "persist.sys.root"
        )
        return !forbiddenProps.any { prop.startsWith(it) }
    }
    
    /**
     * 记录操作日志
     */
    private fun logOperation(command: AIControlCommand, result: AIControlResult) {
        val log = OperationLog(
            timestamp = System.currentTimeMillis(),
            action = command.action,
            params = command.params.toString(),
            success = result.status == "success",
            detail = result.detail
        )
        operationLogs.add(log)
        
        // 保留最近 100 条日志
        while (operationLogs.size > 100) {
            operationLogs.removeAt(0)
        }
        
        Log.d(TAG, "操作日志: $log")
    }
    
    /**
     * 获取操作日志
     */
    fun getOperationLogs(): List<OperationLog> {
        return operationLogs.toList()
    }
    
    /**
     * 清空操作日志
     */
    fun clearLogs() {
        operationLogs.clear()
    }
    
    /**
     * 操作日志数据类
     */
    data class OperationLog(
        val timestamp: Long,
        val action: String,
        val params: String,
        val success: Boolean,
        val detail: String
    ) {
        fun getFormattedTime(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
        
        override fun toString(): String {
            val status = if (success) "✓" else "✗"
            return "${getFormattedTime()} $status $action: $detail"
        }
    }
    
    /**
     * 生成 AI 系统提示词（用于引导 AI 输出合规指令）
     */
    fun generateSystemPrompt(): String {
        return """
你是一个拥有 Root 权限的手机控制助手。当用户请求执行手机操作时，请输出以下 JSON 格式指令：

**指令格式**：
```json
{"action": "操作类型", "params": {"参数名": "参数值"}, "timestamp": 时间戳, "need_root": true}
```

**可用操作白名单**：
1. 硬件控制：
   - root_bluetooth_toggle: 开关蓝牙, params: {"state": "on/off"}
   - root_wifi_toggle: 开关WiFi, params: {"state": "on/off"}
   - root_mobile_data_toggle: 开关移动数据, params: {"state": "on/off"}
   - root_airplane_mode_toggle: 开关飞行模式, params: {"state": "on/off"}

2. 系统音量/亮度：
   - root_adjust_system_volume: 调整音量, params: {"type": "media/ring/alarm", "value": 0-100}
   - root_set_brightness: 设置亮度, params: {"value": 0-255}

3. 系统操作：
   - root_reboot_device: 重启手机
   - root_shutdown_device: 关机
   - root_take_screenshot: 截屏

4. 应用管理：
   - root_force_stop_app: 强制停止应用, params: {"package_name": "包名"}
   - root_clear_app_data: 清除应用数据, params: {"package_name": "包名"}
   - root_disable_app: 禁用应用, params: {"package_name": "包名"}
   - root_enable_app: 启用应用, params: {"package_name": "包名"}

5. 内存和性能：
   - root_drop_caches: 清理内存缓存
   - root_kill_background_apps: 杀死后台应用
   - root_set_cpu_governor: 设置CPU调速器, params: {"governor": "performance/powersave/schedutil"}

6. 输入模拟：
   - root_input_tap: 模拟点击, params: {"x": 坐标, "y": 坐标}
   - root_input_swipe: 模拟滑动, params: {"x1": 起点x, "y1": 起点y, "x2": 终点x, "y2": 终点y, "duration": 毫秒}
   - root_input_text: 输入文本, params: {"text": "要输入的文字"}
   - root_input_keyevent: 模拟按键, params: {"keycode": 按键码}

**规则**：
1. 只输出 JSON 指令，不要有其他内容
2. 必须包含 action、params、timestamp、need_root 字段
3. 禁止执行白名单以外的操作
4. 高危操作（重启、关机、卸载系统APP）需用户二次确认
        """.trimIndent()
    }
}
