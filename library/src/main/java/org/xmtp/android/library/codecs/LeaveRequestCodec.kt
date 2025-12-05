package org.xmtp.android.library.codecs

import com.google.protobuf.ByteString

val ContentTypeLeaveRequest =
    ContentTypeIdBuilder.builderFromAuthorityId(
        "xmtp.org",
        "leave_request",
        versionMajor = 1,
        versionMinor = 0,
    )

object LeaveRequest

data class LeaveRequestCodec(
    override var contentType: ContentTypeId = ContentTypeLeaveRequest,
) : ContentCodec<LeaveRequest> {
    override fun encode(content: LeaveRequest): EncodedContent =
        EncodedContent
            .newBuilder()
            .also {
                it.type = ContentTypeLeaveRequest
                it.content = ByteString.EMPTY
            }.build()

    override fun decode(content: EncodedContent): LeaveRequest = LeaveRequest

    override fun fallback(content: LeaveRequest): String? = null

    override fun shouldPush(content: LeaveRequest): Boolean = false
}
