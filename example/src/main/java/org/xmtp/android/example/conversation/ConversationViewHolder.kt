package org.xmtp.android.example.conversation

import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.MainViewModel
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ListItemConversationBinding
import org.xmtp.android.example.extension.truncatedAddress
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.codecs.DeletedMessage
import org.xmtp.proto.mls.message.contents.TranscriptMessages.GroupUpdated
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ConversationViewHolder(
    private val binding: ListItemConversationBinding,
    clickListener: ConversationsClickListener,
) : RecyclerView.ViewHolder(binding.root) {
    private var conversation: Conversation? = null

    // Avatar colors based on address hash
    private val avatarColors =
        listOf(
            Color.parseColor("#FC4F37"), // XMTP Red
            Color.parseColor("#5856D6"), // Purple
            Color.parseColor("#34C759"), // Green
            Color.parseColor("#FF9500"), // Orange
            Color.parseColor("#007AFF"), // Blue
            Color.parseColor("#AF52DE"), // Magenta
            Color.parseColor("#00C7BE"), // Teal
            Color.parseColor("#FF2D55"), // Pink
        )

    init {
        binding.root.setOnClickListener {
            conversation?.let {
                clickListener.onConversationClick(it)
            }
        }
    }

    fun bind(item: MainViewModel.MainListItem.ConversationItem) {
        conversation = item.conversation

        // Use the display name from the item (group name or peer address)
        val displayText =
            when (item.conversation.type) {
                Conversation.Type.GROUP -> item.displayName
                Conversation.Type.DM -> item.displayName.truncatedAddress()
            }
        binding.peerAddress.text = displayText

        // Set avatar text based on conversation type
        val avatarChars =
            when (item.conversation.type) {
                Conversation.Type.GROUP -> {
                    // For groups, use first 2 chars of group name (or ID if no name)
                    item.displayName
                        .removePrefix("0x")
                        .take(2)
                        .uppercase()
                }
                Conversation.Type.DM -> {
                    // For DMs, use first 2 chars of peer address
                    (item.peerAddress ?: item.conversation.id)
                        .removePrefix("0x")
                        .take(2)
                        .uppercase()
                }
            }
        binding.avatarText.text = avatarChars

        // Set avatar color based on conversation ID hash
        val colorIndex = abs(item.conversation.id.hashCode()) % avatarColors.size
        binding.avatarCard.setCardBackgroundColor(avatarColors[colorIndex])

        // Set message time
        item.mostRecentMessage?.let { message ->
            binding.messageTime.text = formatMessageTime(message.sentAtNs / 1_000_000)
        } ?: run {
            binding.messageTime.text = ""
        }

        // Set message preview
        val messageBody: String =
            when (val content = item.mostRecentMessage?.content<Any>()) {
                is String -> content
                is GroupUpdated -> {
                    val added = content.addedInboxesList?.size ?: 0
                    val removed = content.removedInboxesList?.size ?: 0
                    when {
                        added > 0 && removed > 0 -> "$added added, $removed removed"
                        added > 0 -> "$added member${if (added > 1) "s" else ""} added"
                        removed > 0 -> "$removed member${if (removed > 1) "s" else ""} removed"
                        else -> "Group updated"
                    }
                }
                is DeletedMessage -> "Message deleted"
                else -> item.mostRecentMessage?.body ?: ""
            }

        val isMe = item.mostRecentMessage?.senderInboxId == ClientManager.client.inboxId
        if (messageBody.isNotBlank()) {
            binding.messageBody.text =
                if (isMe) {
                    "You: $messageBody"
                } else {
                    messageBody
                }
            binding.messageBody.setTextColor(
                ContextCompat.getColor(binding.root.context, R.color.text_secondary),
            )
        } else {
            binding.messageBody.text = binding.root.resources.getString(R.string.empty_message)
            binding.messageBody.setTextColor(
                ContextCompat.getColor(binding.root.context, R.color.text_tertiary),
            )
        }
    }

    private fun formatMessageTime(timestampMs: Long): String {
        val messageDate = Date(timestampMs)
        val now = Calendar.getInstance()
        val messageCalendar = Calendar.getInstance().apply { time = messageDate }

        return when {
            // Today - show time
            now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR) -> {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageDate)
            }
            // Yesterday
            now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) == 1 -> {
                "Yesterday"
            }
            // Within the last week - show day name
            now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - messageCalendar.get(Calendar.DAY_OF_YEAR) < 7 -> {
                SimpleDateFormat("EEE", Locale.getDefault()).format(messageDate)
            }
            // Older - show date
            else -> {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(messageDate)
            }
        }
    }
}
