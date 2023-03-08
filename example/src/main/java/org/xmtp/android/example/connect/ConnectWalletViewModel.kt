package org.xmtp.android.example.connect

import android.util.Log
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.trustwallet.walletconnect.WCClient
import com.trustwallet.walletconnect.exceptions.InvalidSessionException
import com.trustwallet.walletconnect.models.WCPeerMeta
import com.trustwallet.walletconnect.models.session.WCSession
import java.lang.Thread.sleep
import java.net.URLEncoder
import java.util.Random
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.komputing.khex.extensions.toNoPrefixHexString
import org.xmtp.android.library.XMTPException
import org.xmtp.android.library.messages.PrivateKeyBuilder

class ConnectWalletViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ConnectUiState>(ConnectUiState.Unknown)
    val uiState: StateFlow<ConnectUiState> = _uiState

    private val okHttpClient = OkHttpClient.Builder().build()
    private val gsonBuilder = GsonBuilder()

    @UiThread
    fun generateWallet() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ConnectUiState.Loading
            try {
                val wallet = PrivateKeyBuilder()
                _uiState.value = ConnectUiState.Success(
                    wallet.address,
                    wallet.encodedPrivateKeyData()
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
                val peerMeta = WCPeerMeta(name = "XMTP Example", url = "https://xmtp.org")
                val key = ByteArray(32).also { Random().nextBytes(it) }.toNoPrefixHexString()
                val bridge = URLEncoder.encode("https://safe-walletconnect.safe.global/", "UTF-8")
                val wcURI = "wc:${UUID.randomUUID()}@1?bridge=$bridge&key=$key"

                val session = WCSession.from(wcURI) ?: throw InvalidSessionException()
                val wcClient = WCClient(gsonBuilder, okHttpClient)
                wcClient.onDisconnect = { _, _ ->
                    Log.e("###", "DISCONNECT")
                    if (wcClient.session != null) {
                        wcClient.killSession()
                    } else {
                        wcClient.disconnect()
                    }
                }
                wcClient.onSignTransaction = { _, _->
                    Log.e("###", "SIGN")
                }
                wcClient.onSessionRequest = { _, peer ->
                    Log.e("###", "SESSION REQ PEER: $peer")
                }
                wcClient.onFailure = { t ->
                    Log.e("###", "FAILURE")
                }
                wcClient.connect(session, peerMeta)
                _uiState.value = ConnectUiState.Connect(wcURI)
                for (i in 0 .. 30) {
                    if (wcClient.isConnected) {
                        Log.e("###", "CONNECTED!")
                        // TODO: HOW DO WE GENERATE A PRIVATE KEY?
                    }
                    sleep(1000)
                }
                wcClient.disconnect()
                _uiState.value = ConnectUiState.Error("Timed out connection after 30s")
            } catch (e: Exception) {
                _uiState.value = ConnectUiState.Error(e.message.orEmpty())
            }
        }
    }

    sealed class ConnectUiState {
        object Unknown : ConnectUiState()
        object Loading : ConnectUiState()
        data class Success(val address: String, val encodedKeyData: String) : ConnectUiState()
        data class Connect(val uri: String): ConnectUiState()
        data class Error(val message: String) : ConnectUiState()
    }
}
