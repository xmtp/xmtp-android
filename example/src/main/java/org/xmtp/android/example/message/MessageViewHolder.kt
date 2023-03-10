package org.xmtp.android.example.message

import android.graphics.Color
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.R
import org.xmtp.android.example.conversation.ConversationDetailViewModel
import org.xmtp.android.example.databinding.ListItemMessageBinding
import org.xmtp.android.example.extension.margins

class MessageViewHolder(
    private val binding: ListItemMessageBinding
) : RecyclerView.ViewHolder(binding.root) {

    private val marginLarge = binding.root.resources.getDimensionPixelSize(R.dimen.message_margin)
    private val marginSmall = binding.root.resources.getDimensionPixelSize(R.dimen.padding)
    private val backgroundMe = Color.LTGRAY
    private val backgroundPeer =
        binding.root.resources.getColor(R.color.teal_700, binding.root.context.theme)

    fun bind(item: ConversationDetailViewModel.MessageListItem.Message) {
        val isFromMe = ClientManager.client.address == item.message.senderAddress
        binding.spacerStart.isVisible = isFromMe
        binding.spacerEnd.isVisible = !isFromMe
        if (isFromMe) {
            binding.messageRow.margins(left = marginLarge, right = marginSmall)
            binding.messageContainer.setCardBackgroundColor(backgroundMe)
            binding.messageBody.setTextColor(Color.BLACK)
        } else {
            binding.messageRow.margins(right = marginLarge, left = marginSmall)
            binding.messageContainer.setCardBackgroundColor(backgroundPeer)
            binding.messageBody.setTextColor(Color.WHITE)
        }
        binding.messageBody.text = item.message.body
    }
}
