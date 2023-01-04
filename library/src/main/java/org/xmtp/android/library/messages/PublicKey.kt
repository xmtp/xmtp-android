package org.xmtp.android.library.messages

import com.google.protobuf.kotlin.toByteString
import org.bouncycastle.crypto.digests.SHA256Digest
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.xmtp.android.library.extensions.millisecondsSinceEpoch
import org.xmtp.proto.message.contents.PublicKeyOuterClass
import java.util.*

typealias PublicKey = org.xmtp.proto.message.contents.PublicKeyOuterClass.PublicKey

class PublicKeyFactory {
    companion object {
        fun create(signedPublicKey: PublicKeyOuterClass.SignedPublicKey): PublicKey {
            val builder = PublicKey.newBuilder()
            val unsignedPublicKey =
                PublicKeyOuterClass.UnsignedPublicKey.newBuilder()
//            unsignedPublicKey = signedPublicKey.keyBytes
            unsignedPublicKey.build()
            builder.timestamp = unsignedPublicKey.createdNs
            val secp256K1Builder = builder.secp256K1UncompressedBuilder
            secp256K1Builder.bytes = unsignedPublicKey.secp256K1Uncompressed.bytes
            secp256K1Builder.build()
            return builder.build()
        }

        fun createFromBytes(data: ByteArray): PublicKey {
            val builder = PublicKey.newBuilder()
            builder.timestamp = Date().millisecondsSinceEpoch.toLong()
            builder.secp256K1UncompressedBuilder.bytes = data.toByteString()
            return builder.build()
        }
    }
}

fun PublicKey.recoverKeySignedPublicKey(): PublicKey {
    if (!hasSignature()) {
//        throw PublicKeyError.noSignature
    }
    // We don't want to include the signature in the key bytes
    val slimKey = PublicKey.newBuilder()
    slimKey.secp256K1UncompressedBuilder.bytes = secp256K1Uncompressed.bytes
    slimKey.timestamp = timestamp
    val bytesToSign = slimKey.build().toByteArray()
    val v = signature.toByteArray().last()
    val r = signature.toByteArray().take(32).toByteArray()
    val s = signature.toByteArray().takeLast(33).dropLast(1).toByteArray()

    val pubKeyData = Sign.signedMessageToKey(
        SHA256Digest(bytesToSign).encodedState,
        Sign.SignatureData(
            v,
            r,
            s
        )
    )
    return PublicKeyFactory.createFromBytes(pubKeyData.toByteArray())
}

val PublicKeyOuterClass.PublicKey.walletAddress: String
    get() {
        return Keys.toChecksumAddress(Keys.getAddress(secp256K1Uncompressed.bytes.toString()))
    }