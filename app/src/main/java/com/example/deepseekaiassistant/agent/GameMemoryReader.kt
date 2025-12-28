package com.example.deepseekaiassistant.agent

import android.content.Context
import android.util.Log
import com.example.deepseekaiassistant.root.RootManager
import java.io.File
import java.util.regex.Pattern

/**
 * 游戏内存读取器（Root 专用）
 * 通过 Root 权限读取游戏进程内存，获取精准游戏数据
 * 
 * ⚠️ 警告：此功能仅用于单机游戏辅助，请勿用于网络游戏作弊
 */
class GameMemoryReader(private val context: Context) {
    
    companion object {
        private const val TAG = "GameMemoryReader"
        
        // 常见游戏数据结构
        private val MEMORY_REGIONS = listOf("heap", "stack", "anon")
    }
    
    private val rootManager = RootManager.getInstance(context)
    
    /**
     * 游戏数据
     */
    data class GameData(
        val heroHp: Int = 0,           // 英雄血量
        val heroMaxHp: Int = 0,        // 最大血量
        val heroMp: Int = 0,           // 蓝量
        val heroMaxMp: Int = 0,        // 最大蓝量
        val heroLevel: Int = 0,        // 等级
        val heroExp: Int = 0,          // 经验值
        val gold: Int = 0,             // 金币
        val skillCooldowns: IntArray = intArrayOf(),  // 技能 CD
        val positionX: Float = 0f,     // X 坐标
        val positionY: Float = 0f,     // Y 坐标
        val enemyCount: Int = 0,       // 附近敌人数量
        val additionalData: Map<String, Any> = emptyMap()
    )
    
    /**
     * 内存搜索结果
     */
    data class MemorySearchResult(
        val address: String,
        val value: String,
        val region: String
    )
    
    // ==================== 进程操作 ====================
    
    /**
     * 获取游戏进程 ID
     */
    fun getGamePid(packageName: String): Int? {
        if (!rootManager.isAppRootAuthorized()) {
            Log.e(TAG, "需要 Root 权限")
            return null
        }
        
        val result = rootManager.executeRootCommand("pidof $packageName")
        if (result.success && result.output.isNotBlank()) {
            return try {
                result.output.trim().split(" ").first().toInt()
            } catch (e: Exception) {
                Log.e(TAG, "解析 PID 失败: ${e.message}")
                null
            }
        }
        return null
    }
    
    /**
     * 检查游戏是否在运行
     */
    fun isGameRunning(packageName: String): Boolean {
        return getGamePid(packageName) != null
    }
    
    /**
     * 获取进程内存映射
     */
    fun getMemoryMaps(pid: Int): List<String> {
        val result = rootManager.executeRootCommand("cat /proc/$pid/maps")
        if (result.success) {
            return result.output.lines().filter { it.isNotBlank() }
        }
        return emptyList()
    }
    
    // ==================== 内存搜索 ====================
    
