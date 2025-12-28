package com.example.deepseekaiassistant.capability

import android.content.Context
import java.util.Calendar
import java.util.regex.Pattern

/**
 * AI 指令解析器 - 解析用户自然语言并执行设备操作
 */
class CommandParser(private val context: Context) {
    
    private val deviceManager = DeviceCapabilityManager(context)
    
    // 指令模式
    private val patterns = mapOf(
        // 电话相关
        "phone" to listOf(
            Pattern.compile("(打电话|拨打|呼叫|call)\\s*(给)?\\s*(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(打给|呼叫)\\s*(.+)", Pattern.CASE_INSENSITIVE)
        ),
        // 短信相关
        "sms" to listOf(
            Pattern.compile("(发短信|发送短信|短信|sms)\\s*(给)?\\s*(.+?)\\s*(内容|说|:)?\\s*(.+)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(告诉|通知)\\s*(.+?)\\s*(说)?\\s*(.+)", Pattern.CASE_INSENSITIVE)
        ),
        // 闹钟相关
        "alarm" to listOf(
            Pattern.compile("(设置?闹钟|闹钟|提醒我?|alarm)\\s*(在)?\\s*(\\d{1,2})[::点时](\\d{0,2})分?\\s*(.*)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\d{1,2})[::点时](\\d{0,2})分?\\s*(叫我|提醒|闹钟)\\s*(.*)?", Pattern.CASE_INSENSITIVE)
        ),
        // 定时器相关
        "timer" to listOf(
            Pattern.compile("(定时器?|计时|倒计时|timer)\\s*(\\d+)\\s*(秒|分钟?|小时?)\\s*(.*)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\d+)\\s*(秒|分钟?|小时?)后?\\s*(提醒|叫我)\\s*(.*)?", Pattern.CASE_INSENSITIVE)
        ),
        // 手电筒相关
        "flashlight" to listOf(
            Pattern.compile("(打开|开启|开|turn on)\\s*(手电筒|闪光灯|flashlight)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(关闭|关掉|关|turn off)\\s*(手电筒|闪光灯|flashlight)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(手电筒|闪光灯)\\s*(打开|开启|开|关闭|关掉|关)", Pattern.CASE_INSENSITIVE)
        ),
        // 位置相关
        "location" to listOf(
            Pattern.compile("(我的位置|当前位置|在哪|位置|定位|location|where am i)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(获取|查看|查询)\\s*(位置|定位)", Pattern.CASE_INSENSITIVE)
        ),
        // 电池相关
        "battery" to listOf(
            Pattern.compile("(电量|电池|battery|还有多少电)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(查看|查询)\\s*(电量|电池)", Pattern.CASE_INSENSITIVE)
        ),
        // 音量相关
        "volume" to listOf(
            Pattern.compile("(音量|声音|volume)\\s*(设置?为?|调到?|调整?为?)?\\s*(\\d+)\\s*%?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(调大|增大|提高)\\s*(音量|声音)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(调小|减小|降低)\\s*(音量|声音)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(静音|mute)", Pattern.CASE_INSENSITIVE)
        ),
        // 联系人相关
        "contacts" to listOf(
            Pattern.compile("(查找|搜索|找)\\s*联系人\\s*(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.+?)\\s*的?(电话|号码|手机号)", Pattern.CASE_INSENSITIVE)
        ),
        // 打开网页
        "url" to listOf(
            Pattern.compile("(打开|访问|open)\\s*(网页|网站|url)?\\s*(https?://\\S+|www\\.\\S+|\\S+\\.com\\S*|\\S+\\.cn\\S*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(搜索|百度|谷歌|google|search)\\s+(.+)", Pattern.CASE_INSENSITIVE)
        ),
        // 打开应用
        "app" to listOf(
            Pattern.compile("(打开|启动|open|launch)\\s*(应用|app)?\\s*(.+)", Pattern.CASE_INSENSITIVE)
        ),
        // 剪贴板
        "clipboard" to listOf(
            Pattern.compile("(复制|copy)\\s*(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(粘贴|paste|剪贴板内容)", Pattern.CASE_INSENSITIVE)
        ),
        // 振动
        "vibrate" to listOf(
            Pattern.compile("(振动|震动|vibrate)\\s*(\\d+)?\\s*(毫秒|秒|ms)?", Pattern.CASE_INSENSITIVE)
        )
    )
    
