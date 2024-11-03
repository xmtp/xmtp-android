package org.xmtp.android.library

import kotlinx.coroutines.flow.Flow
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.libxmtp.Member
import org.xmtp.android.library.libxmtp.Message
import org.xmtp.android.library.messages.PagingInfoSortDirection
import org.xmtp.proto.message.api.v1.MessageApiOuterClass
import org.xmtp.proto.message.contents.Invitation.ConsentProofPayload
import java.util.Date

/**
 * This represents an ongoing conversation.
 * It can be provided to [Client] to [messages] and [send].
 * The [Client] also allows you to [streamMessages] from this [Conversation].
 *
 * It attempts to give uniform shape to v1 and v2 conversations.
 */
sealed class Conversation {
    data class Group(val group: org.xmtp.android.library.Group) : Conversation()
    data class Dm(val dm: org.xmtp.android.library.Dm) : Conversation()

    enum class Version { GROUP, DM }

    val version: Version
        get() {
            return when (this) {
                is Group -> Version.GROUP
                is Dm -> Version.DM
            }
        }

    val id: String
        get() {
            return when (this) {
                is Group -> group.id
                is Dm -> dm.id
            }
        }

    val topic: String
        get() {
            return when (this) {
                is Group -> group.topic
                is Dm -> dm.topic
            }
        }

    val createdAt: Date
        get() {
            return when (this) {
                is Group -> group.createdAt
                is Dm -> dm.createdAt
            }
        }

    fun isCreator(): Boolean {
        return when (this) {
            is Group -> group.isCreator()
            is Dm -> dm.isCreator()
        }
    }

    suspend fun members(): List<Member> {
        return when (this) {
            is Group -> group.members()
            is Dm -> dm.members()
        }
    }

    fun updateConsentState(state: ConsentState) {
        return when (this) {
            is Group -> group.updateConsentState(state)
            is Dm -> dm.updateConsentState(state)
        }
    }

    fun consentState(): ConsentState {
        return when (this) {
            is Group -> group.consentState()
            is Dm -> dm.consentState()
        }
    }

    suspend fun <T> prepareMessage(content: T, options: SendOptions? = null): String {
        return when (this) {
            is Group -> group.prepareMessage(content, options)
            is Dm -> dm.prepareMessage(content, options)
        }
    }

    suspend fun <T> send(content: T, options: SendOptions? = null): String {
        return when (this) {
            is Group -> group.send(content = content, options = options)
            is Dm -> dm.send(content = content, options = options)
        }
    }

    suspend fun send(text: String, sendOptions: SendOptions? = null, sentAt: Date? = null): String {
        return when (this) {
            is Group -> group.send(text)
            is Dm -> dm.send(text)
        }
    }

    suspend fun send(encodedContent: EncodedContent, options: SendOptions? = null): String {
        return when (this) {
            is Group -> group.send(encodedContent = encodedContent)
            is Dm -> dm.send(encodedContent = encodedContent)
        }
    }

    suspend fun sync() {
        return when (this) {
            is Group -> group.sync()
            is Dm -> dm.sync()
        }
    }

    /**
     * This lists messages sent to the [Conversation].
     * @param before initial date to filter
     * @param after final date to create a range of dates and filter
     * @param limit is the number of result that will be returned
     * @param direction is the way of srting the information, by default is descending, you can
     * know more about it in class [MessageApiOuterClass].
     * @see MessageApiOuterClass.SortDirection
     * @return The list of messages sent. If [before] or [after] are specified then this will only list messages
     * sent at or [after] and at or [before].
     * If [limit] is specified then results are pulled in pages of that size.
     * If [direction] is specified then that will control the sort order of te messages.
     */
    fun messages(
        limit: Int? = null,
        before: Date? = null,
        after: Date? = null,
        direction: PagingInfoSortDirection = MessageApiOuterClass.SortDirection.SORT_DIRECTION_DESCENDING,
    ): List<DecodedMessage> {
        return when (this) {
            is Group -> {
                group.messages(
                    limit = limit,
                    before = before,
                    after = after,
                    direction = direction,
                )
            }

            is Dm -> dm.messages(limit, before, after, direction)
        }
    }

    fun decode(message: Message): DecodedMessage {
        return when (this) {
            is Group -> message.decode()
            is Dm -> message.decode()
        }
    }

    suspend fun processMessage(envelopeBytes: ByteArray): Message {
        return when (this) {
            is Group -> group.processMessage(envelopeBytes)
            is Dm -> dm.processMessage(envelopeBytes)
        }
    }

    val consentProof: ConsentProofPayload?
        get() {
            return when (this) {
                is Group -> return null
                is Dm -> return null
            }
        }

    // Get the client according to the version
    val client: Client
        get() {
            return when (this) {
                is Group -> group.client
                is Dm -> dm.client
            }
        }

    /**
     * This exposes a stream of new messages sent to the [Conversation].
     * @return Stream of messages according to the version
     */
    fun streamMessages(): Flow<DecodedMessage> {
        return when (this) {
            is Group -> group.streamMessages()
            is Dm -> dm.streamMessages()
        }
    }
}
