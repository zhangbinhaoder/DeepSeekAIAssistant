package com.example.deepseekaiassistant.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.deepseekaiassistant.DiagnosticManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Termux 集成管理器
 * 
 * 功能：
 * 1. Termux-API 调用 - 访问设备硬件和系统功能
 * 2. Termux-X11 集成 - 图形化 Linux 应用支持
 * 3. 命令执行桥接 - 在 Termux 中执行命令
 * 4. 与本应用 AI 功能联动 - 让 AI 可以调用 Termux 命令
 */
object TermuxIntegration {
    
    private const val TAG = "TermuxIntegration"
    
    // Termux 相关包名
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_API_PACKAGE = "com.termux.api"
    const val TERMUX_X11_PACKAGE = "com.termux.x11"
    const val TERMUX_STYLING_PACKAGE = "com.termux.styling"
    const val TERMUX_BOOT_PACKAGE = "com.termux.boot"
    const val TERMUX_WIDGET_PACKAGE = "com.termux.widget"
    const val TERMUX_FLOAT_PACKAGE = "com.termux.window"
    
    // Termux-API 命令前缀
    private const val TERMUX_API_CMD = "termux-"
    
    // 安装状态
    data class TermuxStatus(
        val termuxInstalled: Boolean = false,
        val apiInstalled: Boolean = false,
        val x11Installed: Boolean = false,
        val stylingInstalled: Boolean = false,
        val bootInstalled: Boolean = false,
        val widgetInstalled: Boolean = false,
        val floatInstalled: Boolean = false,
        val termuxVersion: String? = null
    ) {
        val allCoreInstalled: Boolean get() = termuxInstalled && apiInstalled
        val hasX11Support: Boolean get() = termuxInstalled && x11Installed
    }
    
    /**
     * 检查 Termux 及其插件安装状态
     */
    fun checkTermuxStatus(context: Context): TermuxStatus {
        val pm = context.packageManager
        
        fun isInstalled(packageName: String): Boolean {
            return try {
                pm.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
        
        fun getVersion(packageName: String): String? {
            return try {
                val info = pm.getPackageInfo(packageName, 0)
                info.versionName
            } catch (e: Exception) {
                null
            }
        }
        
        return TermuxStatus(
            termuxInstalled = isInstalled(TERMUX_PACKAGE),
            apiInstalled = isInstalled(TERMUX_API_PACKAGE),
            x11Installed = isInstalled(TERMUX_X11_PACKAGE),
            stylingInstalled = isInstalled(TERMUX_STYLING_PACKAGE),
            bootInstalled = isInstalled(TERMUX_BOOT_PACKAGE),
            widgetInstalled = isInstalled(TERMUX_WIDGET_PACKAGE),
            floatInstalled = isInstalled(TERMUX_FLOAT_PACKAGE),
            termuxVersion = getVersion(TERMUX_PACKAGE)
        )
    }
    
    /**
     * 启动 Termux 主应用
     */
    fun launchTermux(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                Toast.makeText(context, "Termux 未安装", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            DiagnosticManager.error(TAG, "启动 Termux 失败", e.message ?: "")
            false
        }
    }
    
    /**
     * 启动 Termux X11
     */
    fun launchTermuxX11(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_X11_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                Toast.makeText(context, "Termux:X11 未安装", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            DiagnosticManager.error(TAG, "启动 Termux:X11 失败", e.message ?: "")
            false
        }
    }
    
    /**
     * 在 Termux 中执行命令
     * 使用 Termux:RUN_COMMAND intent
     */
    fun runInTermux(context: Context, command: String, background: Boolean = false): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            DiagnosticManager.info(TAG, "发送命令到 Termux", command)
            true
        } catch (e: Exception) {
            DiagnosticManager.error(TAG, "Termux 命令执行失败", e.message ?: "")
            false
        }
    }
    
