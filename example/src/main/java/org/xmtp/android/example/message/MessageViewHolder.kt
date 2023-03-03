package org.xmtp.android.example.message

import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.R
import org.xmtp.android.example.conversation.ConversationDetailViewModel
import org.xmtp.android.example.databinding.ListItemMessageBinding
import org.xmtp.android.example.extension.margins

class MessageViewHolder(
    private val binding: ListItemMessageBinding
) : RecyclerView.ViewHolder(binding.root) {

    private val margin = binding.root.resources.getDimensionPixelSize(R.dimen.message_margin)
    private val backgroundMe = Color.GRAY
    private val backgroundPeer =
        binding.root.resources.getColor(R.color.purple_500, binding.root.context.theme)

    fun bind(item: ConversationDetailViewModel.MessageListItem.Message) {
        val isFromMe = ClientManager.client.address == item.message.senderAddress
        if (isFromMe) {
            binding.messageBody.margins(left = margin)
            binding.messageBody.setBackgroundColor(backgroundMe)
        } else {
            binding.messageBody.margins(right = margin)
            binding.messageBody.setBackgroundColor(backgroundPeer)
        }
        binding.messageBody.text = item.message.body
    }
}
