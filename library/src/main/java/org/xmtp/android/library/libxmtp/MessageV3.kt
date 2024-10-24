package org.xmtp.android.library.libxmtp

import android.util.Log
import org.xmtp.android.library.Client
import org.xmtp.android.library.DecodedMessage
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.codecs.ContentTypeGroupUpdated
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.messages.DecryptedMessage
import org.xmtp.android.library.messages.MessageDeliveryStatus
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.toHex
import uniffi.xmtpv3.FfiDeliveryStatus
import uniffi.xmtpv3.FfiConversationMessageKind
import uniffi.xmtpv3.FfiMessage
import java.util.Date

data class MessageV3(val client: Client, private val libXMTPMessage: FfiMessage) {

    val id: String
        get() = libXMTPMessage.id.toHex()

    val convoId: String
        get() = libXMTPMessage.convoId.toHex()

    val senderInboxId: String
        get() = libXMTPMessage.senderInboxId

    val sentAt: Date
        get() = Date(libXMTPMessage.sentAtNs / 1_000_000)

    val deliveryStatus: MessageDeliveryStatus
        get() = when (libXMTPMessage.deliveryStatus) {
            FfiDeliveryStatus.UNPUBLISHED -> MessageDeliveryStatus.UNPUBLISHED
            FfiDeliveryStatus.PUBLISHED -> MessageDeliveryStatus.PUBLISHED
            FfiDeliveryStatus.FAILED -> MessageDeliveryStatus.FAILED
        }

    fun decode(): DecodedMessage {
        try {
            val decodedMessage = DecodedMessage(
                id = id,
                client = client,
                topic = Topic.groupMessage(convoId).description,
                encodedContent = EncodedContent.parseFrom(libXMTPMessage.content),
                senderAddress = senderInboxId,
                sent = sentAt,
                deliveryStatus = deliveryStatus
            )
            if (decodedMessage.encodedContent.type == ContentTypeGroupUpdated && libXMTPMessage.kind != FfiConversationMessageKind.MEMBERSHIP_CHANGE) {
                throw XMTPException("Error decoding group membership change")
            }
            return decodedMessage
        } catch (e: Exception) {
            throw XMTPException("Error decoding message", e)
        }
    }

    fun decodeOrNull(): DecodedMessage? {
        return try {
            decode()
        } catch (e: Exception) {
            Log.d("MESSAGE_V3", "discarding message that failed to decode", e)
            null
        }
    }

    fun decryptOrNull(): DecryptedMessage? {
        return try {
            decrypt()
        } catch (e: Exception) {
            Log.d("MESSAGE_V3", "discarding message that failed to decrypt", e)
            null
        }
    }

    fun decrypt(): DecryptedMessage {
        return DecryptedMessage(
            id = id,
            topic = Topic.groupMessage(convoId).description,
            encodedContent = decode().encodedContent,
            senderAddress = senderInboxId,
            sentAt = sentAt,
            deliveryStatus = deliveryStatus
        )
    }
}
