package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.protobuf.kotlin.toByteStringUtf8
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Ignore
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.AttachmentCodec
import org.xmtp.android.library.codecs.ContentTypeAttachment
import org.xmtp.android.library.codecs.ContentTypeReaction
import org.xmtp.android.library.codecs.ContentTypeReactionV2
import org.xmtp.android.library.codecs.ContentTypeRemoteAttachment
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.codecs.ReactionCodec
import org.xmtp.android.library.codecs.ReactionSchema
import org.xmtp.android.library.codecs.ReactionV2Codec
import org.xmtp.android.library.codecs.RemoteAttachment
import org.xmtp.android.library.codecs.RemoteAttachmentCodec
import org.xmtp.android.library.codecs.decoded
import org.xmtp.android.library.codecs.id
import org.xmtp.android.library.libxmtp.Message
import org.xmtp.android.library.messages.walletAddress
import uniffi.xmtpv3.FfiMultiEncryptedAttachment
import uniffi.xmtpv3.FfiMultiRemoteAttachment
import uniffi.xmtpv3.FfiReaction
import uniffi.xmtpv3.FfiReactionAction
import uniffi.xmtpv3.FfiReactionSchema
import uniffi.xmtpv3.decryptMultiEncryptedAttachment
import uniffi.xmtpv3.encryptEncodedContentArray
import uniffi.xmtpv3.org.xmtp.android.library.codecs.ContentTypeMultiRemoteAttachment
import uniffi.xmtpv3.org.xmtp.android.library.codecs.MultiRemoteAttachmentCodec
import uniffi.xmtpv3.org.xmtp.android.library.codecs.MultiRemoteAttachmentCodec.Companion.decryptMultiRemoteAttachment
import java.io.File
import java.net.URL

@RunWith(AndroidJUnit4::class)
class MultiRemoteAttachmentTest {

    val attachment1 = Attachment(
        filename = "test123.txt",
        mimeType = "text/plain",
        data = "hello world".toByteStringUtf8(),
    )

    val attachment2 = Attachment(
        filename = "test456.txt",
        mimeType = "text/plain",
        data = "hello world".toByteStringUtf8(),
    )

    lateinit var encryptedAttachment1: ByteArray
    lateinit var encryptedAttachment2: ByteArray

    @Test
    fun testEncryptedContentShouldBeDecryptable() {
        Client.register(codec = AttachmentCodec())
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

        val codec = AttachmentCodec()
        val encodedContent1Bytes = codec.encode(attachment1).toByteArray()
        val encodedContent2Bytes = codec.encode(attachment2).toByteArray()

        val multiEncryptedAttachment = encryptEncodedContentArray(
            listOf(
                codec.encode(attachment1).toByteArray(),
                codec.encode(attachment2).toByteArray()
            )
        )


        val attachmentBytesList = decryptMultiEncryptedAttachment(multiEncryptedAttachment)
        val encodedContent1 = EncodedContent.parseFrom(attachmentBytesList[0])
        val encodedContent2 = EncodedContent.parseFrom(attachmentBytesList[1])

        Assert.assertEquals(encodedContent1.type, ContentTypeAttachment)
        val decoded = encodedContent1.decoded<Attachment>()
        Assert.assertEquals("test1.txt", decoded?.filename)
        val decoded2 = encodedContent2.decoded<Attachment>()
        Assert.assertEquals("test2.txt", decoded2?.filename)
    }

    @Test
    fun testCanUseMultiRemoteAttachmentCodec() {

        Client.register(codec = AttachmentCodec())
        Client.register(codec = MultiRemoteAttachmentCodec())

        val codec = AttachmentCodec()

        val multiEncryptedAttachment: FfiMultiEncryptedAttachment = encryptEncodedContentArray(
            listOf(
                codec.encode(attachment1).toByteArray(),
                codec.encode(attachment2).toByteArray()
            )
        )

        encryptedAttachment1 = multiEncryptedAttachment.encryptedAttachments[0].payload
        encryptedAttachment2 = multiEncryptedAttachment.encryptedAttachments[1].payload

        val uploadLocations = listOf("https://uploadlocation1.com", "https://uploadLocation2.com")
        val multiRemoteAttachment: FfiMultiRemoteAttachment =
            MultiRemoteAttachmentCodec.generateMultiRemoteAttachment(
                multiEncryptedAttachment,
                uploadLocations,
                "https://"
            )!!

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
        Assert.assertEquals(messages.size, 1)

        // Below steps outlines how to handle receiving a MultiRemoteAttachment message
        if (messages.size == 1 && messages[0].encodedContent.type.id.equals(ContentTypeMultiRemoteAttachment)) {
            val loadedMultiRemoteAttachment: FfiMultiRemoteAttachment = messages[0].content()!!

            // Step 1 => utilize the URLs in loadedMultiRemoteAttachment to download 2 encrypted payloads.
            // Next, make sure that these 2 encrypted payloads match the two encrypted payloads that were an input to `generateMultiRemoteAttachment`
            val url1 = loadedMultiRemoteAttachment.attachments[0].url
            val url2 = loadedMultiRemoteAttachment.attachments[1].url
            val download1 = simulateDownload(url1)
            val download2 = simulateDownload(url2)
            Assert.assertEquals(download1.contentToString(), multiEncryptedAttachment.encryptedAttachments[0].payload.contentToString())
            Assert.assertEquals(download2.contentToString(), multiEncryptedAttachment.encryptedAttachments[1].payload.contentToString())

            // Step 2 => call the function decryptMultiRemoteAttachment with the two arguments of a) the multiRemoteAttachment
            // and b) a list of the encrypted payloads that you downloaded
            val encodedContentList: List<EncodedContent> = decryptMultiRemoteAttachment(
                loadedMultiRemoteAttachment,
                listOf(download1, download2)
            )

            // Step 3 => verify that the encoded content returned matched the encoded content passed in
            // through the multiRemoteAttachment to begin with
            runBlocking {
                val attachment1: Attachment? =
                    encodedContentList[0].decoded<Attachment>()
                Assert.assertEquals("test123.txt", attachment1?.filename)
            }
        } else {
            AssertionError("expected a MultiRemoteAttachment message")
        }
    }

    fun simulateDownload(url: String): ByteArray {
        when (url) {
            "https://uploadlocation1.com" -> {
                return encryptedAttachment1
            }

            "https://uploadLocation2.com" -> {
                return encryptedAttachment2
            }
        }
        return byteArrayOf()
    }
}
