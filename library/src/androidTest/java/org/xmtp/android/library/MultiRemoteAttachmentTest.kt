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

    val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testCanUseMultiRemoteAttachmentCodec() {
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

        Client.register(codec = AttachmentCodec())
        Client.register(codec = MultiRemoteAttachmentCodec())

        val codec = AttachmentCodec()

        val multiEncryptedAttachment = encryptEncodedContentArray(
            listOf(
                codec.encode(attachment1).toByteArray(),
                codec.encode(attachment2).toByteArray()
            )
        )

//        val decryptedEncodedContentBytesList = decryptMultiEncryptedAttachment(multiEncryptedAttachment)
//        var encodedContentList: MutableList<EncodedContent> = ArrayList()
//        for (decryptedEncodedContentBytes in decryptedEncodedContentBytesList) {
//            val encodedContent = EncodedContent.parseFrom(decryptedEncodedContentBytes)
//            encodedContentList.add(encodedContent)
//        }
//        runBlocking {
//                val attachment1: Attachment? =
//                    encodedContentList[0].decoded<Attachment>()
//                Assert.assertEquals("test123.txt", attachment1?.filename)
//            }


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
//
//        if (messages.size == 1) {
//            val loadedMultiRemoteAttachment: FfiMultiRemoteAttachment = messages[0].content()!!
//            val downloadedEncryptedAttachment1 =
//                simulateDownload(loadedMultiRemoteAttachment.attachments[0].url)
//            val downloadedEncryptedAttachment2 =
//                simulateDownload(loadedMultiRemoteAttachment.attachments[1].url)
//
//            val encodedContentList: List<EncodedContent> = decryptMultiRemoteAttachment(
//                loadedMultiRemoteAttachment.also {
//                    Log.d("XMTP", "MultiRemoteAttachment nonce[PLZ]: ${it.secret.contentToString()}")
//                },
//                listOf(downloadedEncryptedAttachment1, downloadedEncryptedAttachment2)
//            )
//
//
//            runBlocking {
//                val attachment1: Attachment? =
//                    encodedContentList[0].decoded<Attachment>()
//                Assert.assertEquals("test123.txt", attachment1?.filename)
//            }
//        }
    }

    fun simulateDownload(url: String): ByteArray {
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

        val multiEncryptedAttachment = encryptEncodedContentArray(
            listOf(
                codec.encode(attachment1).toByteArray(),
                codec.encode(attachment2).toByteArray()
            )
        )
        when (url) {
            "https://uploadlocation1.com" -> {
                return multiEncryptedAttachment.encryptedAttachments[0].payload.also {
                    Log.d("XMTP", "MultiRemoteAttachment payload[https://uploadlocation1.com]: ${it.contentToString()}")
                }
            }

            "https://uploadLocation2.com" -> {
                return multiEncryptedAttachment.encryptedAttachments[1].payload.also {
                    Log.d("XMTP", "MultiRemoteAttachment payload[https://uploadlocation2.com]: ${it.contentToString()}")
                }
            }
        }
        return byteArrayOf()
    }
}
