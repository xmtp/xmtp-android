package org.xmtp.android.library

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.crypto.tink.subtle.Base64
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import org.web3j.crypto.Keys
import org.web3j.crypto.Keys.toChecksumAddress
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.libxmtp.MessageV3
import org.xmtp.android.library.libxmtp.XMTPLogger
import org.xmtp.android.library.messages.ContactBundle
import org.xmtp.android.library.messages.EncryptedPrivateKeyBundle
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.EnvelopeBuilder
import org.xmtp.android.library.messages.InvitationV1ContextBuilder
import org.xmtp.android.library.messages.Pagination
import org.xmtp.android.library.messages.PrivateKeyBundle
import org.xmtp.android.library.messages.PrivateKeyBundleBuilder
import org.xmtp.android.library.messages.PrivateKeyBundleV1
import org.xmtp.android.library.messages.PrivateKeyBundleV2
import org.xmtp.android.library.messages.SealedInvitationHeaderV1
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.decrypted
import org.xmtp.android.library.messages.encrypted
import org.xmtp.android.library.messages.ensureWalletSignature
import org.xmtp.android.library.messages.generate
import org.xmtp.android.library.messages.getPublicKeyBundle
import org.xmtp.android.library.messages.rawData
import org.xmtp.android.library.messages.recoverWalletSignerPublicKey
import org.xmtp.android.library.messages.toPublicKeyBundle
import org.xmtp.android.library.messages.toV2
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.message.api.v1.MessageApiOuterClass
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.BatchQueryResponse
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.QueryRequest
import uniffi.xmtpv3.FfiV2SubscribeRequest
import uniffi.xmtpv3.FfiV2Subscription
import uniffi.xmtpv3.FfiV2SubscriptionCallback
import uniffi.xmtpv3.FfiXmtpClient
import uniffi.xmtpv3.createClient
import uniffi.xmtpv3.createV2Client
import uniffi.xmtpv3.generateInboxId
import uniffi.xmtpv3.getInboxIdForAddress
import uniffi.xmtpv3.getVersionInfo
import uniffi.xmtpv3.org.xmtp.android.library.libxmtp.InboxState
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

typealias PublishResponse = org.xmtp.proto.message.api.v1.MessageApiOuterClass.PublishResponse
typealias QueryResponse = org.xmtp.proto.message.api.v1.MessageApiOuterClass.QueryResponse
typealias PreEventCallback = suspend () -> Unit

data class ClientOptions(
    val api: Api = Api(),
    val preCreateIdentityCallback: PreEventCallback? = null,
    val preEnableIdentityCallback: PreEventCallback? = null,
    val preAuthenticateToInboxCallback: PreEventCallback? = null,
    val appContext: Context? = null,
    val enableV3: Boolean = false,
    val dbDirectory: String? = null,
    val dbEncryptionKey: ByteArray? = null,
    val historySyncUrl: String = when (api.env) {
        XMTPEnvironment.PRODUCTION -> "https://message-history.production.ephemera.network/"
        XMTPEnvironment.LOCAL -> "http://0.0.0.0:5558"
        else -> "https://message-history.dev.ephemera.network/"
    },
) {
    data class Api(
        val env: XMTPEnvironment = XMTPEnvironment.DEV,
        val isSecure: Boolean = true,
        val appVersion: String? = null,
    )
}

class Client() {
    lateinit var address: String
    lateinit var contacts: Contacts
    lateinit var conversations: Conversations
    var privateKeyBundleV1: PrivateKeyBundleV1? = null
    var apiClient: ApiClient? = null
    var logger: XMTPLogger = XMTPLogger()
    val libXMTPVersion: String = getVersionInfo()
    var installationId: String = ""
    var v3Client: FfiXmtpClient? = null
    var dbPath: String = ""
    lateinit var inboxId: String
    var hasV2Client: Boolean = true
    lateinit var environment: XMTPEnvironment

