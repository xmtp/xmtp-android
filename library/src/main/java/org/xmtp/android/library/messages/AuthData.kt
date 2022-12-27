package org.xmtp.android.library.messages

import org.xmtp.android.library.extensions.millisecondsSinceEpoch
import org.xmtp.proto.message.api.v1.Authn
import java.util.*

typealias AuthData = org.xmtp.proto.message.api.v1.Authn.AuthData

class AuthDataFactory {
    companion object {
        fun create(walletAddress: String, timestamp: Date? = null): Authn.AuthData {
            val builder = AuthData.newBuilder()
            builder.walletAddr = walletAddress
            val timestamped = timestamp ?: Date()
            builder.createdNs = (timestamped.millisecondsSinceEpoch * 1_000_000).toLong()
            return builder.build()
        }
    }
}