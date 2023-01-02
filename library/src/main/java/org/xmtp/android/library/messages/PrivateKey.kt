package org.xmtp.android.library.messages

import com.google.protobuf.kotlin.toByteString
import com.google.protobuf.kotlin.toByteStringUtf8
import org.bouncycastle.crypto.digests.SHA256Digest
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign
import org.xmtp.android.library.Crypto
import org.xmtp.android.library.extensions.millisecondsSinceEpoch
import org.xmtp.proto.message.contents.PublicKeyOuterClass
import org.xmtp.proto.message.contents.SignatureOuterClass
import java.security.SecureRandom
import java.util.*

typealias PrivateKey = org.xmtp.proto.message.contents.PrivateKeyOuterClass.PrivateKey

class PrivateKeyFactory {
    companion object {
        fun create(privateKeyData: ByteArray): PrivateKey {
            val builder = PrivateKey.newBuilder()
            builder.timestamp = (Date().millisecondsSinceEpoch).toLong()
            builder.secp256K1Builder.bytes = privateKeyData.toByteString()
            val publicData = ECKeyPair.create(privateKeyData)
            builder.publicKeyBuilder.secp256K1UncompressedBuilder.bytes =
                publicData.publicKey.toByteArray().toByteString()
            builder.publicKeyBuilder.timestamp = builder.timestamp
            return builder.build()
        }
    }
}

public val PrivateKey.address: String
    get() = walletAddress

fun PrivateKey.matches(publicKey: PublicKey): Boolean = publicKey.recoverKeySignedPublicKey() == (publicKey.recoverKeySignedPublicKey())

fun PrivateKey.sign(data: ByteArray): Signature {
    val signatureData =
        Sign.signMessage(data, ECKeyPair.create(secp256K1.bytes.toByteArray()), false)
    val signature = SignatureOuterClass.Signature.newBuilder()
    signature.ecdsaCompactBuilder.bytes = signatureData.toString().take(64).toByteStringUtf8()
    signature.ecdsaCompactBuilder.recovery = signatureData.toString()[64].digitToInt()
    return signature.build()
}

suspend fun PrivateKey.sign(message: String): Signature {
    val digest = Signature.newBuilder().build().ethHash(message)
    return sign(digest)
}

fun PrivateKey.generate(): PrivateKey {
    return PrivateKeyFactory.create(SecureRandom().generateSeed(32))
}

val PrivateKey.walletAddress: String
    get() = publicKey.walletAddress

fun PrivateKey.sign(key: PublicKeyOuterClass.UnsignedPublicKey): PublicKeyOuterClass.SignedPublicKey {
    val bytes = key.secp256K1Uncompressed.bytes
    val digest = SHA256Digest(bytes.toByteArray()).encodedState
    val signedPublicKey = PublicKeyOuterClass.SignedPublicKey.newBuilder()
    val signature = sign(digest)
    signedPublicKey.signature = signature
    signedPublicKey.keyBytes = bytes
    return signedPublicKey.build()
}




