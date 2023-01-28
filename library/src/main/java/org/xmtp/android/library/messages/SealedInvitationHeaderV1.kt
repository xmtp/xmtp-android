package org.xmtp.android.library.messages

typealias SealedInvitationHeaderV1 = org.xmtp.proto.message.contents.Invitation.SealedInvitation

constructor(SealedInvitationHeaderV1.sender: SignedPublicKeyBundle, recipient: SignedPublicKeyBundle, createdNs: UInt64) : this() {    this.sender = sender
    this.recipient = recipient
    this.createdNs = createdNs
}
enum class SealedInvitationHeaderV1.CodingKeys (val rawValue: CodingKey) {
    sender(0), recipient(1), createdNs(2);

    companion object {
        operator fun invoke(rawValue: CodingKey) = CodingKeys.values().firstOrNull { it.rawValue == rawValue }
    }
}

public fun SealedInvitationHeaderV1.encode(encoder: Encoder) {
    var container = encoder.container(keyedBy = CodingKeys.self)
    container.encode(sender, forKey = .sender)
    container.encode(recipient, forKey = .recipient)
    container.encode(createdNs, forKey = .createdNs)
}

public constructor(SealedInvitationHeaderV1.decoder: Decoder) : this() {    val container = decoder.container(keyedBy = CodingKeys.self)
    sender = container.decode(SignedPublicKeyBundle.self, forKey = .sender)
    recipient = container.decode(SignedPublicKeyBundle.self, forKey = .recipient)
    createdNs = container.decode(UInt64.self, forKey = .createdNs)
}
