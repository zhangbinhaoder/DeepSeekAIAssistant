package com.example.deepseekaiassistant.agent

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native Agent Core - C/C++ 高性能核心模块 JNI 绑定
 * 
 * 提供极致性能的底层操作:
 * - NEON SIMD 加速图像处理 (20-50x 性能提升)
 * - 直接触控事件注入 (<1ms 延迟)
 * - 微秒级高精度定时器
 * - 快速截图 (framebuffer 直接访问)
 * - SIMD 加速内存搜索
 * 
 * @author DeepSeek AI Assistant
 */
object NativeAgentCore {
    private const val TAG = "NativeAgentCore"
    private var isLoaded = false
    private var hasSimd = false
    
    /**
     * 加载 native 库
     */
    fun load(): Boolean {
        if (isLoaded) return true
        
        return try {
            System.loadLibrary("agent_native")
            val result = nativeInit()
            if (result == 0) {
                hasSimd = nativeHasSimd()
                isLoaded = true
                Log.i(TAG, "Agent Native library loaded successfully")
                Log.i(TAG, "  Version: ${getVersion()}")
                Log.i(TAG, "  SIMD support: $hasSimd")
                true
            } else {
                Log.e(TAG, "Failed to initialize agent_native: $result")
                false
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Failed to load agent_native library: ${e.message}")
            false
        }
    }
    
    /**
     * 检查库是否已加载
     */
    fun isAvailable(): Boolean = isLoaded
    
    /**
     * 检查 SIMD 支持
     */
    fun hasSimdSupport(): Boolean = hasSimd
    
    /**
     * 获取版本
     */
    fun getVersion(): String = if (isLoaded) nativeGetVersion() else "N/A"
    
    /**
     * 清理资源
     */
    fun cleanup() {
        if (isLoaded) {
            nativeCleanup()
            isLoaded = false
        }
    }
    
    // Native 方法
    private external fun nativeInit(): Int
    private external fun nativeCleanup()
    private external fun nativeHasSimd(): Boolean
    private external fun nativeGetVersion(): String
    
    // ============================================================================
    // 极致优化函数 - 汇编级别 (ARM64 NEON + CRC32)
    // ============================================================================
    
    /**
     * 快速内存清零 (NEON 加速)
     */
    fun fastZero(buffer: ByteArray) {
        if (isLoaded) nativeFastZero(buffer)
        else buffer.fill(0)
    }
    
    /**
     * 快速内存复制 (NEON 加速)
     */
    fun fastCopy(dst: ByteArray, src: ByteArray) {
        if (isLoaded) nativeFastCopy(dst, src)
        else System.arraycopy(src, 0, dst, 0, minOf(dst.size, src.size))
    }
    
    /**
     * CRC32 校验 (ARM 硬件加速)
     */
    fun crc32(data: ByteArray): Int {
        return if (isLoaded) nativeCrc32(data) else 0
    }
    
    /**
     * 快速哈希 (xxHash32 汇编实现)
     */
    fun xxHash32(data: ByteArray, seed: Int = 0): Int {
        return if (isLoaded) nativeXxHash32(data, seed) else data.hashCode()
    }
    
    /**
     * 极速字符串长度 (NEON 加速)
     */
    fun fastStrlen(str: String): Int {
        return if (isLoaded) nativeFastStrlen(str) else str.length
    }
    
    /**
     * 极速整数解析
     */
    fun fastAtoi(str: String): Long {
        return if (isLoaded) nativeFastAtoi(str) else str.toLongOrNull() ?: 0
    }
    
    /**
     * 极速十六进制解析
     */
    fun fastHexToLong(str: String): Long {
        return if (isLoaded) nativeFastHexToLong(str) else str.removePrefix("0x").toLongOrNull(16) ?: 0
    }
    
    /**
     * NEON 向量点积
     */
    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        return if (isLoaded) nativeDotProduct(a, b)
        else a.zip(b).sumOf { (it.first * it.second).toDouble() }.toFloat()
    }
    
    /**
     * NEON 向量求和
     */
    fun vectorSum(a: FloatArray): Float {
        return if (isLoaded) nativeVectorSum(a) else a.sum()
    }
    
