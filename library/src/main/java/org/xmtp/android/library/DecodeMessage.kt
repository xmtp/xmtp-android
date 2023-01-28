package org.xmtp.android.library

import org.xmtp.proto.message.contents.Content
import java.util.Date


/// Decrypted messages from a conversation.
public data class DecodedMessage(
    public var encodedContent: Content.EncodedContent,
    public/// The wallet address of the sender of the message
    var senderAddress: String,
    public/// When the message was sent
    var sent: Date
) {

    public constructor(encodedContent: Content.EncodedContent, senderAddress: String, sent: Date) {
        this.encodedContent = encodedContent
        this.senderAddress = senderAddress
        this.sent = sent
    }

    public fun <T> content() : T =
        encodedContent.decoded()
    val fallbackContent: String
        get() = encodedContent.fallback
    val body: String
        get() = do {
            return content()
        } catch {
            return fallbackContent
        }
}

public fun DecodedMessage.Companion.preview(body: String, senderAddress: String, sent: Date) : DecodedMessage {
    val encoded = TextCodec().encode(content = body)
    return DecodedMessage(encodedContent = encoded, senderAddress = senderAddress, sent = sent)
}
