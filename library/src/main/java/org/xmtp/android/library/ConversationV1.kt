package org.xmtp.android.library

import kotlinx.coroutines.flow.flow
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.Message
import org.xmtp.android.library.messages.MessageV1
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.toPublicKeyBundle
import java.util.Date
import java.util.concurrent.Flow


/// Handles legacy message conversations.
public data class ConversationV1(
    var client: Client,
    var peerAddress: String,
    var sentAt: Date
) {
    val topic: Topic
        get() = Topic.directMessageV1(client.address, peerAddress)

    fun send(content: String) {
        send(content = content, options = null, sentAt = null)
    }

    internal fun send(content: String, _: SendOptions? = null, sentAt: Date? = null) {
        val encoder = TextCodec()
        val encodedContent = encoder.encode(content = content)
        send(content = encodedContent, sentAt = sentAt)
    }

    fun <Codec: ContentCodec> send(codec: Codec, content: Codec.T, fallback: String? = null) {
        var encoded = codec.encode(content = content)
        encoded.fallback = fallback ?: ""
        send(content = encoded)
    }

    internal fun send(encodedContent: EncodedContent, _: SendOptions? = null, sentAt: Date? = null) {
        val contact = client.contacts.find(peerAddress) ?: throw ContactBundleError.notFound
        val recipient = contact.toPublicKeyBundle()
        if (!recipient.identityKey.hasSignature) {
            throw Exception("no signature for id key")
        }
        val date = sentAt ?: Date()
        val message = MessageV1.encode(sender = client.privateKeyBundleV1, recipient = recipient, message = encodedContent.serializedData(), timestamp = date)
        var envelopes = listOf(Envelope(topic = .directMessageV1(client.address, peerAddress), timestamp = date, message = Message(v1 = message).serializedData()))
        if (client.contacts.needsIntroduction(peerAddress)) {
            envelopes.append(contentsOf = listOf(Envelope(topic = .userIntro(peerAddress), timestamp = date, message = Message(v1 = message).serializedData()), Envelope(topic = .userIntro(client.address), timestamp = date, message = Message(v1 = message).serializedData())))
            client.contacts.hasIntroduced[peerAddress] = true
        }
        client.publish(envelopes = envelopes)
    }

    public fun streamMessages() : Flow<DecodedMessage, Error> =
        flow { Task { for (envelope in client.subscribe(topics = listOf(topic.description))) {
            val decoded = decode(envelope = envelope)
            continuation.yield(decoded)
        } } }

    fun messages(limit: Int? = null, before: Date? = null, after: Date? = null) : List<DecodedMessage> {
        val envelopes = client.apiClient.query(topics = listOf(.directMessageV1(client.address, peerAddress))).envelopes
        return envelopes.compactMap { envelope  ->
            do {
                return@compactMap decode(envelope = envelope)
            } catch {
                print("ERROR DECODING CONVO V1 MESSAGE: ${error}")
                return@compactMap null
            }
        }
    }

    private fun decode(envelope: Envelope) : DecodedMessage {
        val message = Message(serializedData = envelope.message)
        val decrypted = message.v1.decrypt(with = client.privateKeyBundleV1)
        val encodedMessage = EncodedContent(serializedData = decrypted)
        val header = message.v1.header
        return DecodedMessage(encodedContent = encodedMessage, senderAddress = header.sender.walletAddress, sent = message.v1.sentAt)
    }
}
