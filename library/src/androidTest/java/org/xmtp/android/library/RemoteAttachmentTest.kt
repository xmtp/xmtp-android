package org.xmtp.android.library

import com.google.protobuf.kotlin.toByteStringUtf8
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.AttachmentCodec
import org.xmtp.android.library.codecs.ContentTypeAttachment
import org.xmtp.android.library.codecs.ContentTypeRemoteAttachment
import org.xmtp.android.library.codecs.RemoteAttachment
import org.xmtp.android.library.codecs.RemoteAttachmentCodec
import org.xmtp.android.library.codecs.decoded
import org.xmtp.android.library.codecs.id
import java.io.File
import java.net.URL

class RemoteAttachmentTest {

    private lateinit var fixtures: Fixtures
    private lateinit var filesDir: File

    private lateinit var testFetcher: TestFetcher

    @Before
    fun setUp() {
        fixtures = fixtures()
        filesDir = fixtures.context.filesDir
        testFetcher = TestFetcher(filesDir)
    }

    @Test
    fun testEncryptedContentShouldBeDecryptable() {
        Client.register(codec = AttachmentCodec())
        val attachment = Attachment(
            filename = "test.txt",
            mimeType = "text/plain",
            data = "hello world".toByteStringUtf8(),
        )

        val encrypted = RemoteAttachment.Companion.encodeEncrypted(attachment, AttachmentCodec())

        val decrypted = RemoteAttachment.Companion.decryptEncoded(encrypted)
        Assert.assertEquals(ContentTypeAttachment.id, decrypted.type.id)

        val decoded = decrypted.decoded<Attachment>()
        Assert.assertEquals("test.txt", decoded?.filename)
        Assert.assertEquals("text/plain", decoded?.mimeType)
        Assert.assertEquals("hello world", decoded?.data?.toStringUtf8())
    }

    @Test
    fun testCanUseRemoteAttachmentCodec() {
        val attachment = Attachment(
            filename = "test.txt",
            mimeType = "text/plain",
            data = "hello world".toByteStringUtf8(),
        )

        Client.register(codec = AttachmentCodec())
        Client.register(codec = RemoteAttachmentCodec())

        val encodedEncryptedContent = RemoteAttachment.Companion.encodeEncrypted(
            content = attachment,
            codec = AttachmentCodec(),
        )

        File(filesDir, "abcdefg").writeBytes(encodedEncryptedContent.payload.toByteArray())

        val remoteAttachment = RemoteAttachment.Companion.from(
            url = URL("https://abcdefg"),
            encryptedEncodedContent = encodedEncryptedContent,
        )

        remoteAttachment.contentLength = attachment.data.size()
        remoteAttachment.filename = attachment.filename


        val aliceClient = fixtures.alixClient
        val aliceConversation = runBlocking {
            aliceClient.conversations.newConversation(fixtures.boClient.inboxId)
        }

        runBlocking {
            aliceConversation.send(
                content = remoteAttachment,
                options = SendOptions(contentType = ContentTypeRemoteAttachment),
            )
        }

        val messages = runBlocking { aliceConversation.messages() }
        // membership-change and the remote attachment message
        Assert.assertEquals(messages.size, 2)

        if (messages.size == 2) {
            val loadedRemoteAttachment: RemoteAttachment = messages[0].content()!!
            loadedRemoteAttachment.fetcher = TestFetcher(filesDir)
            runBlocking {
                val attachment2: Attachment =
                    loadedRemoteAttachment.load() ?: throw XMTPException("did not get attachment")
                Assert.assertEquals("test.txt", attachment2.filename)
                Assert.assertEquals("text/plain", attachment2.mimeType)
                Assert.assertEquals("hello world".toByteStringUtf8(), attachment2.data)
            }
        }
    }

    @Test
    fun testCannotUseNonHTTPSURL() {
        val attachment = Attachment(
            filename = "test.txt",
            mimeType = "text/plain",
            data = "hello world".toByteStringUtf8(),
        )

        Client.register(codec = AttachmentCodec())
        Client.register(codec = RemoteAttachmentCodec())

        val encodedEncryptedContent = RemoteAttachment.Companion.encodeEncrypted(
            content = attachment,
            codec = AttachmentCodec(),
        )

        File(filesDir, "abcdefg").writeBytes(encodedEncryptedContent.payload.toByteArray())

        Assert.assertThrows(XMTPException::class.java) {
            RemoteAttachment.Companion.from(
                url = URL("http://abcdefg"),
                encryptedEncodedContent = encodedEncryptedContent,
            )
        }
    }

    @Test
    fun testEnsuresContentDigestMatches() {
        val attachment = Attachment(
            filename = "test.txt",
            mimeType = "text/plain",
            data = "hello world".toByteStringUtf8(),
        )

        Client.register(codec = AttachmentCodec())
        Client.register(codec = RemoteAttachmentCodec())

        val encodedEncryptedContent = RemoteAttachment.Companion.encodeEncrypted(
            content = attachment,
            codec = AttachmentCodec(),
        )

        File(filesDir, "abcdefg").writeBytes(encodedEncryptedContent.payload.toByteArray())

        val remoteAttachment = RemoteAttachment.Companion.from(
            url = URL("https://abcdefg"),
            encryptedEncodedContent = encodedEncryptedContent,
        )

        val aliceClient = fixtures.alixClient
        val aliceConversation = runBlocking {
            aliceClient.conversations.newConversation(fixtures.boClient.inboxId)
        }

        runBlocking {
            aliceConversation.send(
                content = remoteAttachment,
                options = SendOptions(contentType = ContentTypeRemoteAttachment),
            )
        }

        val messages = runBlocking { aliceConversation.messages() }
        // membership-change and the remote attachment message
        Assert.assertEquals(messages.size, 2)

        // Tamper with the payload
        File(filesDir, "abcdefg").writeBytes("sup".toByteArray())

        if (messages.size == 2) {
            val loadedRemoteAttachment: RemoteAttachment = messages[0].content()!!
            loadedRemoteAttachment.fetcher = TestFetcher(filesDir)
            Assert.assertThrows(XMTPException::class.java) {
                runBlocking {
                    val attachment: Attachment? = loadedRemoteAttachment.load()
                }
            }
        }
    }
}