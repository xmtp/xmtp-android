package org.xmtp.android.library

import kotlinx.coroutines.runBlocking
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.compress
import org.xmtp.android.library.libxmtp.Message
import org.xmtp.android.library.messages.EnvelopeBuilder
import org.xmtp.android.library.messages.MessageBuilder
import org.xmtp.android.library.messages.MessageV2Builder
import uniffi.xmtpv3.FfiGroup
import uniffi.xmtpv3.FfiListMessagesOptions
import java.util.Date

class Group(val client: Client, private val libXMTPGroup: FfiGroup) {
    val id: ByteArray
        get() = libXMTPGroup.id()

    val createdAt: Date
        get() = Date(libXMTPGroup.createdAtNs() / 1_000_000)

    fun send(text: String): String {
        runBlocking {
            libXMTPGroup.send(
                contentBytes = prepareMessage(content = text, options = null).toByteArray()
            )
        }
        return id.toString()
    }

    fun <T> prepareMessage(content: T, options: SendOptions?): EncodedContent {
        val codec = Client.codecRegistry.find(options?.contentType)

        fun <Codec : ContentCodec<T>> encode(codec: Codec, content: Any?): EncodedContent {
            val contentType = content as? T
            if (contentType != null) {
                return codec.encode(contentType)
            } else {
                throw XMTPException("Codec type is not registered")
            }
        }

        var encoded = encode(codec = codec as ContentCodec<T>, content = content)
        val fallback = codec.fallback(content)
        if (!fallback.isNullOrBlank()) {
            encoded = encoded.toBuilder().also {
                it.fallback = fallback
            }.build()
        }
        val compression = options?.compression
        if (compression != null) {
            encoded = encoded.compress(compression)
        }
        return encoded
    }

    suspend fun sync() {
        libXMTPGroup.sync()
    }

    fun messages(): List<DecodedMessage> {
        return runBlocking {
            libXMTPGroup.findMessages(
                opts = FfiListMessagesOptions(
                    sentBeforeNs = null,
                    sentAfterNs = null,
                    limit = null
                )
            ).map {
                Message(client, it).decode()
            }
        }
    }

    fun addMembers(addresses: List<String>) {
        runBlocking { libXMTPGroup.addMembers(addresses) }
    }

    fun memberAddresses(): List<String> {
        return runBlocking {
            libXMTPGroup.listMembers().map { it.accountAddress }
        }
    }
}