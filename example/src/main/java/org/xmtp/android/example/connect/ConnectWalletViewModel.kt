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
import org.xmtp.android.example.ClientManager
import org.xmtp.android.example.account.WalletConnectAccount
import org.xmtp.android.library.Client
import org.xmtp.android.library.ClientOptions
import org.xmtp.android.library.XMTPEnvironment
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.PrivateKeyBundleV1Builder

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

    @UiThread
    fun generateWallet() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ConnectUiState.Loading
            try {
                // Build from [8,54,32,15,250,250,23,163,203,139,84,242,45,106,250,96,177,61,164,135,38,84,50,65,173,197,194,80,219,176,224,205]
                // or in hex 0836200ffafa17a3cb8b54f22d6afa60b13da48726543241adc5c250dbb0e0cd
                // aka 2k many convos test wallet
                // Create a ByteArray with the 32 bytes above
                val privateKeyData = listOf(0x08, 0x36, 0x20, 0x0f, 0xfa, 0xfa, 0x17, 0xa3, 0xcb, 0x8b, 0x54, 0xf2, 0x2d, 0x6a, 0xfa, 0x60, 0xb1, 0x3d, 0xa4, 0x87, 0x26, 0x54, 0x32, 0x41, 0xad, 0xc5, 0xc2, 0x50, 0xdb, 0xb0, 0xe0, 0xcd)
                    .map { it.toByte() }
                    .toByteArray()
                // Use hardcoded privateKey
                val privateKey = PrivateKeyBuilder.buildFromPrivateKeyData(privateKeyData)
                val wallet = PrivateKeyBuilder(privateKey)
                val options = ClientOptions(api = ClientOptions.Api(XMTPEnvironment.DEV))
                val client = Client().create(account = wallet, options = options)
                
//                val wallet = PrivateKeyBuilder()
//                val client = Client().create(wallet, ClientManager.CLIENT_OPTIONS)
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
                val wallet = WalletConnectAccount(walletConnectKit)
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
