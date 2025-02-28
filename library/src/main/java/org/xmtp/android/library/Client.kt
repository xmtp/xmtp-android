package org.xmtp.android.library

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.libxmtp.Identity
import org.xmtp.android.library.libxmtp.InboxState
import org.xmtp.android.library.libxmtp.SignatureRequest
import org.xmtp.android.library.libxmtp.toFfiPublicIdentifier
import org.xmtp.android.library.messages.rawData
import uniffi.xmtpv3.FfiXmtpClient
import uniffi.xmtpv3.XmtpApiClient
import uniffi.xmtpv3.connectToBackend
import uniffi.xmtpv3.createClient
import uniffi.xmtpv3.generateInboxId
import uniffi.xmtpv3.getInboxIdForIdentifier
import uniffi.xmtpv3.getVersionInfo
import java.io.File

typealias PreEventCallback = suspend () -> Unit

data class ClientOptions(
    val api: Api = Api(),
    val preAuthenticateToInboxCallback: PreEventCallback? = null,
    val appContext: Context,
    val dbEncryptionKey: ByteArray,
    val historySyncUrl: String? = when (api.env) {
        XMTPEnvironment.PRODUCTION -> "https://message-history.production.ephemera.network/"
        XMTPEnvironment.LOCAL -> "http://0.0.0.0:5558"
        else -> "https://message-history.dev.ephemera.network/"
    },
    val dbDirectory: String? = null,
) {
    data class Api(
        val env: XMTPEnvironment = XMTPEnvironment.DEV,
        val isSecure: Boolean = true,
        val appVersion: String? = null,
    )
}

