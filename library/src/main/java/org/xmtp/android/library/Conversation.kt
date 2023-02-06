package org.xmtp.android.library

import org.xmtp.android.library.codecs.ContentCodec
import java.util.Date
import java.util.concurrent.Flow

sealed class ConversationContainer: java.io.Serializable {
    data class v1(val v1: ConversationV1Container) : ConversationContainer()
    data class v2(val v1: ConversationV2Container) : ConversationContainer()


    fun decode(client: Client) : Conversation {
        return when (this) {
            is v1 -> {
                v1(this.decode(client))
            }
            is v2 -> v2(this.decode(client))
        }
    }
}

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
    val conversationId: String?
        get() {
            when (this) {
                is v1 -> return null
                is v2 -> return conversation.context.conversationID
            }
        }

    fun <T, CodecType: ContentCodec> send(content: T, codec: CodecType, fallback: String? = null) {
        when (this) {
            is v1 -> conversationV1.send(codec = codec, content = content, fallback = fallback)
            is v2 -> conversationV2.send(codec = codec, content = content, fallback = fallback)
        }
    }

    /// Send a message to the conversation
    fun send(text: String) {
        when (this) {
            is v1 -> conversationV1.send(content = text)
            is v2 -> conversationV2.send(content = text)
        }
    }
    val topic: String
        get() {
            when (this) {
                is v1 -> return conversation.topic.description
                is v2 -> return conversation.topic
            }
        }


    fun messages(limit: Int? = null, before: Date? = null, after: Date? = null) : List<DecodedMessage> {
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
