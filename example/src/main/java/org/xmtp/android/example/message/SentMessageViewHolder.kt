package org.xmtp.android.example.message

import android.graphics.BitmapFactory
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.xmtp.android.example.conversation.ConversationDetailViewModel
import org.xmtp.android.example.databinding.ListItemMessageSentBinding
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.DeletedMessage
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.libxmtp.Reply
import java.text.SimpleDateFormat
import java.util.Locale

class SentMessageViewHolder(
    private val binding: ListItemMessageSentBinding,
    private val clickListener: MessageClickListener? = null,
) : RecyclerView.ViewHolder(binding.root) {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun bind(item: ConversationDetailViewModel.MessageListItem.SentMessage) {
        binding.messageContainer.setOnLongClickListener {
            clickListener?.onMessageLongClick(item.message)
            true
        }

        val content = item.message.content<Any>()

        // Reset attachment visibility
        binding.attachmentContainer.visibility = View.GONE
        binding.fileAttachmentContainer.visibility = View.GONE

        when (content) {
            is String -> {
                binding.messageBody.text = content
                binding.messageBody.visibility = View.VISIBLE
            }
            is Reaction -> {
                binding.messageBody.text = "${content.content} (reaction)"
                binding.messageBody.visibility = View.VISIBLE
            }
            is Attachment -> {
                val isImage = content.mimeType.startsWith("image/")
                val isGif = content.mimeType == "image/gif"
                if (isImage) {
                    // Display image/GIF attachment using Glide
                    binding.attachmentContainer.visibility = View.VISIBLE
                    binding.attachmentLoading.visibility = View.GONE
                    try {
                        val bytes = content.data.toByteArray()
                        if (isGif) {
                            // Use Glide for GIF playback
                            Glide.with(binding.attachmentImage.context)
                                .asGif()
                                .load(bytes)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .into(binding.attachmentImage)
                        } else {
                            // Use Glide for regular images too for consistency
                            Glide.with(binding.attachmentImage.context)
                                .load(bytes)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .into(binding.attachmentImage)
                        }
                    } catch (e: Exception) {
                        binding.attachmentImage.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                    binding.messageBody.visibility = View.GONE
                } else {
                    // Display file attachment
                    binding.fileAttachmentContainer.visibility = View.VISIBLE
                    binding.fileName.text = content.filename
                    binding.fileSize.text = formatFileSize(content.data.size())
                    binding.messageBody.visibility = View.GONE
                }
            }
            is Reply -> {
                val replyContent = content.content
                val replyText = if (replyContent is String) replyContent else "Reply"
                binding.messageBody.text = replyText
                binding.messageBody.visibility = View.VISIBLE

                // Show reply container with original message info
                binding.replyContainer.visibility = View.VISIBLE

                // Reset reply image visibility
                binding.replyImageContainer.visibility = View.GONE

                // Get original message info
                val originalMessage = content.inReplyTo
                if (originalMessage != null) {
                    val originalSender = originalMessage.senderInboxId.take(8) + "..."
                    val originalContent = originalMessage.content<Any>()
                    val originalText =
                        when (originalContent) {
                            is String -> originalContent
                            is DeletedMessage -> "Deleted message"
                            is Attachment -> {
                                // Check if it's an image and show thumbnail
                                if (originalContent.mimeType.startsWith("image/")) {
                                    try {
                                        val bytes = originalContent.data.toByteArray()
                                        val isGif = originalContent.mimeType == "image/gif"
                                        if (isGif) {
                                            Glide.with(binding.replyImage.context)
                                                .asGif()
                                                .load(bytes)
                                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                .into(binding.replyImage)
                                        } else {
                                            Glide.with(binding.replyImage.context)
                                                .load(bytes)
                                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                .into(binding.replyImage)
                                        }
                                        binding.replyImageContainer.visibility = View.VISIBLE
                                    } catch (e: Exception) {
                                        // Ignore image decode errors
                                    }
                                    if (originalContent.mimeType == "image/gif") "GIF" else "Photo"
                                } else {
                                    "Attachment"
                                }
                            }
                            is Reply -> {
                                // For replies, show the actual reply content
                                when (val nestedReplyContent = originalContent.content) {
                                    is String -> nestedReplyContent
                                    else -> "Message"
                                }
                            }
                            else -> {
                                // For unknown content types, just show "Message" to avoid "Replied with..." fallback text
                                "Message"
                            }
                        }
                    binding.replyAuthor.text = originalSender
                    binding.replyText.text = originalText

                    // Disable click if message was deleted
                    if (originalContent is DeletedMessage) {
                        binding.replyContainer.setOnClickListener(null)
                    } else {
                        // Set click listener to jump to original message
                        binding.replyContainer.setOnClickListener {
                            clickListener?.onReplyClick(content.referenceId)
                        }
                    }
                } else {
                    binding.replyAuthor.text = "Reply"
                    binding.replyText.text = "Original message"
                    binding.replyContainer.setOnClickListener(null)
                }
            }
            else -> {
                binding.messageBody.text = item.message.fallbackText ?: "Unknown content"
                binding.messageBody.visibility = View.VISIBLE
            }
        }

        // Set time
        binding.messageTime.text = timeFormat.format(item.message.sentAt)

        // Show reactions if present
        if (item.message.hasReactions) {
            val reactions = item.message.reactions
            // Sort reactions by timestamp to ensure correct chronological processing
            val sortedReactions = reactions.sortedBy { it.sentAtNs }
            // Aggregate reactions: track adds and removes per sender+emoji
            val activeReactions = mutableMapOf<String, MutableSet<String>>() // emoji -> set of senderInboxIds
            for (reactionMsg in sortedReactions) {
                val reaction = reactionMsg.content<Reaction>() ?: continue
                val emoji = reaction.content
                val sender = reactionMsg.senderInboxId
                when (reaction.action) {
                    ReactionAction.Added -> {
                        activeReactions.getOrPut(emoji) { mutableSetOf() }.add(sender)
                    }
                    ReactionAction.Removed -> {
                        activeReactions[emoji]?.remove(sender)
                    }
                    else -> {}
                }
            }
            // Filter out emojis with no active reactions (use filter instead of mutable removeAll)
            val filteredReactions = activeReactions.filterValues { it.isNotEmpty() }

            if (filteredReactions.isNotEmpty()) {
                val totalCount = filteredReactions.values.sumOf { it.size }
                val displayEmojis = filteredReactions.keys.take(3).joinToString("")
                binding.messageReactions.visibility = View.VISIBLE
                binding.messageReactions.text = if (totalCount > 1) "$displayEmojis $totalCount" else displayEmojis
            } else {
                binding.messageReactions.visibility = View.GONE
            }
        } else {
            binding.messageReactions.visibility = View.GONE
        }

        // Hide reply container by default unless it's a Reply
        if (content !is Reply) {
            binding.replyContainer.visibility = View.GONE
        }
    }

    private fun formatFileSize(bytes: Int): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
}
