package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.Client.Companion.ffiApplySignatureRequest
import org.xmtp.android.library.Client.Companion.ffiRevokeInstallations
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PublicIdentity
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress
import uniffi.xmtpv3.FfiLogLevel
import uniffi.xmtpv3.FfiLogRotation
import uniffi.xmtpv3.GenericException
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

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

        val clientIdentity = fakeWallet.publicIdentity
        runBlocking {
            client.canMessage(listOf(clientIdentity))[clientIdentity.identifier]?.let { assert(it) }
        }

        val fromBundle = runBlocking {
            Client.build(clientIdentity, options = options)
        }
        assertEquals(client.inboxId, fromBundle.inboxId)

        runBlocking {
            fromBundle.canMessage(listOf(clientIdentity))[clientIdentity.identifier]?.let {
                assert(
                    it
                )
            }
        }
    }

    @Test
    fun testCanBeBuiltOffline() {
        val fixtures = fixtures()
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wallet = PrivateKeyBuilder()
        val options = ClientOptions(
            ClientOptions.Api(XMTPEnvironment.LOCAL, false),
            appContext = context,
            dbEncryptionKey = key
        )
        val client = runBlocking {
            Client.create(account = wallet, options = options)
        }

        client.debugInformation.clearAllStatistics()
        println(client.debugInformation.aggregateStatistics)
        val builtClient = runBlocking {
            Client.build(client.publicIdentity, options = options, client.inboxId)
        }
        println(client.debugInformation.aggregateStatistics)
        assertEquals(client.inboxId, builtClient.inboxId)

        val convos = runBlocking {
            val group = builtClient.conversations.newGroup(listOf(fixtures.alixClient.inboxId))
            group.send("howdy")
            val alixDm = fixtures.alixClient.conversations.newConversation(builtClient.inboxId)
            alixDm.send("howdy")
            val boGroup =
                fixtures.boClient.conversations.newGroupWithIdentities(listOf(builtClient.publicIdentity))
            boGroup.send("howdy")
            builtClient.conversations.syncAllConversations()
            builtClient.conversations.list()
        }

        assertEquals(convos.size, 3)
    }

    @Test
    fun testCreatesAClient() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeWallet = PrivateKeyBuilder()
        val options = ClientOptions(
            ClientOptions.Api(XMTPEnvironment.LOCAL, false, "Testing/0.0.0"),
            appContext = context,
            dbEncryptionKey = key
        )
        val clientIdentity = fakeWallet.publicIdentity

        val inboxId = runBlocking { Client.getOrCreateInboxId(options.api, clientIdentity) }
        val client = runBlocking {
            Client.create(
                account = fakeWallet,
                options = options
            )
        }
        runBlocking {
            client.canMessage(listOf(clientIdentity))[clientIdentity.identifier]?.let { assert(it) }
        }
        assert(client.installationId.isNotEmpty())
        assertEquals(inboxId, client.inboxId)
        assertEquals(fakeWallet.publicIdentity.identifier, client.publicIdentity.identifier)
    }

    @Test
    fun testStaticCanMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = fixtures()
        val notOnNetwork = PrivateKeyBuilder()
        val alixPublicIdentity = PublicIdentity(IdentityKind.ETHEREUM, fixtures.alix.walletAddress)
        val boPublicIdentity = PublicIdentity(IdentityKind.ETHEREUM, fixtures.bo.walletAddress)
        val notOnNetworkPublicIdentity =
            PublicIdentity(IdentityKind.ETHEREUM, notOnNetwork.getPrivateKey().walletAddress)

        val canMessageList = runBlocking {
            Client.canMessage(
                listOf(
                    alixPublicIdentity,
                    notOnNetworkPublicIdentity,
                    boPublicIdentity
                ),
                ClientOptions.Api(XMTPEnvironment.LOCAL, false)
            )
        }

        val expectedResults = mapOf(
            alixPublicIdentity to true,
            notOnNetworkPublicIdentity to false,
            boPublicIdentity to true
        )

        expectedResults.forEach { (id, expected) ->
            assertEquals(expected, canMessageList[id.identifier])
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
            states.first().recoveryPublicIdentity.identifier,
            fixtures.boAccount.publicIdentity.identifier
        )
        assertEquals(
            states.last().recoveryPublicIdentity.identifier,
            fixtures.caroAccount.publicIdentity.identifier
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
        val clientIdentity = fakeWallet.publicIdentity
        runBlocking {
            client.canMessage(listOf(clientIdentity))[clientIdentity.identifier]?.let { assert(it) }
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
        val clientIdentity = fakeWallet.publicIdentity
        runBlocking {
            client.canMessage(listOf(clientIdentity))[clientIdentity.identifier]?.let { assert(it) }
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
                PublicIdentity(
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
            states.first().recoveryPublicIdentity.identifier,
            fixtures.bo.walletAddress
        )
        assertEquals(
            states.last().recoveryPublicIdentity.identifier,
            fixtures.caro.walletAddress
        )
    }

    @Test
    fun testsCanSeeKeyPackageStatus() {
        val fixtures = fixtures()
        runBlocking { Client.connectToApiBackend(ClientOptions.Api(XMTPEnvironment.LOCAL, true)) }
        val inboxState = runBlocking {
            Client.inboxStatesForInboxIds(
                listOf(fixtures.alixClient.inboxId),
                ClientOptions.Api(XMTPEnvironment.LOCAL, true)
            ).first()
        }
        val installationIds = inboxState.installations.map { it.installationId }
        val keyPackageStatus = runBlocking {
            Client.keyPackageStatusesForInstallationIds(
                installationIds,
                ClientOptions.Api(XMTPEnvironment.LOCAL, true)
            )
        }
        for (installationId: String in keyPackageStatus.keys) {
            val thisKPStatus = keyPackageStatus.get(installationId)!!
            val notBeforeDate = thisKPStatus.lifetime?.notBefore?.let {
                java.time.Instant.ofEpochSecond(it.toLong()).toString()
            } ?: "null"
            val notAfterDate = thisKPStatus.lifetime?.notAfter?.let {
                java.time.Instant.ofEpochSecond(it.toLong()).toString()
            } ?: "null"
            println("inst: " + installationId + " - valid from: " + notBeforeDate + " to: " + notAfterDate)
            println("error code: " + thisKPStatus.validationError)
            val notBefore = thisKPStatus.lifetime?.notBefore
            val notAfter = thisKPStatus.lifetime?.notAfter
            if (notBefore != null && notAfter != null) {
                assertEquals((3600 * 24 * 28 * 3 + 3600).toULong(), notAfter - notBefore)
            }
        }
    }

    @Test
    fun testsCanSeeInvalidKeyPackageStatusOnDev() {
        runBlocking { Client.connectToApiBackend(ClientOptions.Api(XMTPEnvironment.PRODUCTION, true)) }
        val inboxState = runBlocking {
            Client.inboxStatesForInboxIds(
                listOf("f87420435131ea1b911ad66fbe4b626b107f81955da023d049f8aef6636b8e1b"),
                ClientOptions.Api(XMTPEnvironment.PRODUCTION, true)
            ).first()
        }
        val installationIds = inboxState.installations.map{ it.installationId }
        val keyPackageStatus = runBlocking {
            Client.keyPackageStatusesForInstallationIds(
                installationIds,
                ClientOptions.Api(XMTPEnvironment.PRODUCTION, true)
            )
        }
        for (installationId: String in keyPackageStatus.keys) {
            val thisKPStatus = keyPackageStatus.get(installationId)!!
            val notBeforeDate = thisKPStatus.lifetime?.notBefore?.let {
                java.time.Instant.ofEpochSecond(it.toLong()).toString()
            } ?: "null"
            val notAfterDate = thisKPStatus.lifetime?.notAfter?.let {
                java.time.Instant.ofEpochSecond(it.toLong()).toString()
            } ?: "null"
            println("inst: " + installationId + " - valid from: " + notBeforeDate + " to: " + notAfterDate)
            println("error code: " + thisKPStatus.validationError)
        }
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
            state.recoveryPublicIdentity.identifier,
            fixtures.alixAccount.publicIdentity.identifier
        )
        assertEquals(
            state.identities.map { it.identifier }.sorted(),
            listOf(
                alix2Wallet.publicIdentity.identifier,
                alix3Wallet.publicIdentity.identifier,
                fixtures.alix.walletAddress
            ).sorted()
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
                    PublicIdentity(
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
            state.recoveryPublicIdentity.identifier,
            fixtures.alixAccount.publicIdentity.identifier
        )

        runBlocking {
            fixtures.alixClient.removeAccount(
                fixtures.alixAccount,
                PublicIdentity(IdentityKind.ETHEREUM, alix2Wallet.getPrivateKey().walletAddress)
            )
        }
        state = runBlocking { fixtures.alixClient.inboxState(true) }
        assertEquals(state.identities.size, 2)
        assertEquals(
            state.recoveryPublicIdentity.identifier,
            fixtures.alix.walletAddress
        )
        assertEquals(
            state.identities.map { it.identifier }.sorted(),
            listOf(
                alix3Wallet.getPrivateKey().walletAddress,
                fixtures.alixAccount.publicIdentity.identifier
            ).sorted()
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
                    fixtures.alixAccount.publicIdentity
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
                    publicIdentity = PublicIdentity(
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
                fakeWallet.publicIdentity
            )
        }
        val client = runBlocking {
            Client.ffiCreateClient(fakeWallet.publicIdentity, options)
        }
        runBlocking {
            val sigRequest = client.ffiSignatureRequest()
            sigRequest?.let { signatureRequest ->
                signatureRequest.addEcdsaSignature(fakeWallet.sign(signatureRequest.signatureText()).rawData)
                client.ffiRegisterIdentity(signatureRequest)
            }
        }
        runBlocking {
            client.canMessage(listOf(fakeWallet.publicIdentity))[fakeWallet.publicIdentity.identifier]?.let {
                assert(
                    it
                )
            }
        }
        assert(client.installationId.isNotEmpty())
        assertEquals(inboxId, client.inboxId)
    }

    @Test
    fun testCanManageAddRemoveManually() = runBlocking {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()
        val boWallet = PrivateKeyBuilder()

        val options = ClientOptions(
            ClientOptions.Api(XMTPEnvironment.LOCAL, false),
            appContext = context,
            dbEncryptionKey = key
        )

        val alix = Client.create(alixWallet, options)

        var inboxState = alix.inboxState(true)
        assertEquals(1, inboxState.identities.size)

        val sigRequest = alix.ffiAddIdentity(boWallet.publicIdentity)
        val signedMessage = boWallet.sign(sigRequest.signatureText()).rawData

        sigRequest.addEcdsaSignature(signedMessage)
        alix.ffiApplySignatureRequest(sigRequest)

        inboxState = alix.inboxState(true)
        assertEquals(2, inboxState.identities.size)

        val sigRequest2 = alix.ffiRevokeIdentity(boWallet.publicIdentity)
        val signedMessage2 = alixWallet.sign(sigRequest2.signatureText()).rawData

        sigRequest2.addEcdsaSignature(signedMessage2)
        alix.ffiApplySignatureRequest(sigRequest2)

        inboxState = alix.inboxState(true)
        assertEquals(1, inboxState.identities.size)
    }

    @Test
    fun testCanManageRevokeManually() = runBlocking {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()
        val alix = Client.create(
            account = alixWallet,
            options = ClientOptions(
                ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                appContext = context,
                dbEncryptionKey = key
            )
        )

        val alix2 = Client.create(
            account = alixWallet,
            options = ClientOptions(
                ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                appContext = context,
                dbEncryptionKey = key,
                dbDirectory = context.filesDir.absolutePath.toString()
            )
        )
        val alix3 = runBlocking {
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

        var inboxState = alix3.inboxState(true)
        assertEquals(inboxState.installations.size, 3)

        val sigText = alix.ffiRevokeInstallations(listOf(alix2.installationId.hexToByteArray()))
        val signedMessage = alixWallet.sign(sigText.signatureText()).rawData

        sigText.addEcdsaSignature(signedMessage)
        alix.ffiApplySignatureRequest(sigText)

        inboxState = alix.inboxState(true)
        assertEquals(2, inboxState.installations.size)

        val sigText2 = alix.ffiRevokeAllOtherInstallations()
        val signedMessage2 = alixWallet.sign(sigText2.signatureText()).rawData

        sigText2.addEcdsaSignature(signedMessage2)
        alix.ffiApplySignatureRequest(sigText2)

        inboxState = alix.inboxState(true)
        assertEquals(1, inboxState.installations.size)
    }

    @Test
    fun testPersistentLogging() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Client.clearXMTPLogs(context)
        val fakeWallet = PrivateKeyBuilder()

        // Create a specific log directory for this test
        val logDirectory = File(context.filesDir, "xmtp_test_logs")
        if (logDirectory.exists()) {
            logDirectory.deleteRecursively()
        }
        logDirectory.mkdirs()

        try {
            // Activate persistent logging with a small number of log files
            Client.activatePersistentLibXMTPLogWriter(
                context,
                FfiLogLevel.TRACE,
                FfiLogRotation.HOURLY,
                3
            )

            // Log the actual log directory path
            val actualLogDir = File(context.filesDir, "xmtp_logs")
            println("Log directory path: ${actualLogDir.absolutePath}")

            // Create a client
            val client = runBlocking {
                Client.create(
                    account = fakeWallet,
                    options = ClientOptions(
                        ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                        appContext = context,
                        dbEncryptionKey = key
                    )
                )
            }

            // Create a group with only the client as a member
            runBlocking {
                client.conversations.newGroup(emptyList())
                client.conversations.sync()
            }

            // Verify the group was created
            val groups = runBlocking { client.conversations.listGroups() }
            assertEquals(1, groups.size)

            // Deactivate logging
            Client.deactivatePersistentLibXMTPLogWriter()

            // Print log files content to console
            val logFiles = File(context.filesDir, "xmtp_logs").listFiles()
            println("Found ${logFiles?.size ?: 0} log files:")

            logFiles?.forEach { file ->
                println("\n--- Log file: ${file.absolutePath} (${file.length()} bytes) ---")
                try {
                    val content = file.readText()
                    // Print first 1000 chars to avoid overwhelming the console
                    println(content.take(1000) + (if (content.length > 1000) "...(truncated)" else ""))
                } catch (e: Exception) {
                    println("Error reading log file: ${e.message}")
                }
            }
        } finally {
            // Make sure logging is deactivated
            Client.deactivatePersistentLibXMTPLogWriter()
        }
        val logFiles = Client.getXMTPLogFilePaths(context)
        assertEquals(logFiles.size, 1)
        println(logFiles.get(0))
        Client.clearXMTPLogs(context)
        val logFiles2 = Client.getXMTPLogFilePaths(context)
        assertEquals(logFiles2.size, 0)
    }

    @Test
    fun testNetworkDebugInformation() = runBlocking {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()
        val alix = Client.create(
            account = alixWallet,
            options = ClientOptions(
                ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                appContext = context,
                dbEncryptionKey = key
            )
        )
        alix.debugInformation.clearAllStatistics()

        val job = CoroutineScope(Dispatchers.IO).launch {
            alix.conversations.streamAllMessages().collect { }
        }
        val group = alix.conversations.newGroup(emptyList())
        group.send("hi")

        delay(4000)

        val aggregateStats2 = alix.debugInformation.aggregateStatistics
        println("Aggregate Stats Create:\n$aggregateStats2")

        val apiStats2 = alix.debugInformation.apiStatistics
        assertEquals(0, apiStats2.uploadKeyPackage)
        assertEquals(0, apiStats2.fetchKeyPackage)
        assertEquals(6, apiStats2.sendGroupMessages)
        assertEquals(0, apiStats2.sendWelcomeMessages)
        assertEquals(1, apiStats2.queryWelcomeMessages)
        assertEquals(1, apiStats2.subscribeWelcomes)

        val identityStats2 = alix.debugInformation.identityStatistics
        assertEquals(0, identityStats2.publishIdentityUpdate)
        assertEquals(2, identityStats2.getIdentityUpdatesV2)
        assertEquals(0, identityStats2.getInboxIds)
        assertEquals(0, identityStats2.verifySmartContractWalletSignature)
        job.cancel()
    }

    @Test
    fun testUploadArchiveDebugInformation() = runBlocking {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()
        val alix = Client.create(
            account = alixWallet,
            options = ClientOptions(
                ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                appContext = context,
                dbEncryptionKey = key
            )
        )
        val uploadKey = alix.debugInformation.uploadDebugInformation()
        assert(uploadKey.isNotEmpty())
    }

    @Test
    fun testCannotCreateMoreThan10Installations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val encryptionKey = SecureRandom().generateSeed(32)
        val wallet = PrivateKeyBuilder()

        val clients = mutableListOf<Client>()

        repeat(10) { i ->
            val client = runBlocking {
                Client.create(
                    account = wallet,
                    options = ClientOptions(
                        ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                        appContext = context,
                        dbEncryptionKey = encryptionKey,
                        dbDirectory = File(context.filesDir, "xmtp_db_$i").absolutePath
                    )
                )
            }
            clients.add(client)
        }

        val state = runBlocking { clients.first().inboxState(true) }
        assertEquals(10, state.installations.size)

        // Attempt to create a 6th installation, should fail
        assertThrows(
            "Error creating V3 client: Client builder error: Cannot register a new installation because the InboxID ${clients[0].inboxId} has already registered 10/10 installations. Please revoke existing installations first.",
            XMTPException::class.java
        ) {
            runBlocking {
                Client.create(
                    account = wallet,
                    options = ClientOptions(
                        ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                        appContext = context,
                        dbEncryptionKey = encryptionKey,
                        dbDirectory = File(context.filesDir, "xmtp_db_10").absolutePath
                    )
                )
            }
        }

        val boWallet = PrivateKeyBuilder()
        val boClient = runBlocking {
            Client.create(
                account = boWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = SecureRandom().generateSeed(32),
                    dbDirectory = File(context.filesDir, "xmtp_bo").absolutePath
                )
            )
        }

        val group = runBlocking {
            boClient.conversations.newGroup(listOf(clients[2].inboxId))
        }

        val members = runBlocking { group.members() }
        val alixMember = members.find { it.inboxId == clients.first().inboxId }
        assertNotNull(alixMember)
        val inboxState =
            runBlocking { boClient.inboxStatesForInboxIds(true, listOf(alixMember!!.inboxId)) }
        assertEquals(10, inboxState.first().installations.size)

        runBlocking {
            clients.first().revokeInstallations(wallet, listOf(clients[9].installationId))
        }

        val stateAfterRevoke = runBlocking { clients.first().inboxState(true) }
        assertEquals(9, stateAfterRevoke.installations.size)

        runBlocking {
            Client.create(
                account = wallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    appContext = context,
                    dbEncryptionKey = encryptionKey,
                    dbDirectory = File(context.filesDir, "xmtp_db_11").absolutePath
                )
            )
        }
        val updatedState = runBlocking { clients.first().inboxState(true) }
        assertEquals(10, updatedState.installations.size)
    }

    @Test
    fun testStaticRevokeOneOfFiveInstallations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wallet = PrivateKeyBuilder()
        val encryptionKey = SecureRandom().generateSeed(32)

        val clients = mutableListOf<Client>()
        repeat(5) { i ->
            val client = runBlocking {
                Client.create(
                    account = wallet,
                    options = ClientOptions(
                        ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                        appContext = context,
                        dbEncryptionKey = encryptionKey,
                        dbDirectory = File(context.filesDir, "xmtp_db_$i").absolutePath
                    )
                )
            }
            clients.add(client)
        }

        var state = runBlocking { clients.last().inboxState(true) }
        assertEquals(5, state.installations.size)

        val toRevokeId = clients[1].installationId
        runBlocking {
            Client.revokeInstallations(
                ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                wallet,
                clients.first().inboxId,
                listOf(toRevokeId)
            )
        }

        state = runBlocking { clients.last().inboxState(true) }
        assertEquals(4, state.installations.size)
        val remainingIds = state.installations.map { it.installationId }
        assertFalse(remainingIds.contains(toRevokeId))
    }

    @Test
    fun testStaticRevokeInstallationsManually() = runBlocking {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()
        val apiOptions = ClientOptions.Api(XMTPEnvironment.LOCAL, false)
        val alix = Client.create(
            account = alixWallet,
            options = ClientOptions(
                apiOptions,
                appContext = context,
                dbEncryptionKey = key
            )
        )

        val alix2 = Client.create(
            account = alixWallet,
            options = ClientOptions(
                apiOptions,
                appContext = context,
                dbEncryptionKey = key,
                dbDirectory = context.filesDir.absolutePath.toString()
            )
        )
        val alix3 = runBlocking {
            Client.create(
                account = alixWallet,
                options = ClientOptions(
                    apiOptions,
                    appContext = context,
                    dbEncryptionKey = key,
                    dbDirectory = File(context.filesDir.absolutePath, "xmtp_db3").toPath()
                        .toString()
                )
            )
        }

        var inboxState = alix3.inboxState(true)
        assertEquals(inboxState.installations.size, 3)

        val sigText = ffiRevokeInstallations(
            apiOptions,
            alixWallet.publicIdentity,
            alix.inboxId,
            listOf(alix2.installationId)
        )
        val signedMessage = alixWallet.sign(sigText.signatureText()).rawData

        sigText.addEcdsaSignature(signedMessage)
        ffiApplySignatureRequest(apiOptions, sigText)

        inboxState = alix.inboxState(true)
        assertEquals(2, inboxState.installations.size)
    }

    @Test
    fun testMassiveSyncAndConsentRace() {
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alixWallet = PrivateKeyBuilder()
        val boWallet = PrivateKeyBuilder()
        val apiOptions = ClientOptions.Api(XMTPEnvironment.LOCAL, false)
        val primary = runBlocking {
            Client.create(
                account = alixWallet,
                options = ClientOptions(
                    apiOptions,
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        val secondary = runBlocking {
            Client.create(
                account = boWallet,
                options = ClientOptions(
                    apiOptions,
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }

//        repeat(100) { i ->
//            val peerKey = PrivateKeyBuilder()
//            val peerClient = runBlocking {
//                Client.create(
//                    account = peerKey,
//                    options = ClientOptions(
//                        apiOptions,
//                        appContext = context,
//                        dbEncryptionKey = key,
//                    )
//                )
//            }
//
//            val inboxId = peerClient.inboxId
//            println("LOPI $inboxId")
//            peerClient.deleteLocalDatabase()
//        }

        // Create 1 DM upfront
        val dm = runBlocking { primary.conversations.findOrCreateDm("0c83ef1431db42d89510cef3f5fe03820ed2ec9b624c89e69d9abc5e31d7c8a8") }

//         Add 100 more DMs
        val inboxIds = listOf("2e3a156fbdae35db3f0de25f2ceb1af0cd95ae9f8aa856511d35de40803a3f83","64cf1236c239f6d56f9b7892be7d46812f9a0ecdfd582efcc274c0b78dc59ca9","c463da87514cedc4c2ef753dc6dd6e9301668b8d31d9ae9242664d52136126f5","50eb93187f4540d83ba751f4a08fc6a9c2bc4cf4d9548a049216c7b4d635954f","31b925addb14e54ec6be57220ca93cc68e70ce317c7f4fb5f8b7034c8f9113b8","22314cd73ba60417a4989ef0c84433f24e41f290f0d8db028f2fd46e760bd8d3","81611e4c22c499b9494ae90b8df6931c081cad86be8e8943490319a2259f9ddc","477d0d34d73fb67fc0eb505498e9e22ba43056c4989ecf5609cdabb6c1c01164","f0d1723f324b5cd029b4d1efffa33652178df585d7ff623713a87f5fb5a397ae","1713a5b537317c742aea7e6d7bc5838942b6b4094025e947f55bb0095b03f93e","57eec2cf6bd147093fd201abed49bc65351f097c803a3da29230cb793cc7d972","19af5f148e00f4e30ec40e7d3ced51cd3d3e8a57a69a8525daaf8146309b3d28","83a76d275b7afbf32ed9376d325161b3ed56ffd1453456fbf328f4ed61ada3e2","d8f20abd7bb13b00cd9b85d9bd377f9bcad6c4d66fd0443d13849e27e651f62e","f338a92e426bc4aa2464bb99618e8192e684ccbab55ee0396eb753ede2bc5926","7fea54d0e8cd7b41b009d84057e33741581d7844472ced737409e99ab88eb76d","4b3a79c7b9ab6681cc71bdccb2a02234f6977f4d3b05f9e8dbe3320a99a871a9","8fe82e73af3f33b0989ceded39ab44190648a7699ddf1d5ff1a9bd31406f8507","6a015dc5d723a30be419b7514891cf9c43b669a450233c32a4867bd6397a8843","ab35c6210b6b58825d776723afac1b004c678544ec1312622f0aadcfe730f63f","16e80f54d788345f18b8234c8f8c48f339e02ce9f6418219664f06dc21f76f55","d3e9b78690558647719dbfaf1995064b8d3ec9ce564ea1ecef98f74e3944aa58","a903bae3a27bb54693837dc89649487aefef851283bbc17b3b20556b65f64669","28b68992eca708b48bee85b331b215ae5bc80f04bc19b72912fd7e9f42ce85e4","965bbd16a3c8f4baccf9f6ed5a6f9870c19520fe1e560534cb487a5caadab88a","12391ca9ba31ff7bcafcfdb6a4448cb512e34195c3bd00790cd531295877d93a","43a5071dfb684859378b3ec5b22c5325ee0444eb31d6ea90498c9b9d99b415e0","8f22226b6cc42f71777c5958f21b6bb75d6c51f68eb04450fc43ee4c32bf284f","99230f07a80e36bd5ebba721037600be874a50d235a958f325ff89a35ba6386c","d4a079ea84e6626d26ef8d59ea0703083d8294bd8618b04f0b2fcc777a87db32","ec95ccffb71caa4d6e720d5e9255a1856ad05c8268c3384d5293fe42e8a87b38","5f52552610f98b60d395866a5b3a4ab5f06bddcaecb67ff480451eea5e03664a","c2399a05c4369e79b05634d6f01f9dac9a866b6f5aa8deed44a9891eb1d35885","1e5f73cc3bc056cf6d34ef92fdca6d2443c0b859234d450f705684427fdf21e1","aa8ff489fece927a20af6aa8e4e4abd9627a92b296321a2ba581284842935e45","38d81850d7fb559c96f1895b53b87189a03773ef4f7995b29e3a95094472d392","21610ca96c1b01ad1a768862e8592646967e5baf9b34537167c0dd9033412d23","d0dbc0d79c368d4c08e84c0309671f45621cfffff8fda2f490fb1f285e7b5fe4","79bddcb1f0f74576b02aa5d6a517837a9ec1ee77032d1ea7eb3372986429dd7f","9fa9ec246888639d40c40d8548ed7ccceaf814d4807c8ed4258cdf053bb6e37f","f8b6ebd893cd416828fa979390236d3f05057ecbd4000c29a12052957b8c2f4f","751f3e3db986c558df537df166271c1e4cf6ae5e77b66f195b24fbcde3a4de16","acce6ec3eee0c35d52cfd4c87dfc1a95f0b6c0efabbf1ab8c51c5f1cbf13c2f7","b516dcb4805ea5608ed3f43af8168c62584de9d5c32d2e88ac57f4801bf3964c","c5458d85544301d7e7b700651990517919a605eec636ff63b9db8f10008a5283","6d6318d07368405274a1fcd2bbb0220aec5d84b2db2eefa2cea33ca5ab80b7fb","c3a8a2cfa7b9bb7441c288907f19879d41201a3f64590f5f2838c10fb70a770b","ef0ccb1356d0a87532a0f5cc44e27410c3f72a532c3d365a088537d7c9cf5f0a","0e81c864f0132bf84ac6c3e0a834fda3aff3d21fd00e04dcb1e3f4ad8450d2e3","f965edd7b88c641c5ef0f55d8c7e2fe9c5649044558b9833e535394f11fe0f62","c5110eeb62e0754656dddbc0f35e4845794b295607b98120c470a332f1f2649b","61ae5aa9027349afdc171c043d36d187bf3df602c58ac5a6882eaa72889da062","5400b9614945ea83f3e3113e383f44f3f81881195fd30942eeef8679801484e8","23c82cef56319bbca94b9335d4410c569cf38364afee505af5e81ebd53a34dda","c09678cfb2f217b5261ffad8f87a2c033a9afa1a3c48dd1de3f3f1943740fe93","c06fd1d81a8febe993faf3fbfc25c6bd47a3e5698c500f12cab0e68caf516dd9","67bd934d302b538b77b1183ab47e39957435e54443bd1f6708c767f693356794","360c5a5221014d81524bd970204f29c17e23e520193118d8593e48798e4927d6","8fbaeecfd79a52b5ebfc6cd7576b97efd53115175981c304221c432c7f7bb4a2","a081b93f3b7d8da914e115496f98b306047f623c70aafc6e2d76805f61690320","e01b5c184659e16aff1f646ab7f99e036facec9e068adc3c08a95ceb1fb66ffb","b20adf6d56ce3919d0e3ca9f5f132b33e66e722346ec0c058879a9b926ccbbcd","fcb362d81467b1be366a94b31ed403016f94cefa1fed27df3c66f192b5e31c81","7f47e037b3ea839ad3624245aca0842c28da346808f39aa88888526dfa3e38e6","e6ac53c0dc6a84f129364ea02b4274a5a29ea2e1b97851d1866f3e7b8d4f4dd1","1b60c46ffe55281386047441acba37c6b65e44f76c74be9d81447cc5ac3e9161","c022cae80768229b12556f515644e089270d81000e79c938d4a9239a27c0f43d","eba0bcdab306f805d7d300b662c5c91656aa3105e64fa83d73a9e8374c90cfe8","eb45259914526798ddaaf77c57014d236ede857ff8e9013e230b2a6adb298d7b","a277d6dda1e8ca0f2cf8584feb202983a4858bcb456298eb14b9b34369dbcb6a","318df964d2c1ee3305c9a16a9a56f1142559a6a4e35581d71b5687874b26917d","0fb71384bf18e01d049939d457e2ab112e538a27553bc1287110b54d05656c1c","540943d9044817eb37ac7f0585ad9421eefc710a2f87e97a94bdd540a41b6d55","ee73e5bb0db698279aef127b5fb5fdd84354a0a9058b2db3518e22e9127d30de","9a106d1fe52b6956e8f2bd752a975a79bb3415247d89e0ace50d3bbb4a8e62e9","3154f382768176de2a3eab4e80403589488cb2782ad4f342e30d20a165ee458f","e37fa14e81191945af62be67b9ba18462e906950ff32499f2d0ea385b0340289","5e2c155117a767de3bcebb74f88555ba6a70434e6eab509cc76e1288bebe25b6","b20cdc2ebf72b8d03858566eb88656a9911ada3a704bb7c5f06c71d883bb909d","523042f012e9491fe45b0065fdef41b863e77794f564c507cd3052d79980a05a","922fa9cef4a267efad753b1b84e33ef4a3b89b944114778dbf64605d6b2eca0d","b05ae91b2e6928cd6733afcabe26f420d79b535f6ef7c8f2426c51a26d221954","bd452eda4e84b03567de89943fc5c198b9bed7c50b9caa16e92024ef99adf2c2","7db1dce4e3b65ab9e7b920942ba9a512547edb5c50174d55d231849a616cfe16","515a43464023a44d28cf3a9c3b32527cbf274ffd0a7bd499aaef754523e9d5ee","ff54cdf53df87216922f4714bce06a493cf0a4fe54c708fe8474874b937e9238","bbacfd531127848102d31cc262f337cbfaffaecfabb4b37df325bc78d6aab1a1","6e8d7c5a3add82456f77021df4e851e70badff75fd85ae2351cbcf5404de9eb0","aed13f8a99e35b93e3aa55974753fa5a59539479a2e26ed92b0d48cb1bd24ce5","5ca5bb6a86d5565fa18a0061ea62606ca3277c7270a281343a1749f03db0091d","c0b33984759601918b221a8137e36d3ccdbac027790c57fbb4621dac91819456","9b0853b7a59d3c4ae94fc980d0c5923ad49e2fc567f62f43ee6941277e290cff","b1de50cb4f881d0825eaa741943a688ebad303bef6ee86ce9fda8f058493015b","6574cee0831720d621eb1abfd90dd94b97ef4e0ac898756823d1bcec6be8b4db","ad5de9522423032917e384805a334f250b2d98e4f6c8e9eb5fdaa4589f44f588","c1e7afa32298972cd142c39b9ece51b81b5ae197e22cacbd407aec64f851d5eb","76f3f2ff9e79816ad3b68000571028af5ee39833ddaa1598fd1c0f0647b9d7f3","a794821d5bb8e378a046d87c175aef226d81f59b3f09bd40d94a5e3aaa0f187d","952d635d1e2fc7a628451baeecfd707934a2ae21a04a46dd7ef2ac521fb44083")

        for (id in inboxIds) {
            runBlocking { primary.conversations.findOrCreateDm(id) }
        }

        // Create 100 group chats (all with same known peer)
        val inboxId = primary.inboxId
        repeat(500) {
            runBlocking { secondary.conversations.newGroup(listOf(primary.inboxId)) }
        }

        println("Starting sync + consent race")

        val job = CoroutineScope(Dispatchers.IO).launch {
            primary.conversations.streamAllMessages().collect { }
            primary.conversations.stream().collect { }
        }
        val duration = measureTimeMillis {
            runBlocking {
                coroutineScope {
                    val syncTask = async {
                        primary.conversations.syncAllConversations()
                    }

                    val consentTask = async {
                        primary.conversations.list(consentStates = listOf(ConsentState.ALLOWED))
                        val conversation = primary.conversations.findConversation(
                            conversationId = dm.id
                        )
                        conversation?.updateConsentState(ConsentState.UNKNOWN)
                    }

                    syncTask.await()
                    consentTask.await()
                }
            }
        }

        job.cancel()
        println("LOPI: 100 groups, 100 DMs created; syncAll + consent change finished in ${duration}ms")
        assert(duration/1000 < 15)
    }
}