    companion object {
        private const val TAG = "Client"

        var codecRegistry = run {
            val registry = CodecRegistry()
            registry.register(codec = TextCodec())
            registry
        }

        suspend fun getOrCreateInboxId(options: ClientOptions, address: String): String {
            var inboxId = getInboxIdForAddress(
                logger = XMTPLogger(),
                host = options.api.env.getUrl(),
                isSecure = options.api.isSecure,
                accountAddress = address
            )
            if (inboxId.isNullOrBlank()) {
                inboxId = generateInboxId(address, 0.toULong())
            }
            return inboxId
        }

        fun register(codec: ContentCodec<*>) {
            codecRegistry.register(codec = codec)
        }

        /**
         * Use the {@param api} to fetch any stored keys belonging to {@param address}.
         *
         * The user will need to be prompted to sign to decrypt each bundle.
         */
        suspend fun authCheck(api: ApiClient, address: String): List<EncryptedPrivateKeyBundle> {
            val topic = Topic.userPrivateStoreKeyBundle(toChecksumAddress(address))
            val res = api.queryTopic(topic)
            return res.envelopesList.mapNotNull {
                try {
                    EncryptedPrivateKeyBundle.parseFrom(it.message)
                } catch (e: Exception) {
                    Log.e(TAG, "discarding malformed private key bundle: ${e.message}", e)
                    null
                }
            }
        }

        /**
         * Use the {@param api} to save the {@param encryptedKeys} for {@param address}.
         *
         * The {@param keys} are used to authorize the publish request.
         */
        suspend fun authSave(
            api: ApiClient,
            v1Key: PrivateKeyBundleV1,
            encryptedKeys: EncryptedPrivateKeyBundle,
        ) {
            val authorizedIdentity = AuthorizedIdentity(v1Key)
            authorizedIdentity.address = v1Key.walletAddress
            val authToken = authorizedIdentity.createAuthToken()
            api.setAuthToken(authToken)
            api.publish(
                envelopes = listOf(
                    EnvelopeBuilder.buildFromTopic(
                        topic = Topic.userPrivateStoreKeyBundle(v1Key.walletAddress),
                        timestamp = Date(),
                        message = encryptedKeys.toByteArray(),
                    ),
                ),
            )
        }

        suspend fun canMessage(peerAddress: String, options: ClientOptions? = null): Boolean {
            val clientOptions = options ?: ClientOptions()
            val v2Client =
                createV2Client(
                    host = clientOptions.api.env.getUrl(),
                    isSecure = clientOptions.api.isSecure
                )
            clientOptions.api.appVersion?.let { v2Client.setAppVersion(it) }
            val api = GRPCApiClient(environment = clientOptions.api.env, rustV2Client = v2Client)
            val topics = api.queryTopic(Topic.contact(peerAddress)).envelopesList
            return topics.isNotEmpty()
        }
    }

    constructor(
        address: String,
        privateKeyBundleV1: PrivateKeyBundleV1,
        apiClient: ApiClient,
        libXMTPClient: FfiXmtpClient? = null,
        dbPath: String = "",
        installationId: String = "",
        inboxId: String,
    ) : this() {
        this.address = address
        this.privateKeyBundleV1 = privateKeyBundleV1
        this.apiClient = apiClient
        this.contacts = Contacts(client = this)
        this.v3Client = libXMTPClient
        this.conversations =
            Conversations(client = this, libXMTPConversations = libXMTPClient?.conversations())
        this.dbPath = dbPath
        this.installationId = installationId
        this.inboxId = inboxId
        this.hasV2Client = true
        this.environment = apiClient.environment
    }

    constructor(
        address: String,
        libXMTPClient: FfiXmtpClient,
        dbPath: String,
        installationId: String,
        inboxId: String,
        environment: XMTPEnvironment,
    ) : this() {
        this.address = address
        this.contacts = Contacts(client = this)
        this.v3Client = libXMTPClient
        this.conversations =
            Conversations(client = this, libXMTPConversations = libXMTPClient.conversations())
        this.dbPath = dbPath
        this.installationId = installationId
        this.inboxId = inboxId
        this.hasV2Client = false
        this.environment = environment
    }

