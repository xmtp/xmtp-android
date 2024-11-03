package org.xmtp.android.library

import com.google.protobuf.kotlin.toByteString
import kotlinx.coroutines.runBlocking
import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.xmtp.android.library.messages.PublicKey
import org.xmtp.android.library.messages.Signature
import org.xmtp.android.library.messages.createIdentityText
import org.xmtp.android.library.messages.ethHash
import org.xmtp.android.library.messages.rawData
import org.xmtp.proto.message.contents.PrivateKeyOuterClass
import org.xmtp.proto.message.contents.PublicKeyOuterClass
import org.xmtp.proto.message.contents.SignatureOuterClass
import java.math.BigInteger
import java.util.Date

interface SigningKey {
    val address: String

    // The wallet type if Smart Contract Wallet this should be type SCW.
    val type: WalletType
        get() = WalletType.EOA

    // The chainId of the Smart Contract Wallet value should be null if not SCW
    var chainId: Long?
        get() = null
        set(_) {}

    // Default blockNumber value set to null
    var blockNumber: Long?
        get() = null
        set(_) {}

    suspend fun sign(data: ByteArray): SignatureOuterClass.Signature? {
        throw NotImplementedError("sign(ByteArray) is not implemented.")
    }

    suspend fun sign(message: String): SignatureOuterClass.Signature? {
        throw NotImplementedError("sign(String) is not implemented.")
    }

    suspend fun signSCW(message: String): ByteArray {
        throw NotImplementedError("signSCW(String) is not implemented.")
    }
}

enum class WalletType {
    SCW, // Smart Contract Wallet
    EOA // Externally Owned Account *Default
}
