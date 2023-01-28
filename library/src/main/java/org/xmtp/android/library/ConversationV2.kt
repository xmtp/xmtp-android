package org.xmtp.android.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.Message
import org.xmtp.android.library.messages.MessageV2
import org.xmtp.android.library.messages.SealedInvitationHeaderV1
import org.xmtp.android.library.messages.getPublicKeyBundle
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.message.contents.Invitation
import java.util.Date


data class SendOptions {}

/// Handles V2 Message conversations.
public data class ConversationV2(
    var topic: String,
    var keyMaterial: ByteArray,
    // MUST be kept secret
    var context: Invitation.InvitationV1.Context,
    var peerAddress: String,
    var client: Client,
    private var header: SealedInvitationHeaderV1
) {
    companion object {

        fun create(client: Client, invitation: Invitation.InvitationV1, header: SealedInvitationHeaderV1) : ConversationV2 {
            val myKeys = client.keys.getPublicKeyBundle()
            val peer = if (myKeys.walletAddress == (header.sender.walletAddress)) header.recipient else header.sender
            val peerAddress = peer.walletAddress
            val keyMaterial = invitation.aes256GcmHkdfSha256.keyMaterial.toByteArray()
            return ConversationV2(topic = invitation.topic, keyMaterial = keyMaterial, context = invitation.context, peerAddress = peerAddress, client = client, header = header)
        }
    }


    constructor(topic: String, keyMaterial: ByteArray, context: Invitation.InvitationV1.Context, peerAddress: String, client: Client, header: SealedInvitationHeaderV1) {
        this.topic = topic
        this.keyMaterial = keyMaterial
        this.context = context
        this.peerAddress = peerAddress
        this.client = client
        this.header = header
    }

    fun messages(limit: Int? = null, before: Date? = null, after: Date? = null) : List<DecodedMessage> {
        val envelopes = client.apiClient.query(topics = listOf(topic)).envelopes
        return envelopes.compactMap { envelope  ->
            do {
                val message = Message(serializedData = envelope.message)
                return@compactMap decode(message.v2)
            } catch {
                print("Error decoding envelope ${error}")
                return@compactMap null
            }
        }
    }

    public fun streamMessages() : Flow<DecodedMessage, Error> =
        flow { continuation  ->
            Task { for (envelope in client.subscribe(topics = listOf(topic.description))) {
                val message = Message(serializedData = envelope.message)
                val decoded = decode(message.v2)
                continuation.yield(decoded)
            } }
        }

    private fun decode(message: MessageV2) : DecodedMessage =
        MessageV2.decode(message, keyMaterial = keyMaterial)

    fun <Codec: ContentCodec> send(codec: Codec, content: Codec.T, fallback: String? = null) {
        var encoded = codec.encode(content = content)
        encoded.fallback = fallback ?: ""
        send(content = encoded, sentAt = Date())
    }

    fun send(content: String, sentAt: Date) {
        val encoder = TextCodec()
        val encodedContent = encoder.encode(content = content)
        send(content = encodedContent, sentAt = sentAt)
    }

    internal fun send(content: EncodedContent, sentAt: Date) {
        if (client.getUserContact(peerAddress = peerAddress) == null) {
            throw ContactBundleError.notFound
        }
        val message = MessageV2.encode(client = client, content = content, topic = topic, keyMaterial = keyMaterial)
        client.publish(envelopes = listOf(Envelope(topic = topic, timestamp = sentAt, message = Message(v2 = message).serializedData())))
    }

    fun send(content: String) {
        send(content = content, sentAt = Date())
    }
}