    suspend fun buildFrom(
        bundle: PrivateKeyBundleV1,
        options: ClientOptions? = null,
        account: SigningKey? = null,
    ): Client {
        return buildFromV1Bundle(bundle, options, account)
    }

    suspend fun create(
        account: SigningKey,
        options: ClientOptions? = null,
    ): Client {
        val clientOptions = options ?: ClientOptions()
        val v2Client =
            createV2Client(
                host = clientOptions.api.env.getUrl(),
                isSecure = clientOptions.api.isSecure
            )
        clientOptions.api.appVersion?.let { v2Client.setAppVersion(it) }
        val apiClient = GRPCApiClient(environment = clientOptions.api.env, rustV2Client = v2Client)
        return create(
            account = account,
            apiClient = apiClient,
            options = options,
        )
    }

    suspend fun create(
        account: SigningKey,
        apiClient: ApiClient,
        options: ClientOptions? = null,
    ): Client {
        val clientOptions = options ?: ClientOptions()
        try {
            val privateKeyBundleV1 = loadOrCreateKeys(
                account,
                apiClient,
                clientOptions
            )
            val inboxId = getOrCreateInboxId(clientOptions, account.address)
            val (libXMTPClient, dbPath) =
                ffiXmtpClient(
                    clientOptions,
                    account,
                    clientOptions.appContext,
                    privateKeyBundleV1,
                    account.address,
                    inboxId
                )

            val client =
                Client(
                    account.address,
                    privateKeyBundleV1,
                    apiClient,
                    libXMTPClient,
                    dbPath,
                    libXMTPClient?.installationId()?.toHex() ?: "",
                    libXMTPClient?.inboxId() ?: inboxId
                )
            client.ensureUserContactPublished()
            return client
        } catch (e: java.lang.Exception) {
            throw XMTPException("Error creating client ${e.message}", e)
        }
    }

    private suspend fun initializeV3Client(
        accountAddress: String,
        clientOptions: ClientOptions,
        signingKey: SigningKey? = null,
    ): Client {
        val inboxId = getOrCreateInboxId(clientOptions, accountAddress)

        val (libXMTPClient, dbPath) = ffiXmtpClient(
            clientOptions,
            signingKey,
            clientOptions.appContext,
            null,
            accountAddress,
            inboxId
        )

        libXMTPClient?.let { client ->
            return Client(
                accountAddress,
                client,
                dbPath,
                client.installationId().toHex(),
                client.inboxId(),
                clientOptions.api.env
            )
        } ?: throw XMTPException("Error creating V3 client: libXMTPClient is null")
    }

    // Function to create a V3 client with a signing key
    suspend fun createV3(
        account: SigningKey,
        options: ClientOptions? = null,
    ): Client {
        this.hasV2Client = false
        val clientOptions = options ?: ClientOptions(enableV3 = true)
        val accountAddress = account.address.lowercase()
        return try {
            initializeV3Client(accountAddress, clientOptions, account)
        } catch (e: Exception) {
            throw XMTPException("Error creating V3 client: ${e.message}", e)
        }
    }

    // Function to build a V3 client without a signing key (using only address (& chainId for SCW))
    suspend fun buildV3(
        address: String,
        options: ClientOptions? = null,
    ): Client {
        this.hasV2Client = false
        val clientOptions = options ?: ClientOptions(enableV3 = true)
        val accountAddress = address.lowercase()
        return try {
            initializeV3Client(accountAddress, clientOptions)
        } catch (e: Exception) {
            throw XMTPException("Error creating V3 client: ${e.message}", e)
        }
    }

    suspend fun buildFromBundle(
        bundle: PrivateKeyBundle,
        options: ClientOptions? = null,
        account: SigningKey? = null,
    ): Client =
        buildFromV1Bundle(v1Bundle = bundle.v1, account = account, options = options)

