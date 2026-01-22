package org.xmtp.android.example

import android.content.Context
import androidx.annotation.UiThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.xmtp.android.example.utils.KeyUtil
import org.xmtp.android.library.Client
import org.xmtp.android.library.ClientOptions
import org.xmtp.android.library.XMTPEnvironment
import org.xmtp.android.library.codecs.AttachmentCodec
import org.xmtp.android.library.codecs.EditMessageCodec
import org.xmtp.android.library.codecs.GroupUpdatedCodec
import org.xmtp.android.library.codecs.ReactionCodec
import org.xmtp.android.library.codecs.RemoteAttachmentCodec
import org.xmtp.android.library.codecs.ReplyCodec
import org.xmtp.android.library.messages.PrivateKeyBuilder
import timber.log.Timber
import uniffi.xmtpv3.FfiLogLevel
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

object ClientManager {
    // Thread-safe environment and log level using AtomicReference
    private val _selectedEnvironment = AtomicReference(XMTPEnvironment.DEV)
    var selectedEnvironment: XMTPEnvironment
        get() = _selectedEnvironment.get()
        set(value) = _selectedEnvironment.set(value)

    private val _selectedLogLevel = AtomicReference<FfiLogLevel?>(null)
    var selectedLogLevel: FfiLogLevel?
        get() = _selectedLogLevel.get()
        set(value) = _selectedLogLevel.set(value)

    // Application-scoped coroutine scope for client operations
    private var managerScope: CoroutineScope? = null

    private fun getOrCreateScope(): CoroutineScope =
        managerScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also {
            managerScope = it
        }

    fun clientOptions(
        appContext: Context,
        address: String,
        environment: XMTPEnvironment = selectedEnvironment,
    ): ClientOptions {
        val keyUtil = KeyUtil(appContext)
        val encryptionKey =
            keyUtil.retrieveKey(address)?.takeUnless { it.isEmpty() }
                ?: SecureRandom().generateSeed(32).also { keyUtil.storeKey(address, it) }

        return ClientOptions(
            api =
                ClientOptions.Api(
                    environment,
                    isSecure = true,
                ),
            appContext = appContext,
            dbEncryptionKey = encryptionKey,
        )
    }

    private val _clientState = MutableStateFlow<ClientState>(ClientState.Unknown)
    val clientState: StateFlow<ClientState> = _clientState

    private var _client: Client? = null

    val client: Client
        get() =
            if (clientState.value == ClientState.Ready) {
                _client!!
            } else {
                throw IllegalStateException("Client called before Ready state")
            }

    @UiThread
    fun createClient(
        address: String,
        appContext: Context,
    ) {
        if (clientState.value is ClientState.Ready) return
        getOrCreateScope().launch {
            try {
                val keyUtil = KeyUtil(appContext)
                val privateKeyBytes = keyUtil.retrievePrivateKey(address)

                // Restore the saved environment, default to DEV if not found
                val savedEnvironment = keyUtil.retrieveEnvironment()
                selectedEnvironment = savedEnvironment?.let {
                    try {
                        XMTPEnvironment.valueOf(it)
                    } catch (e: IllegalArgumentException) {
                        XMTPEnvironment.DEV
                    }
                } ?: XMTPEnvironment.DEV

                Timber.d(
                    "createClient: address=$address, hasPrivateKey=${privateKeyBytes != null}, environment=$selectedEnvironment",
                )

                _client =
                    if (privateKeyBytes != null) {
                        // Rebuild the wallet from stored private key and use Client.create()
                        // This allows signing if identity registration is needed
                        val privateKey = PrivateKeyBuilder.buildFromPrivateKeyData(privateKeyBytes)
                        val wallet = PrivateKeyBuilder(privateKey)
                        val rebuiltAddress = wallet.publicIdentity.identifier
                        Timber.d("createClient: rebuiltAddress=$rebuiltAddress")

                        Client.create(
                            wallet,
                            clientOptions(appContext, rebuiltAddress),
                        )
                    } else {
                        // No private key stored - this could happen on old installations
                        // Signal that we need to re-authenticate
                        throw IllegalStateException("No wallet key found. Please sign in again.")
                    }
                Client.register(codec = GroupUpdatedCodec())
                Client.register(codec = ReplyCodec())
                Client.register(codec = ReactionCodec())
                Client.register(codec = AttachmentCodec())
                Client.register(codec = RemoteAttachmentCodec())
                Client.register(codec = EditMessageCodec())
                _clientState.value = ClientState.Ready
            } catch (e: Exception) {
                Timber.e(e, "createClient failed")
                _clientState.value = ClientState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    // Set a client that was created externally (e.g., during wallet generation)
    fun setClient(client: Client) {
        _client = client
        Client.register(codec = GroupUpdatedCodec())
        Client.register(codec = ReplyCodec())
        Client.register(codec = ReactionCodec())
        Client.register(codec = AttachmentCodec())
        Client.register(codec = RemoteAttachmentCodec())
        Client.register(codec = EditMessageCodec())
        _clientState.value = ClientState.Ready
    }

    @UiThread
    fun clearClient() {
        _clientState.value = ClientState.Unknown
        _client = null
        // Cancel the scope to prevent memory leaks from pending coroutines
        managerScope?.cancel()
        managerScope = null
    }

    sealed class ClientState {
        data object Unknown : ClientState()

        data object Ready : ClientState()

        data class Error(
            val message: String,
        ) : ClientState()
    }
}
