package uniffi.xmtpv3.org.xmtp.android.library.codecs

import android.util.Log
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.ContentTypeId
import org.xmtp.android.library.codecs.ContentTypeIdBuilder
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.LocalAttachment
import uniffi.xmtpv3.FfiEncryptAttachmentResult
import uniffi.xmtpv3.FfiMultiRemoteAttachment
import uniffi.xmtpv3.FfiReaction
import uniffi.xmtpv3.FfiReactionAction
import uniffi.xmtpv3.FfiRemoteAttachmentInfo
import uniffi.xmtpv3.decodeMultiRemoteAttachment
import uniffi.xmtpv3.decodeReaction
import uniffi.xmtpv3.encodeMultiRemoteAttachment
import uniffi.xmtpv3.encodeReaction

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

        fun encryptBytesForLocalAttachment(bytesToEncrypt: ByteArray, filename: String?): FfiEncryptAttachmentResult {
            return uniffi.xmtpv3.encryptBytesForLocalAttachment(bytesToEncrypt, filename)
        }

        fun buildRemoteAttachmentInfo(encryptedAttachment: FfiEncryptAttachmentResult, remoteUrl: String, scheme: String): FfiRemoteAttachmentInfo {
            return uniffi.xmtpv3.buildRemoteAttachmentInfo(encryptedAttachment, remoteUrl, scheme)
        }

        fun buildMultiRemoteAttachment(remoteAttachmentInfos: List<FfiRemoteAttachmentInfo>): FfiMultiRemoteAttachment {
            return uniffi.xmtpv3.buildMultiRemoteAttachment(remoteAttachmentInfos)
        }

        fun buildEncryptAttachmentResult(remoteAttachmentInfo: FfiRemoteAttachmentInfo, encryptedPayload: ByteArray): FfiEncryptAttachmentResult {
            return uniffi.xmtpv3.buildEncryptAttachmentResult(remoteAttachmentInfo, encryptedPayload)
        }

        fun decryptAttachment(encryptedAttachment: FfiEncryptAttachmentResult): ByteArray {
            return uniffi.xmtpv3.decryptAttachment(encryptedAttachment)
        }
    }
}