    suspend fun buildFromV1Bundle(
        v1Bundle: PrivateKeyBundleV1,
        options: ClientOptions? = null,
        account: SigningKey? = null,
    ): Client {
        val address = v1Bundle.identityKey.publicKey.recoverWalletSignerPublicKey().walletAddress
        val newOptions = options ?: ClientOptions()
        val v2Client =
            createV2Client(
                host = newOptions.api.env.getUrl(),
                isSecure = newOptions.api.isSecure
            )
        newOptions.api.appVersion?.let { v2Client.setAppVersion(it) }
        val apiClient = GRPCApiClient(environment = newOptions.api.env, rustV2Client = v2Client)
        val inboxId = getOrCreateInboxId(newOptions, address)
        val (v3Client, dbPath) = if (isV3Enabled(options)) {
            ffiXmtpClient(
                newOptions,
                account,
                options?.appContext,
                v1Bundle,
                address,
                inboxId
            )
        } else Pair(null, "")

        return Client(
            address = address,
            privateKeyBundleV1 = v1Bundle,
            apiClient = apiClient,
            libXMTPClient = v3Client,
            dbPath = dbPath,
            installationId = v3Client?.installationId()?.toHex() ?: "",
            inboxId = v3Client?.inboxId() ?: inboxId
        )
    }

    private fun isV3Enabled(options: ClientOptions?): Boolean {
        return (options != null && options.enableV3 && options.appContext != null)
    }

    private suspend fun ffiXmtpClient(
        options: ClientOptions,
        account: SigningKey?,
        appContext: Context?,
        privateKeyBundleV1: PrivateKeyBundleV1?,
        address: String,
        inboxId: String,
    ): Pair<FfiXmtpClient?, String> {
        var dbPath = ""
        val accountAddress = address.lowercase()
        val v3Client: FfiXmtpClient? =
            if (isV3Enabled(options)) {
                val alias = "xmtp-${options.api.env}-$inboxId"

                val mlsDbDirectory = options.dbDirectory
                val directoryFile = if (mlsDbDirectory != null) {
                    File(mlsDbDirectory)
                } else {
                    File(appContext?.filesDir?.absolutePath, "xmtp_db")
                }
                directoryFile.mkdir()
                dbPath = directoryFile.absolutePath + "/$alias.db3"

                val encryptionKey = options.dbEncryptionKey
                    ?: throw XMTPException("No encryption key passed for the database. Please store and provide a secure encryption key.")

                createClient(
                    logger = logger,
                    host = options.api.env.getUrl(),
                    isSecure = options.api.isSecure,
                    db = dbPath,
                    encryptionKey = encryptionKey,
                    accountAddress = accountAddress,
                    inboxId = inboxId,
                    nonce = 0.toULong(),
                    legacySignedPrivateKeyProto = privateKeyBundleV1?.toV2()?.identityKey?.toByteArray(),
                    historySyncUrl = options.historySyncUrl
                )
            } else {
                null
            }

        if (v3Client != null) {
            options.preAuthenticateToInboxCallback?.let {
                runBlocking {
                    it.invoke()
                }
            }
            v3Client.signatureRequest()?.let { signatureRequest ->
                if (account != null) {
                    if (account.type == WalletType.SCW) {
                        val chainId = account.chainId ?: throw XMTPException("ChainId is required for smart contract wallets")
                        signatureRequest.addScwSignature(
                            account.signSCW(signatureRequest.signatureText()),
                            account.address.lowercase(),
                            chainId.toULong(),
                            account.blockNumber?.toULong()
                        )
                    } else {
                        account.sign(signatureRequest.signatureText())?.let {
                            signatureRequest.addEcdsaSignature(it.rawData)
                        }
                    }

                    v3Client.registerIdentity(signatureRequest)
                } else {
                    throw XMTPException("No signer passed but signer was required.")
                }
            }
        }
        Log.i(TAG, "LibXMTP $libXMTPVersion")
        return Pair(v3Client, dbPath)
    }