    // 常用应用包名映射
    private val appPackages = mapOf(
        "微信" to "com.tencent.mm",
        "wechat" to "com.tencent.mm",
        "qq" to "com.tencent.mobileqq",
        "QQ" to "com.tencent.mobileqq",
        "支付宝" to "com.eg.android.AlipayGphone",
        "alipay" to "com.eg.android.AlipayGphone",
        "淘宝" to "com.taobao.taobao",
        "抖音" to "com.ss.android.ugc.aweme",
        "tiktok" to "com.ss.android.ugc.aweme",
        "bilibili" to "tv.danmaku.bili",
        "b站" to "tv.danmaku.bili",
        "哔哩哔哩" to "tv.danmaku.bili",
        "网易云音乐" to "com.netease.cloudmusic",
        "qq音乐" to "com.tencent.qqmusic",
        "高德地图" to "com.autonavi.minimap",
        "百度地图" to "com.baidu.BaiduMap",
        "美团" to "com.sankuai.meituan",
        "饿了么" to "me.ele",
        "京东" to "com.jingdong.app.mall",
        "拼多多" to "com.xunmeng.pinduoduo",
        "知乎" to "com.zhihu.android",
        "微博" to "com.sina.weibo",
        "今日头条" to "com.ss.android.article.news",
        "设置" to "com.android.settings",
        "相机" to "com.android.camera",
        "相册" to "com.android.gallery3d",
        "日历" to "com.android.calendar",
        "时钟" to "com.android.deskclock",
        "计算器" to "com.android.calculator2",
        "浏览器" to "com.android.browser",
        "chrome" to "com.android.chrome",
        "文件管理" to "com.android.documentsui"
    )
    
