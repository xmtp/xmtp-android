package org.xmtp.android.library

import kotlinx.coroutines.runBlocking
import org.xmtp.android.library.libxmtp.Message
import uniffi.xmtpv3.FfiGroup
import uniffi.xmtpv3.FfiListMessagesOptions
import java.util.Date

class Group(val client: Client, private val libXMTPGroup: FfiGroup) {
    val id: ByteArray
        get() = libXMTPGroup.id()

    val createdAt: Date
        get() = Date(libXMTPGroup.createdAtNs() / 1_000_000)

    fun send(text: String): String {
        runBlocking {
            libXMTPGroup.send(
                contentBytes = text.toByteArray(Charsets.UTF_8)
            )
        }
        return id.toString()
    }

    suspend fun sync() {
        libXMTPGroup.sync()
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