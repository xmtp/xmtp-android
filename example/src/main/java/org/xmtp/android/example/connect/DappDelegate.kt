package org.xmtp.android.example.connect

import android.net.Uri
import androidx.core.net.toUri
import com.walletconnect.wcmodal.client.Modal
import com.walletconnect.wcmodal.client.WalletConnectModal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.web3j.utils.Numeric
import timber.log.Timber
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

object DappDelegate : WalletConnectModal.ModalDelegate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _wcEventModels: MutableSharedFlow<Modal.Model?> = MutableSharedFlow()
    val wcEventModels: SharedFlow<Modal.Model?> = _wcEventModels.asSharedFlow()

    var selectedSessionTopic: String? = null
        private set

    init {
        WalletConnectModal.setDelegate(this)
    }

    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
        selectedSessionTopic = approvedSession.topic

        scope.launch {
            _wcEventModels.emit(approvedSession)
        }
    }

    override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
        scope.launch {
            _wcEventModels.emit(rejectedSession)
        }
    }

    override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
        scope.launch {
            _wcEventModels.emit(updatedSession)
        }
    }

    override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {
        scope.launch {
            _wcEventModels.emit(sessionEvent)
        }
    }

    override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
        deselectAccountDetails()

        scope.launch {
            _wcEventModels.emit(deletedSession)
        }
    }

    override fun onSessionExtend(session: Modal.Model.Session) {
        scope.launch {
            _wcEventModels.emit(session)
        }
    }

    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
        scope.launch {
            _wcEventModels.emit(response)
        }
    }

    fun deselectAccountDetails() {
        selectedSessionTopic = null
    }

    override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {
        Timber.d("WalletConnect", "onConnectionStateChange($state)")
        scope.launch {
            _wcEventModels.emit(state)
        }
    }

    override fun onError(error: Modal.Model.Error) {
        Timber.d("WalletConnect", error.throwable.stackTraceToString())
        scope.launch {
            _wcEventModels.emit(error)
        }
    }

    suspend fun request(
        requestParams: Modal.Params.Request,
        sendSessionRequestDeepLink: (Uri) -> Unit
    ): ByteArray {
        return withContext(Dispatchers.IO) {
            suspendCoroutine { continuation ->
                WalletConnectModal.request(
                    request = requestParams,
                    onSuccess = { sentRequest ->
                        WalletConnectModal.getActiveSessionByTopic(requestParams.sessionTopic)?.redirect?.toUri()
                            ?.let { deepLinkUri ->
                                sendSessionRequestDeepLink(deepLinkUri)
                            }
                        onResponse(continuation, sentRequest)
                    },
                    onError = { Timber.e(it.throwable) }
                )
            }
        }
    }

    private fun onResponse(
        continuation: Continuation<ByteArray>,
        sentRequest: Modal.Model.SentRequest
    ) {
        wcEventModels
            .filterNotNull()
            .onEach { event ->
                when (event) {
                    is Modal.Model.SessionRequestResponse -> {
                        if (event.topic == sentRequest.sessionTopic && event.result.id == sentRequest.requestId) {
                            when (val res = event.result) {
                                is Modal.Model.JsonRpcResponse.JsonRpcResult -> {
                                    var result = res.result
                                    if (result.startsWith("0x") && result.length == 132) {
                                        result = result.drop(2)
                                    }

                                    val resultData = Numeric.hexStringToByteArray(result)

                                    // Ensure we have a valid recovery byte
                                    resultData[resultData.size - 1] =
                                        (1 - resultData[resultData.size - 1] % 2).toByte()

                                    continuation.resumeWith(Result.success(resultData))
                                }

                                is Modal.Model.JsonRpcResponse.JsonRpcError -> {
                                    continuation.resumeWith(Result.failure(Throwable(res.message)))
                                }
                            }
                        } else continuation.resumeWith(Result.failure(Throwable("The result id is different from the request id!")))
                    }

                    else -> {}
                }
            }.launchIn(scope)
    }
}