    /**
     * 解析并执行指令
     * @return 如果是设备指令则返回执行结果，否则返回 null 表示应该交给 AI 处理
     */
    fun parseAndExecute(input: String): CommandResult? {
        val trimmedInput = input.trim()
        
        // 尝试匹配各种指令模式
        
        // 1. 手电筒
        for (pattern in patterns["flashlight"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                val action = matcher.group(0) ?: ""
                val turnOn = action.contains("打开") || action.contains("开启") || 
                             action.contains("开") || action.contains("on", ignoreCase = true)
                val result = deviceManager.toggleFlashlight(turnOn)
                return CommandResult(true, formatResult(result))
            }
        }
        
        // 2. 位置
        for (pattern in patterns["location"]!!) {
            if (pattern.matcher(trimmedInput).find()) {
                val result = deviceManager.getCurrentLocation()
                return CommandResult(true, formatResult(result))
            }
        }
        
        // 3. 电池
        for (pattern in patterns["battery"]!!) {
            if (pattern.matcher(trimmedInput).find()) {
                val result = deviceManager.getBatteryStatus()
                return CommandResult(true, formatResult(result))
            }
        }
        
        // 4. 电话
        for (pattern in patterns["phone"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                val target = extractLastGroup(matcher) ?: continue
                val phoneNumber = extractPhoneNumber(target)
                if (phoneNumber != null) {
                    val result = deviceManager.makePhoneCall(phoneNumber)
                    return CommandResult(true, formatResult(result))
                } else {
                    // 可能是联系人名字，先查找联系人
                    val contactResult = deviceManager.findContactPhone(target)
                    if (contactResult is CapabilityResult.Success && 
                        contactResult.data.containsKey("contacts")) {
                        @Suppress("UNCHECKED_CAST")
                        val contacts = contactResult.data["contacts"] as? List<Map<String, String>>
                        if (!contacts.isNullOrEmpty() && contacts.size == 1) {
                            val phone = contacts[0]["phone"] ?: ""
                            val result = deviceManager.makePhoneCall(phone)
                            return CommandResult(true, formatResult(result))
                        }
                        return CommandResult(true, formatResult(contactResult) + "\n请指定要拨打的号码")
                    }
                    return CommandResult(true, "找不到联系人 '$target'，请直接输入电话号码")
                }
            }
        }
        
        // 5. 闹钟
        for (pattern in patterns["alarm"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                val groups = (1..matcher.groupCount()).mapNotNull { matcher.group(it) }
                val (hour, minute, message) = parseAlarmTime(groups)
                if (hour >= 0) {
                    val result = deviceManager.setAlarm(hour, minute, message)
                    return CommandResult(true, formatResult(result))
                }
            }
        }
        
        // 6. 定时器
        for (pattern in patterns["timer"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                val groups = (1..matcher.groupCount()).mapNotNull { matcher.group(it) }
                val (seconds, message) = parseTimerDuration(groups)
                if (seconds > 0) {
                    val result = deviceManager.setTimer(seconds, message)
                    return CommandResult(true, formatResult(result))
                }
            }
        }
        
        // 7. 音量
        for (pattern in patterns["volume"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                when {
                    trimmedInput.contains("静音") || trimmedInput.contains("mute", ignoreCase = true) -> {
                        val result = deviceManager.setVolume(0)
                        return CommandResult(true, formatResult(result))
                    }
                    trimmedInput.contains("调大") || trimmedInput.contains("增大") -> {
                        val current = deviceManager.getVolume()
                        if (current is CapabilityResult.Success) {
                            val vol = (current.data["volume"] as? Int ?: 50) + 20
                            val result = deviceManager.setVolume(vol.coerceAtMost(100))
                            return CommandResult(true, formatResult(result))
                        }
                    }
                    trimmedInput.contains("调小") || trimmedInput.contains("减小") -> {
                        val current = deviceManager.getVolume()
                        if (current is CapabilityResult.Success) {
                            val vol = (current.data["volume"] as? Int ?: 50) - 20
                            val result = deviceManager.setVolume(vol.coerceAtLeast(0))
                            return CommandResult(true, formatResult(result))
                        }
                    }
                    else -> {
                        // 提取数字
                        val numPattern = Pattern.compile("(\\d+)")
                        val numMatcher = numPattern.matcher(trimmedInput)
                        if (numMatcher.find()) {
                            val volume = numMatcher.group(1)?.toIntOrNull() ?: 50
                            val result = deviceManager.setVolume(volume.coerceIn(0, 100))
                            return CommandResult(true, formatResult(result))
                        }
                    }
                }
            }
        }
        
        // 8. 联系人
        for (pattern in patterns["contacts"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                val name = extractLastGroup(matcher)?.replace("的", "")?.replace("电话", "")
                    ?.replace("号码", "")?.replace("手机号", "")?.trim() ?: continue
                if (name.isNotEmpty()) {
                    val result = deviceManager.findContactPhone(name)
                    return CommandResult(true, formatResult(result))
                }
            }
        }
        
        // 9. 打开应用
        for (pattern in patterns["app"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                val appName = extractLastGroup(matcher)?.trim() ?: continue
                val packageName = appPackages[appName] ?: appPackages[appName.lowercase()]
                if (packageName != null) {
                    val result = deviceManager.openApp(packageName)
                    return CommandResult(true, formatResult(result))
                } else {
                    // 尝试直接作为包名
                    if (appName.contains(".")) {
                        val result = deviceManager.openApp(appName)
                        return CommandResult(true, formatResult(result))
                    }
                    return CommandResult(true, "未找到应用 '$appName'，请检查应用名称")
                }
            }
        }
        
        // 10. 打开网页/搜索
        for (pattern in patterns["url"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                if (trimmedInput.contains("搜索") || trimmedInput.contains("search", ignoreCase = true) ||
                    trimmedInput.contains("百度") || trimmedInput.contains("谷歌")) {
                    val query = extractLastGroup(matcher) ?: continue
                    val searchUrl = "https://www.baidu.com/s?wd=${java.net.URLEncoder.encode(query, "UTF-8")}"
                    val result = deviceManager.openUrl(searchUrl)
                    return CommandResult(true, formatResult(result))
                } else {
                    val url = extractLastGroup(matcher) ?: continue
                    val result = deviceManager.openUrl(url)
                    return CommandResult(true, formatResult(result))
                }
            }
        }
        
        // 11. 剪贴板
        for (pattern in patterns["clipboard"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                if (trimmedInput.contains("粘贴") || trimmedInput.contains("paste") ||
                    trimmedInput.contains("剪贴板内容")) {
                    val result = deviceManager.getClipboardContent()
                    return CommandResult(true, formatResult(result))
                } else {
                    val text = extractLastGroup(matcher) ?: continue
                    val result = deviceManager.copyToClipboard(text)
                    return CommandResult(true, formatResult(result))
                }
            }
        }
        
        // 12. 振动
        for (pattern in patterns["vibrate"]!!) {
            val matcher = pattern.matcher(trimmedInput)
            if (matcher.find()) {
                val durationStr = matcher.group(2)
                var duration = durationStr?.toLongOrNull() ?: 500
                val unit = matcher.group(3) ?: ""
                if (unit.contains("秒")) {
                    duration *= 1000
                }
                val result = deviceManager.vibrate(duration)
                return CommandResult(true, formatResult(result))
            }
        }
        
        // 没有匹配到任何设备指令
        return null
    }
    
