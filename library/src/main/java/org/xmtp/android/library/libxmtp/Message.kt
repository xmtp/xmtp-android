package org.xmtp.android.library.libxmtp

import org.xmtp.android.library.Client
import uniffi.xmtp_dh.FfiMessage

data class Message(val client: Client, val libXMTPMessage: FfiMessage) {
    val id: ByteArray
        get() = libXMTPMessage.id

    val senderAddress: String
        get() = libXMTPMessage.addrFrom

    fun text(): String {
        return libXMTPMessage.content.decodeToString()
    }
}