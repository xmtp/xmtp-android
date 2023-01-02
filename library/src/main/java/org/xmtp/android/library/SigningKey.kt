package org.xmtp.android.library;

import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.xmtp.android.library.extensions.millisecondsSinceEpoch
import org.xmtp.android.library.messages.PublicKey
import org.xmtp.android.library.messages.Signature
import org.xmtp.android.library.messages.createIdentityText
import org.xmtp.android.library.messages.ethHash
import org.xmtp.proto.message.contents.PrivateKeyOuterClass
import org.xmtp.proto.message.contents.PublicKeyOuterClass
import org.xmtp.proto.message.contents.SignatureOuterClass
import java.util.*


public interface SigningKey {
    val address/// A wallet address for this key
            : String
    fun sign(data/// Sign the data and return a secp256k1 compact recoverable signature.
             : ByteArray) : SignatureOuterClass.Signature
    fun sign(message/// Pass a personal Ethereum signed message string text to be signed, returning
             /// a secp256k1 compact recoverable signature. You can use ``Signature.ethPersonalMessage`` to generate this text.
             : String) : SignatureOuterClass.Signature
}

fun SigningKey.createIdentity(identity: PrivateKeyOuterClass.PrivateKey) : AuthorizedIdentity {
    val slimKey = PublicKeyOuterClass.PublicKey.newBuilder()
    slimKey.timestamp = Date().millisecondsSinceEpoch.toLong()
    slimKey.secp256K1Uncompressed = identity.publicKey.secp256K1Uncompressed
    val signatureClass = Signature.newBuilder().build()
    val signatureText = signatureClass.createIdentityText(key = slimKey.build().toByteArray())
    val signature = sign(message = signatureText)
    val digest = signatureClass.ethHash(message = signatureText)

    val recoveredKey = Sign.signedMessageToKey(digest,
        Sign.SignatureData(
            signature.toByteArray(),
            signature.toByteArray(),
            signature.toByteArray()
        )
    )
    val address = Keys.toChecksumAddress(Keys.getAddress(recoveredKey))

    val authorized = PublicKey.newBuilder()
    authorized.secp256K1Uncompressed = slimKey.secp256K1Uncompressed
    authorized.timestamp = slimKey.timestamp
    authorized.signature = signature
    return AuthorizedIdentity(address = address, authorized = authorized.build(), identity = identity)
}

