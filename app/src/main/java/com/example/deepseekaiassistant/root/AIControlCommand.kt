package com.example.deepseekaiassistant.root

import org.json.JSONObject
import org.json.JSONException

/**
 * AI 控制指令数据模型
 * 定义 AI 输出的标准化 Root 控制指令格式
 */
data class AIControlCommand(
    val action: String,           // Root 操作类型
    val params: Map<String, Any>, // 操作参数
    val timestamp: Long,          // 时间戳
    val needRoot: Boolean,        // 是否需要 Root 权限
    val verify: String = ""       // 校验标识（可选）
) {
    companion object {
        
        // ==================== Root 操作白名单 ====================
        
        val ROOT_ACTION_WHITELIST = listOf(
            // 硬件控制
            "root_bluetooth_toggle",      // 开关蓝牙
            "root_wifi_toggle",           // 开关 WiFi
            "root_mobile_data_toggle",    // 开关移动数据
            "root_airplane_mode_toggle",  // 开关飞行模式
            "root_gps_toggle",            // 开关 GPS
            "root_nfc_toggle",            // 开关 NFC
            
            // 系统音量控制
            "root_adjust_system_volume",  // 调整系统音量
            "root_set_brightness",        // 设置屏幕亮度
            
            // 系统操作
            "root_reboot_device",         // 重启手机
            "root_shutdown_device",       // 关机
            "root_reboot_recovery",       // 重启到 Recovery
            "root_reboot_bootloader",     // 重启到 Bootloader
            
            // 应用管理
            "root_force_stop_app",        // 强制停止应用
            "root_clear_app_data",        // 清除应用数据
            "root_disable_app",           // 禁用应用
            "root_enable_app",            // 启用应用
            "root_uninstall_system_app",  // 卸载系统应用
            
            // 系统设置
            "root_set_animation_scale",   // 设置动画缩放
            "root_set_screen_timeout",    // 设置屏幕超时时间
            
            // 内存和性能
            "root_drop_caches",           // 清理内存缓存
            "root_kill_background_apps",  // 杀死后台应用
            "root_set_cpu_governor",      // 设置 CPU 调速器
            
            // 文件操作（受限目录）
            "root_read_file",             // 读取系统文件
            "root_write_prop",            // 写入系统属性
            
            // 网络控制
            "root_set_dns",               // 设置 DNS
            "root_flush_dns",             // 刷新 DNS 缓存
            
            // 屏幕控制
            "root_screen_on",             // 亮屏
            "root_screen_off",            // 息屏
            "root_take_screenshot",       // 截屏
            
            // 输入模拟
            "root_input_tap",             // 模拟点击
            "root_input_swipe",           // 模拟滑动
            "root_input_text",            // 输入文本
            "root_input_keyevent"         // 模拟按键
        )
        
        // 高危操作（需二次确认）
        val HIGH_RISK_ACTIONS = listOf(
            "root_reboot_device",
            "root_shutdown_device",
            "root_reboot_recovery",
            "root_reboot_bootloader",
            "root_uninstall_system_app",
            "root_clear_app_data",
            "root_write_prop"
        )
        
        // 恶意关键词过滤列表
        val MALICIOUS_KEYWORDS = listOf(
            "rm -rf",
            "format",
            "mkfs",
            "dd if=",
            "/dev/block",
            "flash",
            "> /system/",
            "> /data/",
            "chmod 777",
            "setenforce 0"
        )
        
        /**
         * 从 JSON 字符串解析指令
         */
        fun fromJson(jsonStr: String): AIControlCommand? {
            return try {
                val jsonObj = JSONObject(jsonStr)
                val action = jsonObj.getString("action")
                val needRoot = jsonObj.optBoolean("need_root", true)
                val timestamp = jsonObj.optLong("timestamp", System.currentTimeMillis())
                val verify = jsonObj.optString("verify", "")
                
                // 解析 params
                val paramsObj = jsonObj.optJSONObject("params") ?: JSONObject()
                val params = mutableMapOf<String, Any>()
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    params[key] = paramsObj.get(key)
                }
                
                AIControlCommand(action, params, timestamp, needRoot, verify)
            } catch (e: JSONException) {
                null
            }
        }
        
        /**
         * 从 AI 输出文本中提取 JSON 指令
         */
        fun extractFromAIOutput(aiOutput: String): AIControlCommand? {
            // 尝试查找 JSON 块
            val jsonPattern = """\{[^{}]*"action"[^{}]*\}""".toRegex()
            val match = jsonPattern.find(aiOutput)
            
            return match?.value?.let { fromJson(it) }
        }
    }
    
    /**
     * 检查指令是否在白名单内
     */
    fun isActionAllowed(): Boolean {
        return ROOT_ACTION_WHITELIST.contains(action)
    }
    
    /**
     * 检查是否为高危操作
     */
    fun isHighRisk(): Boolean {
        return HIGH_RISK_ACTIONS.contains(action)
    }
    
    /**
     * 检查参数是否包含恶意内容
     */
    fun containsMaliciousContent(): Boolean {
        val paramsStr = params.values.joinToString(" ") { it.toString() }
        return MALICIOUS_KEYWORDS.any { paramsStr.contains(it, ignoreCase = true) }
    }
    
    /**
     * 获取参数值（泛型）
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getParam(key: String, default: T): T {
        return (params[key] as? T) ?: default
    }
    
    /**
     * 获取字符串参数
     */
    fun getStringParam(key: String, default: String = ""): String {
        return params[key]?.toString() ?: default
    }
    
    /**
     * 获取整数参数
     */
    fun getIntParam(key: String, default: Int = 0): Int {
        return when (val value = params[key]) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }
    
    /**
     * 获取布尔参数
     */
    fun getBoolParam(key: String, default: Boolean = false): Boolean {
        return when (val value = params[key]) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Int -> value != 0
            else -> default
        }
    }
    
    /**
     * 转换为 JSON
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("action", action)
            put("params", JSONObject(params))
            put("timestamp", timestamp)
            put("need_root", needRoot)
            put("verify", verify)
        }
    }
    
    override fun toString(): String {
        return "AIControlCommand(action=$action, params=$params, needRoot=$needRoot)"
    }
}

/**
 * AI 控制指令执行结果
 */
data class AIControlResult(
    val action: String,
    val status: String,     // "success" 或 "fail"
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("action", action)
            put("status", status)
            put("detail", detail)
            put("timestamp", timestamp)
        }
    }
    
    companion object {
        fun success(action: String, detail: String): AIControlResult {
            return AIControlResult(action, "success", detail)
        }
        
        fun fail(action: String, detail: String): AIControlResult {
            return AIControlResult(action, "fail", detail)
        }
    }
}
