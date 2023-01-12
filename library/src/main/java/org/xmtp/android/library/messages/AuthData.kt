package org.xmtp.android.library.messages

import org.xmtp.android.library.extensions.millisecondsSinceEpoch
import org.xmtp.proto.message.api.v1.Authn
import java.util.*

typealias AuthData = org.xmtp.proto.message.api.v1.Authn.AuthData

class AuthDataBuilder {
    companion object {
        fun buildFromWalletAddress(walletAddress: String, timestamp: Date? = null): Authn.AuthData {
            val timestamped = timestamp ?: Date()
            return AuthData.newBuilder().apply {
                walletAddr = walletAddress
                createdNs = (timestamped.millisecondsSinceEpoch * 1_000_000).toLong()
            }.build()
        }
    }
}