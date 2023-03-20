package org.xmtp.android.library

import org.web3j.crypto.Hash
import org.xmtp.android.library.messages.Envelope

data class PreparedMessage(
    var messageEnvelope: Envelope,
    var conversation: Conversation,
    var onSend: () -> Unit) {

    public fun decodedMessage() : DecodedMessage =
        conversation.decode(messageEnvelope)

    public fun send() {
        onSend()
    }
    val messageID: String
        get() = Hash.sha256(messageEnvelope.message.toByteArray()).toHex()
}
