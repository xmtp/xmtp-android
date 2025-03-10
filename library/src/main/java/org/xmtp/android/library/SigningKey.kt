package org.xmtp.android.library

import org.xmtp.android.library.libxmtp.PublicIdentity
import org.xmtp.proto.message.contents.SignatureOuterClass
import uniffi.xmtpv3.org.xmtp.android.library.SignedData

interface SigningKey {
    val publicIdentity: PublicIdentity
    val type: SignerType
        get() = SignerType.EOA

    var chainId: Long?
        get() = null
        set(_) {}

    var blockNumber: Long?
        get() = null
        set(_) {}

    suspend fun sign(message: String): SignedData
}

enum class SignerType {
    SCW, // Smart Contract Wallet
    EOA, // Externally Owned Account *Default
    PASSKEY
}
