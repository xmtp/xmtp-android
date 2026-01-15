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
import kotlinx.coroutines.runBlocking
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
import org.xmtp.android.library.libxmtp.DecodedMessageV2
import org.xmtp.proto.mls.message.contents.TranscriptMessages.GroupUpdated

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

    private var conversation: Conversation? = null

    fun setReplyToMessage(message: DecodedMessageV2?) {
        _replyToMessage.value = message
    }

    fun clearReply() {
        _replyToMessage.value = null
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
                conversation?.let {
                    // Sync conversation to get latest messages (including deletions)
                    when (it) {
                        is Conversation.Group -> it.group.sync()
                        is Conversation.Dm -> it.dm.sync()
                    }
                    listItems.addAll(
                        it.enrichedMessages().mapNotNull { message ->
                            message?.let { msg ->
                                val item = classifyMessage(msg)
                                // Filter out deleted messages if hideDeletedMessages is enabled
                                if (hideDeletedMessages && item is MessageListItem.SystemMessage) {
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
            if (conversation == null) {
                conversation =
                    runBlocking {
                        ClientManager.client.conversations.findConversationByTopic(conversationTopic!!)
                    }
            }
            if (conversation != null) {
                conversation!!
                    .streamMessages()
                    .flowWhileShared(
                        subscriptionCount,
                        SharingStarted.WhileSubscribed(1000L),
                    ).flowOn(Dispatchers.IO)
                    .distinctUntilChanged()
                    .mapLatest { message ->
                        // Check if this is a delete or reaction message - if so, signal a refresh is needed
                        val contentTypeId = message.encodedContent.type
                        val isDeleteMessage = contentTypeId?.typeId == "deleteMessage"
                        val isReactionMessage = contentTypeId?.typeId == "reaction"

                        if (isDeleteMessage || isReactionMessage) {
                            // Return a signal to refresh the message list
                            // Reactions and deletes modify existing messages, so we need a full refresh
                            StreamedMessageResult.RefreshNeeded
                        } else {
                            // Convert streamed DecodedMessage to DecodedMessageV2 using findEnrichedMessage
                            val enrichedMessage =
                                ClientManager.client.conversations.findEnrichedMessage(message.id)
                            enrichedMessage?.let {
                                StreamedMessageResult.NewMessage(classifyMessage(it))
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

        object RefreshNeeded : StreamedMessageResult()
    }

    @UiThread
    fun sendMessage(body: String): StateFlow<SendMessageState> {
        val flow = MutableStateFlow<SendMessageState>(SendMessageState.Loading)
        val replyTo = _replyToMessage.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (replyTo != null) {
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
        object Loading : SendMessageState()

        object Success : SendMessageState()

        data class Error(
            val message: String,
        ) : SendMessageState()
    }

    sealed class DeleteMessageState {
        object Loading : DeleteMessageState()

        object Success : DeleteMessageState()

        data class Error(
            val message: String,
        ) : DeleteMessageState()
    }

    sealed class ReactionState {
        object Loading : ReactionState()

        object Success : ReactionState()

        data class Error(
            val message: String,
        ) : ReactionState()
    }

    sealed class SendAttachmentState {
        object Loading : SendAttachmentState()

        object Success : SendAttachmentState()

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
        // Flag to hide deleted messages entirely (set from Activity)
        var hideDeletedMessages: Boolean = false

        fun classifyMessage(message: DecodedMessageV2): MessageListItem {
            val content = message.content<Any>()
            val isFromMe = ClientManager.client.inboxId == message.senderInboxId

            // Check for system messages (deleted, group updates)
            return when (content) {
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
                    val text = listOfNotNull(addedText, removedText).joinToString("\n").ifEmpty { "Group updated" }
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
