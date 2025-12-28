package com.example.deepseekaiassistant

// 消息发送方类型
enum class MessageSender {
    USER, // 用户
    AI    // AI助手
}

data class ChatMessage(
    var content: String, // 消息内容（可变，用于流式更新）
    val sender: MessageSender, // 发送方
    val time: Long = System.currentTimeMillis() // 消息发送时间
)