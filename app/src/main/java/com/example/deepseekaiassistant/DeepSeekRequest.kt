package com.example.deepseekaiassistant

data class DeepSeekRequest(
    val model: String, // 模型名称
    val messages: List<Message>, // 消息列表
    val temperature: Float = 0.7f, // 随机性（0-1，值越大越随机）
    val max_tokens: Int = 2048 // 最大返回令牌数
)

data class Message(
    val role: String, // 角色：user（用户）、assistant（AI）
    val content: String // 消息内容
)