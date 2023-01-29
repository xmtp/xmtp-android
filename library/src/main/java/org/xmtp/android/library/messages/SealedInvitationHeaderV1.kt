package org.xmtp.android.library.messages

import org.bouncycastle.util.encoders.Encoder

typealias SealedInvitationHeaderV1 = org.xmtp.proto.message.contents.Invitation.SealedInvitationHeaderV1

class SealedInvitationHeaderV1Builder {
    companion object {
        fun buildFromSignedPublicBundle(sender: SignedPublicKeyBundle, recipient: SignedPublicKeyBundle, createdNs: Long) : SealedInvitationHeaderV1 {
            return SealedInvitationHeaderV1.newBuilder().also {
                it.sender = sender
                it.recipient = recipient
                it.createdNs = createdNs
            }.build()
        }
    }
}

enum class CodingKeys(val rawValue: Int) {
    sender(0), recipient(1), createdNs(2);

    companion object {
        operator fun invoke(rawValue: Int) = CodingKeys.values().firstOrNull { it.rawValue == rawValue }
    }
}

fun SealedInvitationHeaderV1.encode(encoder: Encoder) {
    var container = encoder.container(keyedBy = CodingKeys())
    container.encode(sender, forKey = sender)
    container.encode(recipient, forKey = recipient)
    container.encode(createdNs, forKey = createdNs)
}


