package org.xmtp.android.library.libxmtp

import android.util.Log
import com.google.protobuf.kotlin.toByteString
import org.xmtp.android.library.InboxId
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.ContentTypeId
import org.xmtp.android.library.codecs.ContentTypeIdBuilder
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.codecs.ReactionSchema
import org.xmtp.android.library.codecs.ReadReceipt
import org.xmtp.android.library.codecs.MultiRemoteAttachment
import org.xmtp.android.library.codecs.RemoteAttachment
import org.xmtp.android.library.codecs.RemoteAttachmentInfo
import org.xmtp.android.library.codecs.TransactionReference
import org.xmtp.android.library.codecs.decoded
import org.xmtp.android.library.toHex
import uniffi.xmtpv3.FfiDecodedMessage
import uniffi.xmtpv3.FfiDecodedMessageBody
import uniffi.xmtpv3.FfiDecodedMessageContent
import uniffi.xmtpv3.FfiDeliveryStatus
import uniffi.xmtpv3.FfiReactionAction
import uniffi.xmtpv3.FfiReactionSchema
import java.net.URL
import java.util.Date

class DecodedMessageV2 private constructor(
    private val libXMTPMessage: FfiDecodedMessage
) {
    val id: String
        get() = libXMTPMessage.id().toHex()

    val conversationId: String
        get() = libXMTPMessage.conversationId().toHex()

    val senderInboxId: InboxId
        get() = libXMTPMessage.senderInboxId()

    val sentAt: Date
        get() = Date(libXMTPMessage.sentAtNs() / 1_000_000)

    val sentAtNs: Long
        get() = libXMTPMessage.sentAtNs()

    val deliveryStatus: DecodedMessage.MessageDeliveryStatus
        get() = when (libXMTPMessage.deliveryStatus()) {
            FfiDeliveryStatus.UNPUBLISHED -> DecodedMessage.MessageDeliveryStatus.UNPUBLISHED
            FfiDeliveryStatus.PUBLISHED -> DecodedMessage.MessageDeliveryStatus.PUBLISHED
            FfiDeliveryStatus.FAILED -> DecodedMessage.MessageDeliveryStatus.FAILED
        }

    val reactions: List<DecodedMessageV2>
        get() = libXMTPMessage.reactions().mapNotNull { create(it) }

    val hasReactions: Boolean
        get() = libXMTPMessage.hasReactions()

    val reactionCount: ULong
        get() = libXMTPMessage.reactionCount()

    val fallbackText: String?
        get() = libXMTPMessage.fallbackText()

    val contentTypeId: ContentTypeId
        get() = ContentTypeIdBuilder.fromFfi(libXMTPMessage.contentTypeId())

    @Suppress("UNCHECKED_CAST")
    fun <T> content(): T? {
        return try {
            decodeContent(libXMTPMessage.content()) as? T
        } catch (e: Exception) {
            Log.e("DecodedMessageV2", "Error decoding content: ${e.message}")
            null
        }
    }

    companion object {
        fun create(libXMTPMessage: FfiDecodedMessage): DecodedMessageV2? {
            return try {
                DecodedMessageV2(libXMTPMessage)
            } catch (e: Exception) {
                Log.e("DecodedMessageV2", "Error creating DecodedMessageV2: ${e.message}")
                null
            }
        }

        /**
         * Decode content from FfiDecodedMessageContent
         */
        internal fun decodeContent(content: FfiDecodedMessageContent): Any? {
            return when (content) {
                is FfiDecodedMessageContent.Text -> content.v1.content

                is FfiDecodedMessageContent.Reaction -> {
                    val ffiReaction = content.v1
                    val action = when (ffiReaction.action) {
                        FfiReactionAction.ADDED -> ReactionAction.Added
                        FfiReactionAction.REMOVED -> ReactionAction.Removed
                        FfiReactionAction.UNKNOWN -> ReactionAction.Unknown
                    }
                    val schema = when (ffiReaction.schema) {
                        FfiReactionSchema.UNICODE -> ReactionSchema.Unicode
                        FfiReactionSchema.SHORTCODE -> ReactionSchema.Shortcode
                        FfiReactionSchema.CUSTOM -> ReactionSchema.Custom
                        FfiReactionSchema.UNKNOWN -> ReactionSchema.Unknown
                    }
                    Reaction(
                        reference = ffiReaction.reference,
                        action = action,
                        content = ffiReaction.content,
                        schema = schema
                    )
                }

                is FfiDecodedMessageContent.Reply -> Reply.create(content.v1)

                is FfiDecodedMessageContent.Attachment -> {
                    val ffiAttachment = content.v1
                    Attachment(
                        filename = ffiAttachment.filename ?: "",
                        mimeType = ffiAttachment.mimeType,
                        data = ffiAttachment.content.toByteString()
                    )
                }

                is FfiDecodedMessageContent.RemoteAttachment -> {
                    val ffiRemote = content.v1
                    RemoteAttachment(
                        url = URL(ffiRemote.url),
                        contentDigest = ffiRemote.contentDigest,
                        secret = ffiRemote.secret.toByteString(),
                        salt = ffiRemote.salt.toByteString(),
                        nonce = ffiRemote.nonce.toByteString(),
                        scheme = ffiRemote.scheme,
                        contentLength = ffiRemote.contentLength.toInt(),
                        filename = ffiRemote.filename
                    )
                }

                is FfiDecodedMessageContent.MultiRemoteAttachment -> {
                    val ffiMulti = content.v1
                    MultiRemoteAttachment(
                        remoteAttachments = ffiMulti.attachments.map { ffiInfo ->
                            RemoteAttachmentInfo(
                                url = ffiInfo.url,
                                filename = ffiInfo.filename ?: "",
                                contentLength = ffiInfo.contentLength?.toLong() ?: 0,
                                contentDigest = ffiInfo.contentDigest,
                                nonce = ffiInfo.nonce.toByteString(),
                                scheme = ffiInfo.scheme,
                                salt = ffiInfo.salt.toByteString(),
                                secret = ffiInfo.secret.toByteString()
                            )
                        }
                    )
                }

                is FfiDecodedMessageContent.TransactionReference -> {
                    val ffiTx = content.v1
                    TransactionReference(
                        namespace = ffiTx.namespace,
                        networkId = ffiTx.networkId,
                        reference = ffiTx.reference,
                        metadata = ffiTx.metadata?.let { meta ->
                            TransactionReference.Metadata(
                                transactionType = meta.transactionType,
                                currency = meta.currency,
                                amount = meta.amount,
                                decimals = meta.decimals,
                                fromAddress = meta.fromAddress,
                                toAddress = meta.toAddress
                            )
                        }
                    )
                }

                is FfiDecodedMessageContent.WalletSendCalls -> {
                    content.v1
                }

                is FfiDecodedMessageContent.GroupUpdated -> {
                    // Return the raw FFI type as GroupUpdatedCodec uses proto types
                    content.v1
                }

                is FfiDecodedMessageContent.ReadReceipt -> ReadReceipt

                is FfiDecodedMessageContent.Custom -> {
                    val ffiEncodedContent = content.v1
                    val encodedContent = EncodedContent.parseFrom(ffiEncodedContent.content)
                    encodedContent.decoded<Any>()
                }

                else -> null
            }
        }

        /**
         * Decode content from FfiDecodedMessageBody (used by Reply)
         */
        internal fun decodeBodyContent(body: FfiDecodedMessageBody): Any? {
            return when (body) {
                is FfiDecodedMessageBody.Text -> body.v1.content

                is FfiDecodedMessageBody.Reaction -> {
                    val ffiReaction = body.v1
                    val action = when (ffiReaction.action) {
                        FfiReactionAction.ADDED -> ReactionAction.Added
                        FfiReactionAction.REMOVED -> ReactionAction.Removed
                        FfiReactionAction.UNKNOWN -> ReactionAction.Unknown
                    }
                    val schema = when (ffiReaction.schema) {
                        FfiReactionSchema.UNICODE -> ReactionSchema.Unicode
                        FfiReactionSchema.SHORTCODE -> ReactionSchema.Shortcode
                        FfiReactionSchema.CUSTOM -> ReactionSchema.Custom
                        FfiReactionSchema.UNKNOWN -> ReactionSchema.Unknown
                    }
                    Reaction(
                        reference = ffiReaction.reference,
                        action = action,
                        content = ffiReaction.content,
                        schema = schema
                    )
                }

                is FfiDecodedMessageBody.Attachment -> {
                    val ffiAttachment = body.v1
                    Attachment(
                        filename = ffiAttachment.filename ?: "",
                        mimeType = ffiAttachment.mimeType,
                        data = ffiAttachment.content.toByteString()
                    )
                }

                is FfiDecodedMessageBody.RemoteAttachment -> {
                    val ffiRemote = body.v1
                    RemoteAttachment(
                        url = URL(ffiRemote.url),
                        contentDigest = ffiRemote.contentDigest,
                        secret = ffiRemote.secret.toByteString(),
                        salt = ffiRemote.salt.toByteString(),
                        nonce = ffiRemote.nonce.toByteString(),
                        scheme = ffiRemote.scheme,
                        contentLength = ffiRemote.contentLength?.toInt(),
                        filename = ffiRemote.filename
                    )
                }

                is FfiDecodedMessageBody.MultiRemoteAttachment -> {
                    val ffiMulti = body.v1
                    MultiRemoteAttachment(
                        remoteAttachments = ffiMulti.attachments.map { ffiInfo ->
                            RemoteAttachmentInfo(
                                url = ffiInfo.url,
                                filename = ffiInfo.filename ?: "",
                                contentLength = ffiInfo.contentLength?.toLong() ?: 0,
                                contentDigest = ffiInfo.contentDigest,
                                nonce = ffiInfo.nonce.toByteString(),
                                scheme = ffiInfo.scheme,
                                salt = ffiInfo.salt.toByteString(),
                                secret = ffiInfo.secret.toByteString()
                            )
                        }
                    )
                }

                is FfiDecodedMessageBody.TransactionReference -> {
                    val ffiTx = body.v1
                    TransactionReference(
                        namespace = ffiTx.namespace,
                        networkId = ffiTx.networkId,
                        reference = ffiTx.reference,
                        metadata = ffiTx.metadata?.let { meta ->
                            TransactionReference.Metadata(
                                transactionType = meta.transactionType,
                                currency = meta.currency,
                                amount = meta.amount,
                                decimals = meta.decimals,
                                fromAddress = meta.fromAddress,
                                toAddress = meta.toAddress
                            )
                        }
                    )
                }

                is FfiDecodedMessageBody.WalletSendCalls -> {
                    body.v1
                }

                is FfiDecodedMessageBody.GroupUpdated -> {
                    // Return the raw FFI type as GroupUpdatedCodec uses proto types
                    body.v1
                }

                is FfiDecodedMessageBody.Custom -> {
                    val encodedContent = EncodedContent.parseFrom(body.v1.content)
                    encodedContent.decoded<Any>()
                }

                else -> null
            }
        }
    }
}
