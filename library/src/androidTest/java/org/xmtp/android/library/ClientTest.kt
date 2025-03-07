package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.web3j.protocol.core.Ethereum
import org.xmtp.android.library.codecs.id
import org.xmtp.android.library.libxmtp.Identity
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.rawData
import org.xmtp.android.library.messages.walletAddress
import uniffi.xmtpv3.GenericException
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ClientTest {
    @Test
    fun testCanBeCreatedWithBundle() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeWallet = PrivateKeyBuilder()
        val options = ClientOptions(
            ClientOptions.Api(XMTPEnvironment.LOCAL, false),
            appContext = context,
            dbEncryptionKey = key
        )
        val client = runBlocking {
            Client.create(account = fakeWallet, options = options)
        }

        val clientIdentity = fakeWallet.identity
        runBlocking {
            client.canMessage(listOf(clientIdentity))[clientIdentity]?.let { assert(it) }
        }

        val fromBundle = runBlocking {
            Client.build(clientIdentity, options = options)
        }
        assertEquals(client.inboxId, fromBundle.inboxId)

        runBlocking {
            fromBundle.canMessage(listOf(clientIdentity))[clientIdentity]?.let { assert(it) }
        }
    }

    @Test
    fun testCreatesAClient() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeWallet = PrivateKeyBuilder()
        val options = ClientOptions(
            ClientOptions.Api(XMTPEnvironment.LOCAL, false),
            appContext = context,
            dbEncryptionKey = key
        )
        val clientIdentity = fakeWallet.identity

        val inboxId = runBlocking { Client.getOrCreateInboxId(options.api, clientIdentity) }
        val client = runBlocking {
            Client.create(
                account = fakeWallet,
                options = options
            )
        }
        runBlocking {
            client.canMessage(listOf(clientIdentity))[clientIdentity]?.let { assert(it) }
        }
        assert(client.installationId.isNotEmpty())
        assertEquals(inboxId, client.inboxId)
    }

    @Test
    fun testStaticCanMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = fixtures()
        val notOnNetwork = PrivateKeyBuilder()
        val alixIdentity = Identity(IdentityKind.ETHEREUM, fixtures.alix.walletAddress)
        val boIdentity = Identity(IdentityKind.ETHEREUM, fixtures.bo.walletAddress)
        val notOnNetworkIdentity =
            Identity(IdentityKind.ETHEREUM, notOnNetwork.getPrivateKey().walletAddress)

        val canMessageList = runBlocking {
            Client.canMessage(
                listOf(
                    alixIdentity,
                    notOnNetworkIdentity,
                    boIdentity
                ),
                ClientOptions.Api(XMTPEnvironment.LOCAL, false)
            )
        }

        val expectedResults = mapOf(
            alixIdentity to true,
            notOnNetworkIdentity to false,
            boIdentity to true
        )

        expectedResults.forEach { (id, expected) ->
            assertEquals(expected, canMessageList[id])
        }
    }

    @Test
    fun testStaticInboxIds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = fixtures()
        val states = runBlocking {
            Client.inboxStatesForInboxIds(
                listOf(fixtures.boClient.inboxId, fixtures.caroClient.inboxId),
                ClientOptions.Api(XMTPEnvironment.LOCAL, false)
            )
        }
        assertEquals(
            states.first().recoveryIdentity,
            fixtures.boAccount.identity
        )
        assertEquals(
            states.last().recoveryIdentity,
            fixtures.caroAccount.identity
        )
    }

    @Test
    fun testCanDeleteDatabase() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeWallet = PrivateKeyBuilder()
        val fakeWallet2 = PrivateKeyBuilder()
        var client = runBlocking {
            Client.create(
                account = fakeWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        val client2 = runBlocking {
            Client.create(
                account = fakeWallet2,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }

        runBlocking {
            client.conversations.newGroup(listOf(client2.inboxId))
            client.conversations.sync()
            assertEquals(client.conversations.listGroups().size, 1)
        }

        assert(client.dbPath.isNotEmpty())
        client.deleteLocalDatabase()

        client = runBlocking {
            Client.create(
                account = fakeWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        runBlocking {
            client.conversations.sync()
            assertEquals(client.conversations.listGroups().size, 0)
        }
    }

    @Test
    fun testCreatesADevClient() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeWallet = PrivateKeyBuilder()
        val client = runBlocking {
            Client.create(
                account = fakeWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.DEV, true),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        val clientIdentity = fakeWallet.identity
        runBlocking {
            client.canMessage(listOf(clientIdentity))[clientIdentity]?.let { assert(it) }
        }
    }

    @Test
    fun testCreatesAProductionClient() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeWallet = PrivateKeyBuilder()
        val client = runBlocking {
            Client.create(
                account = fakeWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.PRODUCTION, true),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        val clientIdentity = fakeWallet.identity
        runBlocking {
            client.canMessage(listOf(clientIdentity))[clientIdentity]?.let { assert(it) }
        }
    }

    @Test
    fun testPreAuthenticateToInboxCallback() {
        val fakeWallet = PrivateKeyBuilder()
        val expectation = CompletableFuture<Unit>()
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val preAuthenticateToInboxCallback: suspend () -> Unit = {
            expectation.complete(Unit)
        }

        val opts = ClientOptions(
            ClientOptions.Api(XMTPEnvironment.LOCAL, false),
            preAuthenticateToInboxCallback = preAuthenticateToInboxCallback,
            appContext = context,
            dbEncryptionKey = key
        )

        try {
            runBlocking { Client.create(account = fakeWallet, options = opts) }
            expectation.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            fail("Error: $e")
        }
    }

    @Test
    fun testCanDropReconnectDatabase() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeWallet = PrivateKeyBuilder()
        val fakeWallet2 = PrivateKeyBuilder()
        val boClient = runBlocking {
            Client.create(
                account = fakeWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        val alixClient = runBlocking {
            Client.create(
                account = fakeWallet2,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }

        runBlocking {
            boClient.conversations.newGroup(listOf(alixClient.inboxId))
            boClient.conversations.sync()
        }

        runBlocking {
            assertEquals(boClient.conversations.listGroups().size, 1)
        }

        boClient.dropLocalDatabaseConnection()

        assertThrows(
            "Client error: storage error: Pool needs to  reconnect before use",
            GenericException::class.java
        ) { runBlocking { boClient.conversations.listGroups() } }

        runBlocking { boClient.reconnectLocalDatabase() }

        runBlocking {
            assertEquals(boClient.conversations.listGroups().size, 1)
        }
    }

    @Test
    fun testCanGetAnInboxIdFromAddress() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()
        val boWallet = PrivateKeyBuilder()
        val alixClient = runBlocking {
            Client.create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        val boClient = runBlocking {
            Client.create(
                account = boWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        val boInboxId = runBlocking {
            alixClient.inboxIdFromIdentity(
                Identity(
                    IdentityKind.ETHEREUM,
                    boWallet.getPrivateKey().walletAddress
                )
            )
        }
        assertEquals(boClient.inboxId, boInboxId)
    }

    @Test
    fun testRevokesInstallations() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()

        val alixClient = runBlocking {
            Client.create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }

        val alixClient2 = runBlocking {
            Client.create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key,
                    dbDirectory = context.filesDir.absolutePath.toString()
                )
            )
        }

        val alixClient3 = runBlocking {
            Client.create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key,
                    dbDirectory = File(context.filesDir.absolutePath, "xmtp_db3").toPath()
                        .toString()
                )
            )
        }

        var state = runBlocking { alixClient3.inboxState(true) }
        assertEquals(state.installations.size, 3)

        runBlocking {
            alixClient3.revokeInstallations(alixWallet, listOf(alixClient2.installationId))
        }

        state = runBlocking { alixClient3.inboxState(true) }
        assertEquals(state.installations.size, 2)
    }

    @Test
    fun testRevokesAllOtherInstallations() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()
        runBlocking {
            val alixClient = Client.create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )

            val alixClient2 = Client.create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key,
                    dbDirectory = context.filesDir.absolutePath.toString()
                )
            )
        }

        val alixClient3 = runBlocking {
            Client.create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key,
                    dbDirectory = File(context.filesDir.absolutePath, "xmtp_db3").toPath()
                        .toString()
                )
            )
        }

        var state = runBlocking { alixClient3.inboxState(true) }
        assertEquals(state.installations.size, 3)
        assert(state.installations.first().createdAt != null)

        runBlocking {
            alixClient3.revokeAllOtherInstallations(alixWallet)
        }

        state = runBlocking { alixClient3.inboxState(true) }
        assertEquals(state.installations.size, 1)
    }

    @Test
    fun testsCanFindOthersInboxStates() {
        val fixtures = fixtures()
        val states = runBlocking {
            fixtures.alixClient.inboxStatesForInboxIds(
                true,
                listOf(fixtures.boClient.inboxId, fixtures.caroClient.inboxId)
            )
        }
        assertEquals(
            states.first().recoveryIdentity,
            Identity(IdentityKind.ETHEREUM, fixtures.bo.walletAddress)
        )
        assertEquals(
            states.last().recoveryIdentity,
            Identity(IdentityKind.ETHEREUM, fixtures.caro.walletAddress)
        )
    }

    @Test
    fun testsSignatures() {
        val fixtures = fixtures()
        val signature = fixtures.alixClient.signWithInstallationKey("Testing")
        assertEquals(fixtures.alixClient.verifySignature("Testing", signature), true)
        assertEquals(fixtures.alixClient.verifySignature("Not Testing", signature), false)

        val alixInstallationId = fixtures.alixClient.installationId
        assertEquals(
            fixtures.alixClient.verifySignatureWithInstallationId(
                "Testing",
                signature,
                alixInstallationId
            ),
            true
        )
        assertEquals(
            fixtures.alixClient.verifySignatureWithInstallationId(
                "Not Testing",
                signature,
                alixInstallationId
            ),
            false
        )
        assertEquals(
            fixtures.alixClient.verifySignatureWithInstallationId(
                "Testing",
                signature,
                fixtures.boClient.installationId
            ),
            false
        )
        assertEquals(
            fixtures.boClient.verifySignatureWithInstallationId(
                "Testing",
                signature,
                alixInstallationId
            ),
            true
        )
        fixtures.alixClient.deleteLocalDatabase()

        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixClient2 = runBlocking {
            Client.create(
                account = fixtures.alixAccount,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }

        assertEquals(
            alixClient2.verifySignatureWithInstallationId(
                "Testing",
                signature,
                alixInstallationId
            ),
            true
        )
        assertEquals(
            alixClient2.verifySignatureWithInstallationId(
                "Testing2",
                signature,
                alixInstallationId
            ),
            false
        )
    }

    @OptIn(DelicateApi::class)
    @Test
    fun testAddAccounts() {
        val fixtures = fixtures()
        val alix2Wallet = PrivateKeyBuilder()
        val alix3Wallet = PrivateKeyBuilder()
        runBlocking { fixtures.alixClient.addAccount(alix2Wallet) }
        runBlocking { fixtures.alixClient.addAccount(alix3Wallet) }

        val state = runBlocking { fixtures.alixClient.inboxState(true) }
        assertEquals(state.installations.size, 1)
        assertEquals(state.identities.size, 3)
        assertEquals(
            state.recoveryIdentity,
            fixtures.alixAccount.identity
        )
        assertEquals(
            state.identities,
            listOf(
                alix2Wallet.identity,
                alix3Wallet.identity,
                Identity(IdentityKind.ETHEREUM, fixtures.alix.walletAddress)
            )
        )
    }

    @OptIn(DelicateApi::class)
    @Test
    fun testAddAccountsWithExistingInboxIds() {
        val fixtures = fixtures()

        assertThrows(
            "This wallet is already associated with inbox ${fixtures.boClient.inboxId}",
            XMTPException::class.java
        ) {
            runBlocking { fixtures.alixClient.addAccount(fixtures.boAccount) }
        }

        assert(fixtures.boClient.inboxId != fixtures.alixClient.inboxId)
        runBlocking { fixtures.alixClient.addAccount(fixtures.boAccount, true) }

        val state = runBlocking { fixtures.alixClient.inboxState(true) }
        assertEquals(state.identities.size, 2)

        val inboxId =
            runBlocking {
                fixtures.alixClient.inboxIdFromIdentity(
                    Identity(
                        IdentityKind.ETHEREUM,
                        fixtures.bo.walletAddress
                    )
                )
            }
        assertEquals(inboxId, fixtures.alixClient.inboxId)
    }

    @OptIn(DelicateApi::class)
    @Test
    fun testRemovingAccounts() {
        val fixtures = fixtures()
        val alix2Wallet = PrivateKeyBuilder()
        val alix3Wallet = PrivateKeyBuilder()
        runBlocking { fixtures.alixClient.addAccount(alix2Wallet) }
        runBlocking { fixtures.alixClient.addAccount(alix3Wallet) }

        var state = runBlocking { fixtures.alixClient.inboxState(true) }
        assertEquals(state.identities.size, 3)
        assertEquals(
            state.recoveryIdentity,
            fixtures.alixAccount.identity
        )

        runBlocking {
            fixtures.alixClient.removeAccount(
                fixtures.alixAccount,
                Identity(IdentityKind.ETHEREUM, alix2Wallet.getPrivateKey().walletAddress)
            )
        }
        state = runBlocking { fixtures.alixClient.inboxState(true) }
        assertEquals(state.identities.size, 2)
        assertEquals(
            state.recoveryIdentity,
            Identity(IdentityKind.ETHEREUM, fixtures.alix.walletAddress)
        )
        assertEquals(
            state.identities,
            listOf(
                Identity(IdentityKind.ETHEREUM, alix3Wallet.getPrivateKey().walletAddress),
                fixtures.alixAccount.identity
            )
        )
        assertEquals(state.installations.size, 1)

        // Cannot remove the recovery address
        assertThrows(
            "Client error: Unknown Signer",
            GenericException::class.java
        ) {
            runBlocking {
                fixtures.alixClient.removeAccount(
                    alix3Wallet,
                    fixtures.alixAccount.identity
                )
            }
        }
    }

    @Test
    fun testErrorsIfDbEncryptionKeyIsLost() {
        val key = SecureRandom().generateSeed(32)
        val badKey = SecureRandom().generateSeed(32)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()

        val alixClient = runBlocking {
            Client.create(
                account = alixWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }

        assertThrows(
            "Error creating V3 client: Storage error: PRAGMA key or salt has incorrect value",
            XMTPException::class.java
        ) {
            runBlocking {
                Client.build(
                    identity = Identity(
                        IdentityKind.ETHEREUM,
                        alixWallet.getPrivateKey().walletAddress
                    ),
                    options = ClientOptions(
                        ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                        appContext = context,
                        dbEncryptionKey = badKey,
                    )
                )
            }
        }

        assertThrows(
            "Error creating V3 client: Storage error: PRAGMA key or salt has incorrect value",
            XMTPException::class.java
        ) {
            runBlocking {
                Client.create(
                    account = alixWallet,
                    options = ClientOptions(
                        ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                        appContext = context,
                        dbEncryptionKey = badKey,
                    )
                )
            }
        }
    }

    @Test
    fun testCreatesAClientManually() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeWallet = PrivateKeyBuilder()
        val options = ClientOptions(
            ClientOptions.Api(XMTPEnvironment.LOCAL, false),
            appContext = context,
            dbEncryptionKey = key
        )
        val inboxId = runBlocking {
            Client.getOrCreateInboxId(
                options.api,
                fakeWallet.identity
            )
        }
        val client = runBlocking {
            Client.ffiCreateClient(fakeWallet.identity, options)
        }
        runBlocking {
            val sigRequest = client.ffiSignatureRequest()
            sigRequest?.let { signatureRequest ->
                signatureRequest.addEcdsaSignature(fakeWallet.sign(signatureRequest.signatureText()).rawData)
                client.ffiRegisterIdentity(signatureRequest)
            }
        }
        runBlocking {
            client.canMessage(listOf(fakeWallet.identity))[fakeWallet.identity]?.let { assert(it) }
        }
        assert(client.installationId.isNotEmpty())
        assertEquals(inboxId, client.inboxId)
    }
}
