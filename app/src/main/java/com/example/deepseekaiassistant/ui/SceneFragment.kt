package com.example.deepseekaiassistant.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deepseekaiassistant.R
import com.example.deepseekaiassistant.databinding.FragmentSceneBinding
import com.example.deepseekaiassistant.local.LocalAIManager
import com.example.deepseekaiassistant.root.AIRootController
import com.example.deepseekaiassistant.root.RootManager
import com.example.deepseekaiassistant.tools.SceneTools
import com.example.deepseekaiassistant.agent.GameAIAgent
import com.example.deepseekaiassistant.agent.MultiSceneAIAgent
import com.example.deepseekaiassistant.agent.OperationType
import java.io.File

/**
 * Scene 玩机功能页面
 */
class SceneFragment : Fragment() {
    
    private var _binding: FragmentSceneBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var sceneTools: SceneTools
    private lateinit var localAIManager: LocalAIManager
    private lateinit var rootManager: RootManager
    private lateinit var aiRootController: AIRootController
    
    // AI 代执行代理
    private lateinit var gameAIAgent: GameAIAgent
    private lateinit var multiSceneAgent: MultiSceneAIAgent
    private var currentSceneType: SceneType = SceneType.GAME
    
    enum class SceneType {
        GAME, VIDEO, SHOP, FOOD
    }
    
    // 下载对话框
    private var downloadDialog: AlertDialog? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSceneBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sceneTools = SceneTools(requireContext())
        localAIManager = LocalAIManager.getInstance(requireContext())
        rootManager = RootManager.getInstance(requireContext())
        aiRootController = AIRootController.getInstance(requireContext())
        