    /**
     * 读取 CPU 周期计数器 (纳秒级精度)
     */
    fun readCycles(): Long {
        return if (isLoaded) nativeReadCycles() else System.nanoTime()
    }
    
    /**
     * 获取 CPU 周期频率
     */
    fun getCycleFreq(): Long {
        return if (isLoaded) nativeGetCycleFreq() else 1_000_000_000L
    }
    
    /**
     * 前导零计数 (Count Leading Zeros)
     */
    fun clz32(x: Int): Int {
        return if (isLoaded) nativeClz32(x) else Integer.numberOfLeadingZeros(x)
    }
    
    /**
     * 人口计数 (Population Count - 1的个数)
     */
    fun popcount32(x: Int): Int {
        return if (isLoaded) nativePopcount32(x) else Integer.bitCount(x)
    }
    
    /**
     * 字节序反转
     */
    fun byteSwap32(x: Int): Int {
        return if (isLoaded) nativeByteSwap32(x) else Integer.reverseBytes(x)
    }
    
    // Native 极致优化方法
    private external fun nativeFastZero(buffer: ByteArray)
    private external fun nativeFastCopy(dst: ByteArray, src: ByteArray)
    private external fun nativeCrc32(data: ByteArray): Int
    private external fun nativeXxHash32(data: ByteArray, seed: Int): Int
    private external fun nativeFastStrlen(str: String): Int
    private external fun nativeFastAtoi(str: String): Long
    private external fun nativeFastHexToLong(str: String): Long
    private external fun nativeDotProduct(a: FloatArray, b: FloatArray): Float
    private external fun nativeVectorSum(a: FloatArray): Float
    private external fun nativeReadCycles(): Long
    private external fun nativeGetCycleFreq(): Long
    private external fun nativeClz32(x: Int): Int
    private external fun nativePopcount32(x: Int): Int
    private external fun nativeByteSwap32(x: Int): Int
}

/**
 * SIMD 加速图像处理引擎
 * 
 * 使用 ARM NEON / x86 SSE 进行并行像素处理
 * 性能提升: 20-50x (相比纯 Java/Kotlin)
 */
object SimdImageEngine {
    private const val TAG = "SimdImageEngine"
    
    /**
     * 检测到的元素
     */
    data class DetectedElement(
        val type: Int,              // 0=敌人血条, 1=友方血条, 2=自己血条
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val confidence: Float
    ) {
        val centerX: Int get() = x + width / 2
        val centerY: Int get() = y + height / 2
    }
    
    /**
     * ARGB 转灰度 (SIMD 加速)
     * @param argbPixels ARGB 像素数据
     * @param width 图像宽度
     * @param height 图像高度
     * @return 灰度图像数据
     */
    fun argbToGrayscale(argbPixels: ByteArray, width: Int, height: Int): ByteArray? {
        if (!NativeAgentCore.isAvailable()) return null
        
        val output = ByteArray(width * height)
        val result = nativeArgbToGrayscale(argbPixels, output, width, height)
        return if (result == 0) output else null
    }
    
    /**
     * 检测敌人血条 (红色区域)
     * @param argbPixels ARGB 像素数据
     * @param width 图像宽度
     * @param height 图像高度
     * @param maxElements 最大检测数量
     * @return 检测到的元素列表
     */
    fun detectEnemyHealthBars(
        argbPixels: ByteArray,
        width: Int,
        height: Int,
        maxElements: Int = 10
    ): List<DetectedElement> {
        if (!NativeAgentCore.isAvailable()) return emptyList()
        
        // 输出缓冲区: 每个元素 24 字节 (6 个 int)
        val outputBuffer = IntArray(maxElements * 6)
        val count = nativeDetectRedRegions(argbPixels, width, height, outputBuffer, maxElements)
        
        return parseDetectedElements(outputBuffer, count, 0)
    }
    
    /**
     * 检测友方血条 (蓝色区域)
     */
    fun detectAllyHealthBars(
        argbPixels: ByteArray,
        width: Int,
        height: Int,
        maxElements: Int = 10
    ): List<DetectedElement> {
        if (!NativeAgentCore.isAvailable()) return emptyList()
        
        val outputBuffer = IntArray(maxElements * 6)
        val count = nativeDetectBlueRegions(argbPixels, width, height, outputBuffer, maxElements)
        
        return parseDetectedElements(outputBuffer, count, 1)
    }
    
