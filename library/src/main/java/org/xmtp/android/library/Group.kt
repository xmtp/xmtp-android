package org.xmtp.android.library

import kotlinx.coroutines.runBlocking
import org.xmtp.android.library.libxmtp.Message
import uniffi.xmtp_dh.FfiGroup
import uniffi.xmtp_dh.FfiListMessagesOptions

class Group(val client: Client, val libXMTPGroup: FfiGroup) {
    val id: List<UByte>
        get() = libXMTPGroup.id()

    fun send(text: String): String {
        runBlocking {
            libXMTPGroup.send(
                contentBytes = text.toByteArray(Charsets.UTF_8).toUByteArray().toList()
            )
        }
        return id.toString()
    }

    fun messages(): List<DecodedMessage> {
        return runBlocking {
            libXMTPGroup.findMessages(
                opts = FfiListMessagesOptions(
                    sentBeforeNs = null,
                    sentAfterNs = null,
                    limit = null
                )
            ).map {
                Message(client, it).decode()
            }
        }
    }
}