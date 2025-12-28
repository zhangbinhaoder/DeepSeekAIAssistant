package com.example.deepseekaiassistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.deepseekaiassistant.service.AIAccessibilityService

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    class SettingsFragment : PreferenceFragmentCompat() {
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            setupProviderPreference()
            setupApiKeyPreference()
            setupModelPreference()
            setupCustomUrlPreference()
            setupDiagnosticPreference()
            setupAccessibilityPreference()
            setupPermissionsPreference()
        }
        
        private fun setupProviderPreference() {
            findPreference<Preference>("ai_provider")?.apply {
                val currentProvider = AIConfigManager.getCurrentProvider(requireContext())
                summary = "${currentProvider.displayName} - ${currentProvider.description}"
                
                setOnPreferenceClickListener {
                    showProviderDialog()
                    true
                }
            }
        }
        
        private fun showProviderDialog() {
            val providers = AIProvider.values()
            val names = providers.map { "${it.displayName}\n${it.description}" }.toTypedArray()
            val currentIndex = providers.indexOf(AIConfigManager.getCurrentProvider(requireContext()))
            
            AlertDialog.Builder(requireContext())
                .setTitle("选择 AI 提供商")
                .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                    val selectedProvider = providers[which]
                    AIConfigManager.setCurrentProvider(requireContext(), selectedProvider)
                    RetrofitClient.clearCache() // 清除缓存以应用新的 Base URL
                    
                    // 更新所有相关设置的显示
                    updateAllPreferences()
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        private fun setupApiKeyPreference() {
            findPreference<EditTextPreference>("api_key")?.apply {
                updateApiKeySummary(this)
                
                setOnPreferenceClickListener {
                    // 加载当前提供商的 API Key
                    text = AIConfigManager.getApiKey(requireContext())
                    false // 返回 false 让默认的点击行为继续
                }
                
                setOnPreferenceChangeListener { _, newValue ->
                    val newKey = newValue as String
                    AIConfigManager.setApiKey(requireContext(), newKey.trim())
                    updateApiKeySummary(this)
                    true
                }
            }
        }
        
        private fun updateApiKeySummary(pref: EditTextPreference) {
            val provider = AIConfigManager.getCurrentProvider(requireContext())
            val currentKey = AIConfigManager.getApiKey(requireContext())
            pref.title = "${provider.displayName} API Key"
            pref.summary = if (currentKey.isNotEmpty()) {
                "已配置 (${currentKey.take(8)}...)"
            } else {
                "未配置 - 点击输入 API Key"
            }
        }
        
        private fun setupModelPreference() {
            findPreference<Preference>("ai_model")?.apply {
                updateModelSummary(this)
                
                setOnPreferenceClickListener {
                    showModelDialog()
                    true
                }
            }
        }
        
        private fun showModelDialog() {
            val provider = AIConfigManager.getCurrentProvider(requireContext())
            
            if (provider == AIProvider.CUSTOM) {
                // 自定义提供商，显示输入框
                val editText = android.widget.EditText(requireContext()).apply {
                    hint = "输入模型名称"
                    setText(AIConfigManager.getModel(requireContext()))
                }
                
                AlertDialog.Builder(requireContext())
                    .setTitle("输入模型名称")
                    .setView(editText)
                    .setPositiveButton("确定") { _, _ ->
                        AIConfigManager.setModel(requireContext(), editText.text.toString().trim())
                        updateModelSummary(findPreference("ai_model")!!)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                // 预定义的模型列表
                val models = provider.models.toTypedArray()
                val currentModel = AIConfigManager.getModel(requireContext())
                val currentIndex = models.indexOf(currentModel).takeIf { it >= 0 } ?: 0
                
                AlertDialog.Builder(requireContext())
                    .setTitle("选择模型")
                    .setSingleChoiceItems(models, currentIndex) { dialog, which ->
                        AIConfigManager.setModel(requireContext(), models[which])
                        updateModelSummary(findPreference("ai_model")!!)
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        
        private fun updateModelSummary(pref: Preference) {
            val provider = AIConfigManager.getCurrentProvider(requireContext())
            val currentModel = AIConfigManager.getModel(requireContext())
            pref.summary = currentModel.ifEmpty { provider.defaultModel }
        }
        
        private fun setupCustomUrlPreference() {
            findPreference<EditTextPreference>("custom_base_url")?.apply {
                val provider = AIConfigManager.getCurrentProvider(requireContext())
                isVisible = (provider == AIProvider.CUSTOM)
                
                val currentUrl = AIConfigManager.getCustomBaseUrl(requireContext())
                text = currentUrl
                summary = currentUrl.ifEmpty { "未配置 - 输入自定义 API 地址" }
                
                setOnPreferenceChangeListener { _, newValue ->
                    val newUrl = newValue as String
                    AIConfigManager.setCustomBaseUrl(requireContext(), newUrl.trim())
                    RetrofitClient.clearCache()
                    summary = newUrl.ifEmpty { "未配置 - 输入自定义 API 地址" }
                    true
                }
            }
        }
        
        private fun setupDiagnosticPreference() {
            findPreference<Preference>("api_diagnostic")?.setOnPreferenceClickListener {
                val intent = Intent(requireContext(), DiagnosticActivity::class.java)
                startActivity(intent)
                true
            }
        }
        
        private fun setupAccessibilityPreference() {
            findPreference<Preference>("accessibility")?.apply {
                summary = if (AIAccessibilityService.isServiceEnabled()) {
                    "已启用"
                } else {
                    "未启用 - 点击开启高级设备控制"
                }
                
                setOnPreferenceClickListener {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    true
                }
            }
        }
        
        private fun setupPermissionsPreference() {
            findPreference<Preference>("permissions")?.setOnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
                true
            }
        }
        
        private fun updateAllPreferences() {
            findPreference<Preference>("ai_provider")?.let {
                val provider = AIConfigManager.getCurrentProvider(requireContext())
                it.summary = "${provider.displayName} - ${provider.description}"
            }
            
            findPreference<EditTextPreference>("api_key")?.let {
                updateApiKeySummary(it)
            }
            
            findPreference<Preference>("ai_model")?.let {
                updateModelSummary(it)
            }
            
            findPreference<EditTextPreference>("custom_base_url")?.let {
                val provider = AIConfigManager.getCurrentProvider(requireContext())
                it.isVisible = (provider == AIProvider.CUSTOM)
            }
        }
        
        override fun onResume() {
            super.onResume()
            updateAllPreferences()
            
            // 更新无障碍服务状态
            findPreference<Preference>("accessibility")?.summary = 
                if (AIAccessibilityService.isServiceEnabled()) "已启用" else "未启用 - 点击开启高级设备控制"
        }
    }
}