    /**
     * 检测自己血条 (绿色区域)
     */
    fun detectSelfHealthBar(
        argbPixels: ByteArray,
        width: Int,
        height: Int
    ): DetectedElement? {
        if (!NativeAgentCore.isAvailable()) return null
        
        val outputBuffer = IntArray(6)
        val count = nativeDetectGreenRegions(argbPixels, width, height, outputBuffer, 1)
        
        return if (count > 0) parseDetectedElements(outputBuffer, 1, 2).firstOrNull() else null
    }
    
    /**
     * 快速图像差异检测 (用于检测画面变化)
     * @return 变化的像素数量
     */
    fun imageDiff(
        img1: ByteArray,
        img2: ByteArray,
        threshold: Int = 30
    ): Int {
        if (!NativeAgentCore.isAvailable()) return -1
        if (img1.size != img2.size) return -1
        
        return nativeImageDiff(img1, img2, img1.size / 4, threshold)
    }
    
    /**
     * 快速方框模糊 (SIMD 加速)
     */
    fun boxBlur(
        argbPixels: ByteArray,
        width: Int,
        height: Int,
        radius: Int
    ): ByteArray? {
        if (!NativeAgentCore.isAvailable()) return null
        
        val output = ByteArray(argbPixels.size)
        val result = nativeBoxBlur(argbPixels, output, width, height, radius)
        return if (result == 0) output else null
    }
    
    private fun parseDetectedElements(buffer: IntArray, count: Int, type: Int): List<DetectedElement> {
        val elements = mutableListOf<DetectedElement>()
        for (i in 0 until count) {
            val offset = i * 6
            elements.add(DetectedElement(
                type = type,
                x = buffer[offset],
                y = buffer[offset + 1],
                width = buffer[offset + 2],
                height = buffer[offset + 3],
                confidence = Float.fromBits(buffer[offset + 4])
            ))
        }
        return elements
    }
    
    // Native 方法
    private external fun nativeArgbToGrayscale(src: ByteArray, dst: ByteArray, width: Int, height: Int): Int
    private external fun nativeDetectRedRegions(src: ByteArray, width: Int, height: Int, output: IntArray, maxElements: Int): Int
    private external fun nativeDetectBlueRegions(src: ByteArray, width: Int, height: Int, output: IntArray, maxElements: Int): Int
    private external fun nativeDetectGreenRegions(src: ByteArray, width: Int, height: Int, output: IntArray, maxElements: Int): Int
    private external fun nativeImageDiff(img1: ByteArray, img2: ByteArray, pixelCount: Int, threshold: Int): Int
    private external fun nativeBoxBlur(src: ByteArray, dst: ByteArray, width: Int, height: Int, radius: Int): Int
}

/**
 * 高性能触控注入引擎
 * 
 * 通过直接写入 /dev/input/eventX 绕过 Android 框架
 * 延迟: <1ms (相比 Accessibility Service 的 10-50ms)
 * 
 * 注意: 需要 Root 权限
 */
object TouchInjector {
    private const val TAG = "TouchInjector"
    
    // 触控事件类型
    const val TOUCH_DOWN = 0
    const val TOUCH_UP = 1
    const val TOUCH_MOVE = 2
    
    private var deviceFd: Int = -1
    private var devicePath: String? = null
    
    /**
     * 初始化触控注入器
     * @param devicePath 触控设备路径，null 表示自动检测
     * @return 是否成功
     */
    fun init(devicePath: String? = null): Boolean {
        if (!NativeAgentCore.isAvailable()) return false
        
        val path = devicePath ?: run {
            val pathBuffer = ByteArray(256)
            val result = nativeFindTouchDevice(pathBuffer, pathBuffer.size)
            if (result == 0) {
                String(pathBuffer).trim('\u0000')
            } else {
                Log.e(TAG, "Failed to find touch device")
                return false
            }
        }
        
        deviceFd = nativeOpenDevice(path)
        if (deviceFd < 0) {
            Log.e(TAG, "Failed to open device: $path (error: $deviceFd)")
            return false
        }
        
        this.devicePath = path
        Log.i(TAG, "Touch injector initialized: $path (fd=$deviceFd)")
        return true
    }
    
