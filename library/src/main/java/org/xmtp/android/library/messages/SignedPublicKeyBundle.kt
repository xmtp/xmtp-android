package org.xmtp.android.library.messages

typealias SignedPublicKeyBundle = org.xmtp.proto.message.contents.PublicKeyOuterClass.SignedPublicKeyBundle

class SignedPublicKeyBundleBuilder {
    companion object {
        fun buildFromKeyBundle(publicKeyBundle: PublicKeyBundle): SignedPublicKeyBundle {
            return SignedPublicKeyBundle.newBuilder().apply {
                identityKey = SignedPublicKeyBuilder.buildFromLegacy(publicKeyBundle.identityKey)
                identityKey.toBuilder().signature = publicKeyBundle.identityKey.signature
                preKey = SignedPublicKeyBuilder.buildFromLegacy(publicKeyBundle.preKey)
                preKey.toBuilder().signature = publicKeyBundle.preKey.signature
            }.build()
        }
    }
}

fun SignedPublicKeyBundle.equals(other: SignedPublicKeyBundle): Boolean =
    identityKey == other.identityKey && preKey == other.preKey

val SignedPublicKeyBundle.walletAddress: String
    get() = identityKey.recoverWalletSignerPublicKey().walletAddress
