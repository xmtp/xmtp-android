package org.xmtp.android.library.messages

import org.xmtp.proto.message.contents.PublicKeyOuterClass

typealias PublicKeyBundle = org.xmtp.proto.message.contents.PublicKeyOuterClass.PublicKeyBundle

fun PublicKeyBundle.from(signedPublicKeyBundle: SignedPublicKeyBundle) : PublicKeyBundle {
    return PublicKeyBundle.newBuilder().apply {
        identityKey = PublicKey.parseFrom(signedPublicKeyBundle.identityKey.keyBytes)
        preKey = PublicKey.parseFrom(signedPublicKeyBundle.preKey.keyBytes)
    }.build()
}
val PublicKeyBundle.walletAddress: String
    get() = // swiftlint:disable no_optional_try
        (try { identityKey.recoverWalletSignerPublicKey().walletAddress } catch (e: Throwable) { null }) ?: ""
