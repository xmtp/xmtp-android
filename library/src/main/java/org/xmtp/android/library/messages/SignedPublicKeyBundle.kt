package org.xmtp.android.library.messages

typealias SignedPublicKeyBundle = org.xmtp.proto.message.contents.PublicKeyOuterClass.SignedPublicKeyBundle

class SignedPublicKeyBundle2  {
    companion object {

    }
}

fun SignedPublicKeyBundle.parseFrom(publicKeyBundle: PublicKeyBundle) : SignedPublicKeyBundle {
    val builder = this.toBuilder()
    val signedPublicKey = SignedPublicKey.newBuilder().build()
    builder.identityKey = signedPublicKey.fromLegacy(publicKeyBundle.identityKey)
    builder.identityKeyBuilder.signature = publicKeyBundle.identityKey.signature
    builder.preKey = signedPublicKey.fromLegacy(publicKeyBundle.preKey)
    builder.preKeyBuilder.signature = publicKeyBundle.preKey.signature
    return builder.build()
}

fun SignedPublicKeyBundle.equals(other: SignedPublicKeyBundle) : Boolean =
    identityKey == other.identityKey && preKey == other.preKey
val SignedPublicKeyBundle.walletAddress: String
    get() = identityKey.recoverWalletSignerPublicKey().walletAddress
