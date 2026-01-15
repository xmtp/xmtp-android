package org.xmtp.android.example.message

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.xmtp.android.example.conversation.ConversationDetailViewModel
import org.xmtp.android.example.databinding.ListItemMessageSystemBinding
import java.text.SimpleDateFormat
import java.util.Locale

class SystemMessageViewHolder(
    private val binding: ListItemMessageSystemBinding,
) : RecyclerView.ViewHolder(binding.root) {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun bind(item: ConversationDetailViewModel.MessageListItem.SystemMessage) {
        binding.systemMessageText.text = item.text

        // Show time for system messages
        binding.systemMessageTime.visibility = View.VISIBLE
        binding.systemMessageTime.text = timeFormat.format(item.message.sentAt)
    }
}
