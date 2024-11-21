package org.xmtp.android.library

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress
import java.lang.Thread.sleep
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class ConversationsTest {
    private lateinit var alixWallet: PrivateKeyBuilder
    private lateinit var boWallet: PrivateKeyBuilder
    private lateinit var alix: PrivateKey
    private lateinit var alixClient: Client
    private lateinit var bo: PrivateKey
    private lateinit var boClient: Client
    private lateinit var caroWallet: PrivateKeyBuilder
    private lateinit var caro: PrivateKey
    private lateinit var caroClient: Client
    private lateinit var davonWallet: PrivateKeyBuilder
    private lateinit var davon: PrivateKey
    private lateinit var davonClient: Client
    private lateinit var fixtures: Fixtures

    @Before
    fun setUp() {
        fixtures = fixtures()
        alixWallet = fixtures.alixAccount
        alix = fixtures.alix
        boWallet = fixtures.boAccount
        bo = fixtures.bo
        caroWallet = fixtures.caroAccount
        caro = fixtures.caro
        davonWallet = fixtures.davonAccount
        davon = fixtures.davon

        alixClient = fixtures.alixClient
        boClient = fixtures.boClient
        caroClient = fixtures.caroClient
        davonClient = fixtures.davonClient
    }

    @Test
    fun testsCanFindConversationByTopic() {
        val group =
            runBlocking { boClient.conversations.newGroup(listOf(caro.walletAddress)) }
        val dm = runBlocking { boClient.conversations.findOrCreateDm(caro.walletAddress) }

        val sameDm = boClient.findConversationByTopic(dm.topic)
        val sameGroup = boClient.findConversationByTopic(group.topic)
        assertEquals(group.id, sameGroup?.id)
        assertEquals(dm.id, sameDm?.id)
    }

    @Test
    fun testsCanListConversations() {
        runBlocking { boClient.conversations.findOrCreateDm(caro.walletAddress) }
        runBlocking { boClient.conversations.newGroup(listOf(caro.walletAddress)) }
        assertEquals(runBlocking { boClient.conversations.list().size }, 2)
        assertEquals(runBlocking { boClient.conversations.listDms().size }, 1)
        assertEquals(runBlocking { boClient.conversations.listGroups().size }, 1)

        runBlocking { caroClient.conversations.sync() }
        assertEquals(
            runBlocking { caroClient.conversations.list().size },
            2
        )
        assertEquals(runBlocking { caroClient.conversations.listGroups().size }, 1)
    }

    @Test
    fun testsCanListConversationsFiltered() {
        runBlocking { boClient.conversations.findOrCreateDm(caro.walletAddress) }
        val group =
            runBlocking { boClient.conversations.newGroup(listOf(caro.walletAddress)) }
        assertEquals(runBlocking { boClient.conversations.list().size }, 2)
        assertEquals(
            runBlocking { boClient.conversations.list(consentState = ConsentState.ALLOWED).size },
            2
        )
        runBlocking { group.updateConsentState(ConsentState.DENIED) }
        assertEquals(
            runBlocking { boClient.conversations.list(consentState = ConsentState.ALLOWED).size },
            1
        )
        assertEquals(
            runBlocking { boClient.conversations.list(consentState = ConsentState.DENIED).size },
            1
        )
        assertEquals(runBlocking { boClient.conversations.list().size }, 2)
    }

    @Test
    fun testCanListConversationsOrder() {
        val dm = runBlocking { boClient.conversations.findOrCreateDm(caro.walletAddress) }
        val group1 =
            runBlocking { boClient.conversations.newGroup(listOf(caro.walletAddress)) }
        val group2 =
            runBlocking { boClient.conversations.newGroup(listOf(caro.walletAddress)) }
        runBlocking { dm.send("Howdy") }
        runBlocking { group2.send("Howdy") }
        runBlocking { boClient.conversations.syncAllConversations() }
        val conversations = runBlocking { boClient.conversations.list() }
        val conversationsOrdered =
            runBlocking { boClient.conversations.list(order = Conversations.ConversationOrder.LAST_MESSAGE) }
        assertEquals(conversations.size, 3)
        assertEquals(conversationsOrdered.size, 3)
        assertEquals(conversations.map { it.id }, listOf(dm.id, group1.id, group2.id))
        assertEquals(conversationsOrdered.map { it.id }, listOf(group2.id, dm.id, group1.id))
    }

    @Test
    fun testCanStreamAllMessages() {
        val group =
            runBlocking { caroClient.conversations.newGroup(listOf(bo.walletAddress)) }
        val conversation =
            runBlocking { boClient.conversations.findOrCreateDm(caro.walletAddress) }
        runBlocking { boClient.conversations.sync() }

        val allMessages = mutableListOf<DecodedMessage>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                boClient.conversations.streamAllMessages()
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
    fun testCanStreamGroupsAndConversations() {
        val allMessages = mutableListOf<String>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                boClient.conversations.stream()
                    .collect { message ->
                        allMessages.add(message.topic)
                    }
            } catch (e: Exception) {
            }
        }
        Thread.sleep(1000)

        runBlocking {
            caroClient.conversations.newGroup(listOf(bo.walletAddress))
            Thread.sleep(1000)
            boClient.conversations.findOrCreateDm(caro.walletAddress)
        }

        Thread.sleep(2000)
        assertEquals(2, allMessages.size)
        job.cancel()
    }

    @Test
    fun testSyncConsent() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()

        val alixClient = runBlocking {
            Client().create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        val dm = runBlocking { alixClient.conversations.findOrCreateDm(bo.walletAddress) }
        runBlocking {
            dm.updateConsentState(ConsentState.DENIED)
            assertEquals(dm.consentState(), ConsentState.DENIED)
            boClient.conversations.sync()
        }
        val boDm = runBlocking { boClient.findConversation(dm.id) }

        val alixClient2 = runBlocking {
            Client().create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key,
                    dbDirectory = context.filesDir.absolutePath.toString()
                )
            )
        }

        val state = runBlocking { alixClient2.inboxState(true) }
        assertEquals(state.installations.size, 2)

        runBlocking {
            boClient.conversations.sync()
            boDm?.sync()
            alixClient.conversations.sync()
            alixClient2.conversations.sync()
            alixClient2.syncConsent()
            alixClient.conversations.syncAllConversations()
            Thread.sleep(2000)
            alixClient2.conversations.syncAllConversations()
            Thread.sleep(2000)
            val dm2 = alixClient2.findConversation(dm.id)!!
            assertEquals(ConsentState.DENIED, dm2.consentState())
            alixClient2.preferences.consentList.setConsentState(
                listOf(
                    ConsentListEntry(
                        dm2.id,
                        EntryType.CONVERSATION_ID,
                        ConsentState.ALLOWED
                    )
                )
            )
            assertEquals(
                alixClient2.preferences.consentList.conversationState(dm2.id),
                ConsentState.ALLOWED
            )
            assertEquals(dm2.consentState(), ConsentState.ALLOWED)
        }
    }

    @Test
    fun testInstallationsDoNotForkMinimum() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val alixGroup =
            runBlocking {
                alixClient.conversations.newGroup(
                    listOf(
                        bo.walletAddress,
                        caro.walletAddress
                    )
                )
            }
        runBlocking {
            boClient.conversations.sync()
            caroClient.conversations.sync()
        }
        val boGroup = boClient.findGroup(alixGroup.id)!!
        val caroGroup = boClient.findGroup(alixGroup.id)!!

        runBlocking {
            alixGroup.send("Message 1")
            boGroup.send("Message 2")
            caroGroup.send("Message 3")
            alixGroup.send("Message 4")
            alixGroup.send("Message 5")
        }

        runBlocking {
            alixGroup.sync()
            boGroup.sync()
            caroGroup.sync()
        }

        assertEquals(alixGroup.messages().size, 6)
        assertEquals(boGroup.messages().size, 5)
        assertEquals(caroGroup.messages().size, 5)

        val clientOptions2 = ClientOptions(
            ClientOptions.Api(XMTPEnvironment.LOCAL, false),
            appContext = context,
            dbEncryptionKey = key,
            dbDirectory = context.filesDir.absolutePath.toString()
        )

        val alixClient2 =
            runBlocking { Client().create(account = alixWallet, options = clientOptions2) }
        runBlocking {
            alixClient.conversations.sync()
            boClient.conversations.sync()
            caroClient.conversations.sync()
            alixClient.conversations.syncAllConversations()
            boClient.conversations.syncAllConversations()
            caroClient.conversations.syncAllConversations()
            alixClient2.conversations.sync()
        }
        val alixGroup2 = alixClient2.findGroup(alixGroup.id)!!

        runBlocking {
            alixGroup2.send("Message 6")
            boGroup.send("Message 7")
            caroGroup.send("Message 8")
            alixGroup.send("Message 9")
            alixGroup2.send("Message 10")
        }

        runBlocking {
            alixClient.conversations.syncAllConversations()
            boClient.conversations.syncAllConversations()
            caroClient.conversations.syncAllConversations()
            alixClient2.conversations.syncAllConversations()
        }

        assertEquals(alixGroup.messages().size, 11)
        assertEquals(boGroup.messages().size, 10)
        assertEquals(caroGroup.messages().size, 10)
        assertEquals(alixGroup2.messages().size, 5) // expect 5 but was 3 -- On a fork with just Alix2 & Caro
    }

    @Test
    fun testInstallationsDoNotFork() {
        runBlocking {
            val key = SecureRandom().generateSeed(32)
            val context = InstrumentationRegistry.getInstrumentation().targetContext

            val alixGroup =
                alixClient.conversations.newGroup(
                    listOf(
                        bo.walletAddress,
                        caro.walletAddress
                    )
                )
            boClient.conversations.sync()
            caroClient.conversations.sync()
            val boGroup = boClient.findGroup(alixGroup.id)!!
            val caroGroup = boClient.findGroup(alixGroup.id)!!

            alixGroup.send("Message 1")
            boGroup.send("Message 2")
            caroGroup.send("Message 3")
            alixGroup.send("Message 4")
            alixGroup.send("Message 5")
            caroGroup.send("Message 6")

            assertEquals(alixGroup.messages().size, 6)
            assertEquals(boGroup.messages().size, 6)
            assertEquals(caroGroup.messages().size, 6)

            val clientOptions2 = ClientOptions(
                ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                appContext = context,
                dbEncryptionKey = key,
                dbDirectory = context.filesDir.absolutePath.toString()
            )

            val alixClient2 = Client().create(account = alixWallet, options = clientOptions2)
            alixClient.conversations.sync()
            boClient.conversations.sync()
            caroClient.conversations.sync()
            alixClient.conversations.syncAllConversations()
            boClient.conversations.syncAllConversations()
            caroClient.conversations.syncAllConversations()
            alixClient2.conversations.sync()
            val alixGroup2 = alixClient2.findGroup(alixGroup.id)!!

            alixGroup2.send("Message 7")
            boGroup.send("Message 8")
            caroGroup.send("Message 9")
            alixGroup.send("Message 10")
            alixGroup2.send("Message 11")
            alixGroup.send("Message 12")
            alixGroup2.send("Message 13")
            alixGroup.send("Message 14")
            caroGroup.send("Message 15")

            alixClient.conversations.syncAllConversations()
            boClient.conversations.syncAllConversations()
            caroClient.conversations.syncAllConversations()
            alixClient2.conversations.syncAllConversations()

            assertEquals(alixGroup.messages().size, 16)
            assertEquals(boGroup.messages().size, 15)
            assertEquals(caroGroup.messages().size, 15)
            assertEquals(alixGroup2.messages().size, 9) // expect 9 but was 7

//         +1 more message
            alixGroup2.addMembers(listOf(davon.walletAddress))
            davonClient.conversations.sync()
            val davonGroup = davonClient.findGroup(alixGroup.id)!!

            alixGroup2.send("Message 16")
            alixGroup.send("Message 17")
            alixGroup2.send("Message 18")
            alixGroup.send("Message 19")
            davonGroup.send("Message 20")

            alixClient.conversations.syncAllConversations()
            boClient.conversations.syncAllConversations()
            caroClient.conversations.syncAllConversations()
            alixClient2.conversations.syncAllConversations()
            davonClient.conversations.syncAllConversations()

            assertEquals(alixGroup.messages().size, 22)
            assertEquals(boGroup.messages().size, 21)
            assertEquals(caroGroup.messages().size, 21)
            alixGroup2.messages().forEach {
                Log.d("LOPI", it.body)
            }
            assertEquals(alixGroup2.messages().size, 15) // Expected 15 but was 13
            assertEquals(davonGroup.messages().size, 5) // Expected 5 but was 4

            val boClient2 = Client().create(account = boWallet, options = clientOptions2)

            alixGroup2.send("Message 21")
            alixGroup.send("Message 22")
            alixGroup2.send("Message 23")
            alixGroup.send("Message 24")
            boGroup.send("Message 25")

            alixClient.conversations.sync()
            boClient.conversations.sync()
            caroClient.conversations.sync()
            alixClient2.conversations.sync()
            alixClient.conversations.syncAllConversations()
            boClient.conversations.syncAllConversations()
            caroClient.conversations.syncAllConversations()
            davonClient.conversations.syncAllConversations()
            alixClient2.conversations.syncAllConversations()
            boClient2.conversations.sync()
            boClient2.conversations.syncAllConversations()
//            val boGroup2 = boClient2.findGroup(alixGroup.id)!! // Cant find the group?

            alixGroup2.send("Message 26")
            alixGroup.send("Message 27")
            alixGroup2.send("Message 28")
            alixGroup.send("Message 29")
            davonGroup.send("Message 30")

            alixClient.conversations.syncAllConversations()
            boClient.conversations.syncAllConversations()
            caroClient.conversations.syncAllConversations()
            davonClient.conversations.syncAllConversations()
            alixClient2.conversations.syncAllConversations()
            boClient2.conversations.syncAllConversations()

            assertEquals(alixGroup.messages().size, 32)
            assertEquals(boGroup.messages().size, 31)
            assertEquals(caroGroup.messages().size, 31)
//            assertEquals(alixGroup2.messages().size, 25) // Expected 25 was 23
//            assertEquals(davonGroup.messages().size, 15) // Expected 15 was 14
//            assertEquals(boGroup2.messages().size, 5)
        }
    }
}
