package org.xmtp.android.library.libxmtp

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import uniffi.xmtpv3.FfiMessage
import uniffi.xmtpv3.FfiMessageCallback

class MessageEmitter {
    private val _messages = MutableSharedFlow<FfiMessage>()
    val messages = _messages.asSharedFlow()

    val callback: FfiMessageCallback = object : FfiMessageCallback {
        override fun onMessage(message: FfiMessage) {
            _messages.tryEmit(message)
        }
    }
}