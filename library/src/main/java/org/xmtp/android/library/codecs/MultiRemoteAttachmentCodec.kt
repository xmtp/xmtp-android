package uniffi.xmtpv3.org.xmtp.android.library.codecs

import android.util.Log
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.ContentTypeId
import org.xmtp.android.library.codecs.ContentTypeIdBuilder
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.LocalAttachment
import uniffi.xmtpv3.FfiEncryptedEncodedContent
import uniffi.xmtpv3.FfiMultiEncryptedAttachment
import uniffi.xmtpv3.FfiMultiRemoteAttachment
import uniffi.xmtpv3.FfiReaction
import uniffi.xmtpv3.FfiReactionAction
import uniffi.xmtpv3.FfiRemoteAttachmentInfo
import uniffi.xmtpv3.decodeMultiRemoteAttachment
import uniffi.xmtpv3.decodeReaction
import uniffi.xmtpv3.decryptMultiEncryptedAttachment
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
        fun generateMultiRemoteAttachment(multiEncryptedAttachment: FfiMultiEncryptedAttachment, urls: List<String>, scheme: String): FfiMultiRemoteAttachment? {
            if (multiEncryptedAttachment.encryptedAttachments.size != urls.size) {
                return null
            }
    
            var remoteAttachmentInfos: MutableList<FfiRemoteAttachmentInfo> = ArrayList()
            for (index in multiEncryptedAttachment.encryptedAttachments.indices) {
                val remoteAttachmentInfo = FfiRemoteAttachmentInfo(
                    url = urls[index],
                    contentDigest = multiEncryptedAttachment.encryptedAttachments[index].contentDigest,
                    nonce = multiEncryptedAttachment.encryptedAttachments[index].nonce,
                    scheme = scheme,
                    filename = multiEncryptedAttachment.encryptedAttachments[index].filename ?: "default attachment name",
                    salt = multiEncryptedAttachment.encryptedAttachments[index].salt
                )
                remoteAttachmentInfos.add(remoteAttachmentInfo)
            }
            return FfiMultiRemoteAttachment(
                secret = multiEncryptedAttachment.secret,
                attachments = remoteAttachmentInfos,
                numAttachments = multiEncryptedAttachment.encryptedAttachments.size.toUInt(),
                maxAttachmentContentLength = null
            )
        }

        fun decryptMultiRemoteAttachment(ffiMultiRemoteAttachment: FfiMultiRemoteAttachment, encryptedByteArrayList: List<ByteArray>): List<EncodedContent> {
            var encodedContentList: MutableList<EncodedContent> = ArrayList()
            var encryptedAttachmentList: MutableList<FfiEncryptedEncodedContent> = ArrayList()
            for (index in ffiMultiRemoteAttachment.attachments.indices) {
                val encryptedAttachment: FfiEncryptedEncodedContent = FfiEncryptedEncodedContent(
                    payload = encryptedByteArrayList[index].also { 
                        Log.d("XMTP", "MultiRemoteAttachment payload[${index}]: ${it.contentToString()}")
                    },
                    contentDigest = ffiMultiRemoteAttachment.attachments[index].contentDigest,
                    nonce = ffiMultiRemoteAttachment.attachments[index].nonce.also {
                        Log.d("XMTP", "MultiRemoteAttachment nonce[${index}]: ${it.contentToString()}")
                    },
                    filename = ffiMultiRemoteAttachment.attachments[index].filename,
                    salt = ffiMultiRemoteAttachment.attachments[index].salt.also {
                        Log.d("XMTP", "MultiRemoteAttachment salt[${index}]: ${it.contentToString()}")
                    },
                    contentLength = null
                )
                encryptedAttachmentList.add(encryptedAttachment)
            }
            val multiEncryptedAttachment = FfiMultiEncryptedAttachment(
                secret = ffiMultiRemoteAttachment.secret.also {
                    Log.d("XMTP", "MultiRemoteAttachment SECRET[]: ${it.contentToString()}")
                },
                encryptedAttachments = encryptedAttachmentList,
                numAttachments = ffiMultiRemoteAttachment.numAttachments,
                maxAttachmentContentLength = ffiMultiRemoteAttachment.maxAttachmentContentLength
            )
            val decryptedEncodedContentBytesList = decryptMultiEncryptedAttachment(multiEncryptedAttachment)
            for (decryptedEncodedContentBytes in decryptedEncodedContentBytesList) {
                val encodedContent = EncodedContent.parseFrom(decryptedEncodedContentBytes)
                encodedContentList.add(encodedContent)
            }
            return encodedContentList
        }
    }
}


