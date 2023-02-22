package org.xmtp.android.example.conversation

import androidx.recyclerview.widget.RecyclerView
import org.xmtp.android.example.MainViewModel
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ListItemConversationFooterBinding

class ConversationFooterViewHolder(private val binding: ListItemConversationFooterBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: MainViewModel.MainListItem.Footer) {
        binding.footer.text = binding.root.resources.getString(
            R.string.conversation_footer,
            item.address,
            item.environment
        )
    }
}
