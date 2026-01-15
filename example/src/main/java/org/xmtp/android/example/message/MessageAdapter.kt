package org.xmtp.android.example.message

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.xmtp.android.example.conversation.ConversationDetailViewModel
import org.xmtp.android.example.databinding.ListItemMessageReceivedBinding
import org.xmtp.android.example.databinding.ListItemMessageSentBinding
import org.xmtp.android.example.databinding.ListItemMessageSystemBinding
import org.xmtp.android.library.codecs.DeletedMessage
import org.xmtp.android.library.libxmtp.DecodedMessageV2

interface MessageClickListener {
    fun onMessageLongClick(message: DecodedMessageV2)

    fun onReplyClick(referenceMessageId: String)

    fun onReactionClick(
        messageId: String,
        emoji: String,
    )
}

class MessageAdapter(
    private val clickListener: MessageClickListener? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val listItems = mutableListOf<ConversationDetailViewModel.MessageListItem>()

    fun setData(newItems: List<ConversationDetailViewModel.MessageListItem>) {
        val diffCallback = MessageDiffCallback(listItems.toList(), newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        listItems.clear()
        listItems.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    fun addItem(item: ConversationDetailViewModel.MessageListItem) {
        listItems.add(0, item)
        notifyItemInserted(0)
    }

    private class MessageDiffCallback(
        private val oldList: List<ConversationDetailViewModel.MessageListItem>,
        private val newList: List<ConversationDetailViewModel.MessageListItem>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean = oldList[oldItemPosition].id == newList[newItemPosition].id

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            // Different item types = different content
            if (oldItem.itemType != newItem.itemType) return false

            // Compare based on item type
            return when {
                oldItem is ConversationDetailViewModel.MessageListItem.SentMessage &&
                    newItem is ConversationDetailViewModel.MessageListItem.SentMessage -> {
                    val oldContent = oldItem.message.content<Any>()
                    val newContent = newItem.message.content<Any>()
                    val oldIsDeleted = oldContent is DeletedMessage
                    val newIsDeleted = newContent is DeletedMessage
                    // Also compare reaction counts to detect reaction changes
                    val oldReactionCount = oldItem.message.reactionCount
                    val newReactionCount = newItem.message.reactionCount
                    if (oldIsDeleted != newIsDeleted) return false
                    if (oldReactionCount != newReactionCount) return false
                    oldContent == newContent
                }
                oldItem is ConversationDetailViewModel.MessageListItem.ReceivedMessage &&
                    newItem is ConversationDetailViewModel.MessageListItem.ReceivedMessage -> {
                    val oldContent = oldItem.message.content<Any>()
                    val newContent = newItem.message.content<Any>()
                    val oldIsDeleted = oldContent is DeletedMessage
                    val newIsDeleted = newContent is DeletedMessage
                    // Also compare reaction counts to detect reaction changes
                    val oldReactionCount = oldItem.message.reactionCount
                    val newReactionCount = newItem.message.reactionCount
                    if (oldIsDeleted != newIsDeleted) return false
                    if (oldReactionCount != newReactionCount) return false
                    oldContent == newContent
                }
                oldItem is ConversationDetailViewModel.MessageListItem.SystemMessage &&
                    newItem is ConversationDetailViewModel.MessageListItem.SystemMessage -> {
                    oldItem.text == newItem.text
                }
                else -> oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ConversationDetailViewModel.MessageListItem.ITEM_TYPE_SENT -> {
                val binding = ListItemMessageSentBinding.inflate(inflater, parent, false)
                SentMessageViewHolder(binding, clickListener)
            }
            ConversationDetailViewModel.MessageListItem.ITEM_TYPE_RECEIVED -> {
                val binding = ListItemMessageReceivedBinding.inflate(inflater, parent, false)
                ReceivedMessageViewHolder(binding, clickListener)
            }
            ConversationDetailViewModel.MessageListItem.ITEM_TYPE_SYSTEM -> {
                val binding = ListItemMessageSystemBinding.inflate(inflater, parent, false)
                SystemMessageViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unsupported view type $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        val item = listItems[position]
        when (holder) {
            is SentMessageViewHolder -> {
                holder.bind(item as ConversationDetailViewModel.MessageListItem.SentMessage)
            }
            is ReceivedMessageViewHolder -> {
                holder.bind(item as ConversationDetailViewModel.MessageListItem.ReceivedMessage)
            }
            is SystemMessageViewHolder -> {
                holder.bind(item as ConversationDetailViewModel.MessageListItem.SystemMessage)
            }
            else -> throw IllegalArgumentException("Unsupported view holder")
        }
    }

    override fun getItemViewType(position: Int) = listItems[position].itemType

    override fun getItemCount() = listItems.size
}