    /**
     * This authenticates using [account] acquired from network storage
     *  encrypted using the [wallet].
     *
     *  e.g. this might be called the first time a user logs in from a new device.
     *  The next time they launch the app they can [buildFromV1Key].
     *
     *  If there are stored keys then this asks the [wallet] to
     *  [encrypted] so that we can decrypt the stored [keys].
     *
     *   If there are no stored keys then this generates a new identityKey
     *   and asks the [wallet] to both [createIdentity] and enable Identity Saving
     *   so we can then store it encrypted for the next time.
     */
    private suspend fun loadOrCreateKeys(
        account: SigningKey,
        apiClient: ApiClient,
        options: ClientOptions? = null,
    ): PrivateKeyBundleV1 {
        val keys = loadPrivateKeys(account, apiClient, options)
        return if (keys != null) {
            keys
        } else {
            val v1Keys = PrivateKeyBundleV1.newBuilder().build().generate(account, options)
            val keyBundle = PrivateKeyBundleBuilder.buildFromV1Key(v1Keys)
            val encryptedKeys = keyBundle.encrypted(account, options?.preEnableIdentityCallback)
            authSave(apiClient, keyBundle.v1, encryptedKeys)
            v1Keys
        }
    }

    /**
     *  This authenticates with [keys] directly received.
     *  e.g. this might be called on subsequent app launches once we
     *  have already stored the keys from a previous session.
     */
    private suspend fun loadPrivateKeys(
        account: SigningKey,
        apiClient: ApiClient,
        options: ClientOptions? = null,
    ): PrivateKeyBundleV1? {
        val encryptedBundles = authCheck(apiClient, account.address)
        for (encryptedBundle in encryptedBundles) {
            try {
                val bundle =
                    encryptedBundle.decrypted(account, options?.preEnableIdentityCallback)
                return bundle.v1
            } catch (e: Throwable) {
                print("Error decoding encrypted private key bundle: $e")
                continue
            }
        }
        return null
    }

    suspend fun publishUserContact(legacy: Boolean = false) {
        val envelopes: MutableList<MessageApiOuterClass.Envelope> = mutableListOf()
        if (legacy) {
            val contactBundle = ContactBundle.newBuilder().also {
                it.v1 = it.v1.toBuilder().also { v1Builder ->
                    v1Builder.keyBundle = v1keys.toPublicKeyBundle()
                }.build()
            }.build()

            val envelope = MessageApiOuterClass.Envelope.newBuilder().apply {
                contentTopic = Topic.contact(address).description
                timestampNs = Date().time * 1_000_000
                message = contactBundle.toByteString()
            }.build()

            envelopes.add(envelope)
        }
        val contactBundle = ContactBundle.newBuilder().also {
            it.v2 = it.v2.toBuilder().also { v2Builder ->
                v2Builder.keyBundle = keys.getPublicKeyBundle()
            }.build()
            it.v2 = it.v2.toBuilder().also { v2Builder ->
                v2Builder.keyBundle = v2Builder.keyBundle.toBuilder().also { keyBuilder ->
                    keyBuilder.identityKey =
                        keyBuilder.identityKey.toBuilder().also { idBuilder ->
                            idBuilder.signature =
                                it.v2.keyBundle.identityKey.signature.ensureWalletSignature()
                        }.build()
                }.build()
            }.build()
        }.build()
        val envelope = MessageApiOuterClass.Envelope.newBuilder().apply {
            contentTopic = Topic.contact(address).description
            timestampNs = Date().time * 1_000_000
            message = contactBundle.toByteString()
        }.build()
        envelopes.add(envelope)
        publish(envelopes = envelopes)
    }

    fun getUserContact(peerAddress: String): ContactBundle? {
        return contacts.find(Keys.toChecksumAddress(peerAddress))
    }

    suspend fun query(topic: Topic, pagination: Pagination? = null): QueryResponse {
        val client = apiClient ?: throw XMTPException("V2 only function")
        return client.queryTopic(topic = topic, pagination = pagination)
    }

