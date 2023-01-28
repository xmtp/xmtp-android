package org.xmtp.android.library.messages

public typealias Message = org.xmtp.proto.message.contents.MessageOuterClass.Message
public enum class MessageVersion (val rawValue: String) : RawRepresentable {
    v1("v1"), v2("v2");

    companion object {
        operator fun invoke(rawValue: String) = MessageVersion.values().firstOrNull { it.rawValue == rawValue }
    }
}

constructor(Message.v1: MessageV1) : this() {    this.v1 = v1
}

constructor(Message.v2: MessageV2) : this() {    this.v2 = v2
}
