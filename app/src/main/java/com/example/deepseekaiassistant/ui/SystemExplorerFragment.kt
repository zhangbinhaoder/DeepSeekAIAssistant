package com.example.deepseekaiassistant.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.deepseekaiassistant.databinding.FragmentSystemExplorerBinding
import com.example.deepseekaiassistant.tools.SystemExplorer
import com.example.deepseekaiassistant.tools.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 底层探测器页面
 * 读取系统底层信息
 */
class SystemExplorerFragment : Fragment() {
    
    private var _binding: FragmentSystemExplorerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var systemExplorer: SystemExplorer
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSystemExplorerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        systemExplorer = SystemExplorer(requireContext())
        
        setupTabButtons()
        loadDeviceInfo()
    }
    
    private fun setupTabButtons() {
        binding.btnDevice.setOnClickListener { loadDeviceInfo() }
        binding.btnCpu.setOnClickListener { loadCpuInfo() }
        binding.btnMemory.setOnClickListener { loadMemoryInfo() }
        binding.btnStorage.setOnClickListener { loadStorageInfo() }
        binding.btnBattery.setOnClickListener { loadBatteryInfo() }
        binding.btnDisplay.setOnClickListener { loadDisplayInfo() }
        binding.btnNetwork.setOnClickListener { loadNetworkInfo() }
        binding.btnSensors.setOnClickListener { loadSensorInfo() }
        binding.btnBuild.setOnClickListener { loadBuildInfo() }
        binding.btnKernel.setOnClickListener { loadKernelInfo() }
        
        // 刷新按钮
        binding.btnRefresh.setOnClickListener {
            binding.tvContent.text = "正在刷新..."
            loadAllInfo()
        }
        
        // 复制按钮
        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("系统信息", binding.tvContent.text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(requireContext(), "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadDeviceInfo() {
        updateButtonState(binding.btnDevice)
        binding.tvTitle.text = "📱 设备信息"
        
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                systemExplorer.getDeviceInfo()
            }
            
            binding.tvContent.text = buildString {
                appendLine("━━━ 设备信息 ━━━")
                appendLine()
                appendLine("制造商: ${info.manufacturer}")
                appendLine("品牌: ${info.brand}")
                appendLine("型号: ${info.model}")
                appendLine("设备名: ${info.device}")
                appendLine("产品: ${info.product}")
                appendLine("硬件: ${info.hardware}")
                appendLine("主板: ${info.board}")
                appendLine("Bootloader: ${info.bootloader}")
                appendLine()
                appendLine("━━━ 指纹信息 ━━━")
                appendLine(info.fingerprint)
            }
        }
    }
    
    private fun loadCpuInfo() {
        updateButtonState(binding.btnCpu)
        binding.tvTitle.text = "⚡ CPU 信息"
        
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                systemExplorer.getCpuInfo()
            }
            
            binding.tvContent.text = buildString {
                appendLine("━━━ CPU 信息 ━━━")
                appendLine()
                appendLine("核心数: ${info.cores}")
                appendLine("架构: ${info.abi.joinToString(", ")}")
                appendLine("处理器: ${info.processor}")
                appendLine("硬件: ${info.hardware}")
                appendLine()
                appendLine("━━━ CPU 特性 ━━━")
                appendLine(info.features)
                appendLine()
                appendLine("━━━ 各核心频率 ━━━")
                for (i in info.currentFreq.indices) {
                    appendLine("CPU$i: ${info.currentFreq.getOrNull(i) ?: 0}MHz (${info.minFreq.getOrNull(i) ?: 0}-${info.maxFreq.getOrNull(i) ?: 0}MHz)")
                }
            }
        }
    }
    
    private fun loadMemoryInfo() {
        updateButtonState(binding.btnMemory)
        binding.tvTitle.text = "💾 内存信息"
        
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                systemExplorer.getMemoryInfo()
            }
            
            val usedPercent = (info.usedRam * 100 / info.totalRam).toInt()
            
            binding.tvContent.text = buildString {
                appendLine("━━━ 内存信息 ━━━")
                appendLine()
                appendLine("总内存: ${formatFileSize(info.totalRam)}")
                appendLine("已用: ${formatFileSize(info.usedRam)} ($usedPercent%)")
                appendLine("可用: ${formatFileSize(info.availableRam)}")
                appendLine()
                appendLine("━━━ 详细信息 ━━━")
                appendLine("低内存阈值: ${formatFileSize(info.threshold)}")
                appendLine("低内存状态: ${if (info.lowMemory) "是" else "否"}")
                appendLine("Buffers: ${formatFileSize(info.buffers)}")
                appendLine("Cached: ${formatFileSize(info.cached)}")
                appendLine()
                appendLine("━━━ Swap ━━━")
                appendLine("Swap 总量: ${formatFileSize(info.swapTotal)}")
                appendLine("Swap 空闲: ${formatFileSize(info.swapFree)}")
            }
        }
    }
    
    private fun loadStorageInfo() {
        updateButtonState(binding.btnStorage)
        binding.tvTitle.text = "💿 存储信息"
        
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                systemExplorer.getStorageInfo()
            }
            
            val internalPercent = if (info.internalTotal > 0) (info.internalUsed * 100 / info.internalTotal).toInt() else 0
            
            binding.tvContent.text = buildString {
                appendLine("━━━ 内部存储 ━━━")
                appendLine()
                appendLine("总容量: ${formatFileSize(info.internalTotal)}")
                appendLine("已用: ${formatFileSize(info.internalUsed)} ($internalPercent%)")
                appendLine("可用: ${formatFileSize(info.internalFree)}")
                
                if (info.externalTotal > 0) {
                    val externalPercent = (info.externalUsed * 100 / info.externalTotal).toInt()
                    appendLine()
                    appendLine("━━━ 外部存储 ━━━")
                    appendLine()
                    appendLine("总容量: ${formatFileSize(info.externalTotal)}")
                    appendLine("已用: ${formatFileSize(info.externalUsed)} ($externalPercent%)")
                    appendLine("可用: ${formatFileSize(info.externalFree)}")
                }
            }
        }
    }
    
    private fun loadBatteryInfo() {
        updateButtonState(binding.btnBattery)
        binding.tvTitle.text = "🔋 电池信息"
        
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                systemExplorer.getBatteryInfo()
            }
            
            binding.tvContent.text = buildString {
                appendLine("━━━ 电池信息 ━━━")
                appendLine()
                appendLine("电量: ${info.percentage}%")
                appendLine("状态: ${info.status}")
                appendLine("电源: ${info.plugged}")
                appendLine("健康: ${info.health}")
                appendLine("温度: ${info.temperature}°C")
                appendLine("电压: ${info.voltage}mV")
                appendLine("技术: ${info.technology}")
            }
        }
    }
    
    private fun loadDisplayInfo() {
        updateButtonState(binding.btnDisplay)
        binding.tvTitle.text = "📺 显示信息"
        
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                systemExplorer.getDisplayInfo()
            }
            
            binding.tvContent.text = buildString {
                appendLine("━━━ 显示信息 ━━━")
                appendLine()
                appendLine("分辨率: ${info.widthPixels} × ${info.heightPixels}")
                appendLine("密度: ${info.density}x (${info.densityDpi} DPI)")
                appendLine("缩放密度: ${info.scaledDensity}")
                appendLine("X DPI: ${info.xdpi}")
                appendLine("Y DPI: ${info.ydpi}")
                appendLine("刷新率: ${info.refreshRate} Hz")
            }
        }
    }
    
    private fun loadNetworkInfo() {
        updateButtonState(binding.btnNetwork)
        binding.tvTitle.text = "🌐 网络信息"
        
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                systemExplorer.getNetworkInfo()
            }
            
            binding.tvContent.text = buildString {
                appendLine("━━━ 网络信息 ━━━")
                appendLine()
                appendLine("连接状态: ${if (info.isConnected) "已连接" else "未连接"}")
                appendLine("网络类型: ${info.type}")
                appendLine("子类型: ${info.subtype}")
                appendLine("漫游: ${if (info.isRoaming) "是" else "否"}")
            }
        }
    }
    
    private fun loadSensorInfo() {
        updateButtonState(binding.btnSensors)
        binding.tvTitle.text = "📡 传感器信息"
        
        lifecycleScope.launch {
            val sensors = withContext(Dispatchers.IO) {
                systemExplorer.getSensorInfo()
            }
            
            binding.tvContent.text = buildString {
                appendLine("━━━ 传感器列表 (${sensors.size}个) ━━━")
                appendLine()
                
                sensors.forEach { sensor ->
                    appendLine("【${sensor.typeName}】")
                    appendLine("  名称: ${sensor.name}")
                    appendLine("  厂商: ${sensor.vendor}")
                    appendLine("  功耗: ${sensor.power} mA")
                    appendLine("  精度: ${sensor.resolution}")
                    appendLine()
                }
            }
        }
    }
    
    private fun loadBuildInfo() {
        updateButtonState(binding.btnBuild)
        binding.tvTitle.text = "🔧 Build 信息"
        
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                systemExplorer.getBuildInfo()
            }
            
            val buildDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(info.buildTime))
            
            binding.tvContent.text = buildString {
                appendLine("━━━ Build 信息 ━━━")
                appendLine()
                appendLine("Android 版本: ${info.versionRelease}")
                appendLine("SDK 版本: ${info.sdkInt}")
                appendLine("版本代号: ${info.versionCodename}")
                appendLine()
                appendLine("Build ID: ${info.buildId}")
                appendLine("Build 显示: ${info.buildDisplay}")
                appendLine("Build 类型: ${info.buildType}")
                appendLine("Build 标签: ${info.buildTags}")
                appendLine("Build 时间: $buildDate")
                appendLine("Build 用户: ${info.buildUser}")
                appendLine("Build 主机: ${info.buildHost}")
                appendLine()
                appendLine("基带版本: ${info.radioVersion}")
            }
        }
    }
    
    private fun loadKernelInfo() {
        updateButtonState(binding.btnKernel)
        binding.tvTitle.text = "🐧 内核信息"
        
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                systemExplorer.getKernelInfo()
            }
            
            binding.tvContent.text = buildString {
                appendLine("━━━ 内核信息 ━━━")
                appendLine()
                appendLine("内核版本: ${info.version}")
                appendLine("架构: ${info.arch}")
                appendLine("SELinux: ${info.selinuxStatus}")
                appendLine()
                appendLine("━━━ 完整版本 ━━━")
                appendLine(info.fullVersion)
            }
        }
    }
    
    private fun loadAllInfo() {
        lifecycleScope.launch {
            val systemInfo = withContext(Dispatchers.IO) {
                systemExplorer.getFullSystemInfo()
            }
            
            binding.tvTitle.text = "📊 完整系统信息"
            binding.tvContent.text = buildString {
                appendLine("=== 设备 ===")
                appendLine("型号: ${systemInfo.device.model}")
                appendLine("品牌: ${systemInfo.device.brand}")
                appendLine()
                appendLine("=== CPU ===")
                appendLine("核心数: ${systemInfo.cpu.cores}")
                appendLine("架构: ${systemInfo.cpu.abi.joinToString(", ")}")
                appendLine()
                appendLine("=== 内存 ===")
                appendLine("总量: ${formatFileSize(systemInfo.memory.totalRam)}")
                appendLine("可用: ${formatFileSize(systemInfo.memory.availableRam)}")
                appendLine()
                appendLine("=== 存储 ===")
                appendLine("总量: ${formatFileSize(systemInfo.storage.internalTotal)}")
                appendLine("可用: ${formatFileSize(systemInfo.storage.internalFree)}")
                appendLine()
                appendLine("=== 电池 ===")
                appendLine("电量: ${systemInfo.battery.percentage}%")
                appendLine("温度: ${systemInfo.battery.temperature}°C")
                appendLine()
                appendLine("=== 系统 ===")
                appendLine("Android: ${systemInfo.build.versionRelease} (SDK ${systemInfo.build.sdkInt})")
                appendLine("内核: ${systemInfo.kernel.version}")
                appendLine("SELinux: ${systemInfo.kernel.selinuxStatus}")
            }
        }
    }
    
    private fun updateButtonState(activeButton: View) {
        // 重置所有按钮状态
        listOf(
            binding.btnDevice, binding.btnCpu, binding.btnMemory, binding.btnStorage,
            binding.btnBattery, binding.btnDisplay, binding.btnNetwork, binding.btnSensors,
            binding.btnBuild, binding.btnKernel
        ).forEach {
            it.alpha = 0.6f
        }
        // 设置当前按钮为激活状态
        activeButton.alpha = 1f
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance() = SystemExplorerFragment()
    }
}
