package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiGroupMember
import uniffi.xmtpv3.FfiPermissionLevel

class Member(private val ffiMember: FfiGroupMember) {
    val inboxId: String
        get() = ffiMember.inboxId
    val addresses: List<String>
        get() = ffiMember.accountAddresses
    val permissionLevel: FfiPermissionLevel
        get() = ffiMember.permissionLevel
}
