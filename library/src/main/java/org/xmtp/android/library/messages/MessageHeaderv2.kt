package org.xmtp.android.library.messages

import org.xmtp.android.library.extensions.millisecondsSinceEpoch
import java.util.Date

typealias MessageHeaderV2 = org.xmtp.proto.message.contents.MessageOuterClass.MessageHeaderV2

class MessageHeaderV2Builder {
    companion object {
        fun buildFromTopic(topic: String, created: Date): MessageHeaderV2 {
            return MessageHeaderV2.newBuilder().also {
                it.topic = topic
                it.createdNs = (created.millisecondsSinceEpoch * 1_000_000).toLong()
            }.build()
        }
    }
}
