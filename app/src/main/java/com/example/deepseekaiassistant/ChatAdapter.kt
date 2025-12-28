package com.example.deepseekaiassistant

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.deepseekaiassistant.databinding.ItemChatUserBinding
import com.example.deepseekaiassistant.databinding.ItemChatAiBinding

class ChatAdapter(private val messageList: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // 视图类型：用户消息、AI消息
    private val VIEW_TYPE_USER = 1
    private val VIEW_TYPE_AI = 2

    // 用户消息ViewHolder
    inner class UserViewHolder(val binding: ItemChatUserBinding) :
        RecyclerView.ViewHolder(binding.root)

    // AI消息ViewHolder
    inner class AiViewHolder(val binding: ItemChatAiBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return when (messageList[position].sender) {
            MessageSender.USER -> VIEW_TYPE_USER
            MessageSender.AI -> VIEW_TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val binding = ItemChatUserBinding.inflate(inflater, parent, false)
            UserViewHolder(binding)
        } else {
            val binding = ItemChatAiBinding.inflate(inflater, parent, false)
            AiViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messageList[position]
        when (holder.itemViewType) {
            VIEW_TYPE_USER -> {
                val userHolder = holder as UserViewHolder
                userHolder.binding.tvUserMessage.text = message.content
            }
            VIEW_TYPE_AI -> {
                val aiHolder = holder as AiViewHolder
                aiHolder.binding.tvAiMessage.text = message.content
            }
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    // 添加消息并刷新列表
    fun addMessage(chatMessage: ChatMessage) {
        messageList.add(chatMessage)
        notifyItemInserted(messageList.size - 1)
    }
}