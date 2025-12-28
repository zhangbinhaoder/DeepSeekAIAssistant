package com.example.deepseekaiassistant.root

import android.content.Context
import android.util.Log
import java.io.*

/**
 * Root 权限管理器
 * 负责检测设备Root状态、获取Root权限、执行Root命令
 */
class RootManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "RootManager"
        
        @Volatile
        private var instance: RootManager? = null
        private var rootAuthorized: Boolean? = null
        
        fun getInstance(context: Context): RootManager {
            return instance ?: synchronized(this) {
                instance ?: RootManager(context.applicationContext).also { instance = it }
            }
        }
        
        // 常见的 su 路径
        private val SU_PATHS = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/magisk/su",
            "/su/bin/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/data/local/su"
        )
    }
    
    /**
     * 检查设备是否已 Root
     */
    fun isDeviceRooted(): Boolean {
        // 方法1: 检查常见的 su 路径
        for (path in SU_PATHS) {
            if (File(path).exists()) {
                Log.d(TAG, "找到 su 文件: $path")
                return true
            }
        }
        
        // 方法2: 尝试执行 which su 命令
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            reader.close()
            process.waitFor()
            
            if (!result.isNullOrBlank()) {
                Log.d(TAG, "which su 返回: $result")
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "which su 执行失败: ${e.message}")
        }
        
        // 方法3: 检查 Magisk 特征
        val magiskPath = "/data/adb/magisk"
        if (File(magiskPath).exists()) {
            Log.d(TAG, "检测到 Magisk: $magiskPath")
            return true
        }
        
        return false
    }
    
    /**
     * 验证 APP 是否已获得 Root 授权
     */
    fun isAppRootAuthorized(): Boolean {
        rootAuthorized?.let { return it }
        
        // 执行简单的 Root 级命令验证权限
        val result = executeRootCommand("id")
        rootAuthorized = result.success && result.output.contains("uid=0")
        Log.d(TAG, "Root 授权状态: $rootAuthorized, id 输出: ${result.output}")
        return rootAuthorized ?: false
    }
    
    /**
     * 请求 Root 权限（触发授权弹窗）
     */
    fun requestRootAccess(): Boolean {
        val result = executeRootCommand("echo 'Root access granted'")
        rootAuthorized = result.success
        return result.success
    }
    
    /**
     * 执行 Root 命令并返回结果
     */
    fun executeRootCommand(command: String): CommandResult {
        var process: Process? = null
        var dos: DataOutputStream? = null
        var brStdout: BufferedReader? = null
        var brStderr: BufferedReader? = null
        
        return try {
            // 启动 su 进程，获取 Root 权限
            process = Runtime.getRuntime().exec("su")
            dos = DataOutputStream(process.outputStream)
            brStdout = BufferedReader(InputStreamReader(process.inputStream))
            brStderr = BufferedReader(InputStreamReader(process.errorStream))
            
            // 执行目标命令
            dos.writeBytes("$command\n")
            dos.writeBytes("exit\n")
            dos.flush()
            
            // 读取命令执行结果
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            
            var line: String?
            while (brStdout.readLine().also { line = it } != null) {
                stdout.append(line).append("\n")
            }
            while (brStderr.readLine().also { line = it } != null) {
                stderr.append(line).append("\n")
            }
            
            // 等待进程执行完成
            val exitCode = process.waitFor()
            
            CommandResult(
                success = exitCode == 0,
                output = stdout.toString().trim(),
                error = stderr.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "执行 Root 命令失败: ${e.message}")
            CommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        } finally {
            try {
                dos?.close()
                brStdout?.close()
                brStderr?.close()
                process?.destroy()
            } catch (e: IOException) {
                Log.e(TAG, "关闭流失败: ${e.message}")
            }
        }
    }
    
    /**
     * 执行普通 Shell 命令（无需 Root）
     */
    fun executeShellCommand(command: String): CommandResult {
        var process: Process? = null
        var brStdout: BufferedReader? = null
        var brStderr: BufferedReader? = null
        
        return try {
            process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            brStdout = BufferedReader(InputStreamReader(process.inputStream))
            brStderr = BufferedReader(InputStreamReader(process.errorStream))
            
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            
            var line: String?
            while (brStdout.readLine().also { line = it } != null) {
                stdout.append(line).append("\n")
            }
            while (brStderr.readLine().also { line = it } != null) {
                stderr.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            
            CommandResult(
                success = exitCode == 0,
                output = stdout.toString().trim(),
                error = stderr.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            CommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        } finally {
            try {
                brStdout?.close()
                brStderr?.close()
                process?.destroy()
            } catch (e: IOException) {
                Log.e(TAG, "关闭流失败: ${e.message}")
            }
        }
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
        val result = executeRootCommand(
            "cat /data/adb/magisk/util_functions.sh 2>/dev/null | grep MAGISK_VER= | cut -d= -f2"
        )
        return if (result.success && result.output.isNotBlank()) {
            result.output.trim().replace("\"", "")
        } else {
            "未安装"
        }
    }
    
    /**
     * 清除 Root 授权缓存（重新验证时使用）
     */
    fun clearAuthCache() {
        rootAuthorized = null
    }
    
    /**
     * 命令执行结果
     */
    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int
    ) {
        override fun toString(): String {
            return if (success) {
                "成功: $output"
            } else {
                "失败(code=$exitCode): $error"
            }
        }
    }
}
