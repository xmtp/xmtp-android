package uniffi.xmtpv3.org.xmtp.android.library.codecs

import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.ContentTypeId
import org.xmtp.android.library.codecs.ContentTypeIdBuilder
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.proto.mls.message.contents.TranscriptMessages.GroupUpdated

typealias GroupMembershipChanges = org.xmtp.proto.mls.message.contents.TranscriptMessages.GroupUpdated

val ContentTypeGroupUpdated = ContentTypeIdBuilder.builderFromAuthorityId(
    "xmtp.org",
    "group_updated",
    versionMajor = 1,
    versionMinor = 0,
)

data class GroupUpdatedCodec(override var contentType: ContentTypeId = ContentTypeGroupUpdated) :
    ContentCodec<GroupUpdated> {

    override fun encode(content: GroupUpdated): EncodedContent {
        return EncodedContent.newBuilder().also {
            it.type = ContentTypeGroupUpdated
            it.content = content.toByteString()
        }.build()
    }

    override fun decode(content: EncodedContent): GroupUpdated {
        return GroupMembershipChanges.parseFrom(content.content)
    }

    override fun fallback(content: GroupUpdated): String? {
        return null
    }

    override fun shouldPush(content: GroupUpdated): Boolean = false
}
