package org.xmtp.android.library.messages

typealias MessageHeaderV1 = org.xmtp.proto.message.contents.MessageOuterClass.MessageHeaderV1

constructor(MessageHeaderV1.sender: PublicKeyBundle, recipient: PublicKeyBundle, timestamp: UInt64) : this() {
    this.sender = sender
    this.recipient = recipient
    this.timestamp = timestamp
}