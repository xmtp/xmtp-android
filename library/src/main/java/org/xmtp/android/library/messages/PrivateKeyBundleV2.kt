package org.xmtp.android.library.messages

public typealias PrivateKeyBundleV2 = org.xmtp.proto.message.contents.PrivateKeyOuterClass.PrivateKeyBundleV2

fun PrivateKeyBundleV2.sharedSecret(
    peer: SignedPublicKeyBundle,
    myPreKey: SignedPublicKey,
    isRecipient: Boolean
): ByteArray {
    val dh1: ByteArray
    val dh2: ByteArray
    val preKey: SignedPrivateKey
    if (isRecipient) {
        preKey = findPreKey(myPreKey)
        dh1 = this.sharedSecret(
            preKey.secp256K1.bytes.toByteArray(),
            peer.identityKey.secp256K1Uncompressed.bytes.toByteArray()
        )
        dh2 = this.sharedSecret(
            identityKey.secp256K1.bytes.toByteArray(),
            peer.preKey.secp256K1Uncompressed.bytes.toByteArray()
        )
    } else {
        preKey = findPreKey(myPreKey)
        dh1 = this.sharedSecret(
            identityKey.secp256K1.bytes.toByteArray(),
            peer.preKey.secp256K1Uncompressed.bytes.toByteArray()
        )
        dh2 = this.sharedSecret(
            preKey.secp256K1.bytes.toByteArray(),
            peer.identityKey.secp256K1Uncompressed.bytes.toByteArray()
        )
    }
    val dh3 = this.sharedSecret(
        preKey.secp256K1.bytes.toByteArray(),
        peer.preKey.secp256K1Uncompressed.bytes.toByteArray()
    )
    return dh1 + dh2 + dh3
}

fun PrivateKeyBundleV2.sharedSecret(privateData: ByteArray, publicData: ByteArray): ByteArray {
    val publicKey = PublicKey.parseFrom(publicData)
    return publicKey.secp256K1Uncompressed.bytes.toByteArray().plus(privateData)
}

fun PrivateKeyBundleV2.findPreKey(myPreKey: SignedPublicKey): SignedPrivateKey {
    for (preKey in preKeysList) {
        if (preKey.matches(myPreKey)) {
            return preKey
        }
    }
    throw IllegalArgumentException("No Pre key set")
}

fun PrivateKeyBundleV2.toV1(): PrivateKeyBundleV1 {
    return PrivateKeyBundleV1.newBuilder().apply {
        identityKey = PrivateKeyBuilder(identityKey).getPrivateKey()
        addAllPreKeys(preKeysList.map { PrivateKeyBuilder(it).getPrivateKey() })
    }.build()
}

fun PrivateKeyBundleV2.getPublicKeyBundle(): SignedPublicKeyBundle {
    return SignedPublicKeyBundle.newBuilder().apply {
        this.identityKey = identityKey
        this.identityKeyBuilder.signature = identityKey.signature
        identityKey.signature.ensureWalletSignature()
        preKey = preKeysList[0].publicKey
    }.build()
}
