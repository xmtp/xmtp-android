package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.EditMessage
import org.xmtp.android.library.codecs.TextCodec

@RunWith(AndroidJUnit4::class)
@Ignore("enable when edit message is released")
class EditMessageTest : BaseInstrumentedTest() {
    private lateinit var fixtures: TestFixtures
    private lateinit var alixClient: Client
    private lateinit var boClient: Client
    private lateinit var caroClient: Client

    @Before
    override fun setUp() {
        super.setUp()
        fixtures = runBlocking { createFixtures() }
        alixClient = fixtures.alixClient
        boClient = fixtures.boClient
        caroClient = fixtures.caroClient
    }

    @Test
    fun testSenderCanEditOwnMessage() {
        val alixGroup =
            runBlocking {
                alixClient.conversations.newGroup(listOf(boClient.inboxId))
            }

        val originalText = "Hello, this message will be edited"
        val messageId =
            runBlocking {
                alixGroup.send(originalText)
            }

        runBlocking { alixGroup.sync() }
        var messages = runBlocking { alixGroup.messages() }
        assertTrue(messages.any { it.id == messageId })

        val editedText = "Hello, this message has been edited"
        val editedContent = TextCodec().encode(editedText)
        val editMessageId =
            runBlocking {
                alixGroup.editMessage(messageId, editedContent.toByteArray())
            }
        assertNotNull(editMessageId)

        runBlocking { alixGroup.sync() }
        messages = runBlocking { alixGroup.messages() }
        assertTrue(messages.any { it.id == editMessageId })
    }

    @Test
    fun testRegularUserCannotEditOthersMessage() {
        val alixGroup =
            runBlocking {
                alixClient.conversations.newGroup(listOf(boClient.inboxId))
            }

        runBlocking { boClient.conversations.sync() }
        val boGroup =
            runBlocking {
                boClient.conversations.listGroups().first { it.id == alixGroup.id }
            }

        val messageId =
            runBlocking {
                alixGroup.send("Hello from Alix")
            }

        runBlocking {
            alixGroup.sync()
            boGroup.sync()
        }

        val editedContent = TextCodec().encode("Edited by Bo")

        assertThrows(XMTPException::class.java) {
            runBlocking {
                boGroup.editMessage(messageId, editedContent.toByteArray())
            }
        }
    }

    @Test
    fun testEditMessageInDm() {
        val alixDm =
            runBlocking {
                alixClient.conversations.findOrCreateDm(boClient.inboxId)
            }

        val originalText = "Hello in DM"
        val messageId =
            runBlocking {
                alixDm.send(originalText)
            }

        runBlocking { alixDm.sync() }
        var messages = runBlocking { alixDm.messages() }
        assertTrue(messages.any { it.id == messageId })

        val editedText = "Edited hello in DM"
        val editedContent = TextCodec().encode(editedText)
        val editMessageId =
            runBlocking {
                alixDm.editMessage(messageId, editedContent.toByteArray())
            }
        assertNotNull(editMessageId)

        runBlocking { alixDm.sync() }
        messages = runBlocking { alixDm.messages() }
        assertTrue(messages.any { it.id == editMessageId })
    }

    @Test
    fun testEditMessageViaConversation() {
        val alixGroup =
            runBlocking {
                alixClient.conversations.newGroup(listOf(boClient.inboxId))
            }

        val conversation: Conversation = Conversation.Group(alixGroup)

        val messageId =
            runBlocking {
                conversation.send("Hello via conversation")
            }

        val editedContent = TextCodec().encode("Edited via conversation")
        val editMessageId =
            runBlocking {
                conversation.editMessage(messageId, editedContent.toByteArray())
            }
        assertNotNull(editMessageId)
    }

    @Test
    fun testEditMessageWithInvalidId() {
        val alixGroup =
            runBlocking {
                alixClient.conversations.newGroup(listOf(boClient.inboxId))
            }

        val editedContent = TextCodec().encode("Edited content")

        assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.editMessage(
                    "0000000000000000000000000000000000000000000000000000000000000000",
                    editedContent.toByteArray(),
                )
            }
        }
    }

    @Test
    fun testReceiverSeesEditedMessageContent() {
        val alixGroup =
            runBlocking {
                alixClient.conversations.newGroup(listOf(boClient.inboxId))
            }

        runBlocking { boClient.conversations.sync() }
        val boGroup =
            runBlocking {
                boClient.conversations.listGroups().first { it.id == alixGroup.id }
            }

        val originalText = "Original message content"
        val messageId =
            runBlocking {
                alixGroup.send(originalText)
            }

        runBlocking {
            alixGroup.sync()
            boGroup.sync()
        }

        var boEnrichedMessages = runBlocking { boGroup.enrichedMessages() }
        val boOriginalEnriched = boEnrichedMessages.find { it.id == messageId }
        assertNotNull(boOriginalEnriched)
        assertEquals(originalText, boOriginalEnriched?.content<String>())

        val editedText = "Edited message content"
        val editedContent = TextCodec().encode(editedText)
        runBlocking {
            alixGroup.editMessage(messageId, editedContent.toByteArray())
            alixGroup.sync()
        }

        runBlocking { boGroup.sync() }

        boEnrichedMessages = runBlocking { boGroup.enrichedMessages() }
        val boEnrichedAfterEdit = boEnrichedMessages.find { it.id == messageId }

        assertNotNull(boEnrichedAfterEdit)

        // The enriched message should now show the edited content
        assertEquals(editedText, boEnrichedAfterEdit?.content<String>())

        // Verify the content type is still text (the original type is preserved)
        assertEquals("xmtp.org", boEnrichedAfterEdit?.contentTypeId?.authorityId)
        assertEquals("text", boEnrichedAfterEdit?.contentTypeId?.typeId)
    }

    @Test
    fun testMultipleEditsShowLatest() {
        val alixGroup =
            runBlocking {
                alixClient.conversations.newGroup(listOf(boClient.inboxId))
            }

        runBlocking { boClient.conversations.sync() }
        val boGroup =
            runBlocking {
                boClient.conversations.listGroups().first { it.id == alixGroup.id }
            }

        val originalText = "Version 1"
        val messageId =
            runBlocking {
                alixGroup.send(originalText)
            }

        runBlocking {
            alixGroup.sync()
            boGroup.sync()
        }

        // First edit
        val edit1Text = "Version 2"
        val edit1Content = TextCodec().encode(edit1Text)
        runBlocking {
            alixGroup.editMessage(messageId, edit1Content.toByteArray())
            alixGroup.sync()
        }

        // Second edit
        val edit2Text = "Version 3"
        val edit2Content = TextCodec().encode(edit2Text)
        runBlocking {
            alixGroup.editMessage(messageId, edit2Content.toByteArray())
            alixGroup.sync()
        }

        runBlocking { boGroup.sync() }

        val boEnrichedMessages = runBlocking { boGroup.enrichedMessages() }
        val boEnrichedMessage = boEnrichedMessages.find { it.id == messageId }

        assertNotNull(boEnrichedMessage)
        // Should show the latest edit
        assertEquals(edit2Text, boEnrichedMessage?.content<String>())
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
