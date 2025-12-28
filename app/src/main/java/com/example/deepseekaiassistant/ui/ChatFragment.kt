package com.example.deepseekaiassistant.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.deepseekaiassistant.*
import com.example.deepseekaiassistant.capability.CommandParser
import com.example.deepseekaiassistant.databinding.FragmentChatBinding
import com.example.deepseekaiassistant.local.LocalAIManager
import com.permissionx.guolindev.PermissionX
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

/**
 * AI 聊天页面（第一页）
 */
class ChatFragment : Fragment() {
    
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessageList = mutableListOf<ChatMessage>()
    private lateinit var commandParser: CommandParser
    private lateinit var localAIManager: LocalAIManager
    
    // 多轮对话历史
    private val conversationHistory = mutableListOf<Message>()
    
    // 模式：本地/联网
    private var isOnlineMode = false  // 默认本地模式
    
    // 语音识别
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        commandParser = CommandParser(requireContext())
        localAIManager = LocalAIManager.getInstance(requireContext())
        
        initChatList()
        initSpeechRecognizer()
        setupClickListeners()
        setupModeButtons()
    }
    
    override fun onResume() {
        super.onResume()
        // 刷新模式状态显示
        updateModeButtons()
    }
    
    private fun initChatList() {
        chatAdapter = ChatAdapter(chatMessageList)
        binding.rvChatList.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
        }
        
        // 欢迎消息
        val welcomeMessage = ChatMessage(getString(R.string.welcome_message), MessageSender.AI)
        chatAdapter.addMessage(welcomeMessage)
    }
    
    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }
                override fun onError(error: Int) {
                    isListening = false
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        binding.etInputMessage.setText(matches[0])
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    
    private fun setupClickListeners() {
        // 发送按钮
        binding.btnSend.setOnClickListener {
            sendMessage()
        }
        
        // 语音输入按钮
        binding.btnVoice.setOnClickListener {
            toggleVoiceInput()
        }
        
        // 附件按钮（显示设备能力帮助）
        binding.btnAttach.setOnClickListener {
            showCapabilitiesHelp()
        }
        
        // 新建对话
        binding.btnNewChat.setOnClickListener {
            newChat()
        }
        
        // 设置按钮
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }
    
    private fun setupModeButtons() {
        // 默认本地模式
        updateModeButtons()
        
        // 本地 AI 按钮
        binding.btnLocalAI.setOnClickListener {
            isOnlineMode = false
            updateModeButtons()
            Toast.makeText(requireContext(), "已切换到本地 AI 模式", Toast.LENGTH_SHORT).show()
        }
        
        // 联网按钮
        binding.btnOnline.setOnClickListener {
            isOnlineMode = true
            updateModeButtons()
            Toast.makeText(requireContext(), "已切换到联网模式", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateModeButtons() {
        if (isOnlineMode) {
            binding.btnOnline.setBackgroundColor(0xFF2196F3.toInt())
            binding.btnOnline.setTextColor(0xFFFFFFFF.toInt())
            binding.btnLocalAI.setBackgroundColor(0xFFE0E0E0.toInt())
            binding.btnLocalAI.setTextColor(0xFF333333.toInt())
            binding.tvModeIndicator.text = "🌐 联网模式"
        } else {
            binding.btnLocalAI.setBackgroundColor(0xFF4CAF50.toInt())
            binding.btnLocalAI.setTextColor(0xFFFFFFFF.toInt())
            binding.btnOnline.setBackgroundColor(0xFFE0E0E0.toInt())
            binding.btnOnline.setTextColor(0xFF333333.toInt())
            
            // 显示本地 AI 详细状态
            val isRealInference = localAIManager.isRealInferenceSupported()
            val isModelReady = localAIManager.isReady()
            binding.tvModeIndicator.text = when {
                isRealInference && isModelReady -> "🧠 本地 AI (已加载)"
                isRealInference -> "🧠 本地 AI (未加载)"
                else -> "📱 本地 AI (模拟)"
            }
        }
    }
    
    private fun sendMessage() {
        val inputContent = binding.etInputMessage.text.toString().trim()
        if (inputContent.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_empty_message, Toast.LENGTH_SHORT).show()
            return
        }
        
        // 添加用户消息
        val userMessage = ChatMessage(inputContent, MessageSender.USER)
        chatAdapter.addMessage(userMessage)
        binding.etInputMessage.setText("")
        scrollToBottom()
        
        // 先尝试解析设备指令
        val commandResult = commandParser.parseAndExecute(inputContent)
        if (commandResult != null) {
            // 是设备指令，直接显示执行结果
            val resultMessage = ChatMessage(commandResult.response, MessageSender.AI)
            chatAdapter.addMessage(resultMessage)
            scrollToBottom()
            return
        }
        
        // 根据模式选择 AI
        if (isOnlineMode) {
            // 联网模式
            if (!AIConfigManager.hasApiKey(requireContext())) {
                val provider = AIConfigManager.getCurrentProvider(requireContext())
                val noKeyMessage = ChatMessage("⚠️ 请先配置 API Key\n\n当前选择的提供商：${provider.displayName}\n\n点击右上角设置按钮，选择 AI 提供商并输入对应的 API Key", MessageSender.AI)
                chatAdapter.addMessage(noKeyMessage)
                scrollToBottom()
                return
            }
            
            if (isNetworkAvailable()) {
                sendOnlineRequest(inputContent)
            } else {
                val offlineMessage = ChatMessage(getString(R.string.offline_message), MessageSender.AI)
                chatAdapter.addMessage(offlineMessage)
                scrollToBottom()
            }
        } else {
            // 本地 AI 模式
            sendLocalRequest(inputContent)
        }
    }
    
    private fun sendLocalRequest(userInput: String) {
        showLoading(true)
        
        DiagnosticManager.info("LocalAI", "本地推理请求", userInput.take(50))
        
        // 创建 AI 消息占位符
        val aiMessage = ChatMessage("", MessageSender.AI)
        chatAdapter.addMessage(aiMessage)
        val messageIndex = chatMessageList.size - 1
        
        localAIManager.generateResponse(
            prompt = userInput,
            onToken = { token ->
                // 流式更新消息
                activity?.runOnUiThread {
                    chatMessageList[messageIndex].content += token
                    chatAdapter.notifyItemChanged(messageIndex)
                }
            },
            onComplete = { response ->
                activity?.runOnUiThread {
                    showLoading(false)
                    scrollToBottom()
                    DiagnosticManager.success("LocalAI", "本地推理完成")
                }
            },
            onError = { error ->
                activity?.runOnUiThread {
                    showLoading(false)
                    chatMessageList[messageIndex].content = "⚠️ 本地 AI 错误: $error"
                    chatAdapter.notifyItemChanged(messageIndex)
                    scrollToBottom()
                    DiagnosticManager.error("LocalAI", "本地推理失败", error)
                }
            }
        )
    }
    
    private fun sendOnlineRequest(userInput: String) {
        showLoading(true)
        
        // 添加到对话历史
        conversationHistory.add(Message("user", userInput))
        
        // 限制对话历史长度
        if (conversationHistory.size > 20) {
            conversationHistory.removeAt(0)
            conversationHistory.removeAt(0)
        }
        
        // 获取当前 AI 配置
        val aiConfig = AIConfigManager.getCurrentConfig(requireContext())
        val providerName = aiConfig.provider.displayName
        
        DiagnosticManager.info("API", "发送请求到 $providerName", "Model: ${aiConfig.model}")
        
        // 添加系统提示
        val systemPrompt = Message("system", """
            你是 $providerName AI 助手，运行在 Android 设备上。你可以：
            1. 回答用户的问题
            2. 帮助用户执行设备操作（用户直接输入的设备指令会被自动处理）
            
            请用中文回复，回复要简洁友好。支持 Markdown 格式。
        """.trimIndent())
        
        val messages = listOf(systemPrompt) + conversationHistory
        
        val request = DeepSeekRequest(
            model = aiConfig.model,
            messages = messages
        )
        
        val apiService = RetrofitClient.getApiService(requireContext())
        val call = apiService.sendChatRequest("Bearer ${aiConfig.apiKey}", request)
        
        val startTime = System.currentTimeMillis()
        
        call.enqueue(object : Callback<DeepSeekResponse> {
            override fun onResponse(call: Call<DeepSeekResponse>, response: Response<DeepSeekResponse>) {
                showLoading(false)
                val responseTime = System.currentTimeMillis() - startTime
                
                DiagnosticManager.info("API", "响应状态码: ${response.code()}", "耗时: ${responseTime}ms")
                
                if (response.isSuccessful) {
                    val aiContent = response.body()?.choices?.getOrNull(0)?.message?.content ?: "暂无回复"
                    
                    DiagnosticManager.success("API", "请求成功")
                    
                    conversationHistory.add(Message("assistant", aiContent))
                    
                    val aiMessage = ChatMessage(aiContent, MessageSender.AI)
                    chatAdapter.addMessage(aiMessage)
                    scrollToBottom()
                } else {
                    val errorBody = response.errorBody()?.string()
                    DiagnosticManager.error("API", "请求失败: HTTP ${response.code()}", errorBody?.take(500))
                    
                    val errorDetail = when (response.code()) {
                        401 -> "⚠️ API Key 无效"
                        402 -> "⚠️ 账户余额不足"
                        429 -> "⚠️ 请求频率超限"
                        else -> "⚠️ 请求失败 (HTTP ${response.code()})"
                    }
                    
                    val aiErrorMsg = ChatMessage("$errorDetail\n\n🔧 点击设置 → API 诊断查看详情", MessageSender.AI)
                    chatAdapter.addMessage(aiErrorMsg)
                    scrollToBottom()
                }
            }
            
            override fun onFailure(call: Call<DeepSeekResponse>, t: Throwable) {
                showLoading(false)
                DiagnosticManager.error("API", "网络请求失败", t.message)
                
                val aiErrorMsg = ChatMessage("⚠️ 网络错误: ${t.message}\n\n🔧 点击设置 → API 诊断查看详情", MessageSender.AI)
                chatAdapter.addMessage(aiErrorMsg)
                scrollToBottom()
            }
        })
    }
    
    private fun toggleVoiceInput() {
        if (!isListening) {
            PermissionX.init(this)
                .permissions(Manifest.permission.RECORD_AUDIO)
                .request { allGranted, _, _ ->
                    if (allGranted) {
                        startListening()
                    } else {
                        Toast.makeText(requireContext(), "需要麦克风权限进行语音输入", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            stopListening()
        }
    }
    
    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
        isListening = true
        Toast.makeText(requireContext(), "请说话...", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }
    
    private fun showCapabilitiesHelp() {
        val helpMessage = commandParser.getCapabilitiesHelp()
        val aiMessage = ChatMessage(helpMessage, MessageSender.AI)
        chatAdapter.addMessage(aiMessage)
        scrollToBottom()
    }
    
    private fun newChat() {
        chatMessageList.clear()
        conversationHistory.clear()
        chatAdapter.notifyDataSetChanged()
        
        val welcomeMessage = ChatMessage(getString(R.string.welcome_message), MessageSender.AI)
        chatAdapter.addMessage(welcomeMessage)
        
        Toast.makeText(requireContext(), "已新建对话", Toast.LENGTH_SHORT).show()
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.isVisible = show
        binding.btnSend.isEnabled = !show
    }
    
    private fun scrollToBottom() {
        binding.rvChatList.post {
            if (chatMessageList.isNotEmpty()) {
                binding.rvChatList.smoothScrollToPosition(chatMessageList.size - 1)
            }
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        _binding = null
    }
    
    companion object {
        fun newInstance() = ChatFragment()
    }
}
