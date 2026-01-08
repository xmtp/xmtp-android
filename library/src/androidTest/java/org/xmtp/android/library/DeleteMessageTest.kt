package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.ContentTypeReaction
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.codecs.ReactionCodec
import org.xmtp.android.library.codecs.ReactionSchema
import org.xmtp.android.library.codecs.ContentTypeReply
import org.xmtp.android.library.codecs.Reply
import org.xmtp.android.library.codecs.ReplyCodec
import org.xmtp.android.library.libxmtp.GroupPermissionPreconfiguration
import uniffi.xmtpv3.GenericException

@RunWith(AndroidJUnit4::class)
class DeleteMessageTest : BaseInstrumentedTest() {
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
    fun testSenderCanDeleteOwnMessage() {
        // Create a group with alix and bo
        val alixGroup = runBlocking {
            alixClient.conversations.newGroup(listOf(boClient.inboxId))
        }

        // Alix sends a message
        val messageId = runBlocking {
            alixGroup.send("Hello, this message will be deleted")
        }

        // Verify message exists
        runBlocking { alixGroup.sync() }
        var messages = runBlocking { alixGroup.messages() }
        assertTrue(messages.any { it.id == messageId })

        // Alix deletes own message
        val deletionMessageId = runBlocking {
            alixGroup.deleteMessage(messageId)
        }
        assertNotNull(deletionMessageId)

        // Sync and verify deletion
        runBlocking { alixGroup.sync() }
        messages = runBlocking { alixGroup.messages() }

        // The deletion message should exist
        assertTrue(messages.any { it.id == deletionMessageId })
    }

    @Test
    fun testSuperAdminCanDeleteOthersMessage() {
        // Alix creates a group (becomes super admin) with bo
        val alixGroup = runBlocking {
            alixClient.conversations.newGroup(listOf(boClient.inboxId))
        }

        // Bo syncs and gets the group
        runBlocking { boClient.conversations.sync() }
        val boGroup = runBlocking {
            boClient.conversations.listGroups().first { it.id == alixGroup.id }
        }

        // Bo sends a message
        val messageId = runBlocking {
            boGroup.send("Hello from Bo")
        }

        // Sync both
        runBlocking {
            alixGroup.sync()
            boGroup.sync()
        }

        // Verify alix is super admin
        assertTrue(runBlocking { alixGroup.isSuperAdmin(alixClient.inboxId) })

        // Alix (super admin) deletes Bo's message
        val deletionMessageId = runBlocking {
            alixGroup.deleteMessage(messageId)
        }
        assertNotNull(deletionMessageId)
    }

    @Test
    fun testRegularUserCannotDeleteOthersMessage() {
        // Alix creates a group with bo
        val alixGroup = runBlocking {
            alixClient.conversations.newGroup(listOf(boClient.inboxId))
        }

        // Bo syncs and gets the group
        runBlocking { boClient.conversations.sync() }
        val boGroup = runBlocking {
            boClient.conversations.listGroups().first { it.id == alixGroup.id }
        }

        // Alix sends a message
        val messageId = runBlocking {
            alixGroup.send("Hello from Alix")
        }

        // Sync both
        runBlocking {
            alixGroup.sync()
            boGroup.sync()
        }

        // Bo is not super admin
        assertTrue(!runBlocking { boGroup.isSuperAdmin(boClient.inboxId) })

        // Bo tries to delete Alix's message - should fail
        assertThrows(XMTPException::class.java) {
            runBlocking {
                boGroup.deleteMessage(messageId)
            }
        }
    }

    @Test
    fun testCannotDeleteAlreadyDeletedMessage() {
        // Create a group
        val alixGroup = runBlocking {
            alixClient.conversations.newGroup(listOf(boClient.inboxId))
        }

        // Send and delete a message
        val messageId = runBlocking {
            alixGroup.send("Message to delete twice")
        }

        runBlocking {
            alixGroup.deleteMessage(messageId)
            alixGroup.sync()
        }

        // Try to delete the same message again - should fail
        assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.deleteMessage(messageId)
            }
        }
    }

    @Test
    fun testDeleteMessageInDm() {
        // Create a DM between alix and bo
        val alixDm = runBlocking {
            alixClient.conversations.findOrCreateDm(boClient.inboxId)
        }

        // Alix sends a message
        val messageId = runBlocking {
            alixDm.send("Hello in DM")
        }

        // Verify message exists
        runBlocking { alixDm.sync() }
        var messages = runBlocking { alixDm.messages() }
        assertTrue(messages.any { it.id == messageId })

        // Alix deletes own message
        val deletionMessageId = runBlocking {
            alixDm.deleteMessage(messageId)
        }
        assertNotNull(deletionMessageId)

        // Sync and verify deletion message exists
        runBlocking { alixDm.sync() }
        messages = runBlocking { alixDm.messages() }
        assertTrue(messages.any { it.id == deletionMessageId })
    }

    @Test
    fun testDeleteMessageViaConversation() {
        // Create a group
        val alixGroup = runBlocking {
            alixClient.conversations.newGroup(listOf(boClient.inboxId))
        }

        // Get as Conversation
        val conversation: Conversation = Conversation.Group(alixGroup)

        // Send a message via conversation
        val messageId = runBlocking {
            conversation.send("Hello via conversation")
        }

        // Delete via conversation
        val deletionMessageId = runBlocking {
            conversation.deleteMessage(messageId)
        }
        assertNotNull(deletionMessageId)
    }

    @Test
    fun testDeleteMessageWithInvalidId() {
        // Create a group
        val alixGroup = runBlocking {
            alixClient.conversations.newGroup(listOf(boClient.inboxId))
        }

        // Try to delete a non-existent message
        assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.deleteMessage("0000000000000000000000000000000000000000000000000000000000000000")
            }
        }
    }
}
