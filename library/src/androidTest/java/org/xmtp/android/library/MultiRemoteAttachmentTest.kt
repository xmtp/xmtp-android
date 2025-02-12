package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.protobuf.kotlin.toByteStringUtf8
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.AttachmentCodec
import org.xmtp.android.library.codecs.ContentTypeText
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.id
import org.xmtp.android.library.messages.walletAddress
import uniffi.xmtpv3.FfiEncryptAttachmentResult
import uniffi.xmtpv3.FfiMultiRemoteAttachment
import uniffi.xmtpv3.FfiRemoteAttachmentInfo
import uniffi.xmtpv3.org.xmtp.android.library.codecs.ContentTypeMultiRemoteAttachment
import uniffi.xmtpv3.org.xmtp.android.library.codecs.MultiRemoteAttachmentCodec
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class MultiRemoteAttachmentTest {

    private val encryptedPayloadUrls = HashMap<String, ByteArray>()

    private fun testUploadEncryptedPayload(encryptedPayload: ByteArray): String {
        val randomUrl: String = "https://" + Random(encryptedPayload.hashCode()).nextInt(0,1000000)
        encryptedPayloadUrls.put(randomUrl, encryptedPayload)
        return randomUrl
    }

    @Test
    fun testCanUseMultiRemoteAttachmentCodec() {
        Client.register(codec = AttachmentCodec())
        Client.register(codec = MultiRemoteAttachmentCodec())
        val attachment1 = Attachment(
            filename = "test1.txt",
            mimeType = "text/plain",
            data = "hello world".toByteStringUtf8(),
        )

        val attachment2 = Attachment(
            filename = "test2.txt",
            mimeType = "text/plain",
            data = "hello world".toByteStringUtf8(),
        )

        val attachmentCodec = AttachmentCodec()
        val remoteAttachmentInfos: MutableList<FfiRemoteAttachmentInfo> = ArrayList()

        for (attachment: Attachment in listOf(attachment1, attachment2)) {
            val encodedBytes = attachmentCodec.encode(attachment).toByteArray()
            val encryptedAttachment = MultiRemoteAttachmentCodec.encryptBytesForLocalAttachment(encodedBytes, attachment.filename)
            val url = testUploadEncryptedPayload(encryptedAttachment.payload)
            val remoteAttachmentInfo = MultiRemoteAttachmentCodec.buildRemoteAttachmentInfo(encryptedAttachment, url, "https://")
            remoteAttachmentInfos.add(remoteAttachmentInfo)
        }

        val multiRemoteAttachment = MultiRemoteAttachmentCodec.buildMultiRemoteAttachment(remoteAttachmentInfos)
        val fixtures = fixtures()
        val aliceClient = fixtures.alixClient
        val aliceConversation = runBlocking {
            aliceClient.conversations.newConversation(fixtures.bo.walletAddress)
        }
        runBlocking {
            aliceConversation.send(
                content = multiRemoteAttachment,
                options = SendOptions(contentType = ContentTypeMultiRemoteAttachment),
            )
        }

        val messages = runBlocking { aliceConversation.messages() }
        assertEquals(messages.size, 1)

        // Below steps outlines how to handle receiving a MultiRemoteAttachment message
        if (messages.size == 1 && messages[0].encodedContent.type.id.equals(ContentTypeMultiRemoteAttachment)) {
            val loadedMultiRemoteAttachment: FfiMultiRemoteAttachment = messages[0].content()!!

            val textAttachments: MutableList<Attachment> = ArrayList()

            for (remoteAttachmentInfo: FfiRemoteAttachmentInfo in loadedMultiRemoteAttachment.attachments) {
                val url = remoteAttachmentInfo.url
                // Simulate Download
                val encryptedPayload: ByteArray = encryptedPayloadUrls[url]!!
                // Combine encrypted payload with RemoteAttachmentInfo
                val encryptedAttachment: FfiEncryptAttachmentResult = MultiRemoteAttachmentCodec.buildEncryptAttachmentResult(remoteAttachmentInfo, encryptedPayload)
                // Decrypt payload
                val decryptedBytes: ByteArray = MultiRemoteAttachmentCodec.decryptAttachment(encryptedAttachment)
                // Convert bytes to EncodedContent
                val encodedContent = EncodedContent.parseFrom(decryptedBytes)
                assertEquals(encodedContent.type.id, ContentTypeText.id)
                // Convert EncodedContent to Attachment
                val attachment = attachmentCodec.decode(encodedContent)
                textAttachments.add(attachment)
            }

            assertEquals(textAttachments[0].filename, "test1.txt")
            assertEquals(textAttachments[1].filename, "test2.txt")
        } else {
            AssertionError("expected a MultiRemoteAttachment message")
        }
    }
}
