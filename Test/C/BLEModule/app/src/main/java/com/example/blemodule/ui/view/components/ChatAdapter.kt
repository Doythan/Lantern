package com.example.blemodule.ui.view.components

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.blemodule.R
import com.example.blemodule.databinding.ItemChatMessageBinding
import com.example.blemodule.ui.viewmodel.ChatMessage

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    inner class ChatViewHolder(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatMessage) {
            binding.tvMessage.text = item.message
            binding.tvSender.text = item.senderId

            // 메시지 정렬 및 스타일
            val params = binding.root.layoutParams as ViewGroup.MarginLayoutParams
            if (item.isMine) {
                // 내가 보낸 메시지: 오른쪽 정렬
                binding.root.setBackgroundResource(R.drawable.bg_message_mine)
                params.marginStart = 100
                params.marginEnd = 0
            } else {
                // 상대 메시지: 왼쪽 정렬
                binding.root.setBackgroundResource(R.drawable.bg_message_other)
                params.marginStart = 0
                params.marginEnd = 100
            }
            binding.root.layoutParams = params
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
        oldItem.timestamp == newItem.timestamp

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
        oldItem == newItem
}
