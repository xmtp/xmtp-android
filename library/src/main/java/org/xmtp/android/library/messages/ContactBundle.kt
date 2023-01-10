package org.xmtp.android.library.messages

import org.web3j.crypto.Keys

typealias ContactBundle = org.xmtp.proto.message.contents.Contact.ContactBundle
typealias ContactBundleV1 = org.xmtp.proto.message.contents.Contact.ContactBundleV1
typealias ContactBundleV2 = org.xmtp.proto.message.contents.Contact.ContactBundleV2

fun ContactBundle.from(envelope: Envelope) : ContactBundle {
    val data = envelope.message
    var contactBundle = ContactBundle()
    // Try to deserialize legacy v1 bundle
    val publicKeyBundle = PublicKeyBundle(serializedData = data)
    contactBundle.v1.keyBundle = publicKeyBundle
    // It's not a legacy bundle so just deserialize as a ContactBundle
    if (contactBundle.v1.keyBundle.identityKey.secp256K1Uncompressed.bytes.isEmpty()) {
        contactBundle.merge(serializedData = data)
    }
    return contactBundle
}

fun ContactBundle.toPublicKeyBundle() : PublicKeyBundle {
    when (version) {
        v1 -> return v1.keyBundle
        v2 -> return PublicKeyBundle(v2.keyBundle)
        else -> throw ContactBundleError.invalidVersion
    }
}

fun ContactBundle.toSignedPublicKeyBundle() : SignedPublicKeyBundle {
    when (version) {
        v1 -> return SignedPublicKeyBundle(v1.keyBundle)
        v2 -> return v2.keyBundle
        none -> throw ContactBundleError.invalidVersion
    }
}
// swiftlint:disable no_optional_try
val ContactBundle.walletAddress: String?
    get() {
        when (version) {
            v1 -> {
                val key = try { v1.keyBundle.identityKey.recoverWalletSignerPublicKey() } catch (e: Throwable) { null }
                if (key != null) {
                    return Keys.toChecksumAddress(Keys.getAddress(key.secp256K1Uncompressed.bytes))
                }
                return null
            }
            v2 -> {
                val key = try { v2.keyBundle.identityKey.recoverWalletSignerPublicKey() } catch (e: Throwable) { null }
                if (key != null) {
                    return Keys.toChecksumAddress(Keys.getAddress(key.secp256K1Uncompressed.bytes))
                }
                return null
            }
            none -> return null
        }
    }
val ContactBundle.identityAddress: String?
    get() {
        when (version) {
            v1 -> return v1.keyBundle.identityKey.walletAddress
            v2 -> {
                val publicKey = try { PublicKey(v2.keyBundle.identityKey) } catch (e: Throwable) { null }
                return publicKey?.walletAddress
            }
            none -> return null
        }
    }
// swiftlint:enable no_optional_try
