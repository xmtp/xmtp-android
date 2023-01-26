package org.xmtp.android.library.messages

import org.web3j.crypto.Hash
import org.xmtp.android.library.SigningKey
import org.xmtp.android.library.createIdentity

typealias PrivateKeyBundleV1 = org.xmtp.proto.message.contents.PrivateKeyOuterClass.PrivateKeyBundleV1

fun PrivateKeyBundleV1.generate(wallet: SigningKey): PrivateKeyBundleV1 {
    val privateKey = PrivateKeyBuilder()
    privateKey.setPrivateKey(PrivateKey.newBuilder().build().generate())
    val authorizedIdentity = wallet.createIdentity(privateKey.getPrivateKey())
    var bundle = authorizedIdentity.toBundle
    var preKey = PrivateKey.newBuilder().build().generate()
    val bytesToSign = UnsignedPublicKeyBuilder.buildFromPublicKey(preKey.publicKey).toByteArray()
    val signature = privateKey.sign(Hash.sha256(bytesToSign))

    preKey = preKey.toBuilder().apply {
        publicKeyBuilder.signature = signature
    }.build()

    val signedPublicKey = privateKey.getPrivateKey()
        .sign(key = UnsignedPublicKeyBuilder.buildFromPublicKey(preKey.publicKey))

    preKey = preKey.toBuilder().apply {
        publicKey = PublicKey.parseFrom(signedPublicKey.keyBytes)
        publicKeyBuilder.signature = signedPublicKey.signature
    }.build()

    bundle = bundle.toBuilder().apply {
        v1Builder.apply {
            identityKey = authorizedIdentity.identity
            identityKeyBuilder.publicKey = authorizedIdentity.authorized
            addPreKeys(preKey)
        }.build()
    }.build()

    return bundle.v1
}

fun PrivateKeyBundleV1.toV2(): PrivateKeyBundleV2 {
    val privateKey = this.identityKey
    val privateKeyList = this.preKeysList
    return PrivateKeyBundleV2.newBuilder().apply {
        this.identityKey =
            SignedPrivateKeyBuilder.buildFromLegacy(privateKey, signedByWallet = false)
        this.addAllPreKeys(privateKeyList.map { SignedPrivateKeyBuilder.buildFromLegacy(it) })
    }.build()
}

fun PrivateKeyBundleV1.toPublicKeyBundle(): PublicKeyBundle {
    val pubKey = this.identityKey.publicKey
    return PublicKeyBundle.newBuilder().apply {
        this.identityKey = pubKey
        this.preKey = preKeysList[0].publicKey
    }.build()
}

fun PrivateKeyBundleV1.sharedSecret(peer: PublicKeyBundle, myPreKey: PublicKey, isRecipient: Boolean) : ByteArray{
    val peerBundle = SignedPublicKeyBundleBuilder.buildFromKeyBundle(peer)
    val preKey = SignedPublicKeyBuilder.buildFromLegacy(myPreKey)
    return toV2().sharedSecret(peer = peerBundle, myPreKey = preKey, isRecipient = isRecipient)
}

