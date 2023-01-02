package org.xmtp.android.library.messages

import com.google.protobuf.kotlin.toByteString
import org.xmtp.android.library.Crypto
import org.xmtp.android.library.SigningKey
import org.xmtp.proto.message.contents.PrivateKeyOuterClass
import java.security.SecureRandom

typealias PrivateKeyBundle = org.xmtp.proto.message.contents.PrivateKeyOuterClass.PrivateKeyBundle

fun PrivateKeyBundle.encrypted(key: SigningKey) : EncryptedPrivateKeyBundle {
    val bundleBytes = byteArrayOf()
    val walletPreKey = SecureRandom().generateSeed(32)
    val signature = key.sign(message = Signature.newBuilder().build().enableIdentityText(key = walletPreKey))
    val cipherText = Crypto.encrypt(signature.rawData, bundleBytes)
    val encryptedBundle = PrivateKeyOuterClass.EncryptedPrivateKeyBundle.newBuilder()
    encryptedBundle.v1Builder.walletPreKey = walletPreKey.toByteString()
    encryptedBundle.v1Builder.ciphertext = cipherText
    return encryptedBundle.build()
}
