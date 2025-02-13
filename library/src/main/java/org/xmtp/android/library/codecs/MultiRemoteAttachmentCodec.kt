package org.xmtp.android.library.codecs

import com.google.protobuf.kotlin.toByteString
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.ContentTypeId
import org.xmtp.android.library.codecs.ContentTypeIdBuilder
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.EncryptedEncodedContent
import org.xmtp.android.library.codecs.RemoteAttachment
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

data class MultiRemoteAttachmentCodec(override var contentType: ContentTypeId = ContentTypeMultiRemoteAttachment) :
    ContentCodec<FfiMultiRemoteAttachment> {

    override fun encode(content: FfiMultiRemoteAttachment): EncodedContent {
        return EncodedContent.parseFrom(encodeMultiRemoteAttachment(content))
    }

    override fun decode(content: EncodedContent): FfiMultiRemoteAttachment {
        return decodeMultiRemoteAttachment(content.toByteArray())
    }

    override fun fallback(content: FfiMultiRemoteAttachment): String = "MultiRemoteAttachment not supported"

    override fun shouldPush(content: FfiMultiRemoteAttachment): Boolean = true

    companion object {

        fun encryptBytesForLocalAttachment(bytesToEncrypt: ByteArray, filename: String): EncryptedEncodedContent {
            return encodeEncryptedBytes(bytesToEncrypt, filename)
        }

        fun buildRemoteAttachmentInfo(encryptedAttachment: EncryptedEncodedContent, remoteUrl: URL): RemoteAttachment {
            return RemoteAttachment.from(remoteUrl, encryptedAttachment)
        }

        fun buildMultiRemoteAttachment(remoteAttachments: List<RemoteAttachment>): FfiMultiRemoteAttachment {
            return FfiMultiRemoteAttachment(attachments = remoteAttachments.map { attachment ->
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
            })
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


