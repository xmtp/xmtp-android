package uniffi.xmtpv3.org.xmtp.android.library.codecs

import com.google.gson.GsonBuilder
import com.google.protobuf.kotlin.toByteStringUtf8
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.ContentTypeId
import org.xmtp.android.library.codecs.ContentTypeIdBuilder
import org.xmtp.android.library.codecs.EncodedContent

val ContentTypeGroupMembershipChange = ContentTypeIdBuilder.builderFromAuthorityId(
    "xmtp.org",
    "group_membership_change",
    versionMajor = 1,
    versionMinor = 0
)

data class GroupMembershipChanges(
    val membersAdded: List<MembershipChange>,
    val membersRemoved: List<MembershipChange>,
    val installationsAdded: List<MembershipChange>,
    val installationsRemoved: List<MembershipChange>,
)

data class MembershipChange(
    val installationIds: List<ByteArray>,
    val accountAddress: String,
    val initiatedByAccountAddress: String,
)

data class GroupMembershipChangeCodec(override var contentType: ContentTypeId = ContentTypeGroupMembershipChange) :
    ContentCodec<GroupMembershipChanges> {

    override fun encode(content: GroupMembershipChanges): EncodedContent {
        val gson = GsonBuilder().create()
        return EncodedContent.newBuilder().also {
            it.type = ContentTypeGroupMembershipChange
            it.content = gson.toJson(content).toByteStringUtf8()
        }.build()
    }

    override fun decode(content: EncodedContent): GroupMembershipChanges {
        val gson = GsonBuilder().create()
        return gson.fromJson(content.content.toStringUtf8(), GroupMembershipChanges::class.java)
    }

    override fun fallback(content: GroupMembershipChanges): String? {
        return null
    }
}