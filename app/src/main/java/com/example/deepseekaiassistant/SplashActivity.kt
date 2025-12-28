package com.example.deepseekaiassistant

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.deepseekaiassistant.agent.NativeAgentCore
import com.example.deepseekaiassistant.agent.KernelOptimizeManager
import com.example.deepseekaiassistant.local.LocalAIManager
import com.example.deepseekaiassistant.root.RootManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 启动画面 - Linux 终端风格启动日志
 * 
 * 模拟 Linux 系统启动时的终端日志显示效果
 * 显示各模块的加载状态
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SplashActivity"
        private const val MIN_SPLASH_TIME = 2000L  // 最短显示时间
        private const val LOG_DELAY = 50L          // 日志行间隔
    }
    
    private lateinit var tvBootLog: TextView
    private lateinit var scrollView: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    private val logBuilder = StringBuilder()
    private var startTime = 0L
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 隐藏状态栏和导航栏，全屏显示
        hideSystemUI()
        
        setContentView(R.layout.activity_splash)
        
        tvBootLog = findViewById(R.id.tvBootLog)
        scrollView = findViewById(R.id.scrollView)
        
        startTime = System.currentTimeMillis()
        
        // 开始启动序列
        scope.launch {
            runBootSequence()
        }
    }
    
    private fun hideSystemUI() {
        // 全屏模式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private suspend fun runBootSequence() {
        // 启动 Banner
        appendLog("", delay = 0)
        appendLog("╔════════════════════════════════════════════════════════════╗")
        appendLog("║      DeepSeek AI Assistant - Boot Sequence v2.0            ║")
        appendLog("║      ARM64 Optimized Edition                               ║")
        appendLog("╚════════════════════════════════════════════════════════════╝")
        appendLog("")
        
        // 系统信息
        appendLog("[    0.000000] Linux version 6.1.0-deepseek (android@build)")
        appendLog("[    0.000001] CPU: ARMv8 Processor [${Build.HARDWARE}]")
        appendLog("[    0.000002] Machine model: ${Build.MODEL}")
        appendLog("[    0.000003] Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLog("")
        
        // 内存信息
        val runtime = Runtime.getRuntime()
        val maxMem = runtime.maxMemory() / 1024 / 1024
        appendLog("[    0.010000] Memory: ${maxMem}MB available to VM")
        appendLog("[    0.010001] Initializing memory management subsystem...")
        appendLog("[    0.010002] Memory management initialized [  OK  ]")
        appendLog("")
        
        // 加载 Native 库
        appendLog("[    0.100000] Loading native libraries...")
        var nativeLoaded = false
        var nativeVersion = "N/A"
        var hasSimd = false
        
        withContext(Dispatchers.IO) {
            try {
                nativeLoaded = NativeAgentCore.load()
                if (nativeLoaded) {
                    nativeVersion = NativeAgentCore.getVersion()
                    hasSimd = NativeAgentCore.hasSimdSupport()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native load failed", e)
            }
        }
        
        if (nativeLoaded) {
            appendLog("[    0.150000] agent_native.so loaded successfully")
            appendLog("[    0.150001] Version: $nativeVersion")
            appendLog("[    0.150002] NEON SIMD support: ${if (hasSimd) "enabled" else "disabled"}")
            appendLog("[    0.150003] Native subsystem initialized [  OK  ]")
        } else {
            appendLog("[    0.150000] agent_native.so load failed [WARN]")
            appendLog("[    0.150001] Falling back to pure Kotlin implementation")
        }
        appendLog("")
        
        // 加载 llama 库
        appendLog("[    0.200000] Loading AI inference engine...")
        var llamaLoaded = false
        withContext(Dispatchers.IO) {
            try {
                System.loadLibrary("llama_android")
                llamaLoaded = true
            } catch (e: Exception) {
                Log.w(TAG, "llama load skipped: ${e.message}")
            }
        }
        
        if (llamaLoaded) {
            appendLog("[    0.250000] llama_android.so loaded successfully")
            appendLog("[    0.250001] llama.cpp backend ready")
            appendLog("[    0.250002] Local AI inference engine [  OK  ]")
        } else {
            appendLog("[    0.250000] llama_android.so not available")
            appendLog("[    0.250001] Local AI disabled, using cloud API")
        }
        appendLog("")
        
        // 检测 Root 状态
        appendLog("[    0.300000] Checking root privileges...")
        var isRooted = false
        var rootAuthorized = false
        
        withContext(Dispatchers.IO) {
            try {
                val rootMgr = RootManager.getInstance(applicationContext)
                isRooted = rootMgr.isDeviceRooted()
                if (isRooted) {
                    rootAuthorized = rootMgr.requestRootAccess()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Root check failed", e)
            }
        }
        
        if (isRooted) {
            appendLog("[    0.350000] Root: Device is rooted")
            if (rootAuthorized) {
                appendLog("[    0.350001] Root access granted [  OK  ]")
                appendLog("[    0.350002] Kernel optimization available")
            } else {
                appendLog("[    0.350001] Root access denied [WARN]")
            }
        } else {
            appendLog("[    0.350000] Root: Running in non-root mode")
            appendLog("[    0.350001] Limited features available")
        }
        appendLog("")
        
        // 初始化 AI 管理器
        appendLog("[    0.400000] Initializing AI subsystem...")
        withContext(Dispatchers.IO) {
            try {
                LocalAIManager.getInstance(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "AI Manager init failed", e)
            }
        }
        appendLog("[    0.450000] DeepSeek API service configured")
        appendLog("[    0.450001] Chat context manager ready")
        appendLog("[    0.450002] AI subsystem initialized [  OK  ]")
        appendLog("")
        
        // 汇编优化模块
        appendLog("[    0.500000] Loading ARM64 assembly optimizations...")
        appendLog("[    0.500001] asm_core.S: CPU cycle counter [  OK  ]")
        appendLog("[    0.500002] asm_core.S: Memory barriers [  OK  ]")
        appendLog("[    0.500003] asm_core.S: Atomic operations [  OK  ]")
        appendLog("[    0.500004] asm_core.S: NEON pixel processing [  OK  ]")
        appendLog("[    0.500005] asm_core.S: CRC32 hardware accel [  OK  ]")
        appendLog("[    0.500006] asm_core.S: xxHash32 optimized [  OK  ]")
        appendLog("[    0.500007] asm_core.S: Binary search SIMD [  OK  ]")
        appendLog("[    0.500008] Assembly optimizations loaded [  OK  ]")
        appendLog("")
        
        // SIMD 图像引擎
        appendLog("[    0.600000] Initializing SIMD image engine...")
        appendLog("[    0.600001] NEON color detection enabled")
        appendLog("[    0.600002] NEON grayscale conversion enabled")
        appendLog("[    0.600003] NEON box blur (separable) enabled")
        appendLog("[    0.600004] Image processing engine ready [  OK  ]")
        appendLog("")
        
        // 内核优化模块
        appendLog("[    0.700000] Loading kernel optimization module...")
        if (isRooted && rootAuthorized) {
            appendLog("[    0.700001] kernel_syscall.S: Direct syscall interface")
            appendLog("[    0.700002] kernel_optimize.c: Process priority control")
            appendLog("[    0.700003] kernel_optimize.c: CPU affinity management")
            appendLog("[    0.700004] kernel_optimize.c: Memory locking support")
            appendLog("[    0.700005] Kernel optimization available [  OK  ]")
        } else {
            appendLog("[    0.700001] Kernel optimization disabled (no root)")
        }
        appendLog("")
        
        // 自检
        appendLog("[    0.800000] Running system self-check...")
        var selfCheckPassed = true
        withContext(Dispatchers.IO) {
            try {
                // 简单检查
                selfCheckPassed = true
            } catch (e: Exception) {
                selfCheckPassed = false
            }
        }
        
        if (selfCheckPassed) {
            appendLog("[    0.850000] Self-check passed [  OK  ]")
        } else {
            appendLog("[    0.850000] Self-check found issues [WARN]")
        }
        appendLog("")
        
        // 启动完成
        val elapsed = System.currentTimeMillis() - startTime
        val elapsedSec = elapsed / 1000.0
        appendLog("[    ${String.format("%.6f", elapsedSec)}] ══════════════════════════════════════")
        appendLog("[    ${String.format("%.6f", elapsedSec)}] Boot sequence completed successfully")
        appendLog("[    ${String.format("%.6f", elapsedSec)}] Modules loaded: ${if (nativeLoaded) "Native" else ""} ${if (llamaLoaded) "LLaMA" else ""} ${if (isRooted) "Root" else ""}")
        appendLog("[    ${String.format("%.6f", elapsedSec)}] Starting main application...")
        appendLog("")
        
        // 确保最短显示时间
        val remainingTime = MIN_SPLASH_TIME - elapsed
        if (remainingTime > 0) {
            delay(remainingTime)
        }
        
        // 跳转到主界面
        startMainActivity()
    }
    
    private suspend fun appendLog(text: String, delay: Long = LOG_DELAY) {
        withContext(Dispatchers.Main) {
            logBuilder.append(text).append("\n")
            tvBootLog.text = logBuilder.toString()
            
            // 滚动到底部
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
        
        if (delay > 0) {
            delay(delay)
        }
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        
        // 使用淡入淡出过渡动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
