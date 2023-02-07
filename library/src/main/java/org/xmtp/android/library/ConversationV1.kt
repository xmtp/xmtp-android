package org.xmtp.android.library

import android.content.res.Resources.NotFoundException
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.EnvelopeBuilder
import org.xmtp.android.library.messages.Message
import org.xmtp.android.library.messages.MessageBuilder
import org.xmtp.android.library.messages.MessageV1Builder
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.decrypt
import org.xmtp.android.library.messages.header
import org.xmtp.android.library.messages.sentAt
import org.xmtp.android.library.messages.toPublicKeyBundle
import org.xmtp.android.library.messages.walletAddress
import java.util.Date

data class ConversationV1Container(
    var peerAddress: String,
    var sentAt: Date
) {

    fun decode(client: Client): ConversationV1 =
        ConversationV1(client = client, peerAddress = peerAddress, sentAt = sentAt)
}

data class ConversationV1(
    var client: Client,
    var peerAddress: String,
    var sentAt: Date
) {
    val topic: Topic
        get() = Topic.directMessageV1(client.address, peerAddress)

    fun send(content: String) {
        send(content = content, sentAt = null)
    }

    private fun send(content: String, sentAt: Date? = null) {
        val encoder = TextCodec()
        val encodedContent = encoder.encode(content = content)
        send(content = encodedContent)
    }

    fun <T : Any> send(content: T, options: SendOptions? = null) {
        val codec = Client().codecRegistry.find(options?.contentType)

        fun <Codec : ContentCodec<T>> encode(codec: Codec, content: Any): EncodedContent {
            val contentType = content as? T
            if (contentType != null) {
                return codec.encode(content = contentType)
            } else {
                throw java.lang.NullPointerException()
            }
        }

        var encoded = encode(codec = codec as ContentCodec<T>, content = content)
        encoded = encoded.toBuilder().also {
            it.fallback = options?.contentFallback ?: ""
        }.build()
        send(content = encoded, options = options)
    }

    private suspend fun send(
        encodedContent: EncodedContent,
        sendOptions: SendOptions? = null,
        sentAt: Date? = null
    ) {
        val contact = client.contacts.find(peerAddress) ?: throw NotFoundException()
        val recipient = contact.toPublicKeyBundle()
        if (!recipient.identityKey.hasSignature()) {
            throw Exception("no signature for id key")
        }
        val date = sentAt ?: Date()
        if (client.privateKeyBundleV1 == null) {
            throw Exception("no private key bundle")
        }
        val message = MessageV1Builder.buildEncode(
            sender = client.privateKeyBundleV1!!,
            recipient = recipient,
            message = encodedContent.toByteArray(),
            timestamp = date
        )
        val envelopes = mutableListOf(
            EnvelopeBuilder.buildFromTopic(
                topic = Topic.directMessageV1(
                    client.address,
                    peerAddress
                ),
                timestamp = date,
                message = MessageBuilder.buildFromMessageV1(v1 = message).toByteArray()
            )
        )
        if (client.contacts.needsIntroduction(peerAddress)) {
            envelopes.addAll(
                listOf(
                    EnvelopeBuilder.buildFromTopic(
                        topic = Topic.userIntro(peerAddress),
                        timestamp = date,
                        message = MessageBuilder.buildFromMessageV1(v1 = message).toByteArray()
                    ),
                    EnvelopeBuilder.buildFromTopic(
                        topic = Topic.userIntro(client.address),
                        timestamp = date,
                        message = MessageBuilder.buildFromMessageV1(v1 = message).toByteArray()
                    )
                )
            )
            client.contacts.hasIntroduced[peerAddress] = true
        }
        client.publish(envelopes = envelopes)
    }

    suspend fun messages(
        limit: Int? = null,
        before: Date? = null,
        after: Date? = null
    ): List<DecodedMessage> {
        val envelopes = client.apiClient.query(
            topics = listOf(
                Topic.directMessageV1(
                    client.address,
                    peerAddress
                )
            )
        ).envelopesList
        return envelopes.flatMap { envelope ->
            listOf(decode(envelope = envelope))
        }
    }

    private fun decode(envelope: Envelope): DecodedMessage {
        val message = Message.parseFrom(envelope.message)
        val decrypted = message.v1.decrypt(client.privateKeyBundleV1)
        val encodedMessage = EncodedContent.parseFrom(decrypted)
        val header = message.v1.header
        return DecodedMessage(
            encodedContent = encodedMessage,
            senderAddress = header.sender.walletAddress,
            sent = message.v1.sentAt
        )
    }
}
