package org.xmtp.android.library.messages

import org.xmtp.android.library.Util
import org.xmtp.android.library.toHex
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.charset.StandardCharsets.UTF_8

typealias Signature = org.xmtp.proto.message.contents.SignatureOuterClass.Signature

fun Signature.ethHash(message: String): ByteArray {
    val prefix = "\\u0019Ethereum Signed Message:\n${message.length}"
    val data = prefix.toByteArray(charset = US_ASCII)
    data.plus(message.toByteArray(charset = UTF_8))
    return Util.keccak256(data)
}

fun Signature.createIdentityText(key: ByteArray): String =
    ("XMTP : Create Identity\n" + "${key.toHex()}\n" + "\n" + "For more info: https://xmtp.org/signatures/")

fun Signature.enableIdentityText(key: ByteArray): String =
    ("XMTP : Enable Identity\n" + "${key.toHex()}\n" + "\n" + "For more info: https://xmtp.org/signatures/")

val Signature.rawData: ByteArray
    get() = ecdsaCompact.bytes.toByteArray() + listOf(ecdsaCompact.recovery.toByte()).toByteArray()
