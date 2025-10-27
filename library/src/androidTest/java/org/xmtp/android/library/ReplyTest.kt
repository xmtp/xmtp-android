package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.ContentTypeReply
import org.xmtp.android.library.codecs.ContentTypeText
import org.xmtp.android.library.codecs.Reply
import org.xmtp.android.library.codecs.ReplyCodec

@RunWith(AndroidJUnit4::class)
class ReplyTest : BaseInstrumentedTest() {
    private lateinit var fixtures: TestFixtures
    private lateinit var alixClient: Client
    private lateinit var boClient: Client

    @org.junit.Before
    override fun setUp() {
        super.setUp()
        fixtures = runBlocking { createFixtures() }
        alixClient = fixtures.alixClient
        boClient = fixtures.boClient
    }

    @Test
    fun testCanUseReplyCodec() {
        Client.register(codec = ReplyCodec())

        val alixConversation =
            runBlocking {
                alixClient.conversations.newConversation(boClient.inboxId)
            }

        runBlocking { alixConversation.send(text = "hey alice 2 bob") }

        val messageToReact = runBlocking { alixConversation.messages()[0] }

        val attachment =
            Reply(
                reference = messageToReact.id,
                content = "Hello",
                contentType = ContentTypeText,
            )

        runBlocking {
            alixConversation.send(
                content = attachment,
                options = SendOptions(contentType = ContentTypeReply),
            )
        }
        val messages = runBlocking { alixConversation.messages() }
        assertEquals(messages.size, 3)
        if (messages.size == 3) {
            val content: Reply? = messages.first().content()
            assertEquals("Hello", content?.content)
            assertEquals(messageToReact.id, content?.reference)
            assertEquals(ContentTypeText, content?.contentType)
        }
    }

//    @Test
//    fun testMessagesV2WithReply() {
//        Client.register(codec = ReplyCodec())
//
//        val aliceGroup = runBlocking { alixClient.conversations.newGroup(listOf(boClient.inboxId)) }
//        runBlocking { boClient.conversations.sync() }
//        val boGroup = runBlocking { boClient.conversations.listGroups().first() }
//
//        runBlocking {
//            val originalMessageId = aliceGroup.send("Original message")
//            boGroup.sync()
//
//            val replyContent =
//                Reply(
//                    reference = originalMessageId,
//                    content = "This is a reply",
//                    contentType = ContentTypeText,
//                )
//
//            boGroup.send(
//                content = replyContent,
//                options = SendOptions(contentType = ContentTypeReply),
//            )
//            aliceGroup.sync()
//        }
//
//        val messagesV2 = runBlocking { aliceGroup.enrichedMessages() }
//        // 2 user messages + 1 GroupUpdated message
//        assertEquals(3, messagesV2.size)
//
//        val replyMessage = messagesV2[0]
//        val replyData = replyMessage.content<org.xmtp.android.library.libxmtp.Reply>()
//        assertNotNull(replyData)
//        assertEquals("This is a reply", replyData?.content)
//        assertNotNull(replyData?.inReplyTo)
//        assertEquals("Original message", replyData?.inReplyTo?.content<String>())
//    }
//
//    @Test
//    fun testMessagesV2WithReplyToDeletedMessage() {
//        Client.register(codec = ReplyCodec())
//
//        val aliceGroup = runBlocking { alixClient.conversations.newGroup(listOf(boClient.inboxId)) }
//        runBlocking { boClient.conversations.sync() }
//        val boGroup = runBlocking { boClient.conversations.listGroups().first() }
//
//        runBlocking {
//            val replyContent =
//                Reply(
//                    reference = "non-existent-message-id",
//                    content = "Reply to deleted",
//                    contentType = ContentTypeText,
//                )
//
//            boGroup.send(
//                content = replyContent,
//                options = SendOptions(contentType = ContentTypeReply),
//            )
//            aliceGroup.sync()
//        }
//
//        val messagesV2 = runBlocking { aliceGroup.enrichedMessages() }
//        // 1 user message + 1 GroupUpdated message
//        assertEquals(2, messagesV2.size)
//
//        val replyMessage = messagesV2[0]
//        val replyData = replyMessage.content<org.xmtp.android.library.libxmtp.Reply>()
//        assertNotNull(replyData)
//        assertEquals("Reply to deleted", replyData?.content)
//        assertNull(replyData?.inReplyTo)
//    }
//
//    @Test
//    fun testMessagesV2WithReplyIncludesReferencedMessage() {
//        Client.register(codec = ReplyCodec())
//
//        val aliceGroup = runBlocking { alixClient.conversations.newGroup(listOf(boClient.inboxId)) }
//        runBlocking { boClient.conversations.sync() }
//        val boGroup = runBlocking { boClient.conversations.listGroups().first() }
//
//        runBlocking {
//            val originalMessageId = aliceGroup.send("Original message")
//            boGroup.sync()
//
//            val textReply =
//                Reply(
//                    reference = originalMessageId,
//                    content = "Text reply",
//                    contentType = ContentTypeText,
//                )
//
//            boGroup.send(content = textReply, options = SendOptions(contentType = ContentTypeReply))
//            aliceGroup.sync()
//        }
//
//        val messagesV2 = runBlocking { aliceGroup.enrichedMessages() }
//        // 2 user messages + 1 GroupUpdated message
//        assertEquals(3, messagesV2.size)
//
//        val replyMessage = messagesV2[0]
//        val replyData = replyMessage.content<org.xmtp.android.library.libxmtp.Reply>()
//        assertNotNull(replyData)
//        assertEquals("Text reply", replyData?.content)
//        assertNotNull(replyData?.inReplyTo)
//    }
}
