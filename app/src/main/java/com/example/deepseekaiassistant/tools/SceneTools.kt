package com.example.deepseekaiassistant.tools

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Scene 玩机工具集
 * 提供系统优化、性能调节、应用管理等功能
 */
class SceneTools(private val context: Context) {
    
    companion object {
        private var hasRootAccess: Boolean? = null
    }
    
    // ===================== ROOT 权限检测 =====================
    
    /**
     * 检测是否有 ROOT 权限
     */
    fun checkRootAccess(): Boolean {
        if (hasRootAccess != null) return hasRootAccess!!
        
        hasRootAccess = try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            
            result?.contains("uid=0") == true
        } catch (e: Exception) {
            false
        }
        
        return hasRootAccess!!
    }
    
    /**
     * 执行 ROOT 命令
     */
    fun executeRootCommand(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = stdout.readText()
            val error = stderr.readText()
            
            val exitCode = process.waitFor()
            
            CommandResult(exitCode == 0, output, error, exitCode)
        } catch (e: Exception) {
            CommandResult(false, "", e.message ?: "Unknown error", -1)
        }
    }
    
    /**
     * 执行普通命令
     */
    fun executeCommand(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = stdout.readText()
            val error = stderr.readText()
            
            val exitCode = process.waitFor()
            
            CommandResult(exitCode == 0, output, error, exitCode)
        } catch (e: Exception) {
            CommandResult(false, "", e.message ?: "Unknown error", -1)
        }
    }
    
    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int
    )
    
    // ===================== CPU 调节 =====================
    
    /**
     * 获取 CPU 调速器列表
     */
    fun getCpuGovernors(): List<String> {
        return try {
            val path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors"
            File(path).readText().trim().split(" ")
        } catch (e: Exception) {
            listOf("performance", "powersave", "ondemand", "interactive", "schedutil")
        }
    }
    
    /**
     * 获取当前 CPU 调速器
     */
    fun getCurrentGovernor(): String {
        return try {
            File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").readText().trim()
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * 设置 CPU 调速器（需要 ROOT）
     */
    fun setCpuGovernor(governor: String): Boolean {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val commands = StringBuilder()
        
        for (i in 0 until cpuCount) {
            commands.append("echo $governor > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor\n")
        }
        
        val result = executeRootCommand(commands.toString())
        return result.success
    }
    
    /**
     * 获取 CPU 频率范围
     */
    fun getCpuFreqRange(): Pair<Long, Long> {
        val minFreq = try {
            File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq").readText().trim().toLong() / 1000
        } catch (e: Exception) { 0L }
        
        val maxFreq = try {
            File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000
        } catch (e: Exception) { 0L }
        
        return Pair(minFreq, maxFreq)
    }
    
    // ===================== 应用管理 =====================
    
    /**
     * 获取已安装应用列表
     */
    fun getInstalledApps(includeSystem: Boolean = false): List<AppInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return packages
            .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { appInfo ->
                val packageInfo = pm.getPackageInfo(appInfo.packageName, 0)
                AppInfo(
                    name = pm.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    versionName = packageInfo.versionName ?: "",
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    },
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    installTime = packageInfo.firstInstallTime,
                    updateTime = packageInfo.lastUpdateTime,
                    sourceDir = appInfo.sourceDir,
                    dataDir = appInfo.dataDir,
                    targetSdkVersion = appInfo.targetSdkVersion,
                    minSdkVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        appInfo.minSdkVersion
                    } else { 0 },
                    enabled = appInfo.enabled
                )
            }
            .sortedBy { it.name.lowercase() }
    }
    
    data class AppInfo(
        val name: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val isSystemApp: Boolean,
        val installTime: Long,
        val updateTime: Long,
        val sourceDir: String,
        val dataDir: String,
        val targetSdkVersion: Int,
        val minSdkVersion: Int,
        val enabled: Boolean
    )
    
    /**
     * 强制停止应用（需要 ROOT）
     */
    fun forceStopApp(packageName: String): Boolean {
        return executeRootCommand("am force-stop $packageName").success
    }
    
    /**
     * 清除应用数据（需要 ROOT）
     */
    fun clearAppData(packageName: String): Boolean {
        return executeRootCommand("pm clear $packageName").success
    }
    
    /**
     * 禁用应用（需要 ROOT）
     */
    fun disableApp(packageName: String): Boolean {
        return executeRootCommand("pm disable-user --user 0 $packageName").success
    }
    
    /**
     * 启用应用（需要 ROOT）
     */
    fun enableApp(packageName: String): Boolean {
        return executeRootCommand("pm enable $packageName").success
    }
    
    /**
     * 卸载应用
     */
    fun uninstallApp(packageName: String): Intent {
        return Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
    }
    
    // ===================== 性能模式 =====================
    
    enum class PerformanceMode {
        POWERSAVE,      // 省电模式
        BALANCED,       // 平衡模式
        PERFORMANCE,    // 性能模式
        GAMING          // 游戏模式
    }
    
    /**
     * 设置性能模式（需要 ROOT）
     */
    fun setPerformanceMode(mode: PerformanceMode): Boolean {
        val commands = when (mode) {
            PerformanceMode.POWERSAVE -> """
                echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
                echo 0 > /sys/devices/system/cpu/cpu4/online
                echo 0 > /sys/devices/system/cpu/cpu5/online
                echo 0 > /sys/devices/system/cpu/cpu6/online
                echo 0 > /sys/devices/system/cpu/cpu7/online
            """.trimIndent()
            
            PerformanceMode.BALANCED -> """
                echo schedutil > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
                echo 1 > /sys/devices/system/cpu/cpu4/online
                echo 1 > /sys/devices/system/cpu/cpu5/online
                echo 1 > /sys/devices/system/cpu/cpu6/online
                echo 1 > /sys/devices/system/cpu/cpu7/online
            """.trimIndent()
            
            PerformanceMode.PERFORMANCE, PerformanceMode.GAMING -> """
                echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
                echo 1 > /sys/devices/system/cpu/cpu4/online
                echo 1 > /sys/devices/system/cpu/cpu5/online
                echo 1 > /sys/devices/system/cpu/cpu6/online
                echo 1 > /sys/devices/system/cpu/cpu7/online
            """.trimIndent()
        }
        
        return executeRootCommand(commands).success
    }
    
    // ===================== 电池管理 =====================
    
    /**
     * 获取电池信息
     */
    fun getBatteryStats(): BatteryStats {
        val current = try {
            File("/sys/class/power_supply/battery/current_now").readText().trim().toLong() / 1000 // μA -> mA
        } catch (e: Exception) { 0L }
        
        val capacity = try {
            File("/sys/class/power_supply/battery/capacity").readText().trim().toInt()
        } catch (e: Exception) { -1 }
        
        val temperature = try {
            File("/sys/class/power_supply/battery/temp").readText().trim().toInt() / 10
        } catch (e: Exception) { -1 }
        
        val voltage = try {
            File("/sys/class/power_supply/battery/voltage_now").readText().trim().toLong() / 1000 // μV -> mV
        } catch (e: Exception) { 0L }
        
        val status = try {
            File("/sys/class/power_supply/battery/status").readText().trim()
        } catch (e: Exception) { "Unknown" }
        
        val health = try {
            File("/sys/class/power_supply/battery/health").readText().trim()
        } catch (e: Exception) { "Unknown" }
        
        return BatteryStats(
            current = current,
            capacity = capacity,
            temperature = temperature,
            voltage = voltage,
            status = status,
            health = health
        )
    }
    
    data class BatteryStats(
        val current: Long,          // mA
        val capacity: Int,          // %
        val temperature: Int,       // °C
        val voltage: Long,          // mV
        val status: String,
        val health: String
    )
    
    // ===================== 内存清理 =====================
    
    /**
     * 清理后台应用（需要 ROOT）
     */
    fun killBackgroundApps(): Int {
        val result = executeRootCommand("am kill-all")
        return if (result.success) 1 else 0
    }
    
    /**
     * 释放内存缓存（需要 ROOT）
     */
    fun dropCaches(): Boolean {
        return executeRootCommand("echo 3 > /proc/sys/vm/drop_caches").success
    }
    
    // ===================== 系统设置 =====================
    
    /**
     * 获取开发者选项状态
     */
    fun isDeveloperOptionsEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取 USB 调试状态
     */
    fun isAdbEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, Settings.Global.ADB_ENABLED) == 1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取动画缩放倍率
     */
    fun getAnimationScale(): Triple<Float, Float, Float> {
        val window = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE)
        } catch (e: Exception) { 1f }
        
        val transition = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE)
        } catch (e: Exception) { 1f }
        
        val animator = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        } catch (e: Exception) { 1f }
        
        return Triple(window, transition, animator)
    }
    
    /**
     * 设置动画缩放（需要 ROOT 或 adb 权限）
     */
    fun setAnimationScale(scale: Float): Boolean {
        val commands = """
            settings put global window_animation_scale $scale
            settings put global transition_animation_scale $scale
            settings put global animator_duration_scale $scale
        """.trimIndent()
        
        return executeRootCommand(commands).success
    }
    
    // ===================== 屏幕控制 =====================
    
    /**
     * 获取屏幕分辨率
     */
    fun getScreenResolution(): Pair<Int, Int> {
        val displayMetrics = context.resources.displayMetrics
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
    
    /**
     * 获取屏幕密度
     */
    fun getScreenDensity(): Int {
        return context.resources.displayMetrics.densityDpi
    }
    
    /**
     * 修改分辨率（需要 ROOT）
     */
    fun setScreenResolution(width: Int, height: Int): Boolean {
        return executeRootCommand("wm size ${width}x${height}").success
    }
    
    /**
     * 修改 DPI（需要 ROOT）
     */
    fun setScreenDensity(dpi: Int): Boolean {
        return executeRootCommand("wm density $dpi").success
    }
    
    /**
     * 重置分辨率和密度
     */
    fun resetScreen(): Boolean {
        val result1 = executeRootCommand("wm size reset")
        val result2 = executeRootCommand("wm density reset")
        return result1.success && result2.success
    }
    
    // ===================== 模块管理 =====================
    
    /**
     * 模块信息
     */
    data class ModuleInfo(
        val id: String,
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val path: String,
        val enabled: Boolean,
        val isMagisk: Boolean  // true=Magisk模块, false=内核模块
    )
    
    /**
     * 获取 Magisk 模块列表
     */
    fun getMagiskModules(): List<ModuleInfo> {
        val modules = mutableListOf<ModuleInfo>()
        val modulesDir = File("/data/adb/modules")
        
        if (!modulesDir.exists() || !checkRootAccess()) {
            return emptyList()
        }
        
        try {
            val result = executeRootCommand("ls /data/adb/modules")
            if (!result.success) return emptyList()
            
            val moduleNames = result.output.lines().filter { it.isNotBlank() }
            
            for (moduleName in moduleNames) {
                val modulePath = "/data/adb/modules/$moduleName"
                val propResult = executeRootCommand("cat $modulePath/module.prop 2>/dev/null")
                
                if (propResult.success) {
                    val props = parseModuleProps(propResult.output)
                    val disableFile = executeRootCommand("test -f $modulePath/disable && echo yes || echo no")
                    val isEnabled = disableFile.output.trim() != "yes"
                    
                    modules.add(ModuleInfo(
                        id = props["id"] ?: moduleName,
                        name = props["name"] ?: moduleName,
                        version = props["version"] ?: "unknown",
                        author = props["author"] ?: "unknown",
                        description = props["description"] ?: "",
                        path = modulePath,
                        enabled = isEnabled,
                        isMagisk = true
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return modules
    }
    
    /**
     * 获取内核模块列表
     */
    fun getKernelModules(): List<ModuleInfo> {
        val modules = mutableListOf<ModuleInfo>()
        
        try {
            val result = executeCommand("cat /proc/modules")
            if (!result.success) return emptyList()
            
            for (line in result.output.lines()) {
                if (line.isBlank()) continue
                val parts = line.split(" ")
                if (parts.isNotEmpty()) {
                    val name = parts[0]
                    val size = parts.getOrNull(1) ?: "0"
                    modules.add(ModuleInfo(
                        id = name,
                        name = name,
                        version = "",
                        author = "",
                        description = "Size: ${size} bytes",
                        path = "/proc/modules",
                        enabled = true,
                        isMagisk = false
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return modules
    }
    
    /**
     * 解析模块属性
     */
    private fun parseModuleProps(content: String): Map<String, String> {
        val props = mutableMapOf<String, String>()
        for (line in content.lines()) {
            if (line.contains("=") && !line.startsWith("#")) {
                val idx = line.indexOf("=")
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                props[key] = value
            }
        }
        return props
    }
    
    /**
     * 启用 Magisk 模块
     */
    fun enableMagiskModule(modulePath: String): Boolean {
        return executeRootCommand("rm -f $modulePath/disable").success
    }
    
    /**
     * 禁用 Magisk 模块
     */
    fun disableMagiskModule(modulePath: String): Boolean {
        return executeRootCommand("touch $modulePath/disable").success
    }
    
    /**
     * 删除 Magisk 模块
     */
    fun removeMagiskModule(modulePath: String): Boolean {
        // 创建 remove 标记文件，重启后 Magisk 会删除模块
        return executeRootCommand("touch $modulePath/remove").success
    }
    
    /**
     * 挂载内核模块（.ko 文件）
     */
    fun loadKernelModule(modulePath: String): Boolean {
        return executeRootCommand("insmod $modulePath").success
    }
    
    /**
     * 卸载内核模块
     */
    fun unloadKernelModule(moduleName: String): Boolean {
        return executeRootCommand("rmmod $moduleName").success
    }
    
    /**
     * 安装 Magisk 模块（从 zip 文件）
     */
    fun installMagiskModule(zipPath: String): CommandResult {
        // 使用 Magisk 的 module_installer.sh 安装
        val commands = """
            export BOOTMODE=true
            export MODPATH=/data/adb/modules_update
            unzip -o "$zipPath" -d /data/local/tmp/module_install
            sh /data/local/tmp/module_install/customize.sh 2>&1 || true
            mv /data/local/tmp/module_install /data/adb/modules/
        """.trimIndent()
        
        return executeRootCommand(commands)
    }
    
    /**
     * 检查是否安装了 Magisk
     */
    fun isMagiskInstalled(): Boolean {
        val result = executeRootCommand("test -d /data/adb/magisk && echo yes || echo no")
        return result.output.trim() == "yes"
    }
    
    /**
     * 获取 Magisk 版本
     */
    fun getMagiskVersion(): String {
        val result = executeRootCommand("cat /data/adb/magisk/util_functions.sh 2>/dev/null | grep MAGISK_VER= | cut -d= -f2")
        return if (result.success && result.output.isNotBlank()) {
            result.output.trim().replace("\"", "")
        } else {
            "未安装"
        }
    }
}
