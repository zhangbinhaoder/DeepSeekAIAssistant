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
import kotlinx.coroutines.*

/**
 * 启动画面 - Linux 终端风格启动日志
 * 
 * 模拟 Linux 系统启动时的终端日志显示效果
 * 显示各模块的加载状态
 * 
 * 安全设计：所有操作都有异常处理，确保不会闪退
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SplashActivity"
        private const val MIN_SPLASH_TIME = 1500L  // 最短显示时间
        private const val LOG_DELAY = 30L          // 日志行间隔
    }
    
    private var tvBootLog: TextView? = null
    private var scrollView: ScrollView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val logBuilder = StringBuilder()
    private var startTime = 0L
    
    private var scope: CoroutineScope? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 最外层 try-catch 确保不会闪退
        try {
            super.onCreate(savedInstanceState)
            
            // 隐藏状态栏和导航栏，全屏显示
            safeHideSystemUI()
            
            setContentView(R.layout.activity_splash)
            
            // 安全获取视图引用
            tvBootLog = findViewById(R.id.tvBootLog)
            scrollView = findViewById(R.id.scrollView)
            
            startTime = System.currentTimeMillis()
            
            // 创建协程作用域并开始启动序列
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            scope?.launch {
                safeRunBootSequence()
            }
        } catch (e: Exception) {
            // 如果 onCreate 失败，直接跳转到主界面
            Log.e(TAG, "onCreate failed, jumping to MainActivity", e)
            safeStartMainActivity()
        }
    }
    
    /**
     * 安全隐藏系统 UI
     */
    private fun safeHideSystemUI() {
        try {
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
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to hide system UI", e)
        }
    }
    
    /**
     * 安全的启动序列 - 简化版
     * 所有操作都有异常处理，确保不会闪退
     */
    private suspend fun safeRunBootSequence() {
        try {
            // 启动 Banner
            appendLogSafe("")
            appendLogSafe("╔════════════════════════════════════════════════════════════╗")
            appendLogSafe("║      DeepSeek AI Assistant - Boot Sequence v2.0            ║")
            appendLogSafe("║      ARM64 Optimized Edition                               ║")
            appendLogSafe("╚════════════════════════════════════════════════════════════╝")
            appendLogSafe("")
            
            // 系统信息
            appendLogSafe("[    0.000000] Linux version 6.1.0-deepseek (android@build)")
            appendLogSafe("[    0.000001] CPU: ARMv8 Processor [${Build.HARDWARE}]")
            appendLogSafe("[    0.000002] Machine model: ${Build.MODEL}")
            appendLogSafe("[    0.000003] Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLogSafe("")
            
            // 内存信息
            val runtime = Runtime.getRuntime()
            val maxMem = runtime.maxMemory() / 1024 / 1024
            appendLogSafe("[    0.010000] Memory: ${maxMem}MB available to VM")
            appendLogSafe("[    0.010001] Memory management initialized [  OK  ]")
            appendLogSafe("")
            
            // 加载 Native 库 - 完全安全
            appendLogSafe("[    0.100000] Loading native libraries...")
            val nativeLoaded = safeLoadNativeLibraries()
            if (nativeLoaded) {
                appendLogSafe("[    0.150000] Native subsystem initialized [  OK  ]")
            } else {
                appendLogSafe("[    0.150000] Native libraries not available [WARN]")
                appendLogSafe("[    0.150001] Using pure Kotlin fallback")
            }
            appendLogSafe("")
            
            // AI 模块
            appendLogSafe("[    0.200000] Initializing AI subsystem...")
            appendLogSafe("[    0.250000] DeepSeek API service configured")
            appendLogSafe("[    0.250001] AI subsystem initialized [  OK  ]")
            appendLogSafe("")
            
            // 组件模块
            appendLogSafe("[    0.300000] Loading application components...")
            appendLogSafe("[    0.350000] Chat module ready")
            appendLogSafe("[    0.350001] Math module ready")
            appendLogSafe("[    0.350002] Browser module ready")
            appendLogSafe("[    0.350003] System explorer ready")
            appendLogSafe("[    0.350004] All modules loaded [  OK  ]")
            appendLogSafe("")
            
            // 启动完成
            val elapsed = System.currentTimeMillis() - startTime
            val elapsedSec = elapsed / 1000.0
            appendLogSafe("[    ${String.format("%.6f", elapsedSec)}] ══════════════════════════════════════")
            appendLogSafe("[    ${String.format("%.6f", elapsedSec)}] Boot sequence completed successfully")
            appendLogSafe("[    ${String.format("%.6f", elapsedSec)}] Starting main application...")
            appendLogSafe("")
            
            // 确保最短显示时间
            val remainingTime = MIN_SPLASH_TIME - elapsed
            if (remainingTime > 0) {
                delay(remainingTime)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Boot sequence error", e)
            try {
                appendLogSafe("")
                appendLogSafe("[ERROR] Boot sequence encountered an error")
                appendLogSafe("[ERROR] Proceeding to main activity...")
            } catch (ignored: Exception) {}
            delay(500)
        }
        
        // 无论如何都跳转到主界面
        safeStartMainActivity()
    }
    
    /**
     * 安全加载 Native 库
     */
    private suspend fun safeLoadNativeLibraries(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                System.loadLibrary("agent_native")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "agent_native not available: ${e.message}")
                false
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load native libs: ${e.message}")
                false
            }
        }
    }
    
    /**
     * 安全添加日志
     */
    private suspend fun appendLogSafe(text: String) {
        try {
            withContext(Dispatchers.Main) {
                logBuilder.append(text).append("\n")
                tvBootLog?.text = logBuilder.toString()
                scrollView?.post {
                    scrollView?.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
            if (LOG_DELAY > 0) {
                delay(LOG_DELAY)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to append log: ${e.message}")
        }
    }
    
    /**
     * 安全启动主界面
     */
    private fun safeStartMainActivity() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity", e)
        } finally {
            try {
                finish()
            } catch (ignored: Exception) {}
        }
    }
    
    override fun onDestroy() {
        try {
            super.onDestroy()
            scope?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "onDestroy error", e)
        }
    }
}
