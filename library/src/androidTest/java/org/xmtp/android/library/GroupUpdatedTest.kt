package org.xmtp.android.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.ContentTypeGroupUpdated
import org.xmtp.android.library.codecs.GroupUpdated
import org.xmtp.android.library.codecs.GroupUpdatedCodec
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class GroupUpdatedTest {
    lateinit var alixWallet: FakePasskeyWallet
    lateinit var boWallet: FakePasskeyWallet
    lateinit var alixClient: Client
    lateinit var boClient: Client
    lateinit var caroWallet: PrivateKeyBuilder
    lateinit var caro: PrivateKey
    lateinit var caroClient: Client
    lateinit var fixtures: Fixtures

    @Before
    fun setUp() {
        val key = SecureRandom().generateSeed(32)
        fixtures = fixtures()
        alixWallet = fixtures.alixAccount
        boWallet = fixtures.boAccount
        caroWallet = fixtures.caroAccount
        caro = fixtures.caro

        alixClient = fixtures.alixClient
        boClient = fixtures.boClient
        caroClient = fixtures.caroClient
    }

    @Test
    fun testCanAddMembers() {
        Client.register(codec = GroupUpdatedCodec())

        val group = runBlocking {
            alixClient.conversations.newGroup(
                listOf(
                    boClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        val messages = runBlocking { group.messages() }
        assertEquals(messages.size, 1)
        val content: GroupUpdated? = messages.first().content()
        assertEquals(
            listOf(boClient.inboxId, caroClient.inboxId).sorted(),
            content?.addedInboxesList?.map { it.inboxId }?.sorted()
        )
        assert(content?.removedInboxesList.isNullOrEmpty())
    }

    @Test
    fun testCanRemoveMembers() {
        Client.register(codec = GroupUpdatedCodec())

        val group = runBlocking {
            alixClient.conversations.newGroup(
                listOf(
                    boClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        val messages = runBlocking { group.messages() }
        assertEquals(messages.size, 1)
        assertEquals(runBlocking { group.members().size }, 3)
        runBlocking { group.removeMembers(listOf(caroClient.inboxId)) }
        val updatedMessages = runBlocking { group.messages() }
        assertEquals(updatedMessages.size, 2)
        assertEquals(runBlocking { group.members().size }, 2)
        val content: GroupUpdated? = updatedMessages.first().content()

        assertEquals(
            listOf(caroClient.inboxId),
            content?.removedInboxesList?.map { it.inboxId }?.sorted()
        )
        assert(content?.addedInboxesList.isNullOrEmpty())
    }

    @Test
    fun testRemovesInvalidMessageKind() {
        Client.register(codec = GroupUpdatedCodec())

        val membershipChange = GroupUpdated.newBuilder().build()

        val group = runBlocking {
            alixClient.conversations.newGroup(
                listOf(
                    boClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        val messages = runBlocking { group.messages() }
        assertEquals(messages.size, 1)
        assertEquals(runBlocking { group.members().size }, 3)
        runBlocking {
            group.send(
                content = membershipChange,
                options = SendOptions(contentType = ContentTypeGroupUpdated),
            )
            group.sync()
        }
        val updatedMessages = runBlocking { group.messages() }
        assertEquals(updatedMessages.size, 1)
    }

    @Test
    fun testIfNotRegisteredReturnsFallback() {
        val group = runBlocking {
            alixClient.conversations.newGroup(
                listOf(
                    boClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        val messages = runBlocking { group.messages() }
        assertEquals(messages.size, 1)
        assert(messages.first().fallback.isBlank())
    }

    @Test
    fun testCanUpdateGroupName() {
        Client.register(codec = GroupUpdatedCodec())

        val group = runBlocking {
            alixClient.conversations.newGroup(
                listOf(
                    boClient.inboxId,
                    caroClient.inboxId
                ),
                groupName = "Start Name"
            )
        }
        var messages = runBlocking { group.messages() }
        assertEquals(messages.size, 1)
        runBlocking {
            group.updateName("Group Name")
            messages = group.messages()
            assertEquals(messages.size, 2)

            val content: GroupUpdated? = messages.first().content()
            assertEquals(
                "Start Name",
                content?.metadataFieldChangesList?.first()?.oldValue
            )
            assertEquals(
                "Group Name",
                content?.metadataFieldChangesList?.first()?.newValue
            )
        }
    }
}
