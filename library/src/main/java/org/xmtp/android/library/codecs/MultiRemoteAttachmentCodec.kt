package org.xmtp.android.library.codecs

import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import org.xmtp.android.library.codecs.RemoteAttachment.Companion.decryptEncoded
import org.xmtp.android.library.codecs.RemoteAttachment.Companion.encodeEncryptedBytes
import uniffi.xmtpv3.FfiMultiRemoteAttachment
import uniffi.xmtpv3.FfiRemoteAttachmentInfo
import uniffi.xmtpv3.decodeMultiRemoteAttachment
import uniffi.xmtpv3.encodeMultiRemoteAttachment
import java.net.URL

val ContentTypeMultiRemoteAttachment = ContentTypeIdBuilder.builderFromAuthorityId(
    "xmtp.org",
    "multiRemoteStaticAttachment",
    versionMajor = 1,
    versionMinor = 0,
)

data class MultiRemoteAttachment(
    val remoteAttachments: List<RemoteAttachmentInfo>
)

data class RemoteAttachmentInfo(
    val url: String,
    val filename: String,
    val contentLength: Long,
    val contentDigest: String,
    val nonce: ByteString,
    val scheme: String,
    val salt: ByteString,
    val secret: ByteString
)

data class MultiRemoteAttachmentCodec(override var contentType: ContentTypeId = ContentTypeMultiRemoteAttachment) :
    ContentCodec<MultiRemoteAttachment> {

    override fun encode(content: MultiRemoteAttachment): EncodedContent {
        val ffiMultiRemoteAttachment = FfiMultiRemoteAttachment(
            attachments = content.remoteAttachments.map { attachment ->
                FfiRemoteAttachmentInfo(
                    url = attachment.url,
                    filename = attachment.filename,
                    contentDigest = attachment.contentDigest,
                    nonce = attachment.nonce.toByteArray(),
                    scheme = attachment.scheme,
                    salt = attachment.salt.toByteArray(),
                    secret = attachment.secret.toByteArray(),
                    contentLengthKb = attachment.contentLength?.toUInt(),
                )
            }
        )
        return EncodedContent.parseFrom(encodeMultiRemoteAttachment(ffiMultiRemoteAttachment))
    }

    override fun decode(content: EncodedContent): MultiRemoteAttachment {
        val ffiMultiRemoteAttachment = decodeMultiRemoteAttachment(content.toByteArray())
        return MultiRemoteAttachment(
            remoteAttachments = ffiMultiRemoteAttachment.attachments.map { attachment ->
                RemoteAttachmentInfo(
                    url = attachment.url,
                    filename = attachment.filename ?: "",
                    contentLength = attachment.contentLengthKb?.toLong() ?: 0,
                    contentDigest = attachment.contentDigest,
                    nonce = attachment.nonce.toProtoByteString(),
                    scheme = attachment.scheme,
                    salt = attachment.salt.toProtoByteString(),
                    secret = attachment.secret.toProtoByteString(),
                )
            }
        )
    }

    override fun fallback(content: MultiRemoteAttachment): String = "MultiRemoteAttachment not supported"

    override fun shouldPush(content: MultiRemoteAttachment): Boolean = true

    companion object {

        fun encryptBytesForLocalAttachment(bytesToEncrypt: ByteArray, filename: String): EncryptedEncodedContent {
            return encodeEncryptedBytes(bytesToEncrypt, filename)
        }

        fun buildRemoteAttachmentInfo(encryptedAttachment: EncryptedEncodedContent, remoteUrl: URL): RemoteAttachment {
            return RemoteAttachment.from(remoteUrl, encryptedAttachment)
        }

        fun buildMultiRemoteAttachment(remoteAttachments: List<RemoteAttachment>): FfiMultiRemoteAttachment {
            return FfiMultiRemoteAttachment(
                attachments = remoteAttachments.map { attachment ->
                    FfiRemoteAttachmentInfo(
                        url = attachment.url.toString(),
                        filename = attachment.filename,
                        contentDigest = attachment.contentDigest,
                        nonce = attachment.nonce.toByteArray(),
                        scheme = attachment.scheme,
                        salt = attachment.salt.toByteArray(),
                        secret = attachment.secret.toByteArray(),
                        contentLengthKb = attachment.contentLength?.toUInt(),
                    )
                }
            )
        }

        fun buildEncryptAttachmentResult(remoteAttachment: RemoteAttachment, encryptedPayload: ByteArray): EncryptedEncodedContent {
            return EncryptedEncodedContent(
                remoteAttachment.contentDigest,
                remoteAttachment.secret,
                remoteAttachment.salt,
                remoteAttachment.nonce,
                encryptedPayload.toByteString(),
                remoteAttachment.contentLength,
                remoteAttachment.filename,
            )
        }

        fun decryptAttachment(encryptedAttachment: EncryptedEncodedContent): EncodedContent {
            val decrypted = decryptEncoded(encryptedAttachment)

            return decrypted
        }
    }
}

private fun ByteArray.toProtoByteString(): ByteString {
    return ByteString.copyFrom(this)
}
