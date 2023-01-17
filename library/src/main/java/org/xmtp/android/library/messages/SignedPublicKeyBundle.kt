package org.xmtp.android.library.messages

typealias SignedPublicKeyBundle = org.xmtp.proto.message.contents.PublicKeyOuterClass.SignedPublicKeyBundle

class SignedPublicKeyBundleBuilder {
    companion object {
        fun buildFromKeyBundle(publicKeyBundle: PublicKeyBundle) : SignedPublicKeyBundle {
            val signedPublicKey = SignedPublicKey.newBuilder().build()
            return SignedPublicKeyBundle.newBuilder().apply {
                identityKey = signedPublicKey.fromLegacy(publicKeyBundle.identityKey)
                identityKeyBuilder.signature = publicKeyBundle.identityKey.signature
                preKey = signedPublicKey.fromLegacy(publicKeyBundle.preKey)
                preKeyBuilder.signature = publicKeyBundle.preKey.signature
            }.build()
        }
    }
}

fun SignedPublicKeyBundle.equals(other: SignedPublicKeyBundle) : Boolean =
    identityKey == other.identityKey && preKey == other.preKey
val SignedPublicKeyBundle.walletAddress: String
    get() = identityKey.recoverWalletSignerPublicKey().walletAddress
