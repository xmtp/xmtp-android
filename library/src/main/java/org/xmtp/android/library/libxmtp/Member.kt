package uniffi.xmtpv3.org.xmtp.android.library.libxmtp

import org.xmtp.android.library.Client
import uniffi.xmtpv3.FfiGroupMember

class Member(private val ffiMember: FfiGroupMember) {

    val inboxId: String
        get() = ffiMember.inboxId
    val addresses: List<String>
        get() = ffiMember.accountAddresses
}