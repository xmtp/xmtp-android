package org.xmtp.android.library.messages

import org.web3j.crypto.Hash
import org.xmtp.android.library.SigningKey
import org.xmtp.android.library.createIdentity

typealias PrivateKeyBundleV1 = org.xmtp.proto.message.contents.PrivateKeyOuterClass.PrivateKeyBundleV1

fun PrivateKeyBundleV1.generate(wallet: SigningKey) : PrivateKeyBundleV1 {
    val privateKey = PrivateKeyFactory()
    privateKey.setPrivateKey(PrivateKey.newBuilder().build().generate())
    val authorizedIdentity = wallet.createIdentity(privateKey.getPrivateKey())
    val bundle = authorizedIdentity.toBundle
    val preKey = PrivateKey.newBuilder().build().generate()
    val bytesToSign = UnsignedPublicKeyFactory.create(preKey.publicKey).toByteArray()
    val signature = privateKey.sign(Hash.sha256(bytesToSign))
    val builder = bundle.toBuilder()
    builder.v1Builder.identityKey = authorizedIdentity.identity
    builder.v1Builder.identityKeyBuilder.publicKey = authorizedIdentity.authorized
    val preKeyBuilder = preKey.toBuilder()
    preKeyBuilder.publicKeyBuilder.signature = signature
    preKeyBuilder.build()
    val signedPublicKey = privateKey.getPrivateKey().sign(key = UnsignedPublicKeyFactory.create(preKey.publicKey))
    preKeyBuilder.publicKey = PublicKey.parseFrom(signedPublicKey.keyBytes)
    preKeyBuilder.publicKeyBuilder.signature = signedPublicKey.signature
    builder.v1Builder.addPreKeys(preKey)
    return builder.build().v1
}