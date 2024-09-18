package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.messages.MessageDeliveryStatus
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class V3ClientTest {
    private lateinit var alixWallet: PrivateKeyBuilder
    private lateinit var boWallet: PrivateKeyBuilder
    private lateinit var alix: PrivateKey
    private lateinit var alixClient: Client
    private lateinit var bo: PrivateKey
    private lateinit var boClient: Client
    private lateinit var caroWallet: PrivateKeyBuilder
    private lateinit var caro: PrivateKey
    private lateinit var caroClient: Client

    @Before
    fun setUp() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Pure V2
        alixWallet = PrivateKeyBuilder()
        alix = alixWallet.getPrivateKey()
        alixClient = runBlocking {
            Client().create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, isSecure = false)
                )
            )
        }

        // Pure V3
        boWallet = PrivateKeyBuilder()
        bo = boWallet.getPrivateKey()
        boClient = runBlocking {
            Client().createOrBuild(
                account = boWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    enableV3 = true,
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }

        // Both V3 & V2
        caroWallet = PrivateKeyBuilder()
        caro = caroWallet.getPrivateKey()
        caroClient =
            runBlocking {
                Client().create(
                    account = caroWallet,
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
    fun testsCanCreateGroup() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(caro.walletAddress)) }
        assertEquals(
            group.members().map { it.inboxId }.sorted(),
            listOf(caroClient.inboxId, boClient.inboxId).sorted()
        )

        Assert.assertThrows("Recipient not on network", XMTPException::class.java) {
            runBlocking { boClient.conversations.newGroup(listOf(alix.walletAddress)) }
        }
    }

    @Test
    fun testsCanSendMessages() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(caro.walletAddress)) }
        runBlocking { group.send("howdy") }
        val messageId = runBlocking { group.send("gm") }
        runBlocking { group.sync() }
        assertEquals(group.messages().first().body, "gm")
        assertEquals(group.messages().first().id, messageId)
        assertEquals(group.messages().first().deliveryStatus, MessageDeliveryStatus.PUBLISHED)
        assertEquals(group.messages().size, 3)

        runBlocking { caroClient.conversations.syncGroups() }
        val sameGroup = runBlocking { caroClient.conversations.listGroups().last() }
        runBlocking { sameGroup.sync() }
        assertEquals(sameGroup.messages().size, 2)
        assertEquals(sameGroup.messages().first().body, "gm")
    }

    @Test
    fun testGroupConsent() {
        runBlocking {
            val group = boClient.conversations.newGroup(listOf(caro.walletAddress))
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
    fun testCanAllowAndDenyInboxId() {
        runBlocking {
            val boGroup = boClient.conversations.newGroup(listOf(caro.walletAddress))
            assert(!boClient.contacts.isInboxAllowed(caroClient.inboxId))
            assert(!boClient.contacts.isInboxDenied(caroClient.inboxId))

            boClient.contacts.allowInboxes(listOf(caroClient.inboxId))
            var caroMember = boGroup.members().firstOrNull { it.inboxId == caroClient.inboxId }
            assertEquals(caroMember!!.consentState, ConsentState.ALLOWED)

            assert(boClient.contacts.isInboxAllowed(caroClient.inboxId))
            assert(!boClient.contacts.isInboxDenied(caroClient.inboxId))
            assert(boClient.contacts.isAllowed(caroClient.address))
            assert(!boClient.contacts.isDenied(caroClient.address))

            boClient.contacts.denyInboxes(listOf(caroClient.inboxId))
            caroMember = boGroup.members().firstOrNull { it.inboxId == caroClient.inboxId }
            assertEquals(caroMember!!.consentState, ConsentState.DENIED)

            assert(!boClient.contacts.isInboxAllowed(caroClient.inboxId))
            assert(boClient.contacts.isInboxDenied(caroClient.inboxId))

            boClient.contacts.allow(listOf(alixClient.address))
            assert(boClient.contacts.isAllowed(alixClient.address))
            assert(!boClient.contacts.isDenied(alixClient.address))
        }
    }

    @Test
    fun testCanStreamAllMessagesFromV2andV3Users() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(caro.walletAddress)) }
        val conversation =
            runBlocking { alixClient.conversations.newConversation(caro.walletAddress) }
        runBlocking { caroClient.conversations.syncGroups() }

        val allMessages = mutableListOf<DecodedMessage>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                caroClient.conversations.streamAllMessages(includeGroups = true)
                    .collect { message ->
                        allMessages.add(message)
                    }
            } catch (e: Exception) {
            }
        }
        Thread.sleep(1000)
        runBlocking {
            group.send("hi")
            conversation.send("hi")
        }
        Thread.sleep(1000)
        assertEquals(2, allMessages.size)
        job.cancel()
    }

    @Test
    fun testCanStreamGroupsAndConversationsFromV2andV3Users() {
        val allMessages = mutableListOf<String>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                caroClient.conversations.streamAll()
                    .collect { message ->
                        allMessages.add(message.topic)
                    }
            } catch (e: Exception) {
            }
        }
        Thread.sleep(1000)

        runBlocking {
            alixClient.conversations.newConversation(caro.walletAddress)
            Thread.sleep(1000)
            boClient.conversations.newGroup(listOf(caro.walletAddress))
        }

        Thread.sleep(2000)
        assertEquals(2, allMessages.size)
        job.cancel()
    }
}
