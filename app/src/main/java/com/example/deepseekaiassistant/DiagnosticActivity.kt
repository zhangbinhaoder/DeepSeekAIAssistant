package com.example.deepseekaiassistant

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.example.deepseekaiassistant.databinding.ActivityDiagnosticBinding

class DiagnosticActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDiagnosticBinding
    private var logListener: ((DiagnosticManager.LogEntry) -> Unit)? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "API 诊断"
        
        setupUI()
        setupLogListener()
        showCurrentConfig()
    }
    
    private fun setupUI() {
        // 运行诊断按钮
        binding.btnRunDiagnostic.setOnClickListener {
            runDiagnostic()
        }
        
        // 清空日志按钮
        binding.btnClearLogs.setOnClickListener {
            DiagnosticManager.clearLogs()
            binding.tvLogs.text = ""
            binding.tvStatus.text = "日志已清空"
            binding.tvStatus.setTextColor(Color.GRAY)
        }
        
        // 快速连通测试
        binding.btnQuickTest.setOnClickListener {
            quickConnectivityTest()
        }
    }
    
    private fun setupLogListener() {
        // 显示已有日志
        val existingLogs = DiagnosticManager.getAllLogs()
        if (existingLogs.isNotEmpty()) {
            val sb = SpannableStringBuilder()
            existingLogs.forEach { entry ->
                appendLogEntry(sb, entry)
            }
            binding.tvLogs.text = sb
            scrollToBottom()
        }
        
        // 监听新日志
        logListener = { entry ->
            appendLog(entry)
        }
        DiagnosticManager.addLogListener(logListener!!)
    }
    
    private fun appendLog(entry: DiagnosticManager.LogEntry) {
        val sb = SpannableStringBuilder(binding.tvLogs.text)
        appendLogEntry(sb, entry)
        binding.tvLogs.text = sb
        scrollToBottom()
    }
    
    private fun appendLogEntry(sb: SpannableStringBuilder, entry: DiagnosticManager.LogEntry) {
        val color = when (entry.level) {
            DiagnosticManager.LogLevel.SUCCESS -> Color.parseColor("#4CAF50")
            DiagnosticManager.LogLevel.ERROR -> Color.parseColor("#F44336")
            DiagnosticManager.LogLevel.WARNING -> Color.parseColor("#FF9800")
            DiagnosticManager.LogLevel.DEBUG -> Color.parseColor("#9E9E9E")
            DiagnosticManager.LogLevel.INFO -> Color.parseColor("#2196F3")
        }
        
        val start = sb.length
        sb.append("[${entry.timestamp}] ")
        sb.append("[${entry.level}] ")
        sb.append(entry.message)
        if (entry.details != null) {
            sb.append("\n  → ${entry.details}")
        }
        sb.append("\n")
        sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    
    private fun scrollToBottom() {
        binding.scrollLogs.post {
            binding.scrollLogs.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    private fun showCurrentConfig() {
        val config = AIConfigManager.getCurrentConfig(this)
        val configText = buildString {
            appendLine("━━━ 当前配置 ━━━")
            appendLine("提供商: ${config.provider.displayName}")
            appendLine("Base URL: ${config.baseUrl}")
            appendLine("模型: ${config.model}")
            appendLine("API Key: ${if (config.apiKey.isNotEmpty()) "${config.apiKey.take(8)}...${config.apiKey.takeLast(4)}" else "❌ 未配置"}")
        }
        binding.tvConfig.text = configText
    }
    
    private fun runDiagnostic() {
        binding.btnRunDiagnostic.isEnabled = false
        binding.btnRunDiagnostic.text = "诊断中..."
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvStatus.text = "正在执行诊断..."
        binding.tvStatus.setTextColor(Color.GRAY)
        
        DiagnosticManager.runDiagnostic(
            context = this,
            onProgress = { progress ->
                runOnUiThread {
                    binding.tvStatus.text = progress
                }
            },
            onComplete = { result ->
                runOnUiThread {
                    binding.btnRunDiagnostic.isEnabled = true
                    binding.btnRunDiagnostic.text = "运行诊断"
                    binding.progressBar.visibility = android.view.View.GONE
                    
                    if (result.success) {
                        binding.tvStatus.text = "✓ 诊断成功! 响应时间: ${result.responseTime}ms"
                        binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                        
                        binding.tvResult.visibility = android.view.View.VISIBLE
                        binding.tvResult.text = buildString {
                            appendLine("━━━ 诊断结果: 成功 ━━━")
                            appendLine("提供商: ${result.providerName}")
                            appendLine("状态码: ${result.statusCode}")
                            appendLine("响应时间: ${result.responseTime}ms")
                            appendLine("✓ API 连接正常，可以正常使用")
                        }
                        binding.tvResult.setTextColor(Color.parseColor("#4CAF50"))
                    } else {
                        binding.tvStatus.text = "✗ 诊断失败"
                        binding.tvStatus.setTextColor(Color.parseColor("#F44336"))
                        
                        binding.tvResult.visibility = android.view.View.VISIBLE
                        binding.tvResult.text = buildString {
                            appendLine("━━━ 诊断结果: 失败 ━━━")
                            appendLine("提供商: ${result.providerName}")
                            if (result.statusCode != null) {
                                appendLine("状态码: ${result.statusCode}")
                            }
                            appendLine("响应时间: ${result.responseTime}ms")
                            appendLine()
                            appendLine("错误信息:")
                            appendLine(result.errorMessage)
                            appendLine()
                            appendLine("━━━ 可能的解决方案 ━━━")
                            appendLine(getSuggestion(result))
                        }
                        binding.tvResult.setTextColor(Color.parseColor("#F44336"))
                    }
                }
            }
        )
    }
    
    private fun quickConnectivityTest() {
        binding.btnQuickTest.isEnabled = false
        binding.tvStatus.text = "测试连通性..."
        
        DiagnosticManager.testConnectivity(this) { success, message ->
            binding.btnQuickTest.isEnabled = true
            if (success) {
                binding.tvStatus.text = "✓ $message"
                binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                binding.tvStatus.text = "✗ $message"
                binding.tvStatus.setTextColor(Color.parseColor("#F44336"))
            }
        }
    }
    
    private fun getSuggestion(result: DiagnosticManager.DiagnosticResult): String {
        return when (result.statusCode) {
            401 -> """
                1. 检查 API Key 是否正确复制（无多余空格）
                2. 确认 API Key 未过期或被禁用
                3. 前往 ${result.providerName} 控制台重新生成 API Key
            """.trimIndent()
            402 -> """
                1. 登录 ${result.providerName} 控制台
                2. 检查账户余额并充值
                3. 部分提供商有免费额度，请确认是否已用完
            """.trimIndent()
            429 -> """
                1. 请等待几分钟后重试
                2. 检查是否有其他应用在使用同一 API Key
                3. 考虑升级账户配额或使用其他提供商
            """.trimIndent()
            404 -> """
                1. 检查模型名称是否正确
                2. 确认 Base URL 格式正确
                3. 尝试使用默认模型
            """.trimIndent()
            null -> """
                1. 检查网络连接是否正常
                2. 尝试使用 VPN（部分服务需要）
                3. 检查 Base URL 是否正确
                4. 确认手机能访问该服务
            """.trimIndent()
            else -> """
                1. 稍后重试
                2. 检查 ${result.providerName} 服务状态
                3. 尝试切换到其他 AI 提供商
            """.trimIndent()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        logListener?.let { DiagnosticManager.removeLogListener(it) }
    }
}
