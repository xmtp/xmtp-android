package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import com.google.protobuf.kotlin.toByteStringUtf8
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.AttachmentCodec
import org.xmtp.android.library.codecs.ContentTypeMultiRemoteAttachment
import org.xmtp.android.library.codecs.ContentTypeText
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.EncryptedEncodedContent
import org.xmtp.android.library.codecs.MultiRemoteAttachmentCodec
import org.xmtp.android.library.codecs.RemoteAttachment
import org.xmtp.android.library.codecs.id
import org.xmtp.android.library.messages.walletAddress
import uniffi.xmtpv3.FfiMultiRemoteAttachment
import java.net.URL
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class MultiRemoteAttachmentTest {

    private val encryptedPayloadUrls = HashMap<String, ByteArray>()

    private fun testUploadEncryptedPayload(encryptedPayload: ByteArray): String {
        val randomUrl: String = "https://" + Random(encryptedPayload.hashCode()).nextInt(0, 1000000)
        encryptedPayloadUrls.put(randomUrl, encryptedPayload)
        return randomUrl
    }
    @Test
    fun testBenchMarkEncryptionDecryption() {
        // Generate 20MB of random data (20 * 1024 * 1024 bytes)
        val randomData = ByteArray(10 * 1024 * 1024).apply {
            Random.nextBytes(this)
        }
        // Generate 20MB of random data as ByteString
        val randomDataByteString: ByteString = randomData.toByteString()
        Client.register(codec = AttachmentCodec())

        val attachment = Attachment(
            filename = "test1.txt",
            mimeType = "text/plain",
            data = randomDataByteString
        )

        val encodedBytes = AttachmentCodec().encode(attachment).toByteArray()

        val startTime = System.nanoTime()
        val encodedEncryptedContent = RemoteAttachment.encodeEncryptedBytes(
            encodedContent = encodedBytes,
            filename = attachment.filename
        )
        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000.0 // Convert nanoseconds to milliseconds
        println("Encryption time for 20MB in Kotlin: $durationMs ms")

        val startTime2 = System.nanoTime()
        val encodedEncryptedContent2 = MultiRemoteAttachmentCodec.encryptBytesForLocalAttachment(
            encodedBytes, "test1.txt"
        )
        val endTime2 = System.nanoTime()
        val durationMs2 = (endTime2 - startTime2) / 1_000_000.0 // Convert nanoseconds to milliseconds
        println("Encryption time for 20MB in Rust: $durationMs2 ms")
        assertEquals(encodedEncryptedContent.filename, encodedEncryptedContent2.filename)

        assert(durationMs2 - durationMs < 1)
        assert(true)
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
        val remoteAttachmentInfos: MutableList<RemoteAttachment> = ArrayList()

        for (attachment: Attachment in listOf(attachment1, attachment2)) {
            val encodedBytes = attachmentCodec.encode(attachment).toByteArray()
            val encryptedAttachment = MultiRemoteAttachmentCodec.encryptBytesForLocalAttachment(encodedBytes, attachment.filename)
            val url = testUploadEncryptedPayload(encryptedAttachment.payload.toByteArray())
            val remoteAttachmentInfo = MultiRemoteAttachmentCodec.buildRemoteAttachmentInfo(encryptedAttachment, URL(url))
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

            for (
                remoteAttachment: RemoteAttachment in loadedMultiRemoteAttachment.attachments.map { attachment ->
                    RemoteAttachment(
                        url = URL(attachment.url),
                        filename = attachment.filename,
                        contentDigest = attachment.contentDigest,
                        nonce = attachment.nonce.toByteString(),
                        scheme = attachment.scheme,
                        salt = attachment.salt.toByteString(),
                        secret = attachment.secret.toByteString(),
                        contentLength = attachment.contentLength?.toInt(),
                    )
                }
            ) {
                val url = remoteAttachment.url.toString()
                // Simulate Download
                val encryptedPayload: ByteArray = encryptedPayloadUrls[url]!!
                // Combine encrypted payload with RemoteAttachmentInfo
                val encryptedAttachment: EncryptedEncodedContent = MultiRemoteAttachmentCodec.buildEncryptAttachmentResult(remoteAttachment, encryptedPayload)
                // Decrypt payload
                val encodedContent: EncodedContent = MultiRemoteAttachmentCodec.decryptAttachment(encryptedAttachment)
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
