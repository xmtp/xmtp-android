package org.xmtp.android.library.libxmtp

import org.xmtp.android.library.Client
import org.xmtp.android.library.DecodedMessage
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.messages.DecryptedMessage
import org.xmtp.android.library.messages.MessageKind
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.toHex
import uniffi.xmtpv3.FfiGroupMessageKind
import uniffi.xmtpv3.FfiMessage
import java.util.Date

data class MessageV3(val client: Client, private val libXMTPMessage: FfiMessage) {

    val id: ByteArray
        get() = libXMTPMessage.id

    val convoId: ByteArray
        get() = libXMTPMessage.convoId

    val senderAddress: String
        get() = libXMTPMessage.addrFrom

    val sentAt: Date
        get() = Date(libXMTPMessage.sentAtNs / 1_000_000)

    val kind: MessageKind
        get() = when (libXMTPMessage.kind) {
            FfiGroupMessageKind.APPLICATION -> MessageKind.APPLICATION
            FfiGroupMessageKind.MEMBERSHIP_CHANGE -> MessageKind.MEMBERSHIP_CHANGE
        }

    fun decode(): DecodedMessage {
        try {
            return DecodedMessage(
                id = id.toHex(),
                client = client,
                topic = Topic.groupMessage(convoId.toHex()).description,
                encodedContent = EncodedContent.parseFrom(libXMTPMessage.content),
                senderAddress = senderAddress,
                sent = sentAt,
                kind = kind
            )
        } catch (e: Exception) {
            throw XMTPException("Error decoding message", e)
        }
    }

    fun decrypt(): DecryptedMessage {
        return DecryptedMessage(
            id = id.toHex(),
            topic = Topic.groupMessage(convoId.toHex()).description,
            encodedContent = decode().encodedContent,
            senderAddress = senderAddress,
            sentAt = Date(),
            kind = kind
        )
    }
}
