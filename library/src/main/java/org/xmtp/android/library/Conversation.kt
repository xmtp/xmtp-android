package org.xmtp.android.library

import org.xmtp.android.library.codecs.ContentCodec
import java.util.Date
import java.util.concurrent.Flow

sealed class Conversation {
    data class v1
        (val v1: ConversationV1) : Conversation()
    data class v2(val v1: ConversationV2) : Conversation()

    public/// The wallet address of the other person in this conversation.
    val peerAddress: String
        get() {
            when (this) {
                is v1 -> return conversationV1.peerAddress
                is v2 -> return conversationV2.peerAddress
            }
        }
    public/// An optional string that can specify a different context for a conversation with another account address.
    ///
    /// > Note: ``conversationID`` is only available for ``ConversationV2`` conversations.
    val conversationID: String?
        get() {
            when (this) {
                is v1 -> return null
                is v2 -> return conversation.context.conversationID
            }
        }

    public fun <T, CodecType: ContentCodec> send(content: T, codec: CodecType, fallback: String? = null) {
        when (this) {
            is v1 -> conversationV1.send(codec = codec, content = content, fallback = fallback)
            is v2 -> conversationV2.send(codec = codec, content = content, fallback = fallback)
        }
    }

    /// Send a message to the conversation
    public fun send(text: String) {
        when (this) {
            is v1 -> conversationV1.send(content = text)
            is v2 -> conversationV2.send(content = text)
        }
    }
    public/// The topic identifier for this conversation
    val topic: String
        get() {
            when (this) {
                is v1 -> return conversation.topic.description
                is v2 -> return conversation.topic
            }
        }

    /// Returns a stream you can iterate through to receive new messages in this conversation.
    public///
    /// > Note: All messages in the conversation are returned by this stream. If you want to filter out messages
    /// by a sender, you can check the ``Client`` address against the message's ``peerAddress``.
    fun streamMessages() : Flow<DecodedMessage, Error> {
        when (this) {
            is v1 -> return conversation.streamMessages()
            is v2 -> return conversation.streamMessages()
        }
    }

    /// List messages in the conversation
    public fun messages(limit: Int? = null, before: Date? = null, after: Date? = null) : List<DecodedMessage> {
        when (this) {
            is v1 -> return conversationV1.messages(limit = limit, before = before, after = after)
            is v2 -> return conversationV2.messages(limit = limit, before = before, after = after)
        }
    }
    val client: Client
        get() {
            when (this) {
                is v1 -> return conversationV1.client
                is v2 -> return conversationV2.client
            }
        }
}

public fun Conversation.Companion.==(lhs: Conversation, rhs: Conversation) : Boolean =
    lhs.topic == rhs.topic

public fun Conversation.hash(hasher: inout Hasher) {
    hasher.combine(topic)
}