    /**
     * 关闭触控注入器
     */
    fun close() {
        if (deviceFd >= 0) {
            nativeCloseDevice(deviceFd)
            deviceFd = -1
            devicePath = null
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = deviceFd >= 0
    
    /**
     * 点击 (点按)
     * @param x X 坐标
     * @param y Y 坐标
     * @param durationUs 按下持续时间 (微秒)
     */
    fun tap(x: Int, y: Int, durationUs: Int = 50000): Boolean {
        if (deviceFd < 0) return false
        return nativeInjectTap(deviceFd, x, y, durationUs) == 0
    }
    
    /**
     * 按下
     */
    fun down(x: Int, y: Int): Boolean {
        if (deviceFd < 0) return false
        return nativeInjectDown(deviceFd, x, y) == 0
    }
    
    /**
     * 抬起
     */
    fun up(x: Int, y: Int): Boolean {
        if (deviceFd < 0) return false
        return nativeInjectUp(deviceFd, x, y) == 0
    }
    
    /**
     * 移动
     */
    fun move(x: Int, y: Int): Boolean {
        if (deviceFd < 0) return false
        return nativeInjectMove(deviceFd, x, y) == 0
    }
    
    /**
     * 滑动
     * @param x1, y1 起始坐标
     * @param x2, y2 结束坐标
     * @param durationUs 滑动持续时间 (微秒)
     * @param steps 中间步骤数
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationUs: Int = 300000, steps: Int = 20): Boolean {
        if (deviceFd < 0) return false
        return nativeInjectSwipe(deviceFd, x1, y1, x2, y2, durationUs, steps) == 0
    }
    
    /**
     * 双指缩放
     * @param centerX, centerY 中心坐标
     * @param startDistance 起始距离
     * @param endDistance 结束距离
     * @param durationUs 持续时间 (微秒)
     */
    fun pinch(
        centerX: Int,
        centerY: Int,
        startDistance: Int,
        endDistance: Int,
        durationUs: Int = 300000,
        steps: Int = 20
    ): Boolean {
        if (deviceFd < 0) return false
        return nativeInjectPinch(deviceFd, centerX, centerY, startDistance, endDistance, durationUs, steps) == 0
    }
    
    /**
     * 多点触控
     * @param points 触控点数组 [id, x, y, pressure] * n
     * @param type 事件类型
     */
    fun multiTouch(points: IntArray, type: Int): Boolean {
        if (deviceFd < 0) return false
        if (points.size % 4 != 0) return false
        
        return nativeInjectMulti(deviceFd, points, points.size / 4, type) == 0
    }
    
    // Native 方法
    private external fun nativeOpenDevice(path: String): Int
    private external fun nativeCloseDevice(fd: Int)
    private external fun nativeFindTouchDevice(pathOut: ByteArray, pathSize: Int): Int
    private external fun nativeInjectDown(fd: Int, x: Int, y: Int): Int
    private external fun nativeInjectUp(fd: Int, x: Int, y: Int): Int
    private external fun nativeInjectMove(fd: Int, x: Int, y: Int): Int
    private external fun nativeInjectTap(fd: Int, x: Int, y: Int, durationUs: Int): Int
    private external fun nativeInjectSwipe(fd: Int, x1: Int, y1: Int, x2: Int, y2: Int, durationUs: Int, steps: Int): Int
    private external fun nativeInjectPinch(fd: Int, centerX: Int, centerY: Int, startDist: Int, endDist: Int, durationUs: Int, steps: Int): Int
    private external fun nativeInjectMulti(fd: Int, points: IntArray, count: Int, type: Int): Int
}

/**
 * 高精度定时器
 * 
 * 使用 nanosleep + busy-wait 混合策略
 * 精度: 微秒级
 * 
 * 适用场景:
 * - 游戏 60fps 帧同步
 * - 精确操作时序
 * - 连招/技能释放
 */
object PrecisionTimer {
    private const val TAG = "PrecisionTimer"
    
    /**
     * 获取当前时间 (纳秒)
     */
    fun nowNs(): Long {
        if (!NativeAgentCore.isAvailable()) return System.nanoTime()
        return nativeNowNs()
    }
    
    /**
     * 获取当前时间 (微秒)
     */
    fun nowUs(): Long {
        if (!NativeAgentCore.isAvailable()) return System.nanoTime() / 1000
        return nativeNowUs()
    }
    
    /**
     * 获取当前时间 (毫秒)
     */
    fun nowMs(): Long {
        if (!NativeAgentCore.isAvailable()) return System.currentTimeMillis()
        return nativeNowMs()
    }
    
    /**
     * 高精度睡眠 (纳秒)
     * 使用混合策略: nanosleep + busy-wait
     */
    fun sleepNs(ns: Long) {
        if (!NativeAgentCore.isAvailable()) {
            val ms = ns / 1_000_000
            if (ms > 0) Thread.sleep(ms)
            return
        }
        nativeSleepNs(ns)
    }
    
    /**
     * 高精度睡眠 (微秒)
     */
    fun sleepUs(us: Long) {
        if (!NativeAgentCore.isAvailable()) {
            val ms = us / 1000
            if (ms > 0) Thread.sleep(ms)
            return
        }
        nativeSleepUs(us)
    }
    
    /**
     * 高精度睡眠 (毫秒)
     */
    fun sleepMs(ms: Long) {
        if (!NativeAgentCore.isAvailable()) {
            Thread.sleep(ms)
            return
        }
        nativeSleepMs(ms)
    }
    
    /**
     * 精确等待到指定时间
     * @param targetNs 目标时间 (纳秒)
     */
    fun waitUntil(targetNs: Long) {
        val remaining = targetNs - nowNs()
        if (remaining > 0) sleepNs(remaining)
    }
    
    /**
     * 帧同步睡眠 (用于游戏循环)
     * @param fps 目标帧率
     * @param lastFrameNs 上一帧时间
     * @return 当前帧时间
     */
    fun frameSleep(fps: Int, lastFrameNs: Long): Long {
        val frameInterval = 1_000_000_000L / fps
        val targetNs = lastFrameNs + frameInterval
        waitUntil(targetNs)
        return nowNs()
    }
    
    /**
     * 间隔定时器
     */
    class IntervalTimer(private val intervalUs: Long) {
        private var timerHandle: Int = -1
        
        fun start(): Boolean {
            if (!NativeAgentCore.isAvailable()) return false
            timerHandle = nativeCreateIntervalTimer(intervalUs)
            return timerHandle >= 0
        }
        
        fun waitNext(): Boolean {
            if (timerHandle < 0) return false
            return nativeWaitNextTimer(timerHandle) == 0
        }
        
        fun stop() {
            if (timerHandle >= 0) {
                nativeDestroyTimer(timerHandle)
                timerHandle = -1
            }
        }
    }
    
    // Native 方法
    private external fun nativeNowNs(): Long
    private external fun nativeNowUs(): Long
    private external fun nativeNowMs(): Long
    private external fun nativeSleepNs(ns: Long)
    private external fun nativeSleepUs(us: Long)
    private external fun nativeSleepMs(ms: Long)
    private external fun nativeCreateIntervalTimer(intervalUs: Long): Int
    private external fun nativeWaitNextTimer(handle: Int): Int
    private external fun nativeDestroyTimer(handle: Int)
}

/**
 * 快速截图引擎
 * 
 * 通过 framebuffer 直接读取屏幕 (Root)
 * 性能: 5-10ms/帧 (相比 MediaProjection 的 30-50ms)
 */
object FastScreenCapture {
    private const val TAG = "FastScreenCapture"
    
    data class ScreenInfo(
        val width: Int,
        val height: Int,
        val stride: Int,
        val format: Int,
        val bpp: Int
    )
    
    private var screenInfo: ScreenInfo? = null
    
    /**
     * 获取屏幕信息
     */
    fun getScreenInfo(): ScreenInfo? {
        if (!NativeAgentCore.isAvailable()) return screenInfo
        
        val info = IntArray(5)
        val result = nativeGetScreenInfo(info)
        if (result != 0) return null
        
        screenInfo = ScreenInfo(
            width = info[0],
            height = info[1],
            stride = info[2],
            format = info[3],
            bpp = info[4]
        )
        return screenInfo
    }
    
    /**
     * 截取全屏
     * @return ARGB 像素数据
     */
    fun captureScreen(): ByteArray? {
        if (!NativeAgentCore.isAvailable()) return null
        
        val info = getScreenInfo() ?: return null
        val bufferSize = info.stride * info.height
        val buffer = ByteArray(bufferSize)
        
        val result = nativeCaptureScreen(buffer, bufferSize)
        return if (result > 0) buffer.copyOf(result) else null
    }
    
    /**
     * 截取屏幕区域 (更快)
     */
    fun captureRegion(x: Int, y: Int, width: Int, height: Int): ByteArray? {
        if (!NativeAgentCore.isAvailable()) return null
        
        val bufferSize = width * height * 4 // ARGB
        val buffer = ByteArray(bufferSize)
        
        val result = nativeCaptureRegion(buffer, bufferSize, x, y, width, height)
        return if (result > 0) buffer.copyOf(result) else null
    }
    
    // Native 方法
    private external fun nativeGetScreenInfo(info: IntArray): Int
    private external fun nativeCaptureScreen(buffer: ByteArray, bufferSize: Int): Int
    private external fun nativeCaptureRegion(buffer: ByteArray, bufferSize: Int, x: Int, y: Int, width: Int, height: Int): Int
}

/**
 * 高性能内存搜索引擎
 * 
 * 使用 SIMD 加速内存模式匹配 (Root)
 * 性能: 比纯 Java 快 10-20x
 */
object MemorySearchEngine {
    private const val TAG = "MemorySearchEngine"
    
    /**
     * 在进程内存中搜索模式
     * @param pid 目标进程 ID
     * @param startAddr 起始地址
     * @param endAddr 结束地址
     * @param pattern 搜索模式
     * @param maxResults 最大结果数
     * @return 匹配地址列表
     */
    fun searchPattern(
        pid: Int,
        startAddr: Long,
        endAddr: Long,
        pattern: ByteArray,
        maxResults: Int = 100
    ): LongArray {
        if (!NativeAgentCore.isAvailable()) return LongArray(0)
        
        val results = LongArray(maxResults)
        val count = nativeSearchPattern(pid, startAddr, endAddr, pattern, pattern.size, results, maxResults)
        
        return if (count > 0) results.copyOf(count) else LongArray(0)
    }
    
    /**
     * 读取进程内存
     */
    fun readMemory(pid: Int, address: Long, size: Int): ByteArray? {
        if (!NativeAgentCore.isAvailable()) return null
        
        val buffer = ByteArray(size)
        val result = nativeReadMemory(pid, address, buffer, size)
        return if (result == 0) buffer else null
    }
    
    /**
     * 写入进程内存
     */
    fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean {
        if (!NativeAgentCore.isAvailable()) return false
        return nativeWriteMemory(pid, address, data, data.size) == 0
    }
    
    // Native 方法
    private external fun nativeSearchPattern(
        pid: Int, startAddr: Long, endAddr: Long,
        pattern: ByteArray, patternLen: Int,
        results: LongArray, maxResults: Int
    ): Int
    private external fun nativeReadMemory(pid: Int, address: Long, buffer: ByteArray, size: Int): Int
    private external fun nativeWriteMemory(pid: Int, address: Long, data: ByteArray, size: Int): Int
}

/**
 * 性能优化工具
 */
object PerformanceUtils {
    
    /**
     * 设置当前线程为实时优先级
     * @return 是否成功
     */
    fun setRealtimePriority(): Boolean {
        if (!NativeAgentCore.isAvailable()) return false
        return nativeSetRealtimePriority() == 0
    }
    
    /**
     * 将当前线程绑定到指定 CPU 核心
     */
    fun setCpuAffinity(cpuId: Int): Boolean {
        if (!NativeAgentCore.isAvailable()) return false
        return nativeSetCpuAffinity(cpuId) == 0
    }
    
    /**
     * 锁定 CPU 频率 (防止降频)
     * 需要 Root
     */
    fun lockCpuFrequency(): Boolean {
        if (!NativeAgentCore.isAvailable()) return false
        return nativeLockCpuFrequency() == 0
    }
    
    // Native 方法
    private external fun nativeSetRealtimePriority(): Int
    private external fun nativeSetCpuAffinity(cpuId: Int): Int
    private external fun nativeLockCpuFrequency(): Int
}
