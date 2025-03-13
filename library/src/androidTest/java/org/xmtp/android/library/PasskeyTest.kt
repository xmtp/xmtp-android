package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.xmtp.android.library.libxmtp.DecodedMessage
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import uniffi.xmtpv3.GenericException

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PasskeyTest {
    companion object {
        private lateinit var davonPasskey: FakePasskeyWallet
        private lateinit var davonPasskeyClient: Client
        private lateinit var eriPasskey: FakePasskeyWallet
        private lateinit var eriPasskeyClient: Client
        private lateinit var options: ClientOptions
        private lateinit var boEOAWallet: PrivateKeyBuilder
        private lateinit var boEOA: PrivateKey
        private lateinit var boEOAClient: Client

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val key = byteArrayOf(
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
                0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
            )
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            options = ClientOptions(
                ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                appContext = context,
                dbEncryptionKey = key
            )

            // EOA
            boEOAWallet = PrivateKeyBuilder()
            boEOA = boEOAWallet.getPrivateKey()
            boEOAClient = runBlocking {
                Client.create(
                    account = boEOAWallet,
                    options = options
                )
            }

            // Passkey
            davonPasskey = FakePasskeyWallet()
            davonPasskeyClient = runBlocking {
                Client.create(
                    account = davonPasskey,
                    options = options
                )
            }

            // Passkey
            eriPasskey = FakePasskeyWallet()
            eriPasskeyClient = runBlocking {
                Client.create(
                    account = eriPasskey,
                    options = options
                )
            }
        }
    }

    @Test
    fun test1_CanBuildAPasskey() {
        val davonPasskeyClient2 = runBlocking {
            Client.build(
                publicIdentity = davonPasskey.publicIdentity,
                options = options
            )
        }
        assertEquals(davonPasskeyClient.inboxId, davonPasskeyClient2.inboxId)
        assertEquals(
            davonPasskeyClient2.inboxId,
            runBlocking { davonPasskeyClient.inboxIdFromIdentity(davonPasskey.publicIdentity) })


        runBlocking {
            davonPasskeyClient.canMessage(listOf(boEOAWallet.publicIdentity))[boEOAWallet.publicIdentity.identifier]?.let {
                assert(
                    it
                )
            }
        }

        runBlocking {
            boEOAClient.canMessage(listOf(davonPasskey.publicIdentity))[davonPasskey.publicIdentity.identifier]?.let {
                assert(
                    it
                )
            }
        }
    }

    @Test
    fun test2_CanCreateGroup() {
        val group1 = runBlocking {
            boEOAClient.conversations.newGroup(
                listOf(
                    davonPasskeyClient.inboxId,
                    eriPasskeyClient.inboxId
                )
            )
        }
        val group2 = runBlocking {
            davonPasskeyClient.conversations.newGroup(
                listOf(
                    boEOAClient.inboxId,
                    eriPasskeyClient.inboxId
                )
            )
        }

        assertEquals(
            runBlocking { group1.members().map { it.inboxId }.sorted() },
            listOf(
                davonPasskeyClient.inboxId,
                boEOAClient.inboxId,
                eriPasskeyClient.inboxId
            ).sorted()
        )
        assertEquals(
            runBlocking { group2.members().map { it.identities.first().identifier }.sorted() },
            listOf(
                davonPasskey.publicIdentity.identifier,
                boEOAWallet.publicIdentity.identifier,
                eriPasskey.publicIdentity.identifier
            ).sorted()
        )
    }

    @Test
    fun test3_CanSendMessages() {
        val boGroup = runBlocking {
            boEOAClient.conversations.newGroup(
                listOf(
                    davonPasskeyClient.inboxId,
                    eriPasskeyClient.inboxId
                )
            )
        }
        runBlocking { boGroup.send("howdy") }
        val messageId = runBlocking { boGroup.send("gm") }
        runBlocking { boGroup.sync() }
        assertEquals(runBlocking { boGroup.messages() }.first().body, "gm")
        assertEquals(runBlocking { boGroup.messages() }.first().id, messageId)
        assertEquals(
            runBlocking { boGroup.messages() }.first().deliveryStatus,
            DecodedMessage.MessageDeliveryStatus.PUBLISHED
        )
        assertEquals(runBlocking { boGroup.messages() }.size, 3)

        runBlocking { davonPasskeyClient.conversations.sync() }
        val davonGroup = runBlocking { davonPasskeyClient.conversations.findGroup(boGroup.id) }!!
        runBlocking { davonGroup.sync() }
        assertEquals(runBlocking { davonGroup.messages() }.size, 2)
        assertEquals(runBlocking { davonGroup.messages() }.first().body, "gm")
        runBlocking { davonGroup.send("from davon") }

        runBlocking { eriPasskeyClient.conversations.sync() }
        val eriGroup = runBlocking { davonPasskeyClient.conversations.findGroup(davonGroup.id) }
        runBlocking { eriGroup?.sync() }
        assertEquals(runBlocking { eriGroup?.messages() }?.size, 3)
        assertEquals(runBlocking { eriGroup?.messages() }?.first()?.body, "from davon")
        runBlocking { eriGroup?.send("from eri") }
    }

    @Test
    fun test4_GroupConsent() {
        runBlocking {
            val davonGroup = runBlocking {
                davonPasskeyClient.conversations.newGroup(
                    listOf(
                        boEOAClient.inboxId,
                        eriPasskeyClient.inboxId
                    )
                )
            }
            assertEquals(
                davonPasskeyClient.preferences.conversationState(davonGroup.id),
                ConsentState.ALLOWED
            )
            assertEquals(davonGroup.consentState(), ConsentState.ALLOWED)

            davonPasskeyClient.preferences.setConsentState(
                listOf(
                    ConsentRecord(
                        davonGroup.id,
                        EntryType.CONVERSATION_ID,
                        ConsentState.DENIED
                    )
                )
            )
            assertEquals(
                davonPasskeyClient.preferences.conversationState(davonGroup.id),
                ConsentState.DENIED
            )
            assertEquals(davonGroup.consentState(), ConsentState.DENIED)

            davonGroup.updateConsentState(ConsentState.ALLOWED)
            assertEquals(
                davonPasskeyClient.preferences.conversationState(davonGroup.id),
                ConsentState.ALLOWED
            )
            assertEquals(davonGroup.consentState(), ConsentState.ALLOWED)
        }
    }

    @Test
    fun test5_CanAllowAndDenyInboxId() {
        runBlocking {
            val davonGroup = runBlocking {
                davonPasskeyClient.conversations.newGroup(
                    listOf(
                        boEOAClient.inboxId,
                        eriPasskeyClient.inboxId
                    )
                )
            }
            assertEquals(
                davonPasskeyClient.preferences.inboxIdState(
                    boEOAClient.inboxId
                ),
                ConsentState.UNKNOWN
            )
            davonPasskeyClient.preferences.setConsentState(
                listOf(
                    ConsentRecord(
                        boEOAClient.inboxId,
                        EntryType.INBOX_ID,
                        ConsentState.ALLOWED
                    )
                )
            )
            var alixMember = davonGroup.members().firstOrNull { it.inboxId == boEOAClient.inboxId }
            assertEquals(alixMember!!.consentState, ConsentState.ALLOWED)

            assertEquals(
                davonPasskeyClient.preferences.inboxIdState(
                    boEOAClient.inboxId
                ),
                ConsentState.ALLOWED
            )

            davonPasskeyClient.preferences.setConsentState(
                listOf(
                    ConsentRecord(
                        boEOAClient.inboxId,
                        EntryType.INBOX_ID,
                        ConsentState.DENIED
                    )
                )
            )
            alixMember = davonGroup.members().firstOrNull { it.inboxId == boEOAClient.inboxId }
            assertEquals(alixMember!!.consentState, ConsentState.DENIED)

            assertEquals(
                davonPasskeyClient.preferences.inboxIdState(
                    boEOAClient.inboxId
                ),
                ConsentState.DENIED
            )
        }
    }

    @Test
    fun test6_CanStreamAllMessages() {
        val group1 = runBlocking {
            davonPasskeyClient.conversations.newGroup(
                listOf(
                    boEOAClient.inboxId,
                    eriPasskeyClient.inboxId
                )
            )
        }
        val group2 = runBlocking {
            boEOAClient.conversations.newGroup(
                listOf(
                    davonPasskeyClient.inboxId,
                    eriPasskeyClient.inboxId
                )
            )
        }
        val dm1 = runBlocking {
            davonPasskeyClient.conversations.findOrCreateDm(
                eriPasskeyClient.inboxId
            )
        }
        val dm2 = runBlocking {
            boEOAClient.conversations.findOrCreateDm(
                davonPasskeyClient.inboxId
            )
        }
        runBlocking { davonPasskeyClient.conversations.sync() }

        val allMessages = mutableListOf<DecodedMessage>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                davonPasskeyClient.conversations.streamAllMessages()
                    .collect { message ->
                        allMessages.add(message)
                    }
            } catch (e: Exception) {
            }
        }
        Thread.sleep(1000)
        runBlocking {
            group1.send("hi")
            group2.send("hi")
            dm1.send("hi")
            dm2.send("hi")
        }
        Thread.sleep(1000)
        assertEquals(4, allMessages.size)
        job.cancel()
    }

    @Test
    fun test7_CanStreamConversations() {
        val fixtures = fixtures()
        val allMessages = mutableListOf<String>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                davonPasskeyClient.conversations.stream()
                    .collect { message ->
                        allMessages.add(message.topic)
                    }
            } catch (e: Exception) {
            }
        }
        Thread.sleep(1000)

        runBlocking {
            eriPasskeyClient.conversations.newGroup(
                listOf(
                    boEOAClient.inboxId, davonPasskeyClient.inboxId
                )
            )
            boEOAClient.conversations.newGroup(
                listOf(
                    eriPasskeyClient.inboxId, davonPasskeyClient.inboxId
                )
            )
            davonPasskeyClient.conversations.findOrCreateDm(
                fixtures.alixClient.inboxId
            )
            fixtures.caroClient.conversations.findOrCreateDm(davonPasskeyClient.inboxId)
        }

        Thread.sleep(1000)
        assertEquals(4, allMessages.size)
        job.cancel()
    }

    @Test
    fun test8_AddAndRemovingAccounts() {
        val davonEOA = PrivateKeyBuilder()
        val davonSCW2 = FakeSCWWallet.generate(ANVIL_TEST_PRIVATE_KEY_3)
        val davonPasskey2 = FakePasskeyWallet()

        runBlocking { davonPasskeyClient.addAccount(davonEOA) }
        runBlocking { davonPasskeyClient.addAccount(davonSCW2) }
        runBlocking { davonPasskeyClient.addAccount(davonPasskey2) }

        var state = runBlocking { davonPasskeyClient.inboxState(true) }
        assertEquals(state.installations.size, 1)
        assertEquals(state.identities.size, 4)
        assertEquals(
            state.recoveryPublicIdentity.identifier,
            davonPasskey.publicIdentity.identifier
        )
        assertEquals(
            state.identities.map { it.identifier }.sorted(),
            listOf(
                davonEOA.publicIdentity.identifier,
                davonSCW2.publicIdentity.identifier,
                davonPasskey.publicIdentity.identifier,
                davonPasskey2.publicIdentity.identifier
            ).sorted()
        )

        runBlocking { davonPasskeyClient.removeAccount(davonPasskey, davonPasskey2.publicIdentity) }
        state = runBlocking { davonPasskeyClient.inboxState(true) }
        assertEquals(state.identities.size, 3)
        assertEquals(
            state.recoveryPublicIdentity.identifier,
            davonPasskey.publicIdentity.identifier
        )
        assertEquals(
            state.identities.map { it.identifier }.sorted(),
            listOf(
                davonEOA.publicIdentity.identifier,
                davonSCW2.publicIdentity.identifier,
                davonPasskey.publicIdentity.identifier
            ).sorted()
        )
        assertEquals(state.installations.size, 1)

        // Cannot remove the recovery address
        Assert.assertThrows(
            "Client error: Unknown Signer",
            GenericException::class.java
        ) {
            runBlocking {
                davonPasskeyClient.removeAccount(
                    davonEOA,
                    davonPasskey.publicIdentity
                )
            }
        }
    }
}
