package org.xmtp.android.library.codecs

import com.google.protobuf.ByteString

/**
 * Represents a leave request message sent when a user wants to leave a group.
 *
 * @property authenticatedNote Optional authenticated note for the leave request
 */
data class LeaveRequest(
    val authenticatedNote: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LeaveRequest

        return authenticatedNote.contentEquals(other.authenticatedNote)
    }

    override fun hashCode(): Int = authenticatedNote?.contentHashCode() ?: 0
}

val ContentTypeLeaveRequest =
    ContentTypeIdBuilder.builderFromAuthorityId(
        "xmtp.org",
        "leave_request",
        versionMajor = 1,
        versionMinor = 0,
    )

data class LeaveRequestCodec(
    override var contentType: ContentTypeId = ContentTypeLeaveRequest,
) : ContentCodec<LeaveRequest> {
    override fun encode(content: LeaveRequest): EncodedContent =
        EncodedContent
            .newBuilder()
            .also {
                it.type = ContentTypeLeaveRequest
                content.authenticatedNote?.let { note ->
                    it.content = ByteString.copyFrom(note)
                }
            }.build()

    override fun decode(content: EncodedContent): LeaveRequest =
        LeaveRequest(
            authenticatedNote = if (content.content.isEmpty) null else content.content.toByteArray(),
        )

    override fun fallback(content: LeaveRequest): String? = "A member has requested to leave the group"

    override fun shouldPush(content: LeaveRequest): Boolean = false
}
