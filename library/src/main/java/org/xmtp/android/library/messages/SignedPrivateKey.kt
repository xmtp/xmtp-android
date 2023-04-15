package org.xmtp.android.library.messages

import kotlinx.coroutines.runBlocking

typealias SignedPrivateKey = org.xmtp.proto.message.contents.PrivateKeyOuterClass.SignedPrivateKey

class SignedPrivateKeyBuilder {
    companion object {
        fun buildFromLegacy(key: PrivateKey): SignedPrivateKey {
            return SignedPrivateKey.newBuilder().apply {
                createdNs = key.timestamp * 1_000_000
                secp256K1.toBuilder().bytes = key.secp256K1.bytes
                publicKey = SignedPublicKeyBuilder.buildFromLegacy(
                    key.publicKey,
                )
                publicKey.toBuilder().signature = key.publicKey.signature
            }.build()
        }
    }
}

fun SignedPrivateKey.sign(data: ByteArray): Signature {
    val key = PrivateKeyBuilder.buildFromPrivateKeyData(secp256K1.bytes.toByteArray())
    return runBlocking {
        PrivateKeyBuilder(key).sign(data)
    }
}

fun SignedPrivateKey.matches(signedPublicKey: SignedPublicKey): Boolean {
    return publicKey.recoverWalletSignerPublicKey().walletAddress == signedPublicKey.recoverWalletSignerPublicKey().walletAddress
}
