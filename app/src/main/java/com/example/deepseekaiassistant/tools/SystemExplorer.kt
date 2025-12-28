package com.example.deepseekaiassistant.tools

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.RandomAccessFile

/**
 * 系统底层探测器
 * 用于读取系统底层信息
 */
class SystemExplorer(private val context: Context) {
    
    /**
     * 获取完整系统信息
     */
    fun getFullSystemInfo(): SystemInfo {
        return SystemInfo(
            device = getDeviceInfo(),
            cpu = getCpuInfo(),
            memory = getMemoryInfo(),
            storage = getStorageInfo(),
            battery = getBatteryInfo(),
            display = getDisplayInfo(),
            network = getNetworkInfo(),
            sensors = getSensorInfo(),
            build = getBuildInfo(),
            kernel = getKernelInfo()
        )
    }
    
    /**
     * 设备基本信息
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            bootloader = Build.BOOTLOADER,
            fingerprint = Build.FINGERPRINT
        )
    }
    
    /**
     * CPU 信息
     */
    @SuppressLint("NewApi")
    fun getCpuInfo(): CpuInfo {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuAbi = Build.SUPPORTED_ABIS.toList()
        
        // 读取 /proc/cpuinfo
        val cpuInfoMap = mutableMapOf<String, String>()
        try {
            File("/proc/cpuinfo").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(":")
                    if (parts.size == 2) {
                        cpuInfoMap[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略读取错误
        }
        
        // 获取 CPU 频率
        val frequencies = getCpuFrequencies()
        
        return CpuInfo(
            cores = cpuCores,
            abi = cpuAbi,
            processor = cpuInfoMap["Processor"] ?: cpuInfoMap["model name"] ?: "Unknown",
            hardware = cpuInfoMap["Hardware"] ?: Build.HARDWARE,
            features = cpuInfoMap["Features"] ?: "",
            currentFreq = frequencies.map { it.current },
            maxFreq = frequencies.map { it.max },
            minFreq = frequencies.map { it.min }
        )
    }
    
    /**
     * 获取每个 CPU 核心的频率
     */
    private fun getCpuFrequencies(): List<CpuCoreFreq> {
        val frequencies = mutableListOf<CpuCoreFreq>()
        val cpuCount = Runtime.getRuntime().availableProcessors()
        
        for (i in 0 until cpuCount) {
            val basePath = "/sys/devices/system/cpu/cpu$i/cpufreq"
            
            val current = readFileAsLong("$basePath/scaling_cur_freq") / 1000 // kHz -> MHz
            val max = readFileAsLong("$basePath/scaling_max_freq") / 1000
            val min = readFileAsLong("$basePath/scaling_min_freq") / 1000
            
            frequencies.add(CpuCoreFreq(current, max, min))
        }
        
        return frequencies
    }
    
    /**
     * 内存信息
     */
    fun getMemoryInfo(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        // 读取 /proc/meminfo 获取更详细信息
        val memInfoMap = mutableMapOf<String, Long>()
        try {
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(":")
                    if (parts.size == 2) {
                        val value = parts[1].trim().replace(" kB", "").trim()
                        memInfoMap[parts[0].trim()] = value.toLongOrNull() ?: 0
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略
        }
        
        return MemoryInfo(
            totalRam = memInfo.totalMem,
            availableRam = memInfo.availMem,
            usedRam = memInfo.totalMem - memInfo.availMem,
            threshold = memInfo.threshold,
            lowMemory = memInfo.lowMemory,
            buffers = (memInfoMap["Buffers"] ?: 0) * 1024,
            cached = (memInfoMap["Cached"] ?: 0) * 1024,
            swapTotal = (memInfoMap["SwapTotal"] ?: 0) * 1024,
            swapFree = (memInfoMap["SwapFree"] ?: 0) * 1024
        )
    }
    
    /**
     * 存储信息
     */
    fun getStorageInfo(): StorageInfo {
        val internalPath = Environment.getDataDirectory()
        val internalStat = StatFs(internalPath.path)
        
        val internalTotal = internalStat.blockSizeLong * internalStat.blockCountLong
        val internalFree = internalStat.blockSizeLong * internalStat.availableBlocksLong
        
        // 外部存储
        var externalTotal = 0L
        var externalFree = 0L
        
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val externalPath = Environment.getExternalStorageDirectory()
            val externalStat = StatFs(externalPath.path)
            externalTotal = externalStat.blockSizeLong * externalStat.blockCountLong
            externalFree = externalStat.blockSizeLong * externalStat.availableBlocksLong
        }
        
        return StorageInfo(
            internalTotal = internalTotal,
            internalFree = internalFree,
            internalUsed = internalTotal - internalFree,
            externalTotal = externalTotal,
            externalFree = externalFree,
            externalUsed = externalTotal - externalFree
        )
    }
    
    /**
     * 电池信息
     */
    fun getBatteryInfo(): BatteryInfo {
        val batteryIntent = context.registerReceiver(null, 
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        
        val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val health = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val temperature = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltage = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val technology = batteryIntent?.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        
        return BatteryInfo(
            percentage = percentage,
            status = getBatteryStatus(status),
            plugged = getPluggedStatus(plugged),
            health = getBatteryHealth(health),
            temperature = temperature / 10.0f,
            voltage = voltage,
            technology = technology
        )
    }
    
    /**
     * 显示信息
     */
    fun getDisplayInfo(): DisplayInfo {
        val displayMetrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        
        return DisplayInfo(
            widthPixels = displayMetrics.widthPixels,
            heightPixels = displayMetrics.heightPixels,
            density = displayMetrics.density,
            densityDpi = displayMetrics.densityDpi,
            scaledDensity = displayMetrics.scaledDensity,
            xdpi = displayMetrics.xdpi,
            ydpi = displayMetrics.ydpi,
            screenLayout = configuration.screenLayout,
            refreshRate = getRefreshRate()
        )
    }
    
    /**
     * 网络信息
     */
    @SuppressLint("MissingPermission")
    fun getNetworkInfo(): NetworkInfo {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        
        return NetworkInfo(
            isConnected = activeNetwork?.isConnected == true,
            type = activeNetwork?.typeName ?: "None",
            subtype = activeNetwork?.subtypeName ?: "",
            isRoaming = activeNetwork?.isRoaming == true
        )
    }
    
    /**
     * 传感器信息
     */
    fun getSensorInfo(): List<SensorInfo> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensors = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)
        
        return sensors.map { sensor ->
            SensorInfo(
                name = sensor.name,
                vendor = sensor.vendor,
                type = sensor.type,
                typeName = getSensorTypeName(sensor.type),
                version = sensor.version,
                power = sensor.power,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution
            )
        }
    }
    
    /**
     * Build 信息
     */
    fun getBuildInfo(): BuildInfo {
        return BuildInfo(
            versionRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            versionCodename = Build.VERSION.CODENAME,
            buildId = Build.ID,
            buildDisplay = Build.DISPLAY,
            buildType = Build.TYPE,
            buildTags = Build.TAGS,
            buildTime = Build.TIME,
            buildUser = Build.USER,
            buildHost = Build.HOST,
            radioVersion = Build.getRadioVersion() ?: "Unknown"
        )
    }
    
    /**
     * 内核信息
     */
    fun getKernelInfo(): KernelInfo {
        val kernelVersion = System.getProperty("os.version") ?: "Unknown"
        val kernelArch = System.getProperty("os.arch") ?: "Unknown"
        
        // 读取 /proc/version
        val fullVersion = try {
            File("/proc/version").readText().trim()
        } catch (e: Exception) {
            "Unknown"
        }
        
        // 读取 SELinux 状态
        val selinuxStatus = try {
            File("/sys/fs/selinux/enforce").readText().trim()
        } catch (e: Exception) {
            "Unknown"
        }
        
        return KernelInfo(
            version = kernelVersion,
            arch = kernelArch,
            fullVersion = fullVersion,
            selinuxStatus = when (selinuxStatus) {
                "1" -> "Enforcing"
                "0" -> "Permissive"
                else -> selinuxStatus
            }
        )
    }
    
    // 辅助方法
    private fun readFileAsLong(path: String): Long {
        return try {
            File(path).readText().trim().toLong()
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getRefreshRate(): Float {
        return try {
            val display = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            display.refreshRate
        } catch (e: Exception) {
            60f
        }
    }
    
    private fun getBatteryStatus(status: Int): String = when (status) {
        android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
        android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
        android.os.BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
        else -> "未知"
    }
    
    private fun getPluggedStatus(plugged: Int): String = when (plugged) {
        android.os.BatteryManager.BATTERY_PLUGGED_AC -> "交流电"
        android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
        else -> "未连接"
    }
    
    private fun getBatteryHealth(health: Int): String = when (health) {
        android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
        android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
        android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
        android.os.BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
        else -> "未知"
    }
    
    private fun getSensorTypeName(type: Int): String = when (type) {
        android.hardware.Sensor.TYPE_ACCELEROMETER -> "加速度计"
        android.hardware.Sensor.TYPE_GYROSCOPE -> "陀螺仪"
        android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> "磁力计"
        android.hardware.Sensor.TYPE_LIGHT -> "光线传感器"
        android.hardware.Sensor.TYPE_PROXIMITY -> "距离传感器"
        android.hardware.Sensor.TYPE_PRESSURE -> "气压计"
        android.hardware.Sensor.TYPE_GRAVITY -> "重力传感器"
        android.hardware.Sensor.TYPE_LINEAR_ACCELERATION -> "线性加速度"
        android.hardware.Sensor.TYPE_ROTATION_VECTOR -> "旋转矢量"
        android.hardware.Sensor.TYPE_STEP_COUNTER -> "计步器"
        android.hardware.Sensor.TYPE_STEP_DETECTOR -> "步伐检测"
        android.hardware.Sensor.TYPE_HEART_RATE -> "心率传感器"
        android.hardware.Sensor.TYPE_AMBIENT_TEMPERATURE -> "环境温度"
        android.hardware.Sensor.TYPE_RELATIVE_HUMIDITY -> "相对湿度"
        else -> "其他传感器"
    }
    
    // 数据类定义
    data class SystemInfo(
        val device: DeviceInfo,
        val cpu: CpuInfo,
        val memory: MemoryInfo,
        val storage: StorageInfo,
        val battery: BatteryInfo,
        val display: DisplayInfo,
        val network: NetworkInfo,
        val sensors: List<SensorInfo>,
        val build: BuildInfo,
        val kernel: KernelInfo
    )
    
    data class DeviceInfo(
        val manufacturer: String,
        val brand: String,
        val model: String,
        val device: String,
        val product: String,
        val hardware: String,
        val board: String,
        val bootloader: String,
        val fingerprint: String
    )
    
    data class CpuInfo(
        val cores: Int,
        val abi: List<String>,
        val processor: String,
        val hardware: String,
        val features: String,
        val currentFreq: List<Long>,
        val maxFreq: List<Long>,
        val minFreq: List<Long>
    )
    
    data class CpuCoreFreq(
        val current: Long,
        val max: Long,
        val min: Long
    )
    
    data class MemoryInfo(
        val totalRam: Long,
        val availableRam: Long,
        val usedRam: Long,
        val threshold: Long,
        val lowMemory: Boolean,
        val buffers: Long,
        val cached: Long,
        val swapTotal: Long,
        val swapFree: Long
    )
    
    data class StorageInfo(
        val internalTotal: Long,
        val internalFree: Long,
        val internalUsed: Long,
        val externalTotal: Long,
        val externalFree: Long,
        val externalUsed: Long
    )
    
    data class BatteryInfo(
        val percentage: Int,
        val status: String,
        val plugged: String,
        val health: String,
        val temperature: Float,
        val voltage: Int,
        val technology: String
    )
    
    data class DisplayInfo(
        val widthPixels: Int,
        val heightPixels: Int,
        val density: Float,
        val densityDpi: Int,
        val scaledDensity: Float,
        val xdpi: Float,
        val ydpi: Float,
        val screenLayout: Int,
        val refreshRate: Float
    )
    
    data class NetworkInfo(
        val isConnected: Boolean,
        val type: String,
        val subtype: String,
        val isRoaming: Boolean
    )
    
    data class SensorInfo(
        val name: String,
        val vendor: String,
        val type: Int,
        val typeName: String,
        val version: Int,
        val power: Float,
        val maxRange: Float,
        val resolution: Float
    )
    
    data class BuildInfo(
        val versionRelease: String,
        val sdkInt: Int,
        val versionCodename: String,
        val buildId: String,
        val buildDisplay: String,
        val buildType: String,
        val buildTags: String,
        val buildTime: Long,
        val buildUser: String,
        val buildHost: String,
        val radioVersion: String
    )
    
    data class KernelInfo(
        val version: String,
        val arch: String,
        val fullVersion: String,
        val selinuxStatus: String
    )
}
