package org.xmtp.android.library

import java.util.Date

sealed class ConversationContainer {
    data class V1(val conversationV1: ConversationV1Container) : ConversationContainer()
    data class V2(val conversationV2: ConversationV2Container) : ConversationContainer()


    fun decode(client: Client): Conversation {
        return when (this) {
            is V1 -> Conversation.V1(conversationV1.decode(client))
            is V2 -> Conversation.V2(conversationV2.decode(client))
        }
    }
}

sealed class Conversation {
    data class V1(val conversationV1: ConversationV1) : Conversation()
    data class V2(val conversationV2: ConversationV2) : Conversation()

    public/// The wallet address of the other person in this conversation.
    val peerAddress: String
        get() {
            return when (this) {
                is V1 -> conversationV1.peerAddress
                is V2 -> conversationV2.peerAddress
            }
        }

    /// An optional string that can specify a different context for a conversation with another account address.
    ///
    /// > Note: ``conversationId`` is only available for ``ConversationV2`` conversations.
    val conversationId: String?
        get() {
            return when (this) {
                is V1 -> null
                is V2 -> conversationV2.context.conversationId
            }
        }


    suspend fun <T> send(content: T, options: SendOptions? = null) {
        when (this) {
            is V1 -> conversationV1.send(content = content as String, options = options)
            is V2 -> conversationV2.send(content = content, options = options)
        }
    }

    suspend fun send(text: String) {
        when (this) {
            is V1 -> conversationV1.send(content = text)
            is V2 -> conversationV2.send(content = text)
        }
    }

    val topic: String
        get() {
            return when (this) {
                is V1 -> conversationV1.topic.description
                is V2 -> conversationV2.topic
            }
        }


    suspend fun messages(
        limit: Int? = null,
        before: Date? = null,
        after: Date? = null
    ): List<DecodedMessage> {
        return when (this) {
            is V1 -> conversationV1.messages(limit = limit, before = before, after = after)
            is V2 -> conversationV2.messages(limit = limit, before = before, after = after)
        }
    }

    val client: Client
        get() {
            return when (this) {
                is V1 -> conversationV1.client
                is V2 -> conversationV2.client
            }
        }
}
