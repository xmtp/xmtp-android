package uniffi.xmtpv3.org.xmtp.android.library.libxmtp

import org.xmtp.android.library.InboxId
import org.xmtp.android.library.toHex
import uniffi.xmtpv3.FfiUpdateGroupMembershipResult

class GroupMembershipResult(private val ffiUpdateGroupMembershipResult: FfiUpdateGroupMembershipResult) {
    val addedMembers: Map<InboxId, ULong>
        get() = ffiUpdateGroupMembershipResult.addedMembers
    val removedMembers: List<InboxId>
        get() = ffiUpdateGroupMembershipResult.removedMembers
    val failedInstallationIds: List<String>
        get() = ffiUpdateGroupMembershipResult.failedInstallations.map { it.toHex() }

}