    private fun formatResult(result: CapabilityResult): String {
        return when (result) {
            is CapabilityResult.Success -> "✅ ${result.message}"
            is CapabilityResult.Error -> "❌ ${result.message}"
            is CapabilityResult.PermissionDenied -> "⚠️ ${result.message}\n请在设置中授予相应权限"
        }
    }
    
    private fun extractLastGroup(matcher: java.util.regex.Matcher): String? {
        for (i in matcher.groupCount() downTo 1) {
            val group = matcher.group(i)
            if (!group.isNullOrBlank()) {
                return group.trim()
            }
        }
        return null
    }
    
    private fun extractPhoneNumber(input: String): String? {
        val phonePattern = Pattern.compile("1[3-9]\\d{9}|\\d{3,4}-?\\d{7,8}")
        val matcher = phonePattern.matcher(input.replace(" ", ""))
        return if (matcher.find()) matcher.group() else null
    }
    
    private fun parseAlarmTime(groups: List<String>): Triple<Int, Int, String> {
        var hour = -1
        var minute = 0
        var message = "AI助手提醒"
        
        for (group in groups) {
            val num = group.toIntOrNull()
            when {
                num != null && hour < 0 && num in 0..23 -> hour = num
                num != null && hour >= 0 && num in 0..59 -> minute = num
                !group.matches(Regex("\\d+")) && group.length > 1 -> message = group
            }
        }
        
        return Triple(hour, minute, message)
    }
    
    private fun parseTimerDuration(groups: List<String>): Pair<Int, String> {
        var seconds = 0
        var message = "计时结束"
        
        for ((index, group) in groups.withIndex()) {
            val num = group.toIntOrNull()
            when {
                num != null -> {
                    val nextUnit = groups.getOrNull(index + 1) ?: ""
                    seconds = when {
                        nextUnit.contains("分") -> num * 60
                        nextUnit.contains("小时") -> num * 3600
                        else -> num
                    }
                }
                !group.matches(Regex("\\d+|秒|分钟?|小时?")) && group.length > 1 -> {
                    message = group
                }
            }
        }
        
        return Pair(seconds, message)
    }
    
    /**
     * 获取设备能力帮助信息
     */
    fun getCapabilitiesHelp(): String {
        val capabilities = deviceManager.getAvailableCapabilities()
        val sb = StringBuilder()
        sb.appendLine("📱 **设备控制能力**\n")
        
        capabilities.forEach { cap ->
            val status = if (cap.isAvailable) "✅" else "❌"
            sb.appendLine("$status **${cap.name}** - `${cap.usage}`")
        }
        
        sb.appendLine("\n💡 **使用示例：**")
        sb.appendLine("- \"打电话给 13800138000\"")
        sb.appendLine("- \"发短信给张三 说 明天见\"")
        sb.appendLine("- \"设置闹钟 7:30 起床\"")
        sb.appendLine("- \"10分钟后提醒我开会\"")
        sb.appendLine("- \"打开手电筒\"")
        sb.appendLine("- \"我的位置\"")
        sb.appendLine("- \"电量多少\"")
        sb.appendLine("- \"打开微信\"")
        sb.appendLine("- \"搜索 天气预报\"")
        
        return sb.toString()
    }
}

/**
 * 指令执行结果
 */
data class CommandResult(
    val isDeviceCommand: Boolean,  // 是否为设备指令
    val response: String           // 响应消息
)