class Client(
    libXMTPClient: FfiXmtpClient,
    val dbPath: String,
    val installationId: String,
    val inboxId: String,
    val environment: XMTPEnvironment,
) {
    val preferences: PrivatePreferences =
        PrivatePreferences(client = this, ffiClient = libXMTPClient)
    val conversations: Conversations = Conversations(
        client = this,
        ffiConversations = libXMTPClient.conversations(),
        ffiClient = libXMTPClient
    )
    val libXMTPVersion: String = getVersionInfo()
    private val ffiClient: FfiXmtpClient = libXMTPClient

    companion object {
        private const val TAG = "Client"

        var codecRegistry = run {
            val registry = CodecRegistry()
            registry.register(codec = TextCodec())
            registry
        }

        private val apiClientCache = mutableMapOf<String, XmtpApiClient>()
        private val cacheLock = Mutex()

        suspend fun connectToApiBackend(api: ClientOptions.Api): XmtpApiClient {
            val cacheKey = api.env.getUrl()
            return cacheLock.withLock {
                apiClientCache.getOrPut(cacheKey) {
                    connectToBackend(api.env.getUrl(), api.isSecure)
                }
            }
        }

        suspend fun getOrCreateInboxId(
            api: ClientOptions.Api,
            identity: SignerIdentity,
        ): String {
            val rootIdentity = identity.toFfiRootIdentifier()
            var inboxId = getInboxIdForIdentifier(
                api = connectToApiBackend(api),
                accountIdentifier = rootIdentity
            )
            if (inboxId.isNullOrBlank()) {
                inboxId = generateInboxId(rootIdentity, 0.toULong())
            }
            return inboxId
        }

        fun register(codec: ContentCodec<*>) {
            codecRegistry.register(codec = codec)
        }

        private suspend fun <T> withFfiClient(
            api: ClientOptions.Api,
            useClient: suspend (ffiClient: FfiXmtpClient) -> T,
        ): T {
            val identity = SignerIdentity(
                SignerType.EOA,
                "0x0000000000000000000000000000000000000000"
            )
            val inboxId = getOrCreateInboxId(api, identity)

            val ffiClient = createClient(
                api = connectToApiBackend(api),
                db = null,
                encryptionKey = null,
                accountIdentifier = identity.toFfiRootIdentifier(),
                inboxId = inboxId,
                nonce = 0.toULong(),
                legacySignedPrivateKeyProto = null,
                historySyncUrl = null
            )

            return useClient(ffiClient)
        }

        suspend fun inboxStatesForInboxIds(
            inboxIds: List<String>,
            api: ClientOptions.Api,
        ): List<InboxState> {
            return withFfiClient(api) { ffiClient ->
                ffiClient.addressesFromInboxId(true, inboxIds).map { InboxState(it) }
            }
        }

        suspend fun canMessage(
            identities: List<Identity>,
            api: ClientOptions.Api,
        ): Map<Identity, Boolean> {
            return withFfiClient(api) { ffiClient ->
                val ffiIdentifiers = identities.map { it.toFfiPublicIdentifier() }
                val result = ffiClient.canMessage(ffiIdentifiers)

                result.mapKeys { (ffiIdentifier, _) ->
                    Identity(ffiIdentifier)
                }
            }
        }

        private suspend fun initializeV3Client(
            identity: SignerIdentity,
            clientOptions: ClientOptions,
            signingKey: SigningKey? = null,
            inboxId: String? = null,
        ): Client {
            val recoveredInboxId =
                inboxId ?: getOrCreateInboxId(clientOptions.api, identity)

            val (ffiClient, dbPath) = createFfiClient(
                identity,
                recoveredInboxId,
                clientOptions,
                clientOptions.appContext,
            )
            clientOptions.preAuthenticateToInboxCallback?.let {
                runBlocking {
                    it.invoke()
                }
            }
            ffiClient.signatureRequest()?.let { signatureRequest ->
                signingKey?.let { handleSignature(SignatureRequest(signatureRequest), it) }
                    ?: throw XMTPException("No signer passed but signer was required.")
                ffiClient.registerIdentity(signatureRequest)
            }

            return Client(
                ffiClient,
                dbPath,
                ffiClient.installationId().toHex(),
                ffiClient.inboxId(),
                clientOptions.api.env,
            )
        }

        // Function to create a client with a signing key
        suspend fun create(
            account: SigningKey,
            options: ClientOptions,
        ): Client {
            return try {
                initializeV3Client(account.identity, options, account)
            } catch (e: Exception) {
                throw XMTPException("Error creating V3 client: ${e.message}", e)
            }
        }

        // Function to build a client from a address
        suspend fun build(
            identity: SignerIdentity,
            options: ClientOptions,
            inboxId: String? = null,
        ): Client {
            return try {
                initializeV3Client(identity, options, inboxId = inboxId)
            } catch (e: Exception) {
                throw XMTPException("Error creating V3 client: ${e.message}", e)
            }
        }

        private suspend fun createFfiClient(
            identity: SignerIdentity,
            inboxId: String,
            options: ClientOptions,
            appContext: Context,
        ): Pair<FfiXmtpClient, String> {
            val alias = "xmtp-${options.api.env}-$inboxId"

            val mlsDbDirectory = options.dbDirectory
            val directoryFile = if (mlsDbDirectory != null) {
                File(mlsDbDirectory)
            } else {
                File(appContext.filesDir.absolutePath, "xmtp_db")
            }
            directoryFile.mkdir()
            val dbPath = directoryFile.absolutePath + "/$alias.db3"

            val ffiClient = createClient(
                api = connectToApiBackend(options.api),
                db = dbPath,
                encryptionKey = options.dbEncryptionKey,
                accountIdentifier = identity.toFfiRootIdentifier(),
                inboxId = inboxId,
                nonce = 0.toULong(),
                legacySignedPrivateKeyProto = null,
                historySyncUrl = options.historySyncUrl
            )

            return Pair(ffiClient, dbPath)
        }

        private suspend fun handleSignature(
            signatureRequest: SignatureRequest,
            signingKey: SigningKey,
        ) {
            if (signingKey.identity.type == SignerType.SCW) {
                val chainId = signingKey.chainId
                    ?: throw XMTPException("ChainId is required for smart contract wallets")
                signatureRequest.addScwSignature(
                    signingKey.signSCW(signatureRequest.signatureText()),
                    signingKey.identity.identifier.lowercase(),
                    chainId.toULong(),
                    signingKey.blockNumber?.toULong()
                )
            } else {
                signingKey.sign(signatureRequest.signatureText())?.let {
                    signatureRequest.addEcdsaSignature(it.rawData)
                }
            }
        }

        @DelicateApi("This function is delicate and should be used with caution. Creating an FfiClient without signing or registering will create a broken experience use `create()` instead")
        suspend fun ffiCreateClient(identity: SignerIdentity, clientOptions: ClientOptions): Client {
            val recoveredInboxId = getOrCreateInboxId(clientOptions.api, identity)

            val (ffiClient, dbPath) = createFfiClient(
                identity,
                recoveredInboxId,
                clientOptions,
                clientOptions.appContext,
            )
            return Client(
                ffiClient,
                dbPath,
                ffiClient.installationId().toHex(),
                ffiClient.inboxId(),
                clientOptions.api.env,
            )
        }
    }

    suspend fun revokeInstallations(signingKey: SigningKey, installationIds: List<String>) {
        val ids = installationIds.map { it.hexToByteArray() }
        val signatureRequest = ffiRevokeInstallations(ids)
        handleSignature(signatureRequest, signingKey)
        ffiApplySignatureRequest(signatureRequest)
    }

    suspend fun revokeAllOtherInstallations(signingKey: SigningKey) {
        val signatureRequest = ffiRevokeAllOtherInstallations()
        handleSignature(signatureRequest, signingKey)
        ffiApplySignatureRequest(signatureRequest)
    }

    @DelicateApi("This function is delicate and should be used with caution. Adding a wallet already associated with an inboxId will cause the wallet to lose access to that inbox. See: inboxIdFromAddress(address)")
    suspend fun addAccount(newAccount: SigningKey, allowReassignInboxId: Boolean = false) {
        val inboxId: String? =
            if (!allowReassignInboxId) inboxIdFromIdentity(
                Identity(
                    newAccount.identity.type.toIdentityKind(),
                    newAccount.identity.identifier
                )) else null

        if (allowReassignInboxId || inboxId.isNullOrBlank()) {
            val signatureRequest = ffiAddWallet(newAccount.identity.toFfiRootIdentifier())
            handleSignature(signatureRequest, newAccount)
            ffiApplySignatureRequest(signatureRequest)
        } else {
            throw XMTPException("This wallet is already associated with inbox $inboxId")
        }
    }

    suspend fun removeAccount(recoverAccount: SigningKey, addressToRemove: String) {
        val signatureRequest = ffiRevokeWallet(addressToRemove.lowercase())
        handleSignature(signatureRequest, recoverAccount)
        ffiApplySignatureRequest(signatureRequest)
    }

    fun signWithInstallationKey(message: String): ByteArray {
        return ffiClient.signWithInstallationKey(message)
    }

    fun verifySignature(message: String, signature: ByteArray): Boolean {
        return try {
            ffiClient.verifySignedWithInstallationKey(message, signature)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun verifySignatureWithInstallationId(
        message: String,
        signature: ByteArray,
        installationId: String,
    ): Boolean {
        return try {
            ffiClient.verifySignedWithPublicKey(message, signature, installationId.hexToByteArray())
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun canMessage(identities: List<Identity>): Map<Identity, Boolean> {
        val ffiIdentifiers = identities.map { it.toFfiPublicIdentifier() }
        val result = ffiClient.canMessage(ffiIdentifiers)

        return result.mapKeys { (ffiIdentifier, _) ->
            Identity(ffiIdentifier)
        }
    }

    suspend fun inboxIdFromIdentity(identity: Identity): String? {
        return ffiClient.findInboxId(identity.toFfiPublicIdentifier())
    }

    fun deleteLocalDatabase() {
        dropLocalDatabaseConnection()
        File(dbPath).delete()
    }

    @DelicateApi("This function is delicate and should be used with caution. App will error if database not properly reconnected. See: reconnectLocalDatabase()")
    fun dropLocalDatabaseConnection() {
        ffiClient.releaseDbConnection()
    }

    suspend fun reconnectLocalDatabase() {
        ffiClient.dbReconnect()
    }

    suspend fun inboxStatesForInboxIds(
        refreshFromNetwork: Boolean,
        inboxIds: List<String>,
    ): List<InboxState> {
        return ffiClient.addressesFromInboxId(refreshFromNetwork, inboxIds).map { InboxState(it) }
    }

    suspend fun inboxState(refreshFromNetwork: Boolean): InboxState {
        return InboxState(ffiClient.inboxState(refreshFromNetwork))
    }

    @DelicateApi("This function is delicate and should be used with caution. Should only be used if trying to manage the signature flow independently otherwise use `addAccount(), removeAccount(), or revoke()` instead")
    suspend fun ffiApplySignatureRequest(signatureRequest: SignatureRequest) {
        ffiClient.applySignatureRequest(signatureRequest.ffiSignatureRequest)
    }

    @DelicateApi("This function is delicate and should be used with caution. Should only be used if trying to manage the signature flow independently otherwise use `revokeInstallations()` instead")
    suspend fun ffiRevokeInstallations(ids: List<ByteArray>): SignatureRequest {
        return SignatureRequest(ffiClient.revokeInstallations(ids))
    }

    @DelicateApi("This function is delicate and should be used with caution. Should only be used if trying to manage the signature flow independently otherwise use `revokeAllOtherInstallations()` instead")
    suspend fun ffiRevokeAllOtherInstallations(): SignatureRequest {
        return SignatureRequest(
            ffiClient.revokeAllOtherInstallations()
        )
    }

    @DelicateApi("This function is delicate and should be used with caution. Should only be used if trying to manage the signature flow independently otherwise use `removeWallet()` instead")
    suspend fun ffiRevokeWallet(addressToRemove: String): SignatureRequest {
        return SignatureRequest(ffiClient.revokeWallet(addressToRemove.lowercase()))
    }

    @DelicateApi("This function is delicate and should be used with caution. Should only be used if trying to manage the create and register flow independently otherwise use `addWallet()` instead")
    suspend fun ffiAddWallet(addressToAdd: String): SignatureRequest {
        return SignatureRequest(ffiClient.addWallet(addressToAdd.lowercase()))
    }

    @DelicateApi("This function is delicate and should be used with caution. Should only be used if trying to manage the signature flow independently otherwise use `create()` instead")
    fun ffiSignatureRequest(): SignatureRequest? {
        return ffiClient.signatureRequest()?.let { SignatureRequest(it) }
    }

    @DelicateApi("This function is delicate and should be used with caution. Should only be used if trying to manage the create and register flow independently otherwise use `create()` instead")
    suspend fun ffiRegisterIdentity(signatureRequest: SignatureRequest) {
        ffiClient.registerIdentity(signatureRequest.ffiSignatureRequest)
    }
}
