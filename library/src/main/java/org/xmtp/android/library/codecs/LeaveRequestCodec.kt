package org.xmtp.android.library.codecs

import com.google.protobuf.ByteString

/**
 * Content type identifier for leave request messages.
 *
 * Authority: xmtp.org
 * Type: leave_request
 * Version: 1.0
 */
val ContentTypeLeaveRequest =
    ContentTypeIdBuilder.builderFromAuthorityId(
        "xmtp.org",
        "leave_request",
        versionMajor = 1,
        versionMinor = 0,
    )

/**
 * Marker object representing a leave request in a group conversation.
 *
 * When sent, this signals the sender's intent to leave the group.
 * The leave request is processed by group admins who can then remove the member.
 */
object LeaveRequest

/**
 * Codec for encoding/decoding leave request messages.
 *
 * Leave requests are marker messages with no content payload. They signal
 * a member's intent to leave a group and trigger automated removal by admins.
 *
 * **Characteristics:**
 * - Does not trigger push notifications (shouldPush = false)
 * - No fallback representation needed
 * - Empty content payload (no data beyond the content type)
 * - Content type: xmtp.org/leave_request:1.0
 */
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