    suspend fun batchQuery(requests: List<QueryRequest>): BatchQueryResponse {
        val client = apiClient ?: throw XMTPException("V2 only function")
        return client.batchQuery(requests)
    }

    suspend fun subscribe(
        topics: List<String>,
        callback: FfiV2SubscriptionCallback,
    ): FfiV2Subscription {
        return subscribe2(FfiV2SubscribeRequest(topics), callback)
    }

    suspend fun subscribe2(
        request: FfiV2SubscribeRequest,
        callback: FfiV2SubscriptionCallback,
    ): FfiV2Subscription {
        val client = apiClient ?: throw XMTPException("V2 only function")
        return client.subscribe(request, callback)
    }

    suspend fun fetchConversation(
        topic: String?,
        includeGroups: Boolean = false,
    ): Conversation? {
        if (topic.isNullOrBlank()) return null
        return conversations.list(includeGroups = includeGroups).firstOrNull {
            it.topic == topic
        }
    }

    @Deprecated("Find now includes DMs and Groups", replaceWith = ReplaceWith("findConversation"))
    fun findGroup(groupId: String): Group? {
        val client = v3Client ?: throw XMTPException("Error no V3 client initialized")
        try {
            return Group(this, client.conversation(groupId.hexToByteArray()))
        } catch (e: Exception) {
            return null
        }
    }

    fun findConversation(conversationId: String): Conversation? {
        val client = v3Client ?: throw XMTPException("Error no V3 client initialized")
        val conversation = client.conversation(conversationId.hexToByteArray())
        return if (conversation.groupMetadata().conversationType() == "dm") {
            Conversation.Dm(Dm(this, conversation))
        } else if (conversation.groupMetadata().conversationType() == "group") {
            Conversation.Group(Group(this, conversation))
        } else {
            null
        }
    }

    fun findConversationByTopic(topic: String): Conversation? {
        val client = v3Client ?: throw XMTPException("Error no V3 client initialized")
        val regex = """/xmtp/mls/1/g-(.*?)/proto""".toRegex()
        val matchResult = regex.find(topic)
        val conversationId = matchResult?.groupValues?.get(1) ?: ""
        val conversation = client.conversation(conversationId.hexToByteArray())
        return if (conversation.groupMetadata().conversationType() == "dm") {
            Conversation.Dm(Dm(this, conversation))
        } else if (conversation.groupMetadata().conversationType() == "group") {
            Conversation.Group(Group(this, conversation))
        } else {
            null
        }
    }

    suspend fun findDm(address: String): Dm? {
        val client = v3Client ?: throw XMTPException("Error no V3 client initialized")
        val inboxId =
            inboxIdFromAddress(address.lowercase()) ?: throw XMTPException("No inboxId present")
        try {
            return Dm(this, client.dmConversation(inboxId))
        } catch (e: Exception) {
            return null
        }
    }

