package org.xmtp.android.example.conversation

import androidx.annotation.UiThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.kotlin.toByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.extension.flowWhileShared
import org.xmtp.android.example.extension.stateFlow
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.SendOptions
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.ContentTypeAttachment
import org.xmtp.android.library.codecs.ContentTypeReaction
import org.xmtp.android.library.codecs.ContentTypeReply
import org.xmtp.android.library.codecs.ContentTypeText
import org.xmtp.android.library.codecs.DeletedBy
import org.xmtp.android.library.codecs.DeletedMessage
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.codecs.ReactionSchema
import org.xmtp.android.library.codecs.Reply
import org.xmtp.android.library.codecs.ReplyCodec
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.libxmtp.DecodedMessageV2
import org.xmtp.proto.mls.message.contents.TranscriptMessages.GroupUpdated
import org.xmtp.android.library.libxmtp.Reply as EnrichedReply

class ConversationDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val conversationTopicFlow =
        savedStateHandle.getStateFlow<String?>(
            ConversationDetailActivity.EXTRA_CONVERSATION_TOPIC,
            null,
        )

    private val conversationTopic = conversationTopicFlow.value

    fun setConversationTopic(conversationTopic: String?) {
        savedStateHandle[ConversationDetailActivity.EXTRA_CONVERSATION_TOPIC] = conversationTopic
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading(null))
    val uiState: StateFlow<UiState> = _uiState

    private val _replyToMessage = MutableStateFlow<DecodedMessageV2?>(null)
    val replyToMessage: StateFlow<DecodedMessageV2?> = _replyToMessage

    private val _editingMessage = MutableStateFlow<DecodedMessageV2?>(null)
    val editingMessage: StateFlow<DecodedMessageV2?> = _editingMessage

    // Instance-level setting - set from Activity based on user preferences
    private val _hideDeletedMessages = MutableStateFlow(false)

    fun setHideDeletedMessages(hide: Boolean) {
        _hideDeletedMessages.value = hide
    }

    private var conversation: Conversation? = null

    fun setReplyToMessage(message: DecodedMessageV2?) {
        _replyToMessage.value = message
    }

    fun clearReply() {
        _replyToMessage.value = null
    }

    fun setEditingMessage(message: DecodedMessageV2?) {
        _editingMessage.value = message
    }

    fun clearEdit() {
        _editingMessage.value = null
    }

    @UiThread
    fun fetchMessages() {
        when (val uiState = uiState.value) {
            is UiState.Success -> _uiState.value = UiState.Loading(uiState.listItems)
            else -> _uiState.value = UiState.Loading(null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val listItems = mutableListOf<MessageListItem>()
            try {
                if (conversation == null) {
                    conversation = ClientManager.client.conversations.findConversationByTopic(conversationTopic!!)
                }
                conversation?.let { conv ->
                    // Sync conversation to get latest messages (including deletions)
                    val isDm =
                        when (conv) {
                            is Conversation.Group -> {
                                conv.group.sync()
                                false
                            }

                            is Conversation.Dm -> {
                                conv.dm.sync()
                                true
                            }
                        }
                    val shouldHideDeleted = _hideDeletedMessages.value
                    listItems.addAll(
                        conv.enrichedMessages().mapNotNull { message ->
                            message?.let { msg ->
                                val item = classifyMessage(msg, isDm) ?: return@mapNotNull null
                                // Filter out deleted messages if hideDeletedMessages is enabled
                                if (shouldHideDeleted && item is MessageListItem.SystemMessage) {
                                    val content = msg.content<Any>()
                                    if (content is DeletedMessage) {
                                        return@mapNotNull null
                                    }
                                }
                                item
                            }
                        },
                    )
                }
                _uiState.value = UiState.Success(listItems)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val streamMessages: StateFlow<StreamedMessageResult?> =
        stateFlow(viewModelScope, null) { subscriptionCount ->
            // Ensure conversation is initialized
            if (conversation == null) {
                conversation = ClientManager.client.conversations.findConversationByTopic(conversationTopic!!)
            }
            if (conversation != null) {
                val isDm = conversation is Conversation.Dm
                conversation!!
                    .streamMessages()
                    .flowWhileShared(
                        subscriptionCount,
                        SharingStarted.WhileSubscribed(1000L),
                    ).flowOn(Dispatchers.IO)
                    .distinctUntilChanged()
                    .mapLatest { message ->
                        // Check if this is a delete, reaction, or edit message - if so, signal a refresh is needed
                        val contentTypeId = message.encodedContent.type
                        val isDeleteMessage = contentTypeId?.typeId == "deleteMessage"
                        val isReactionMessage = contentTypeId?.typeId == "reaction"
                        val isEditMessage = contentTypeId?.typeId == "editMessage"

                        if (isDeleteMessage || isReactionMessage || isEditMessage) {
                            // Return a signal to refresh the message list
                            // Reactions, deletes, and edits modify existing messages, so we need a full refresh
                            StreamedMessageResult.RefreshNeeded
                        } else {
                            // Convert streamed DecodedMessage to DecodedMessageV2 using findEnrichedMessage
                            val enrichedMessage =
                                ClientManager.client.conversations.findEnrichedMessage(message.id)
                            enrichedMessage?.let {
                                classifyMessage(it, isDm)?.let { item ->
                                    StreamedMessageResult.NewMessage(item)
                                }
                            }
                        }
                    }.catch { _ ->
                        emptyFlow<StreamedMessageResult>()
                    }
            } else {
                emptyFlow()
            }
        }

    sealed class StreamedMessageResult {
        data class NewMessage(
            val item: MessageListItem,
        ) : StreamedMessageResult()

        data object RefreshNeeded : StreamedMessageResult()
    }

    @UiThread
    fun sendMessage(body: String): StateFlow<SendMessageState> {
        val flow = MutableStateFlow<SendMessageState>(SendMessageState.Loading)
        val replyTo = _replyToMessage.value
        val editMessage = _editingMessage.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (editMessage != null) {
                    // Send as edit message using native editMessage API
                    // Check if the original message was a Reply - if so, preserve the Reply structure
                    val originalContent = editMessage.content<Any>()
                    val editedContent =
                        if (originalContent is EnrichedReply) {
                            // Preserve the Reply structure with the new content
                            val updatedReply =
                                Reply(
                                    reference = originalContent.referenceId,
                                    content = body,
                                    contentType = ContentTypeText,
                                )
                            ReplyCodec().encode(updatedReply)
                        } else {
                            // Regular text message
                            TextCodec().encode(body)
                        }
                    conversation?.editMessage(editMessage.id, editedContent.toByteArray())
                    _editingMessage.value = null
                } else if (replyTo != null) {
                    // Send as reply using Reply codec
                    val replyContent =
                        Reply(
                            reference = replyTo.id,
                            content = body,
                            contentType = ContentTypeText,
                        )
                    conversation?.send(
                        content = replyContent,
                        options = SendOptions(contentType = ContentTypeReply),
                    )
                    _replyToMessage.value = null
                } else {
                    // Send as regular message
                    conversation?.send(body)
                }
                flow.value = SendMessageState.Success
            } catch (e: Exception) {
                flow.value = SendMessageState.Error(e.localizedMessage.orEmpty())
            }
        }
        return flow
    }

    @UiThread
    fun deleteMessage(messageId: String): StateFlow<DeleteMessageState> {
        val flow = MutableStateFlow<DeleteMessageState>(DeleteMessageState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                conversation?.deleteMessage(messageId)
                flow.value = DeleteMessageState.Success
            } catch (e: Exception) {
                flow.value = DeleteMessageState.Error(e.localizedMessage.orEmpty())
            }
        }
        return flow
    }

    suspend fun sendReaction(
        messageId: String,
        emoji: String,
        isRemoving: Boolean = false,
    ): ReactionState =
        withContext(Dispatchers.IO) {
            try {
                val reaction =
                    Reaction(
                        reference = messageId,
                        action = if (isRemoving) ReactionAction.Removed else ReactionAction.Added,
                        content = emoji,
                        schema = ReactionSchema.Unicode,
                    )
                conversation?.send(
                    content = reaction,
                    options = SendOptions(contentType = ContentTypeReaction),
                )
                // Sync to ensure the reaction is persisted and available for enrichedMessages
                conversation?.let {
                    when (it) {
                        is Conversation.Group -> it.group.sync()
                        is Conversation.Dm -> it.dm.sync()
                    }
                }
                ReactionState.Success
            } catch (e: Exception) {
                ReactionState.Error(e.localizedMessage.orEmpty())
            }
        }

    suspend fun sendAttachment(
        filename: String,
        mimeType: String,
        data: ByteArray,
    ): SendAttachmentState =
        withContext(Dispatchers.IO) {
            try {
                val attachment =
                    Attachment(
                        filename = filename,
                        mimeType = mimeType,
                        data = data.toByteString(),
                    )
                conversation?.send(
                    content = attachment,
                    options = SendOptions(contentType = ContentTypeAttachment),
                )
                SendAttachmentState.Success
            } catch (e: Exception) {
                SendAttachmentState.Error(e.localizedMessage.orEmpty())
            }
        }

    sealed class UiState {
        data class Loading(
            val listItems: List<MessageListItem>?,
        ) : UiState()

        data class Success(
            val listItems: List<MessageListItem>,
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    sealed class SendMessageState {
        data object Loading : SendMessageState()

        data object Success : SendMessageState()

        data class Error(
            val message: String,
        ) : SendMessageState()
    }

    sealed class DeleteMessageState {
        data object Loading : DeleteMessageState()

        data object Success : DeleteMessageState()

        data class Error(
            val message: String,
        ) : DeleteMessageState()
    }

    sealed class ReactionState {
        data object Loading : ReactionState()

        data object Success : ReactionState()

        data class Error(
            val message: String,
        ) : ReactionState()
    }

    sealed class SendAttachmentState {
        data object Loading : SendAttachmentState()

        data object Success : SendAttachmentState()

        data class Error(
            val message: String,
        ) : SendAttachmentState()
    }

    sealed class MessageListItem(
        open val id: String,
        val itemType: Int,
    ) {
        companion object {
            const val ITEM_TYPE_SENT = 1
            const val ITEM_TYPE_RECEIVED = 2
            const val ITEM_TYPE_SYSTEM = 3
        }

        data class SentMessage(
            override val id: String,
            val message: DecodedMessageV2,
        ) : MessageListItem(id, ITEM_TYPE_SENT)

        data class ReceivedMessage(
            override val id: String,
            val message: DecodedMessageV2,
        ) : MessageListItem(id, ITEM_TYPE_RECEIVED)

        data class SystemMessage(
            override val id: String,
            val message: DecodedMessageV2,
            val text: String,
        ) : MessageListItem(id, ITEM_TYPE_SYSTEM)
    }

    companion object {
        // Protocol message types that should not be displayed in the UI
        private val HIDDEN_CONTENT_TYPES = setOf("editMessage", "deleteMessage", "reaction")

        fun classifyMessage(
            message: DecodedMessageV2,
            isDm: Boolean = false,
        ): MessageListItem? {
            // Filter out protocol messages that modify other messages
            val contentTypeId = message.contentTypeId.typeId
            if (contentTypeId in HIDDEN_CONTENT_TYPES) {
                return null
            }

            val content = message.content<Any>()
            val isFromMe = ClientManager.client.inboxId == message.senderInboxId

            // Check for system/protocol messages
            return when (content) {
                // Placeholder for deleted message content (shown in UI)
                is DeletedMessage -> {
                    val deletedByText =
                        when (content.deletedBy) {
                            is DeletedBy.Sender -> "sender"
                            is DeletedBy.Admin -> "admin"
                        }
                    MessageListItem.SystemMessage(
                        message.id,
                        message,
                        "This message was deleted by $deletedByText",
                    )
                }

                is GroupUpdated -> {
                    // For DMs, show "Conversation started by [initiator]" instead of member changes
                    val text =
                        if (isDm) {
                            val initiatorId = content.initiatedByInboxId
                            if (initiatorId.isNotEmpty()) {
                                "Conversation started by ${initiatorId.take(8)}..."
                            } else {
                                "Conversation started"
                            }
                        } else {
                            val addedText =
                                content.addedInboxesList
                                    ?.mapNotNull { it.inboxId }
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { "Added: ${it.joinToString(", ") { id -> id.take(8) + "..." }}" }
                            val removedText =
                                content.removedInboxesList
                                    ?.mapNotNull { it.inboxId }
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { "Removed: ${it.joinToString(", ") { id -> id.take(8) + "..." }}" }
                            listOfNotNull(addedText, removedText).joinToString("\n").ifEmpty { "Group updated" }
                        }
                    MessageListItem.SystemMessage(message.id, message, text)
                }

                else -> {
                    if (isFromMe) {
                        MessageListItem.SentMessage(message.id, message)
                    } else {
                        MessageListItem.ReceivedMessage(message.id, message)
                    }
                }
            }
        }
    }
}
