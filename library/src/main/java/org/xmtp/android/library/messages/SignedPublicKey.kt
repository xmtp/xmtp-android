package org.xmtp.android.library.messages

import org.bouncycastle.jcajce.provider.digest.Keccak
import org.web3j.crypto.Sign
import org.xmtp.android.library.KeyUtil
import org.xmtp.proto.message.contents.PublicKeyOuterClass

typealias SignedPublicKey = org.xmtp.proto.message.contents.PublicKeyOuterClass.SignedPublicKey

class SignedPublicKeyBuilder {
    companion object {
        fun buildFromLegacy(
            legacyKey: PublicKey,
            signedByWallet: Boolean? = false
        ): SignedPublicKey {
            val publicKey = PublicKey.newBuilder().apply {
                secp256K1Uncompressed = legacyKey.secp256K1Uncompressed
                timestamp = legacyKey.timestamp
            }.build()
            return SignedPublicKey.newBuilder().apply {
                keyBytes = publicKey.toByteString()
                signature = legacyKey.signature
            }.build()
        }

        fun parseFromPublicKey(publicKey: PublicKey, sig: Signature): SignedPublicKey {
            val builder = SignedPublicKey.newBuilder().apply {
                signature = sig
            }
            val unsignedKey = PublicKey.newBuilder().apply {
                timestamp = publicKey.timestamp
                secp256K1UncompressedBuilder.bytes = publicKey.secp256K1Uncompressed.bytes
            }.build()
            builder.keyBytes = unsignedKey.toByteString()
            return builder.build()
        }
    }
}

val SignedPublicKey.secp256K1Uncompressed: PublicKeyOuterClass.PublicKey.Secp256k1Uncompressed
    get() {
        // swiftlint:disable force_try
        val key = PublicKey.parseFrom(keyBytes)
        // swiftlint:enable force_try
        return key.secp256K1Uncompressed
    }

fun SignedPublicKey.verify(key: SignedPublicKey): Boolean {
    if (!key.hasSignature()) {
        return false
    }
    return signature.verify(
        PublicKeyBuilder.buildFromSignedPublicKey(key),
        key.keyBytes.toByteArray()
    )
}

fun SignedPublicKey.recoverKeySignedPublicKey(): PublicKey {
    val publicKey = PublicKeyBuilder.buildFromSignedPublicKey(this)
    val slimKey = PublicKey.newBuilder()
    slimKey.secp256K1UncompressedBuilder.bytes = secp256K1Uncompressed.toByteString()
    slimKey.timestamp = publicKey.timestamp
    val bytesToSign = slimKey.build().toByteArray()
    val pubKeyData = Sign.signedMessageToKey(
        Keccak.Digest256().digest(bytesToSign),
        KeyUtil.getSignatureData(signature.rawData)
    )
    return PublicKey.parseFrom(pubKeyData.toByteArray())
}

fun SignedPublicKey.recoverWalletSignerPublicKey(): PublicKey {
    val sig = Signature.newBuilder().build()
    val sigText = sig.createIdentityText(keyBytes.toByteArray())
    val sigHash = sig.ethHash(sigText)
    val pubKeyData = Sign.signedMessageToKey(sigHash, KeyUtil.getSignatureData(signature.rawData))
    return PublicKey.parseFrom(pubKeyData.toByteArray())
}
