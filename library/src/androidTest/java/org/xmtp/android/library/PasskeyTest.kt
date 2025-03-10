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
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.libxmtp.DecodedMessage
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import uniffi.xmtpv3.GenericException

@RunWith(AndroidJUnit4::class)
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
    fun testCanBuildAPasskey() {
        val davonPasskeyClient2 = runBlocking {
            Client.build(
                publicIdentity = davonPasskey.publicIdentity,
                options = options
            )
        }

        assertEquals(davonPasskeyClient.inboxId, davonPasskeyClient2.inboxId)
    }

    @Test
    fun testsCanCreateGroup() {
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
            listOf(davonPasskeyClient.inboxId, boEOAClient.inboxId, eriPasskeyClient.inboxId).sorted()
        )
        assertEquals(
            runBlocking { group2.members().map { it.identities.first() } },
            listOf(davonPasskey.publicIdentity, boEOAWallet.publicIdentity, eriPasskey.publicIdentity)
        )
    }

    @Test
    fun testsCanSendMessages() {
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
        val davonGroup = runBlocking { davonPasskeyClient.conversations.listGroups().last() }
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

}