        setupUI()
        setupLocalAI()
        setupModuleManagement()
        setupAIControlPermission()
        setupAIAgent()
        checkRootStatus()
        loadSystemInfo()
    }
    
    override fun onResume() {
        super.onResume()
        updateModelStatus()
        loadModuleInfo()
    }
    
    private fun setupUI() {
        // ROOT 权限检测
        binding.btnCheckRoot.setOnClickListener {
            checkRootStatus()
        }
        
        // 性能模式切换
        binding.chipGroupPerformance.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val mode = when (checkedIds.first()) {
                    R.id.chipPowersave -> SceneTools.PerformanceMode.POWERSAVE
                    R.id.chipBalanced -> SceneTools.PerformanceMode.BALANCED
                    R.id.chipPerformance -> SceneTools.PerformanceMode.PERFORMANCE
                    R.id.chipGaming -> SceneTools.PerformanceMode.GAMING
                    else -> return@setOnCheckedStateChangeListener
                }
                applyPerformanceMode(mode)
            }
        }
        
        // 内存清理
        binding.btnClearMemory.setOnClickListener {
            clearMemory()
        }
        
        // 动画设置
        binding.sliderAnimation.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvAnimationValue.text = String.format("%.1fx", value)
            }
        }
        
        binding.btnApplyAnimation.setOnClickListener {
            val scale = binding.sliderAnimation.value
            applyAnimationScale(scale)
        }
        
        // 应用管理
        binding.btnAppManager.setOnClickListener {
            // 跳转到应用管理
            Toast.makeText(requireContext(), "应用管理功能开发中", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkRootStatus() {
        val hasRoot = sceneTools.checkRootAccess()
        binding.tvRootStatus.text = if (hasRoot) {
            "✓ ROOT 权限已获取"
        } else {
            "✗ 未获取 ROOT 权限（部分功能不可用）"
        }
        binding.tvRootStatus.setTextColor(
            if (hasRoot) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt()
        )
    }
    
    private fun loadSystemInfo() {
        // CPU 调速器
        val currentGovernor = sceneTools.getCurrentGovernor()
        binding.tvCurrentGovernor.text = "当前调速器: $currentGovernor"
        
        // CPU 频率
        val (minFreq, maxFreq) = sceneTools.getCpuFreqRange()
        binding.tvCpuFreqRange.text = "频率范围: ${minFreq}MHz - ${maxFreq}MHz"
        
        // 电池信息
        val battery = sceneTools.getBatteryStats()
        binding.tvBatteryInfo.text = buildString {
            append("电池: ${battery.capacity}%")
            append(" | 温度: ${battery.temperature}°C")
            append(" | 电流: ${battery.current}mA")
        }
        
        // 动画缩放
        val (window, transition, animator) = sceneTools.getAnimationScale()
        binding.sliderAnimation.value = window.coerceIn(0f, 2f)
        binding.tvAnimationValue.text = String.format("%.1fx", window)
        
        // 开发者选项
        val devEnabled = sceneTools.isDeveloperOptionsEnabled()
        val adbEnabled = sceneTools.isAdbEnabled()
        binding.tvDevOptions.text = buildString {
            append("开发者选项: ${if (devEnabled) "已开启" else "未开启"}")
            append(" | USB 调试: ${if (adbEnabled) "已开启" else "未开启"}")
        }
    }
    
    private fun applyPerformanceMode(mode: SceneTools.PerformanceMode) {
        if (!sceneTools.checkRootAccess()) {
            Toast.makeText(requireContext(), "需要 ROOT 权限", Toast.LENGTH_SHORT).show()
            return
        }
        
        val success = sceneTools.setPerformanceMode(mode)
        val modeName = when (mode) {
            SceneTools.PerformanceMode.POWERSAVE -> "省电模式"
            SceneTools.PerformanceMode.BALANCED -> "平衡模式"
            SceneTools.PerformanceMode.PERFORMANCE -> "性能模式"
            SceneTools.PerformanceMode.GAMING -> "游戏模式"
        }
        
        Toast.makeText(
            requireContext(),
            if (success) "$modeName 已应用" else "设置失败",
            Toast.LENGTH_SHORT
        ).show()
        
        loadSystemInfo()
    }
    
    private fun clearMemory() {
        if (!sceneTools.checkRootAccess()) {
            Toast.makeText(requireContext(), "需要 ROOT 权限", Toast.LENGTH_SHORT).show()
            return
        }
        
        sceneTools.killBackgroundApps()
        sceneTools.dropCaches()
        
        Toast.makeText(requireContext(), "内存已清理", Toast.LENGTH_SHORT).show()
    }
    
    private fun applyAnimationScale(scale: Float) {
        val success = sceneTools.setAnimationScale(scale)
        Toast.makeText(
            requireContext(),
            if (success) "动画缩放已设置为 ${scale}x" else "设置失败（需要 ROOT 权限）",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    // ==================== 本地 AI 模型管理 ====================
    
    private fun setupLocalAI() {
        // 查看模型状态按钮
        binding.btnRefreshModelStatus.setOnClickListener {
            updateModelStatus()
        }
        
        // 下载模型按钮
        binding.btnDownloadModel.setOnClickListener {
            showModelDownloadDialog()
        }
        
        // 管理模型按钮
        binding.btnManageModels.setOnClickListener {
            showModelManageDialog()
        }
        
        // 加载模型按钮
        binding.btnLoadModel.setOnClickListener {
            loadSelectedModel()
        }
        
        // 初始更新状态
        updateModelStatus()
    }
    
    private fun updateModelStatus() {
        val downloadedModels = localAIManager.getDownloadedModels()
        val isReady = localAIManager.isReady()
        val isRealInference = localAIManager.isRealInferenceSupported()
        
        // 更新模型状态文本
        binding.tvModelStatus.text = buildString {
            appendLine("🧠 AI 引擎: ${if (isRealInference) "llama.cpp (原生)" else "模拟模式"}")
            appendLine("📦 已下载模型: ${downloadedModels.size} 个")
            append("🟢 模型状态: ${if (isReady) "已加载" else "未加载"}")
        }
        
        // 更新模型列表
        if (downloadedModels.isNotEmpty()) {
            binding.tvDownloadedModels.text = downloadedModels.joinToString("\n") { file ->
                "• ${file.name} (${formatFileSize(file.length())})"
            }
            binding.tvDownloadedModels.visibility = View.VISIBLE
            binding.btnLoadModel.visibility = View.VISIBLE
            binding.btnManageModels.visibility = View.VISIBLE
        } else {
            binding.tvDownloadedModels.text = "暂无已下载的模型\n点击“下载模型”开始"
            binding.tvDownloadedModels.visibility = View.VISIBLE
            binding.btnLoadModel.visibility = View.GONE
            binding.btnManageModels.visibility = View.GONE
        }
        
        // 更新加载按钮状态
        binding.btnLoadModel.text = if (isReady) "✅ 已加载" else "加载模型"
        binding.btnLoadModel.isEnabled = !isReady && downloadedModels.isNotEmpty()
    }
    
    private fun showModelDownloadDialog() {
        val models = localAIManager.availableModels
        val modelNames = models.map { "${it.displayName}\n${it.description} (~${formatFileSize(it.sizeBytes)})" }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("📥 选择要下载的模型")
            .setItems(modelNames) { _, which ->
                val selectedModel = models[which]
                startModelDownload(selectedModel)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun startModelDownload(modelConfig: LocalAIManager.ModelConfig) {
        // 创建下载进度对话框
        val dialogView = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val tvProgress = dialogView.findViewById<TextView>(R.id.tvProgress)
        val tvModelName = dialogView.findViewById<TextView>(R.id.tvModelName)
        
        tvModelName.text = "正在下载: ${modelConfig.displayName}"
        progressBar.max = 100
        progressBar.progress = 0
        
        downloadDialog = AlertDialog.Builder(requireContext())
            .setTitle("📥 下载模型")
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(requireContext(), "下载已取消", Toast.LENGTH_SHORT).show()
            }
            .show()
        
        localAIManager.downloadModel(
            modelConfig = modelConfig,
            onProgress = { progress ->
                activity?.runOnUiThread {
                    progressBar.progress = progress
                    tvProgress.text = "$progress%"
                }
            },
            onComplete = { success, error ->
                activity?.runOnUiThread {
                    downloadDialog?.dismiss()
                    if (success) {
                        Toast.makeText(requireContext(), "✅ 模型下载完成!", Toast.LENGTH_LONG).show()
                        updateModelStatus()
                    } else {
                        Toast.makeText(requireContext(), "❌ 下载失败: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    
    private fun showModelManageDialog() {
        val downloadedModels = localAIManager.getDownloadedModels()
        if (downloadedModels.isEmpty()) {
            Toast.makeText(requireContext(), "没有已下载的模型", Toast.LENGTH_SHORT).show()
            return
        }
        
        val modelNames = downloadedModels.map { "${it.name} (${formatFileSize(it.length())})" }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("🗂️ 管理模型")
            .setItems(modelNames) { _, which ->
                val selectedModel = downloadedModels[which]
                showModelOptionsDialog(selectedModel)
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun showModelOptionsDialog(modelFile: File) {
        AlertDialog.Builder(requireContext())
            .setTitle(modelFile.name)
            .setMessage("文件大小: ${formatFileSize(modelFile.length())}")
            .setPositiveButton("加载此模型") { _, _ ->
                loadModel(modelFile.absolutePath)
            }
            .setNegativeButton("删除") { _, _ ->
                confirmDeleteModel(modelFile)
            }
            .setNeutralButton("取消", null)
            .show()
    }
    
    private fun confirmDeleteModel(modelFile: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除模型 ${modelFile.name} 吗？\n\n此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                val success = localAIManager.deleteModel(modelFile.name)
                if (success) {
                    Toast.makeText(requireContext(), "模型已删除", Toast.LENGTH_SHORT).show()
                    updateModelStatus()
                } else {
                    Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun loadSelectedModel() {
        val downloadedModels = localAIManager.getDownloadedModels()
        if (downloadedModels.isEmpty()) {
            Toast.makeText(requireContext(), "没有可用的模型", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (downloadedModels.size == 1) {
            loadModel(downloadedModels.first().absolutePath)
        } else {
            // 多个模型，让用户选择
            val modelNames = downloadedModels.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("选择要加载的模型")
                .setItems(modelNames) { _, which ->
                    loadModel(downloadedModels[which].absolutePath)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    private fun loadModel(modelPath: String) {
        binding.btnLoadModel.isEnabled = false
        binding.btnLoadModel.text = "加载中..."
        
        localAIManager.loadModel(
            modelName = modelPath,
            onProgress = { progress ->
                activity?.runOnUiThread {
                    binding.btnLoadModel.text = "加载中... $progress%"
                }
            },
            onComplete = { success, error ->
                activity?.runOnUiThread {
                    if (success) {
                        Toast.makeText(requireContext(), "✅ 模型加载成功!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "❌ 加载失败: $error", Toast.LENGTH_SHORT).show()
                    }
                    updateModelStatus()
                }
            }
        )
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
    
    // ==================== 模块管理 ====================
    
    private fun setupModuleManagement() {
        // 刷新模块状态
        binding.btnRefreshModules.setOnClickListener {
            loadModuleInfo()
        }
        
        // 管理 Magisk 模块
        binding.btnManageMagiskModules.setOnClickListener {
            showMagiskModulesDialog()
        }
        
        // 管理内核模块
        binding.btnManageKernelModules.setOnClickListener {
            showKernelModulesDialog()
        }
        
        // 初始加载
        loadModuleInfo()
    }
    
    private fun loadModuleInfo() {
        // 检测 Magisk
        val hasMagisk = sceneTools.isMagiskInstalled()
        val magiskVersion = if (hasMagisk) sceneTools.getMagiskVersion() else "未安装"
        
        binding.tvMagiskStatus.text = buildString {
            append("Magisk: ")
            if (hasMagisk) {
                append("✅ 已安装 ($magiskVersion)")
            } else {
                append("❌ 未安装")
            }
        }
        
        // 加载 Magisk 模块
        val magiskModules = sceneTools.getMagiskModules()
        binding.tvMagiskModules.text = if (magiskModules.isNotEmpty()) {
            magiskModules.take(5).joinToString("\n") { module ->
                val status = if (module.enabled) "✅" else "⚫"
                "$status ${module.name} v${module.version}"
            } + if (magiskModules.size > 5) "\n... 还有 ${magiskModules.size - 5} 个模块" else ""
        } else {
            "暂无已安装的 Magisk 模块"
        }
        
        // 加载内核模块
        val kernelModules = sceneTools.getKernelModules()
        binding.tvKernelModules.text = if (kernelModules.isNotEmpty()) {
            kernelModules.take(8).joinToString("\n") { module ->
                "• ${module.name} (${module.description.substringAfter("Size: ").substringBefore(" bytes")} bytes)"
            } + if (kernelModules.size > 8) "\n... 还有 ${kernelModules.size - 8} 个模块" else ""
        } else {
            "无内核模块信息"
        }
    }
    
    private fun showMagiskModulesDialog() {
        if (!sceneTools.checkRootAccess()) {
            Toast.makeText(requireContext(), "需要 ROOT 权限", Toast.LENGTH_SHORT).show()
            return
        }
        
        val modules = sceneTools.getMagiskModules()
        if (modules.isEmpty()) {
            Toast.makeText(requireContext(), "没有已安装的 Magisk 模块", Toast.LENGTH_SHORT).show()
            return
        }
        
        val moduleNames = modules.map { 
            val status = if (it.enabled) "✅" else "⚫"
            "$status ${it.name} v${it.version}\n   ${it.description.take(50)}"
        }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("📦 Magisk 模块管理")
            .setItems(moduleNames) { _, which ->
                showModuleOptionsDialog(modules[which])
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun showModuleOptionsDialog(module: SceneTools.ModuleInfo) {
        val options = if (module.enabled) {
            arrayOf("禁用模块", "删除模块", "查看详情")
        } else {
            arrayOf("启用模块", "删除模块", "查看详情")
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(module.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // 启用/禁用
                        val success = if (module.enabled) {
                            sceneTools.disableMagiskModule(module.path)
                        } else {
                            sceneTools.enableMagiskModule(module.path)
                        }
                        val actionText = if (module.enabled) "禁用" else "启用"
                        val message = if (success) "模块已${actionText}，重启后生效" else "${actionText}失败"
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        loadModuleInfo()
                    }
                    1 -> { // 删除
                        confirmDeleteModule(module)
                    }
                    2 -> { // 详情
                        showModuleDetails(module)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun confirmDeleteModule(module: SceneTools.ModuleInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除模块 ${module.name} 吗？\n\n重启后将完全删除。")
            .setPositiveButton("删除") { _, _ ->
                val success = sceneTools.removeMagiskModule(module.path)
                Toast.makeText(requireContext(),
                    if (success) "模块已标记删除，重启后生效" else "删除失败",
                    Toast.LENGTH_SHORT).show()
                loadModuleInfo()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showModuleDetails(module: SceneTools.ModuleInfo) {
        val details = buildString {
            appendLine("📌 ID: ${module.id}")
            appendLine("📝 名称: ${module.name}")
            appendLine("📊 版本: ${module.version}")
            appendLine("👤 作者: ${module.author}")
            appendLine("📁 路径: ${module.path}")
            appendLine("🟢 状态: ${if (module.enabled) "已启用" else "已禁用"}")
            appendLine()
            appendLine("📖 描述:")
            append(module.description.ifEmpty { "无描述" })
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(module.name)
            .setMessage(details)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showKernelModulesDialog() {
        val modules = sceneTools.getKernelModules()
        if (modules.isEmpty()) {
            Toast.makeText(requireContext(), "无内核模块信息", Toast.LENGTH_SHORT).show()
            return
        }
        
        val moduleNames = modules.map { 
            "${it.name}\n   ${it.description}"
        }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("⚙️ 内核模块列表")
            .setItems(moduleNames) { _, which ->
                val module = modules[which]
                if (sceneTools.checkRootAccess()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(module.name)
                        .setMessage("是否卸载此内核模块？\n\n警告：卸载内核模块可能导致系统不稳定！")
                        .setPositiveButton("卸载") { _, _ ->
                            val success = sceneTools.unloadKernelModule(module.name)
                            Toast.makeText(requireContext(),
                                if (success) "内核模块已卸载" else "卸载失败",
                                Toast.LENGTH_SHORT).show()
                            loadModuleInfo()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "需要 ROOT 权限", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    // ==================== AI 控制权限 ====================
    
    private fun setupAIControlPermission() {
        // 加载保存的状态
        val localEnabled = aiRootController.isLocalAIControlEnabled()
        val cloudEnabled = aiRootController.isCloudAIControlEnabled()
        
        binding.switchLocalAIControl.isChecked = localEnabled
        binding.switchCloudAIControl.isChecked = cloudEnabled
        
        updateAIControlStatus()
        
        // 本地 AI 控制开关
        binding.switchLocalAIControl.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !rootManager.isAppRootAuthorized()) {
                Toast.makeText(requireContext(), "需要 ROOT 权限才能开启 AI 控制权", Toast.LENGTH_SHORT).show()
                binding.switchLocalAIControl.isChecked = false
                return@setOnCheckedChangeListener
            }
            
            aiRootController.setLocalAIControlEnabled(isChecked)
            updateAIControlStatus()
            
            val msg = if (isChecked) "本地 AI 控制权已开启" else "本地 AI 控制权已关闭"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
        
        // 云端 AI 控制开关
        binding.switchCloudAIControl.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !rootManager.isAppRootAuthorized()) {
                Toast.makeText(requireContext(), "需要 ROOT 权限才能开启 AI 控制权", Toast.LENGTH_SHORT).show()
                binding.switchCloudAIControl.isChecked = false
                return@setOnCheckedChangeListener
            }
            
            if (isChecked) {
                // 云端 AI 需要额外确认
                AlertDialog.Builder(requireContext())
                    .setTitle("⚠️ 安全警告")
                    .setMessage("您即将允许云端 AI 执行系统命令。\n\n这可能带来安全风险，请确保您信任所连接的 AI 服务。\n\n确定要开启吗？")
                    .setPositiveButton("开启") { _, _ ->
                        aiRootController.setCloudAIControlEnabled(true)
                        updateAIControlStatus()
                        Toast.makeText(requireContext(), "云端 AI 控制权已开启", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消") { _, _ ->
                        binding.switchCloudAIControl.isChecked = false
                    }
                    .show()
            } else {
                aiRootController.setCloudAIControlEnabled(false)
                updateAIControlStatus()
                Toast.makeText(requireContext(), "云端 AI 控制权已关闭", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置高危操作确认回调
        aiRootController.highRiskConfirmCallback = { command, callback ->
            activity?.runOnUiThread {
                AlertDialog.Builder(requireContext())
                    .setTitle("⚠️ 高危操作确认")
                    .setMessage("AI 请求执行高危操作：\n\n${command.action}\n\n确定要执行吗？")
                    .setPositiveButton("执行") { _, _ -> callback(true) }
                    .setNegativeButton("拒绝") { _, _ -> callback(false) }
                    .setCancelable(false)
                    .show()
            }
        }
    }
    
    private fun updateAIControlStatus() {
        val localEnabled = binding.switchLocalAIControl.isChecked
        val cloudEnabled = binding.switchCloudAIControl.isChecked
        
        binding.tvAIControlStatus.text = when {
            localEnabled && cloudEnabled -> "🔓 本地+云端 AI 均有控制权"
            localEnabled -> "🧠 本地 AI 拥有控制权"
            cloudEnabled -> "☁️ 云端 AI 拥有控制权"
            else -> "🔒 AI 控制权限已关闭"
        }
    }
    
    /**
     * 显示 AI 操作日志
     */
    private fun showAIControlLogs() {
        val logs = aiRootController.getOperationLogs()
        if (logs.isEmpty()) {
            Toast.makeText(requireContext(), "暂无操作日志", Toast.LENGTH_SHORT).show()
            return
        }
        
        val logText = logs.takeLast(20).reversed().joinToString("\n\n") { it.toString() }
        
        AlertDialog.Builder(requireContext())
            .setTitle("📜 AI 控制操作日志")
            .setMessage(logText)
            .setPositiveButton("确定", null)
            .setNegativeButton("清空日志") { _, _ ->
                aiRootController.clearLogs()
                Toast.makeText(requireContext(), "日志已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    // ==================== AI 代执行操作 ====================
    
    private fun setupAIAgent() {
        // 初始化 AI 代理
        gameAIAgent = GameAIAgent(requireContext())
        multiSceneAgent = MultiSceneAIAgent(requireContext())
        
        // 设置游戏 AI 监听器
        gameAIAgent.setListener(object : GameAIAgent.GameAIListener {
            override fun onStateChanged(isRunning: Boolean, isPaused: Boolean) {
                activity?.runOnUiThread {
                    updateAgentUI(isRunning, isPaused)
                }
            }
            
            override fun onOperationExecuted(log: GameAIAgent.OperationLogEntry) {
                activity?.runOnUiThread {
                    binding.tvAgentStats.text = buildString {
                        append("最近操作: ${log.description}\n")
                        append("状态: ${if (log.success) "✅ 成功" else "❌ 失败"}")
                    }
                }
            }
            
            override fun onError(message: String) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onStats(totalOps: Int, successOps: Int, runningTime: Long) {
                activity?.runOnUiThread {
                    val successRate = if (totalOps > 0) (successOps * 100 / totalOps) else 0
                    val minutes = runningTime / 60000
                    val seconds = (runningTime % 60000) / 1000
                    binding.tvAgentStats.text = buildString {
                        append("操作统计: $totalOps 次 | 成功率: $successRate%\n")
                        append("运行时间: ${minutes}分${seconds}秒")
                    }
                }
            }
        })
        
        // 场景选择
        binding.chipGroupScene.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds.first()) {
                    R.id.chipSceneGame -> {
                        currentSceneType = SceneType.GAME
                        binding.layoutGameType.visibility = View.VISIBLE
                    }
                    R.id.chipSceneVideo -> {
                        currentSceneType = SceneType.VIDEO
                        binding.layoutGameType.visibility = View.GONE
                    }
                    R.id.chipSceneShop -> {
                        currentSceneType = SceneType.SHOP
                        binding.layoutGameType.visibility = View.GONE
                    }
                    R.id.chipSceneFood -> {
                        currentSceneType = SceneType.FOOD
                        binding.layoutGameType.visibility = View.GONE
                    }
                }
            }
        }
        
        // 游戏类型选择
        binding.chipGroupGameType.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val gameType = when (checkedIds.first()) {
                    R.id.chipGame2D -> GameAIAgent.GameType.ELIMINATE_2D
                    R.id.chipGameMOBA -> GameAIAgent.GameType.MOBA_3D
                    R.id.chipGameFPS -> GameAIAgent.GameType.FPS_3D
                    R.id.chipGameRPG -> GameAIAgent.GameType.RPG_3D
                    else -> GameAIAgent.GameType.UNKNOWN
                }
                // 配置游戏类型
                gameAIAgent.configureGame("", gameType)
            }
        }
        
        // Root 模式开关
        binding.switchAgentRoot.setOnCheckedChangeListener { _, isChecked ->
            gameAIAgent.setRootMode(isChecked)
            if (isChecked && !rootManager.isAppRootAuthorized()) {
                Toast.makeText(requireContext(), "需要 ROOT 权限", Toast.LENGTH_SHORT).show()
                binding.switchAgentRoot.isChecked = false
            }
        }
        
        // 启动按钮
        binding.btnStartAgent.setOnClickListener {
            startAIAgent()
        }
        
        // 暂停按钮
        binding.btnPauseAgent.setOnClickListener {
            gameAIAgent.togglePause()
        }
        
        // 停止按钮
        binding.btnStopAgent.setOnClickListener {
            stopAIAgent()
        }
    }
    
    private fun startAIAgent() {
        when (currentSceneType) {
            SceneType.GAME -> {
                // 检查是否配置了游戏类型
                val stats = gameAIAgent.getStats()
                if (stats["currentStrategy"] == "无") {
                    Toast.makeText(requireContext(), "请先选择游戏类型", Toast.LENGTH_SHORT).show()
                    return
                }
                gameAIAgent.start()
            }
            SceneType.VIDEO -> {
                val preferences = listOf("宠物", "搞笑", "美食")
                multiSceneAgent.startShortVideoMode(preferences)
            }
            SceneType.SHOP -> {
                multiSceneAgent.startECommerceMode()
            }
            SceneType.FOOD -> {
                Toast.makeText(requireContext(), "外卖模式需要配置店铺和菜品", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        updateAgentUI(true, false)
        Toast.makeText(requireContext(), "AI 代理已启动", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopAIAgent() {
        gameAIAgent.stop()
        multiSceneAgent.stop()
        updateAgentUI(false, false)
        Toast.makeText(requireContext(), "AI 代理已停止", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateAgentUI(isRunning: Boolean, isPaused: Boolean) {
        binding.btnStartAgent.isEnabled = !isRunning
        binding.btnPauseAgent.isEnabled = isRunning
        binding.btnStopAgent.isEnabled = isRunning
        
        binding.btnPauseAgent.text = if (isPaused) "▶ 继续" else "⏸ 暂停"
        
        binding.tvAgentStatus.text = when {
            isRunning && isPaused -> "已暂停"
            isRunning -> "运行中"
            else -> "未运行"
        }
        
        binding.tvAgentStatus.setBackgroundColor(when {
            isRunning && isPaused -> 0x33FFFF00.toInt()
            isRunning -> 0x3300FF00.toInt()
            else -> 0x33FF0000.toInt()
        })
        
        binding.tvAgentStatus.setTextColor(when {
            isRunning && isPaused -> 0xFFFFAA00.toInt()
            isRunning -> 0xFF00AA00.toInt()
            else -> 0xFFFF0000.toInt()
        })
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // 释放 AI 代理资源
        if (::gameAIAgent.isInitialized) {
            gameAIAgent.release()
        }
        if (::multiSceneAgent.isInitialized) {
            multiSceneAgent.release()
        }
        _binding = null
    }
    
    companion object {
        fun newInstance() = SceneFragment()
        
        /**
         * 检查本地 AI 是否有控制权限
         */
        fun isLocalAIControlEnabled(context: Context): Boolean {
            return AIRootController.getInstance(context).isLocalAIControlEnabled()
        }
        
        /**
         * 检查云端 AI 是否有控制权限
         */
        fun isCloudAIControlEnabled(context: Context): Boolean {
            return AIRootController.getInstance(context).isCloudAIControlEnabled()
        }
        
        /**
         * 获取 AI Root 控制器实例
         */
        fun getAIRootController(context: Context): AIRootController {
            return AIRootController.getInstance(context)
        }
    }
}
