package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.ContentTypeGroupUpdated
import org.xmtp.android.library.codecs.ContentTypeReaction
import org.xmtp.android.library.codecs.GroupUpdatedCodec
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.codecs.ReactionCodec
import org.xmtp.android.library.codecs.ReactionSchema
import org.xmtp.android.library.messages.DecryptedMessage
import org.xmtp.android.library.messages.MessageDeliveryStatus
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.mls.message.contents.TranscriptMessages
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class DmTest {
    private lateinit var alixWallet: PrivateKeyBuilder
    private lateinit var boWallet: PrivateKeyBuilder
    private lateinit var caroWallet: PrivateKeyBuilder
    private lateinit var alix: PrivateKey
    private lateinit var alixClient: Client
    private lateinit var bo: PrivateKey
    private lateinit var boClient: Client
    private lateinit var caro: PrivateKey
    private lateinit var caroClient: Client

    @Before
    fun setUp() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        alixWallet = PrivateKeyBuilder()
        alix = alixWallet.getPrivateKey()
        alixClient = runBlocking {
            Client().createOrBuild(
                account = alixWallet,
                address = alixWallet.address,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    enableV3 = true,
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        boWallet = PrivateKeyBuilder()
        bo = boWallet.getPrivateKey()
        boClient = runBlocking {
            Client().createOrBuild(
                account = boWallet,
                address = boWallet.address,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    enableV3 = true,
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }

        caroWallet = PrivateKeyBuilder()
        caro = caroWallet.getPrivateKey()
        caroClient = runBlocking {
            Client().createOrBuild(
                account = caroWallet,
                address = caroWallet.address,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    enableV3 = true,
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }

    }

    @Test
    fun testCanCreateADm() {
        runBlocking {
            val convo1 = boClient.conversations.findOrCreateDm(alix.walletAddress)
            val convo2 = caroClient.conversations.newConversation(alix.walletAddress)
            alixClient.conversations.syncConversations()
            val sameConvo1 = alixClient.conversations.findOrCreateDm(bo.walletAddress)
            val sameConvo2 = alixClient.conversations.newConversation(caro.walletAddress)
            assertEquals(convo1.id, sameConvo1.id)
            assertEquals(convo2.id, sameConvo2.id)
        }
    }
    @Test
    fun testCanListDmMembers() {
        val group = runBlocking {
            boClient.conversations.findOrCreateDm(
                    alix.walletAddress,
            )
        }
        assertEquals(
            runBlocking { group.members().map { it.inboxId }.sorted() },
            listOf(
                alixClient.inboxId,
                boClient.inboxId
            ).sorted()
        )

        assertEquals(
            Conversation.Dm(group).peerAddresses.sorted(),
            listOf(
                alixClient.inboxId,
            ).sorted()
        )

        assertEquals(
            runBlocking { group.peerInboxId() },
                alixClient.inboxId,
        )
    }

    @Test
    fun testDmMetadata() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(alix.walletAddress),
                groupName = "Starting Name",
                groupImageUrlSquare = "startingurl.com"
            )
        }
        runBlocking {
            assertEquals("Starting Name", boGroup.name)
            assertEquals("startingurl.com", boGroup.imageUrlSquare)
            boGroup.updateGroupName("This Is A Great Group")
            boGroup.updateGroupImageUrlSquare("thisisanewurl.com")
            boGroup.sync()
            alixClient.conversations.syncGroups()
        }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }
        runBlocking { alixGroup.sync() }
        assertEquals("This Is A Great Group", boGroup.name)
        assertEquals("This Is A Great Group", alixGroup.name)
        assertEquals("thisisanewurl.com", boGroup.imageUrlSquare)
        assertEquals("thisisanewurl.com", alixGroup.imageUrlSquare)
    }

    @Test
    fun testCanListDms() {
        runBlocking {
            boClient.conversations.findOrCreateDm(alix.walletAddress)
            boClient.conversations.newGroup(listOf(caro.walletAddress))
        }
        val groups = runBlocking { boClient.conversations.listGroups() }
        assertEquals(groups.size, 2)
    }

    @Test
    fun testCannotCreateDmWithMemberNotOnV3() {
        val chuxAccount = PrivateKeyBuilder()
        val chux: PrivateKey = chuxAccount.getPrivateKey()
        runBlocking { Client().create(account = chuxAccount) }

        assertThrows("Recipient not on network", XMTPException::class.java) {
            runBlocking { boClient.conversations.newGroup(listOf(chux.walletAddress)) }
        }
    }

    @Test
    fun testCannotStartDmWithSelf() {
        assertThrows("Recipient is sender", XMTPException::class.java) {
            runBlocking { boClient.conversations.newGroup(listOf(bo.walletAddress)) }
        }
    }

    @Test
    fun testDmStartsWithAllowedState() {
        runBlocking {
            val group = boClient.conversations.newGroup(listOf(alix.walletAddress))
            group.send("howdy")
            group.send("gm")
            group.sync()
            assert(boClient.contacts.isGroupAllowed(group.id))
            assertEquals(boClient.contacts.consentList.groupState(group.id), ConsentState.ALLOWED)
        }
    }

    @Test
    fun testCanSendMessageToDm() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(alix.walletAddress)) }
        runBlocking { group.send("howdy") }
        val messageId = runBlocking { group.send("gm") }
        runBlocking { group.sync() }
        assertEquals(group.messages().first().body, "gm")
        assertEquals(group.messages().first().id, messageId)
        assertEquals(group.messages().first().deliveryStatus, MessageDeliveryStatus.PUBLISHED)
        assertEquals(group.messages().size, 3)

        runBlocking { alixClient.conversations.syncGroups() }
        val sameGroup = runBlocking { alixClient.conversations.listGroups().last() }
        runBlocking { sameGroup.sync() }
        assertEquals(sameGroup.messages().size, 2)
        assertEquals(sameGroup.messages().first().body, "gm")
    }

    @Test
    fun testCanListDmMessages() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(alix.walletAddress)) }
        runBlocking {
            group.send("howdy")
            group.send("gm")
        }

        assertEquals(group.messages().size, 3)
        assertEquals(group.messages(deliveryStatus = MessageDeliveryStatus.PUBLISHED).size, 3)
        runBlocking { group.sync() }
        assertEquals(group.messages().size, 3)
        assertEquals(group.messages(deliveryStatus = MessageDeliveryStatus.UNPUBLISHED).size, 0)
        assertEquals(group.messages(deliveryStatus = MessageDeliveryStatus.PUBLISHED).size, 3)

        runBlocking { alixClient.conversations.syncGroups() }
        val sameGroup = runBlocking { alixClient.conversations.listGroups().last() }
        runBlocking { sameGroup.sync() }
        assertEquals(sameGroup.messages(deliveryStatus = MessageDeliveryStatus.PUBLISHED).size, 2)
    }

    @Test
    fun testCanSendContentTypesToDm() {
        Client.register(codec = ReactionCodec())

        val group = runBlocking { boClient.conversations.newGroup(listOf(alix.walletAddress)) }
        runBlocking { group.send("gm") }
        runBlocking { group.sync() }
        val messageToReact = group.messages()[0]

        val reaction = Reaction(
            reference = messageToReact.id,
            action = ReactionAction.Added,
            content = "U+1F603",
            schema = ReactionSchema.Unicode
        )

        runBlocking {
            group.send(
                content = reaction,
                options = SendOptions(contentType = ContentTypeReaction)
            )
        }
        runBlocking { group.sync() }

        val messages = group.messages()
        assertEquals(messages.size, 3)
        val content: Reaction? = messages.first().content()
        assertEquals("U+1F603", content?.content)
        assertEquals(messageToReact.id, content?.reference)
        assertEquals(ReactionAction.Added, content?.action)
        assertEquals(ReactionSchema.Unicode, content?.schema)
    }

    @Test
    fun testCanStreamDmMessages() = kotlinx.coroutines.test.runTest {
        Client.register(codec = GroupUpdatedCodec())
        val membershipChange = TranscriptMessages.GroupUpdated.newBuilder().build()

        val group = boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
        alixClient.conversations.syncGroups()
        val alixGroup = alixClient.conversations.listGroups().first()
        group.streamMessages().test {
            alixGroup.send("hi")
            assertEquals("hi", awaitItem().body)
            alixGroup.send(
                content = membershipChange,
                options = SendOptions(contentType = ContentTypeGroupUpdated),
            )
            alixGroup.send("hi again")
            assertEquals("hi again", awaitItem().body)
        }
    }

    @Test
    fun testCanStreamAllDmMessages() {
        val group = runBlocking { caroClient.conversations.newGroup(listOf(alix.walletAddress)) }
        runBlocking { alixClient.conversations.syncGroups() }

        val allMessages = mutableListOf<DecodedMessage>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                alixClient.conversations.streamAllGroupMessages().collect { message ->
                    allMessages.add(message)
                }
            } catch (e: Exception) {
            }
        }
        Thread.sleep(2500)

        for (i in 0 until 2) {
            runBlocking { group.send(text = "Message $i") }
            Thread.sleep(100)
        }
        assertEquals(2, allMessages.size)

        val caroGroup =
            runBlocking { caroClient.conversations.newGroup(listOf(alixClient.address)) }
        Thread.sleep(2500)

        for (i in 0 until 2) {
            runBlocking { caroGroup.send(text = "Message $i") }
            Thread.sleep(100)
        }

        assertEquals(4, allMessages.size)

        job.cancel()
    }

    @Test
    fun testCanStreamDecryptedDmMessages() = kotlinx.coroutines.test.runTest {
        val group = boClient.conversations.newGroup(listOf(alix.walletAddress))
        alixClient.conversations.syncGroups()
        val alixGroup = alixClient.conversations.listGroups().first()
        group.streamDecryptedMessages().test {
            alixGroup.send("hi")
            assertEquals("hi", awaitItem().encodedContent.content.toStringUtf8())
            alixGroup.send("hi again")
            assertEquals("hi again", awaitItem().encodedContent.content.toStringUtf8())
        }
    }

    @Test
    fun testCanStreamAllDecryptedDmMessages() {
        val group = runBlocking { caroClient.conversations.newGroup(listOf(alix.walletAddress)) }
        runBlocking { alixClient.conversations.syncGroups() }

        val allMessages = mutableListOf<DecryptedMessage>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                alixClient.conversations.streamAllGroupDecryptedMessages().collect { message ->
                    allMessages.add(message)
                }
            } catch (e: Exception) {
            }
        }
        Thread.sleep(2500)

        for (i in 0 until 2) {
            runBlocking { group.send(text = "Message $i") }
            Thread.sleep(100)
        }
        assertEquals(2, allMessages.size)

        val caroGroup =
            runBlocking { caroClient.conversations.newGroup(listOf(alixClient.address)) }
        Thread.sleep(2500)

        for (i in 0 until 2) {
            runBlocking { caroGroup.send(text = "Message $i") }
            Thread.sleep(100)
        }

        assertEquals(4, allMessages.size)

        job.cancel()
    }

    @Test
    fun testCanStreamDms() = kotlinx.coroutines.test.runTest {
        boClient.conversations.streamGroups().test {
            val group =
                alixClient.conversations.newGroup(listOf(bo.walletAddress))
            assertEquals(group.id, awaitItem().id)
            val group2 =
                caroClient.conversations.newGroup(listOf(bo.walletAddress))
            assertEquals(group2.id, awaitItem().id)
        }
    }

    @Test
    fun testDmConsent() {
        runBlocking {
            val group =
                boClient.conversations.newGroup(
                    listOf(
                        alix.walletAddress,
                        caro.walletAddress
                    )
                )
            assert(boClient.contacts.isGroupAllowed(group.id))
            assertEquals(group.consentState(), ConsentState.ALLOWED)

            boClient.contacts.denyGroups(listOf(group.id))
            assert(boClient.contacts.isGroupDenied(group.id))
            assertEquals(group.consentState(), ConsentState.DENIED)

            group.updateConsentState(ConsentState.ALLOWED)
            assert(boClient.contacts.isGroupAllowed(group.id))
            assertEquals(group.consentState(), ConsentState.ALLOWED)
        }
    }

    @Test
    fun testCanFetchDmById() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alix.walletAddress,
                    caro.walletAddress
                )
            )
        }
        runBlocking { alixClient.conversations.syncGroups() }
        val alixGroup = alixClient.findGroup(boGroup.id)

        assertEquals(alixGroup?.id, boGroup.id)
    }
}