    fun findMessage(messageId: String): MessageV3? {
        val client = v3Client ?: throw XMTPException("Error no V3 client initialized")
        return try {
            MessageV3(this, client.message(messageId.hexToByteArray()))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun publish(envelopes: List<Envelope>) {
        val client = apiClient ?: throw XMTPException("V2 only function")
        val authorized = AuthorizedIdentity(
            address = address,
            authorized = v1keys.identityKey.publicKey,
            identity = v1keys.identityKey,
        )
        val authToken = authorized.createAuthToken()
        client.setAuthToken(authToken)

        client.publish(envelopes = envelopes)
    }

    suspend fun ensureUserContactPublished() {
        val contact = getUserContact(peerAddress = address)
        if (contact != null && keys.getPublicKeyBundle() == contact.v2.keyBundle) {
            return
        }

        publishUserContact(legacy = true)
    }

    fun importConversation(conversationData: ByteArray): Conversation {
        val gson = GsonBuilder().create()
        val v2Export = gson.fromJson(
            conversationData.toString(StandardCharsets.UTF_8),
            ConversationV2Export::class.java,
        )
        return try {
            importV2Conversation(export = v2Export)
        } catch (e: java.lang.Exception) {
            val v1Export = gson.fromJson(
                conversationData.toString(StandardCharsets.UTF_8),
                ConversationV1Export::class.java,
            )
            try {
                importV1Conversation(export = v1Export)
            } catch (e: java.lang.Exception) {
                throw XMTPException("Invalid input data", e)
            }
        }
    }

    fun importV2Conversation(export: ConversationV2Export): Conversation {
        val keyMaterial = Base64.decode(export.keyMaterial)
        return Conversation.V2(
            ConversationV2(
                topic = export.topic,
                keyMaterial = keyMaterial,
                context = InvitationV1ContextBuilder.buildFromConversation(
                    conversationId = export.context?.conversationId ?: "",
                    metadata = export.context?.metadata ?: mapOf(),
                ),
                peerAddress = export.peerAddress,
                client = this,
                header = SealedInvitationHeaderV1.newBuilder().build(),
            ),
        )
    }

    fun importV1Conversation(export: ConversationV1Export): Conversation {
        val sentAt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Date.from(Instant.parse(export.createdAt))
        } else {
            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            df.timeZone = TimeZone.getTimeZone("UTC")
            df.parse(export.createdAt)
        }
        return Conversation.V1(
            ConversationV1(
                client = this,
                peerAddress = export.peerAddress,
                sentAt = sentAt,
            ),
        )
    }

    /**
     * Whether or not we can send messages to [address].
     * @param peerAddress is the address of the client that you want to send messages
     *
     * @return false when [peerAddress] has never signed up for XMTP
     * or when the message is addressed to the sender (no self-messaging).
     */
    suspend fun canMessage(peerAddress: String): Boolean {
        return query(Topic.contact(peerAddress)).envelopesList.size > 0
    }

    suspend fun canMessageV3(addresses: List<String>): Map<String, Boolean> {
        return v3Client?.canMessage(addresses)
            ?: throw XMTPException("Error no V3 client initialized")
    }

    suspend fun inboxIdFromAddress(address: String): String? {
        return v3Client?.findInboxId(address.lowercase())
            ?: throw XMTPException("Error no V3 client initialized")
    }

    fun deleteLocalDatabase() {
        dropLocalDatabaseConnection()
        File(dbPath).delete()
    }

    @Deprecated(
        message = "This function is delicate and should be used with caution. App will error if database not properly reconnected. See: reconnectLocalDatabase()",
    )
    fun dropLocalDatabaseConnection() {
        v3Client?.releaseDbConnection()
    }

    suspend fun reconnectLocalDatabase() {
        v3Client?.dbReconnect() ?: throw XMTPException("Error no V3 client initialized")
    }

    suspend fun requestMessageHistorySync() {
        v3Client?.requestHistorySync() ?: throw XMTPException("Error no V3 client initialized")
    }

    suspend fun revokeAllOtherInstallations(signingKey: SigningKey) {
        val client = v3Client ?: throw XMTPException("Error no V3 client initialized")
        val signatureRequest = client.revokeAllOtherInstallations()
        signingKey.sign(signatureRequest.signatureText())?.let {
            signatureRequest.addEcdsaSignature(it.rawData)
            client.applySignatureRequest(signatureRequest)
        }
    }

    suspend fun inboxState(refreshFromNetwork: Boolean): InboxState {
        val client = v3Client ?: throw XMTPException("Error no V3 client initialized")
        return InboxState(client.inboxState(refreshFromNetwork))
    }

    val privateKeyBundle: PrivateKeyBundle
        get() = PrivateKeyBundleBuilder.buildFromV1Key(v1keys)

    val v1keys: PrivateKeyBundleV1
        get() = privateKeyBundleV1 ?: throw XMTPException("V2 only function")

    val keys: PrivateKeyBundleV2
        get() = v1keys.toV2()
}