    /**
     * 在游戏内存中搜索整数值
     */
    fun searchInt(pid: Int, value: Int): List<MemorySearchResult> {
        val results = mutableListOf<MemorySearchResult>()
        
        if (!rootManager.isAppRootAuthorized()) {
            Log.e(TAG, "需要 Root 权限")
            return results
        }
        
        // 使用 scanmem 或手动搜索
        // 这里使用简化版本：搜索 /proc/pid/mem
        try {
            val mapsResult = rootManager.executeRootCommand("cat /proc/$pid/maps | grep -E 'heap|stack|anon'")
            if (!mapsResult.success) return results
            
            val regions = mapsResult.output.lines().filter { it.isNotBlank() }
            
            for (region in regions.take(10)) {  // 限制搜索区域
                val match = Pattern.compile("([0-9a-f]+)-([0-9a-f]+)").matcher(region)
                if (match.find()) {
                    val startAddr = match.group(1) ?: continue
                    val endAddr = match.group(2) ?: continue
                    
                    // 使用 xxd 搜索
                    val hexValue = String.format("%08x", value).chunked(2).reversed().joinToString("")
                    val searchCmd = "dd if=/proc/$pid/mem bs=1 skip=$((0x$startAddr)) count=$((0x$endAddr - 0x$startAddr)) 2>/dev/null | xxd -p | grep -b '$hexValue'"
                    
                    val searchResult = rootManager.executeRootCommand(searchCmd)
                    if (searchResult.success && searchResult.output.isNotBlank()) {
                        results.add(
                            MemorySearchResult(
                                address = startAddr,
                                value = value.toString(),
                                region = region.substringAfterLast(" ")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "内存搜索异常: ${e.message}")
        }
        
        return results
    }
    
    /**
     * 读取指定地址的整数值
     */
    fun readInt(pid: Int, address: Long): Int? {
        if (!rootManager.isAppRootAuthorized()) return null
        
        try {
            val hexAddr = String.format("%x", address)
            val cmd = "dd if=/proc/$pid/mem bs=1 skip=$((0x$hexAddr)) count=4 2>/dev/null | xxd -p"
            val result = rootManager.executeRootCommand(cmd)
            
            if (result.success && result.output.isNotBlank()) {
                // 小端序转换
                val hex = result.output.trim()
                if (hex.length >= 8) {
                    val bytes = hex.chunked(2).reversed().joinToString("")
                    return bytes.toLong(16).toInt()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取内存失败: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 写入指定地址的整数值
     */
    fun writeInt(pid: Int, address: Long, value: Int): Boolean {
        if (!rootManager.isAppRootAuthorized()) return false
        
        try {
            val hexAddr = String.format("%x", address)
            // 转换为小端序
            val hexValue = String.format("%08x", value).chunked(2).reversed().joinToString("")
            
            val cmd = "echo -n '$hexValue' | xxd -r -p | dd of=/proc/$pid/mem bs=1 seek=$((0x$hexAddr)) conv=notrunc 2>/dev/null"
            val result = rootManager.executeRootCommand(cmd)
            
            return result.success
        } catch (e: Exception) {
            Log.e(TAG, "写入内存失败: ${e.message}")
        }
        
        return false
    }
    
    // ==================== 游戏数据读取 ====================
    
    /**
     * 读取王者荣耀游戏数据
     * 注意：内存地址需要根据游戏版本调整
     */
    fun readMobaGameData(packageName: String): GameData? {
        val pid = getGamePid(packageName) ?: return null
        
        // 这里使用示例地址，实际需要通过内存分析获取
        // 不同版本游戏地址可能不同
        
        return GameData(
            heroHp = 0,  // 需要实际的内存地址
            heroMaxHp = 0,
            heroMp = 0,
            heroMaxMp = 0,
            heroLevel = 0
        )
    }
    
    /**
     * 通用游戏数据扫描
     * 尝试通过特征识别常见游戏数据结构
     */
    fun scanGameData(packageName: String): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        val pid = getGamePid(packageName) ?: return data
        
        try {
            // 获取游戏内存布局
            val maps = getMemoryMaps(pid)
            data["memoryRegions"] = maps.size
            
            // 查找主要内存区域大小
            var totalHeapSize = 0L
            for (map in maps) {
                if (map.contains("heap")) {
                    val match = Pattern.compile("([0-9a-f]+)-([0-9a-f]+)").matcher(map)
                    if (match.find()) {
                        val start = match.group(1)?.toLong(16) ?: 0
                        val end = match.group(2)?.toLong(16) ?: 0
                        totalHeapSize += (end - start)
                    }
                }
            }
            data["heapSize"] = totalHeapSize
            
            // 获取进程状态
            val statusResult = rootManager.executeRootCommand("cat /proc/$pid/status")
            if (statusResult.success) {
                val lines = statusResult.output.lines()
                for (line in lines) {
                    when {
                        line.startsWith("VmRSS:") -> {
                            data["memoryUsage"] = line.substringAfter(":").trim()
                        }
                        line.startsWith("Threads:") -> {
                            data["threadCount"] = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "扫描游戏数据异常: ${e.message}")
        }
        
        return data
    }
    
    // ==================== 安全检测 ====================
    
    /**
     * 检测游戏是否有反作弊保护
     */
    fun detectAntiCheat(packageName: String): List<String> {
        val antiCheatList = mutableListOf<String>()
        val pid = getGamePid(packageName) ?: return antiCheatList
        
        // 检查常见反作弊模块
        val maps = getMemoryMaps(pid)
        
        val antiCheatPatterns = listOf(
            "libtersafe" to "腾讯 TerSafe",
            "libNetHTProtect" to "网易 NetHTProtect",
            "libsecuritysdk" to "Security SDK",
            "libanogs" to "AntiCheat",
            "libGameGuard" to "GameGuard"
        )
        
        for ((pattern, name) in antiCheatPatterns) {
            if (maps.any { it.contains(pattern) }) {
                antiCheatList.add(name)
            }
        }
        
        return antiCheatList
    }
    
    /**
     * 检查是否可以安全操作
     * 存在反作弊时返回 false
     */
    fun isSafeToOperate(packageName: String): Boolean {
        val antiCheats = detectAntiCheat(packageName)
        if (antiCheats.isNotEmpty()) {
            Log.w(TAG, "检测到反作弊: $antiCheats")
            return false
        }
        return true
    }
    
    // ==================== 辅助功能 ====================
    
    /**
     * 冻结游戏数值（使数值不变）
     */
    fun freezeValue(pid: Int, address: Long, value: Int, intervalMs: Long = 100): Thread {
        return Thread {
            while (!Thread.currentThread().isInterrupted) {
                writeInt(pid, address, value)
                try {
                    Thread.sleep(intervalMs)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply { start() }
    }
    
    /**
     * 搜索字符串
     */
    fun searchString(pid: Int, text: String): List<MemorySearchResult> {
        val results = mutableListOf<MemorySearchResult>()
        
        if (!rootManager.isAppRootAuthorized()) return results
        
        try {
            // 转换为 hex
            val hexText = text.toByteArray().joinToString("") { String.format("%02x", it) }
            
            val mapsResult = rootManager.executeRootCommand("cat /proc/$pid/maps | grep -E 'heap'")
            if (!mapsResult.success) return results
            
            val regions = mapsResult.output.lines().filter { it.isNotBlank() }
            
            for (region in regions.take(5)) {
                val match = Pattern.compile("([0-9a-f]+)-([0-9a-f]+)").matcher(region)
                if (match.find()) {
                    val startAddr = match.group(1) ?: continue
                    
                    val searchCmd = "grep -ab '$text' /proc/$pid/maps 2>/dev/null"
                    val searchResult = rootManager.executeRootCommand(searchCmd)
                    
                    if (searchResult.success && searchResult.output.isNotBlank()) {
                        results.add(
                            MemorySearchResult(
                                address = startAddr,
                                value = text,
                                region = "heap"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "字符串搜索异常: ${e.message}")
        }
        
        return results
    }
    
    /**
     * 获取游戏配置文件路径
     */
    fun getGameConfigPath(packageName: String): List<String> {
        val paths = mutableListOf<String>()
        
        // 常见游戏配置路径
        val basePaths = listOf(
            "/data/data/$packageName/shared_prefs",
            "/data/data/$packageName/files",
            "/data/data/$packageName/databases",
            "/sdcard/Android/data/$packageName/files"
        )
        
        for (path in basePaths) {
            val result = rootManager.executeRootCommand("ls $path 2>/dev/null")
            if (result.success && result.output.isNotBlank()) {
                paths.add(path)
            }
        }
        
        return paths
    }
    
    /**
     * 备份游戏数据
     */
    fun backupGameData(packageName: String, backupDir: String): Boolean {
        if (!rootManager.isAppRootAuthorized()) return false
        
        val backupPath = "$backupDir/${packageName}_backup_${System.currentTimeMillis()}"
        val result = rootManager.executeRootCommand(
            "mkdir -p $backupPath && cp -r /data/data/$packageName $backupPath/"
        )
        
        return result.success
    }
    
    /**
     * 恢复游戏数据
     */
    fun restoreGameData(packageName: String, backupPath: String): Boolean {
        if (!rootManager.isAppRootAuthorized()) return false
        
        // 先停止游戏
        rootManager.executeRootCommand("am force-stop $packageName")
        
        // 恢复数据
        val result = rootManager.executeRootCommand(
            "rm -rf /data/data/$packageName && cp -r $backupPath/$packageName /data/data/"
        )
        
        // 修复权限
        if (result.success) {
            val uidResult = rootManager.executeRootCommand(
                "stat -c '%u' /data/data/$packageName"
            )
            if (uidResult.success) {
                val uid = uidResult.output.trim()
                rootManager.executeRootCommand(
                    "chown -R $uid:$uid /data/data/$packageName"
                )
            }
        }
        
        return result.success
    }
}
