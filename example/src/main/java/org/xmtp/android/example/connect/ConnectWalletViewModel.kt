package org.xmtp.android.example.connect

import android.app.Application
import android.net.Uri
import androidx.annotation.UiThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walletconnect.wcmodal.client.Modal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.utils.KeyUtil
import org.xmtp.android.library.Client
import org.xmtp.android.library.XMTPEnvironment
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.messages.PrivateKeyBuilder
import uniffi.xmtpv3.FfiLogLevel
import uniffi.xmtpv3.FfiLogRotation

class ConnectWalletViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _showWalletState = MutableStateFlow(ShowWalletForSigningState(showWallet = false))
    val showWalletState: StateFlow<ShowWalletForSigningState>
        get() = _showWalletState.asStateFlow()

    private val _uiState = MutableStateFlow<ConnectUiState>(ConnectUiState.Unknown)
    val uiState: StateFlow<ConnectUiState> = _uiState

    @UiThread
    fun generateWallet(
        environment: XMTPEnvironment,
        logLevel: FfiLogLevel?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ConnectUiState.Loading
            try {
                // Store the selected environment and log level
                ClientManager.selectedEnvironment = environment
                ClientManager.selectedLogLevel = logLevel

                // Activate logging if a log level is selected
                if (logLevel != null) {
                    Client.activatePersistentLibXMTPLogWriter(
                        getApplication(),
                        logLevel,
                        FfiLogRotation.MINUTELY,
                        3,
                    )
                }

                val wallet = PrivateKeyBuilder()
                val address = wallet.publicIdentity.identifier

                // Store the private key and environment for later use on app restart
                val keyUtil = KeyUtil(getApplication())
                val privateKeyBytes =
                    wallet
                        .getPrivateKey()
                        .secp256K1.bytes
                        .toByteArray()
                keyUtil.storePrivateKey(address, privateKeyBytes)
                keyUtil.storeEnvironment(environment.name)

                val client =
                    Client.create(
                        wallet,
                        ClientManager.clientOptions(
                            getApplication(),
                            address,
                            environment,
                        ),
                    )
                // Store the client in ClientManager so it can be used by MainActivity
                ClientManager.setClient(client)
                _uiState.value =
                    ConnectUiState.Success(
                        address,
                    )
            } catch (e: XMTPException) {
                _uiState.value = ConnectUiState.Error(e.message.orEmpty())
            }
        }
    }

    fun clearShowWalletState() {
        _showWalletState.update {
            it.copy(showWallet = false)
        }
    }

    sealed class ConnectUiState {
        object Unknown : ConnectUiState()

        object Loading : ConnectUiState()

        data class Success(
            val address: String,
        ) : ConnectUiState()

        data class Error(
            val message: String,
        ) : ConnectUiState()
    }

    data class ShowWalletForSigningState(
        val showWallet: Boolean,
        val uri: Uri? = null,
    )
}
