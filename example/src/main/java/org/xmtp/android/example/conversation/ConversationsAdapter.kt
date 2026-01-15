package org.xmtp.android.example.conversation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.xmtp.android.example.MainViewModel
import org.xmtp.android.example.databinding.ListItemConversationBinding

class ConversationsAdapter(
    private val clickListener: ConversationsClickListener,
) : RecyclerView.Adapter<ConversationViewHolder>() {
    init {
        setHasStableIds(true)
    }

    private val listItems = mutableListOf<MainViewModel.MainListItem.ConversationItem>()

    fun setData(newItems: List<MainViewModel.MainListItem>) {
        listItems.clear()
        listItems.addAll(newItems.filterIsInstance<MainViewModel.MainListItem.ConversationItem>())
        notifyDataSetChanged()
    }

    fun addItem(item: MainViewModel.MainListItem) {
        if (item !is MainViewModel.MainListItem.ConversationItem) return
        // Check if item already exists and update it instead of adding duplicate
        val existingIndex = listItems.indexOfFirst { it.id == item.id }
        if (existingIndex >= 0) {
            listItems.removeAt(existingIndex)
        }
        listItems.add(0, item)
        notifyDataSetChanged()
    }

    fun updateConversationMessage(
        topic: String,
        message: org.xmtp.android.library.libxmtp.DecodedMessage,
    ) {
        val index = listItems.indexOfFirst { it.id == topic }
        if (index >= 0) {
            val existingItem = listItems[index]
            val updatedItem = existingItem.copy(mostRecentMessage = message)
            listItems.removeAt(index)
            listItems.add(0, updatedItem) // Move to top
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ConversationViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemConversationBinding.inflate(inflater, parent, false)
        return ConversationViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(
        holder: ConversationViewHolder,
        position: Int,
    ) {
        holder.bind(listItems[position])
    }

    override fun getItemCount() = listItems.size

    override fun getItemId(position: Int) = listItems[position].id.hashCode().toLong()
}
