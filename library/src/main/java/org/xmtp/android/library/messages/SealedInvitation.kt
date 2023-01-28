package org.xmtp.android.library.messages

import java.util.Date

typealias SealedInvitation = org.xmtp.proto.message.contents.Invitation.SealedInvitation
enum class SealedInvitationError (val rawValue: Error) {
    noSignature(0);

    companion object {
        operator fun invoke(rawValue: Error) = SealedInvitationError.values().firstOrNull { it.rawValue == rawValue }
    }
}

fun SealedInvitation.Companion.createV1(sender: PrivateKeyBundleV2, recipient: SignedPublicKeyBundle, created: Date, invitation: InvitationV1) : SealedInvitation {
    val header = SealedInvitationHeaderV1(sender = sender.getPublicKeyBundle(), recipient = recipient, createdNs = UInt64(created.millisecondsSinceEpoch * 1_000_000))
    val secret = sender.sharedSecret(peer = recipient, myPreKey = sender.preKeys[0].publicKey, isRecipient = false)
    val headerBytes = header.serializedData()
    val invitationBytes = invitation.serializedData()
    val ciphertext = Crypto.encrypt(secret, invitationBytes, additionalData = headerBytes)
    return SealedInvitation(headerBytes = headerBytes, ciphertext = ciphertext)
}

constructor(SealedInvitation.headerBytes: Data, ciphertext: CipherText) : this() {    v1.headerBytes = headerBytes
    v1.ciphertext = ciphertext
}

fun SealedInvitation.involves(contact: ContactBundle) : Boolean =
    do {
        val contactSignedPublicKeyBundle = contact.toSignedPublicKeyBundle()
        return v1.header.recipient.equals(contactSignedPublicKeyBundle) || v1.header.sender.equals(contactSignedPublicKeyBundle)
    } catch {
        return false
    }
