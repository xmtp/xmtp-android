package org.xmtp.android.example.connect

import android.app.Application
import androidx.annotation.UiThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pinkroom.walletconnectkit.WalletConnectKit
import dev.pinkroom.walletconnectkit.WalletConnectKitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import org.xmtp.android.example.ClientManager
import org.xmtp.android.library.Client
import org.xmtp.android.library.SigningKey
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.PrivateKeyBundleV1Builder
import org.xmtp.android.library.messages.SignatureBuilder
import org.xmtp.proto.message.contents.SignatureOuterClass

class ConnectWalletViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ConnectUiState>(ConnectUiState.Unknown)
    val uiState: StateFlow<ConnectUiState> = _uiState

    private val walletConnectKitConfig = WalletConnectKitConfig(
        context = application,
        bridgeUrl = "https://safe-walletconnect.safe.global/",
        appUrl = "https://xmtp.org",
        appName = "XMTP Example",
        appDescription = "Example app using the xmtp-android SDK"
    )
    val walletConnectKit = WalletConnectKit.Builder(walletConnectKitConfig).build()

    data class WCAccount(private val wcKit: WalletConnectKit) : SigningKey {
        override val address: String
            get() = Keys.toChecksumAddress(wcKit.address.orEmpty())

        override suspend fun sign(data: ByteArray): SignatureOuterClass.Signature? {
            return sign(String(data))
        }

        override suspend fun sign(message: String): SignatureOuterClass.Signature? {
            runCatching { wcKit.personalSign(message) }
                .onSuccess {
                    var result = it.result as String
                    if (result.startsWith("0x") && result.length == 132) {
                        result = result.drop(2)
                    }

                    val resultData = Numeric.hexStringToByteArray(result)

                    // Ensure we have a valid recovery byte
                    resultData[resultData.size - 1] =
                        (1 - resultData[resultData.size - 1] % 2).toByte()

                    return SignatureBuilder.buildFromSignatureData(resultData)
                }
                .onFailure {}
            return null
        }
    }

    @UiThread
    fun generateWallet() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ConnectUiState.Loading
            try {
                val wallet = PrivateKeyBuilder()
                val client = Client().create(wallet, ClientManager.CLIENT_OPTIONS)
                _uiState.value = ConnectUiState.Success(
                    wallet.address,
                    PrivateKeyBundleV1Builder.encodeData(client.privateKeyBundleV1)
                )
            } catch (e: XMTPException) {
                _uiState.value = ConnectUiState.Error(e.message.orEmpty())
            }
        }
    }

    @UiThread
    fun connectWallet() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ConnectUiState.Loading
            try {
                val wallet = WCAccount(walletConnectKit)
                val client = Client().create(wallet, ClientManager.CLIENT_OPTIONS)
                _uiState.value = ConnectUiState.Success(
                    wallet.address,
                    PrivateKeyBundleV1Builder.encodeData(client.privateKeyBundleV1)
                )
            } catch (e: Exception) {
                _uiState.value = ConnectUiState.Error(e.message.orEmpty())
            }
        }
    }

    sealed class ConnectUiState {
        object Unknown : ConnectUiState()
        object Loading : ConnectUiState()
        data class Success(val address: String, val encodedKeyData: String) : ConnectUiState()
        data class Error(val message: String) : ConnectUiState()
    }
}
