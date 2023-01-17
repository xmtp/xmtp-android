package org.xmtp.android.library.messages

import com.google.protobuf.kotlin.toByteString
import org.web3j.crypto.ECDSASignature
import org.xmtp.android.library.messages.PublicKey
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.web3j.crypto.Sign
import org.xmtp.android.library.KeyUtil
import org.xmtp.android.library.toHex
import java.math.BigInteger

typealias Signature = org.xmtp.proto.message.contents.SignatureOuterClass.Signature

private const val MESSAGE_PREFIX = "\u0019Ethereum Signed Message:\n"

fun Signature.ethHash(message: String): ByteArray {
    val input = MESSAGE_PREFIX + message.length + message
    val digest256 = Keccak.Digest256()
    return digest256.digest(input.toByteArray())
}

fun Signature.createIdentityText(key: ByteArray): String =
    ("XMTP : Create Identity\n" + "${key.toHex()}\n" + "\n" + "For more info: https://xmtp.org/signatures/")

fun Signature.enableIdentityText(key: ByteArray): String =
    ("XMTP : Enable Identity\n" + "${key.toHex()}\n" + "\n" + "For more info: https://xmtp.org/signatures/")

val Signature.rawData: ByteArray
    get() = ecdsaCompact.bytes.toByteArray() + listOf(ecdsaCompact.recovery.toByte()).toByteArray()

fun Signature.verify(signedBy: PublicKey, digest: ByteArray): Boolean {
    val signatureData = KeyUtil.getSignatureData(signature.rawData.toByteString().toByteArray())
    val publicKey = Sign.recoverFromSignature(
        BigInteger(signatureData.v).toInt(),
        ECDSASignature(BigInteger(1, signatureData.r), BigInteger(signatureData.s)),
        digest
    )
    val recoverySignature =
        ECDSASignature(BigInteger(ecdsaCompact.toByteArray()), ecdsaCompact.recovery.toBigInteger())
    val ecdsaSignature = recoverySignature
    val signingKey = secp256k1.Signing.PublicKey(
        rawRepresentation = signedBy.secp256K1Uncompressed.bytes,
        format = . uncompressed)
    return signingKey.ecdsa.isValidSignature(ecdsaSignature, digest)
}
