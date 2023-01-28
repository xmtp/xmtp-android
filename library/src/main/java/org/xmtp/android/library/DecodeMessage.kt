package org.xmtp.android.library

import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.proto.message.contents.Content
import java.util.Date

data class DecodedMessage(
    var encodedContent: Content.EncodedContent,
    var senderAddress: String,
    var sent: Date
) {

    companion object {
        fun preview(body: String, senderAddress: String, sent: Date) : DecodedMessage {
            val encoded = TextCodec().encode(content = body)
            return DecodedMessage(encodedContent = encoded, senderAddress = senderAddress, sent = sent)
        }
    }

    fun <T> content() : T =
        encodedContent.from()
    val fallbackContent: String
        get() = encodedContent.fallback
    val body: String
        get() = do {
            return content()
        } catch {
            return fallbackContent
        }
}


