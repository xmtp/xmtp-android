package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.ContentTypeReply
import org.xmtp.android.library.codecs.ContentTypeText
import org.xmtp.android.library.codecs.Reply
import org.xmtp.android.library.codecs.ReplyCodec

@RunWith(AndroidJUnit4::class)
class ReplyTest {

    @Test
    fun testCanUseReplyCodec() {
        Client.register(codec = ReplyCodec())

        val fixtures = fixtures()
        val aliceClient = fixtures.alixClient
        val aliceConversation = runBlocking {
            aliceClient.conversations.newConversation(fixtures.boClient.inboxId)
        }

        runBlocking { aliceConversation.send(text = "hey alice 2 bob") }

        val messageToReact = runBlocking { aliceConversation.messages()[0] }

        val attachment = Reply(
            reference = messageToReact.id,
            content = "Hello",
            contentType = ContentTypeText
        )

        runBlocking {
            aliceConversation.send(
                content = attachment,
                options = SendOptions(contentType = ContentTypeReply),
            )
        }
        val messages = runBlocking { aliceConversation.messages() }
        assertEquals(messages.size, 3)
        if (messages.size == 3) {
            val content: Reply? = messages.first().content()
            assertEquals("Hello", content?.content)
            assertEquals(messageToReact.id, content?.reference)
            assertEquals(ContentTypeText, content?.contentType)
        }
    }
}