    /**
     * 打开 Termux 并执行命令（前台显示）
     */
    fun openTermuxWithCommand(context: Context, command: String): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, "com.termux.app.TermuxActivity")
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, command)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // 备用方案：直接启动 Termux
            launchTermux(context)
        }
    }
    
    // ==================== Termux-API 功能 ====================
    
    /**
     * Termux-API 功能分类
     */
    enum class ApiCategory(val displayName: String, val icon: String) {
        DEVICE("设备信息", "📱"),
        SENSORS("传感器", "🎛️"),
        COMMUNICATION("通讯功能", "📞"),
        MEDIA("媒体功能", "🎵"),
        LOCATION("位置服务", "📍"),
        SYSTEM("系统功能", "⚙️"),
        NETWORK("网络功能", "🌐"),
        STORAGE("存储功能", "💾")
    }
    
    /**
     * Termux-API 命令定义
     */
    data class ApiCommand(
        val name: String,
        val command: String,
        val description: String,
        val category: ApiCategory,
        val requiresArgs: Boolean = false,
        val argsHint: String = ""
    )
    
    // 所有支持的 Termux-API 命令
    val apiCommands = listOf(
        // 设备信息
        ApiCommand("电池状态", "termux-battery-status", "获取电池电量、温度、充电状态", ApiCategory.DEVICE),
        ApiCommand("设备信息", "termux-telephony-deviceinfo", "获取设备 IMEI、信号等信息", ApiCategory.DEVICE),
        ApiCommand("WiFi信息", "termux-wifi-connectioninfo", "获取当前 WiFi 连接信息", ApiCategory.DEVICE),
        ApiCommand("WiFi扫描", "termux-wifi-scaninfo", "扫描附近 WiFi 网络", ApiCategory.DEVICE),
        ApiCommand("红外频率", "termux-infrared-frequencies", "获取支持的红外频率", ApiCategory.DEVICE),
        
        // 传感器
        ApiCommand("传感器列表", "termux-sensor -l", "列出所有可用传感器", ApiCategory.SENSORS),
        ApiCommand("加速度计", "termux-sensor -s accelerometer -n 1", "读取加速度传感器", ApiCategory.SENSORS),
        ApiCommand("陀螺仪", "termux-sensor -s gyroscope -n 1", "读取陀螺仪数据", ApiCategory.SENSORS),
        ApiCommand("光线传感器", "termux-sensor -s light -n 1", "读取环境光强度", ApiCategory.SENSORS),
        ApiCommand("指纹验证", "termux-fingerprint", "请求指纹验证", ApiCategory.SENSORS),
        
        // 通讯功能
        ApiCommand("通话记录", "termux-call-log", "获取通话记录", ApiCategory.COMMUNICATION),
        ApiCommand("联系人列表", "termux-contact-list", "获取联系人列表", ApiCategory.COMMUNICATION),
        ApiCommand("短信列表", "termux-sms-list", "获取短信列表", ApiCategory.COMMUNICATION),
        ApiCommand("发送短信", "termux-sms-send -n", "发送短信", ApiCategory.COMMUNICATION, true, "号码 内容"),
        ApiCommand("拨打电话", "termux-telephony-call", "拨打电话", ApiCategory.COMMUNICATION, true, "电话号码"),
        
        // 媒体功能
        ApiCommand("拍照", "termux-camera-photo", "使用摄像头拍照", ApiCategory.MEDIA, true, "输出文件路径"),
        ApiCommand("录音", "termux-microphone-record", "录制音频", ApiCategory.MEDIA),
        ApiCommand("播放音频", "termux-media-player", "播放音频文件", ApiCategory.MEDIA, true, "文件路径"),
        ApiCommand("TTS朗读", "termux-tts-speak", "文字转语音朗读", ApiCategory.MEDIA, true, "要朗读的文字"),
        ApiCommand("音量控制", "termux-volume", "获取/设置音量", ApiCategory.MEDIA),
        ApiCommand("震动", "termux-vibrate -d 500", "让设备震动", ApiCategory.MEDIA),
        ApiCommand("手电筒", "termux-torch on", "打开/关闭手电筒", ApiCategory.MEDIA),
        
        // 位置服务
        ApiCommand("获取位置", "termux-location", "获取 GPS 位置", ApiCategory.LOCATION),
        
        // 系统功能
        ApiCommand("剪贴板获取", "termux-clipboard-get", "获取剪贴板内容", ApiCategory.SYSTEM),
        ApiCommand("剪贴板设置", "termux-clipboard-set", "设置剪贴板内容", ApiCategory.SYSTEM, true, "内容"),
        ApiCommand("通知", "termux-notification", "发送系统通知", ApiCategory.SYSTEM, true, "-t 标题 -c 内容"),
        ApiCommand("Toast提示", "termux-toast", "显示 Toast 消息", ApiCategory.SYSTEM, true, "消息内容"),
        ApiCommand("分享", "termux-share", "分享文件或文本", ApiCategory.SYSTEM, true, "文件路径"),
        ApiCommand("URL打开", "termux-open-url", "在浏览器打开 URL", ApiCategory.SYSTEM, true, "URL地址"),
        ApiCommand("对话框", "termux-dialog", "显示输入对话框", ApiCategory.SYSTEM),
        ApiCommand("亮度", "termux-brightness", "设置屏幕亮度", ApiCategory.SYSTEM, true, "0-255"),
        ApiCommand("壁纸", "termux-wallpaper", "设置壁纸", ApiCategory.SYSTEM, true, "-f 图片路径"),
        
        // 网络功能
        ApiCommand("下载文件", "termux-download", "下载文件", ApiCategory.NETWORK, true, "URL"),
        ApiCommand("USB设备", "termux-usb", "列出 USB 设备", ApiCategory.NETWORK),
        
        // 存储功能
        ApiCommand("存储访问", "termux-setup-storage", "设置存储权限", ApiCategory.STORAGE)
    )
    
    /**
     * 按分类获取 API 命令
     */
    fun getApiByCategory(category: ApiCategory): List<ApiCommand> {
        return apiCommands.filter { it.category == category }
    }
    
    // ==================== Termux-X11 功能 ====================
    
    /**
     * X11 应用定义
     */
    data class X11App(
        val name: String,
        val packageName: String,
        val installCommand: String,
        val launchCommand: String,
        val description: String,
        val icon: String
    )
    
    // 常用 X11 应用
    val x11Apps = listOf(
        X11App(
            "Firefox", "firefox", 
            "pkg install firefox", 
            "DISPLAY=:0 firefox &",
            "开源网页浏览器", "🦊"
        ),
        X11App(
            "GIMP", "gimp",
            "pkg install gimp",
            "DISPLAY=:0 gimp &",
            "图像编辑软件", "🎨"
        ),
        X11App(
            "VLC", "vlc",
            "pkg install vlc",
            "DISPLAY=:0 vlc &",
            "多媒体播放器", "▶️"
        ),
        X11App(
            "LibreOffice", "libreoffice",
            "pkg install libreoffice",
            "DISPLAY=:0 libreoffice &",
            "办公套件", "📄"
        ),
        X11App(
            "VS Code", "code-oss",
            "pkg install code-oss",
            "DISPLAY=:0 code-oss &",
            "代码编辑器", "💻"
        ),
        X11App(
            "Thunar", "thunar",
            "pkg install thunar",
            "DISPLAY=:0 thunar &",
            "文件管理器", "📁"
        ),
        X11App(
            "XFCE4 终端", "xfce4-terminal",
            "pkg install xfce4-terminal",
            "DISPLAY=:0 xfce4-terminal &",
            "图形终端", "🖥️"
        ),
        X11App(
            "Gedit", "gedit",
            "pkg install gedit",
            "DISPLAY=:0 gedit &",
            "文本编辑器", "📝"
        ),
        X11App(
            "Blender", "blender",
            "pkg install blender",
            "DISPLAY=:0 blender &",
            "3D建模软件", "🎬"
        ),
        X11App(
            "Inkscape", "inkscape",
            "pkg install inkscape",
            "DISPLAY=:0 inkscape &",
            "矢量图编辑", "✏️"
        )
    )
    
    /**
     * 启动 X11 桌面环境
     */
    fun startX11Desktop(context: Context, desktop: String = "xfce4"): Boolean {
        val command = when (desktop.lowercase()) {
            "xfce4" -> "export DISPLAY=:0 && startxfce4 &"
            "lxqt" -> "export DISPLAY=:0 && startlxqt &"
            "openbox" -> "export DISPLAY=:0 && openbox-session &"
            "fluxbox" -> "export DISPLAY=:0 && startfluxbox &"
            else -> "export DISPLAY=:0 && startxfce4 &"
        }
        
        // 先启动 Termux:X11
        launchTermuxX11(context)
        
        // 然后在 Termux 中启动桌面
        return runInTermux(context, command, background = true)
    }
    
    // ==================== 脚本管理 ====================
    
    /**
     * 预置脚本
     */
    data class TermuxScript(
        val name: String,
        val script: String,
        val description: String,
        val category: String
    )
    
    val presetScripts = listOf(
        TermuxScript(
            "系统信息",
            """
                echo "=== 系统信息 ==="
                uname -a
                echo ""
                echo "=== CPU 信息 ==="
                cat /proc/cpuinfo | head -20
                echo ""
                echo "=== 内存信息 ==="
                free -h
                echo ""
                echo "=== 存储信息 ==="
                df -h
            """.trimIndent(),
            "显示系统、CPU、内存、存储信息",
            "系统"
        ),
        TermuxScript(
            "安装 Python",
            "pkg update -y && pkg install -y python python-pip",
            "安装 Python 和 pip",
            "开发环境"
        ),
        TermuxScript(
            "安装 Node.js",
            "pkg update -y && pkg install -y nodejs-lts",
            "安装 Node.js LTS 版本",
            "开发环境"
        ),
        TermuxScript(
            "安装 Git",
            "pkg update -y && pkg install -y git",
            "安装 Git 版本控制",
            "开发环境"
        ),
        TermuxScript(
            "安装开发工具集",
            "pkg update -y && pkg install -y git vim nano wget curl clang make cmake",
            "安装常用开发工具",
            "开发环境"
        ),
        TermuxScript(
            "安装 XFCE4 桌面",
            """
                pkg update -y
                pkg install -y x11-repo
                pkg install -y xfce4 xfce4-terminal
                echo "XFCE4 安装完成，使用 Termux:X11 启动"
            """.trimIndent(),
            "安装 XFCE4 桌面环境",
            "X11桌面"
        ),
        TermuxScript(
            "启动 SSH 服务器",
            """
                pkg install -y openssh
                sshd
                echo "SSH 服务已启动"
                echo "连接: ssh -p 8022 $(whoami)@$(termux-wifi-connectioninfo | grep ip | cut -d'"' -f4)"
            """.trimIndent(),
            "安装并启动 SSH 服务",
            "网络服务"
        ),
        TermuxScript(
            "启动 HTTP 服务器",
            """
                pkg install -y python
                echo "在当前目录启动 HTTP 服务器..."
                python -m http.server 8080
            """.trimIndent(),
            "启动简易 HTTP 服务器",
            "网络服务"
        ),
        TermuxScript(
            "网络测速",
            """
                pkg install -y speedtest-go
                speedtest-go
            """.trimIndent(),
            "测试网络速度",
            "网络工具"
        ),
        TermuxScript(
            "备份 Termux",
            """
                cd /data/data/com.termux/files
                tar -czvf ~/storage/shared/termux-backup-$(date +%Y%m%d).tar.gz home usr
                echo "备份完成: ~/storage/shared/termux-backup-$(date +%Y%m%d).tar.gz"
            """.trimIndent(),
            "备份 Termux 环境",
            "系统"
        )
    )
    
    // ==================== 与 AI 联动 ====================
    
    /**
     * AI 可调用的 Termux 命令接口
     * 供 AI 代理使用
     */
    interface TermuxAIBridge {
        suspend fun executeCommand(command: String): String
        suspend fun getDeviceInfo(): Map<String, Any>
        suspend fun sendNotification(title: String, content: String)
        suspend fun capturePhoto(outputPath: String): Boolean
        suspend fun getLocation(): Pair<Double, Double>?
    }
    
    /**
     * 创建 AI 桥接实现
     */
    fun createAIBridge(context: Context): TermuxAIBridge {
        return object : TermuxAIBridge {
            override suspend fun executeCommand(command: String): String {
                // 通过 Termux 执行命令并获取结果
                runInTermux(context, command, background = true)
                return "命令已发送到 Termux: $command"
            }
            
            override suspend fun getDeviceInfo(): Map<String, Any> {
                return mapOf(
                    "model" to Build.MODEL,
                    "manufacturer" to Build.MANUFACTURER,
                    "sdk" to Build.VERSION.SDK_INT,
                    "termux" to checkTermuxStatus(context)
                )
            }
            
            override suspend fun sendNotification(title: String, content: String) {
                val cmd = "termux-notification -t \"$title\" -c \"$content\""
                runInTermux(context, cmd, background = true)
            }
            
            override suspend fun capturePhoto(outputPath: String): Boolean {
                val cmd = "termux-camera-photo $outputPath"
                return runInTermux(context, cmd, background = false)
            }
            
            override suspend fun getLocation(): Pair<Double, Double>? {
                // 需要异步处理实际结果
                runInTermux(context, "termux-location", background = false)
                return null // 实际需要解析结果
            }
        }
    }
    
    // ==================== 安装引导 ====================
    
    /**
     * 打开 F-Droid 下载页面
     */
    fun openFDroidDownload(context: Context, packageName: String) {
        val url = "https://f-droid.org/packages/$packageName/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    /**
     * 打开 GitHub Releases 页面
     */
    fun openGitHubReleases(context: Context, repo: String = "termux/termux-app") {
        val url = "https://github.com/$repo/releases"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    /**
     * 获取安装指南
     */
    fun getInstallGuide(): String {
        return """
            |## Termux 安装指南
            |
            |### 1. 安装 Termux 主程序
            |从 F-Droid 或 GitHub Releases 下载安装
            |⚠️ 请勿使用 Google Play 版本（已停止更新）
            |
            |### 2. 安装 Termux-API
            |用于访问设备硬件（相机、传感器等）
            |
            |### 3. 安装 Termux:X11（可选）
            |用于运行图形化 Linux 应用
            |
            |### 4. 初始化配置
            |打开 Termux 执行：
            |```
            |pkg update && pkg upgrade
            |termux-setup-storage
            |```
            |
            |### 5. 安装 Termux-API 命令
            |```
            |pkg install termux-api
            |```
        """.trimMargin()
    }
}
