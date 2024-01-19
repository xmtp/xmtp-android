package org.xmtp.android.library

import org.xmtp.android.library.libxmtp.Message
import uniffi.xmtp_dh.FfiGroup
import uniffi.xmtp_dh.FfiListMessagesOptions

class Group(val client: Client, val libXMTPGroup: FfiGroup) {
    val id: List<UByte>
        get() = libXMTPGroup.id()

    suspend fun send(text: String) {
        libXMTPGroup.send(contentBytes = text.toByteArray(Charsets.UTF_8).toUByteArray().toList())
    }

    suspend fun messages(): List<Message> {
        return libXMTPGroup.findMessages(
            opts = FfiListMessagesOptions(
                sentBeforeNs = null,
                sentAfterNs = null,
                limit = null
            )
        ).map {
            Message(client, it)
        }
    }
}