package org.xmtp.android.library.messages

import org.bouncycastle.jcajce.provider.digest.Keccak
import org.xmtp.android.library.toHex
import org.xmtp.proto.message.contents.SignatureOuterClass
import java.security.Signature as ECDSASig

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

val Signature.rawDataWithNormalizedRecovery: ByteArray
    get() {
        val data = rawData
        if (data[64] == 0.toByte()) {
            data[64] = 27.toByte()
        } else if (data[64] == 1.toByte()) {
            data[64] = 28.toByte()
        }
        return data
    }

fun Signature.verify(signedBy: PublicKey, digest: ByteArray): Boolean {
    val ecdsaVerify = ECDSASig.getInstance("SHA256withECDSA")

    return ecdsaVerify.verify(signedBy.signature.rawData)
}

fun Signature.ensureWalletSignature() {
    when (unionCase) {
        SignatureOuterClass.Signature.UnionCase.ECDSA_COMPACT -> {
            val walletEcdsa = SignatureOuterClass.Signature.WalletECDSACompact.newBuilder().apply {
                bytes = ecdsaCompact.bytes
                recovery = ecdsaCompact.recovery
            }.build()
            this.toBuilder().apply {
                walletEcdsaCompact = walletEcdsa
            }.build()
        }
        else -> return
    }
}
