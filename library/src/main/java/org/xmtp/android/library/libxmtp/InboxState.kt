package org.xmtp.android.library.libxmtp

import org.xmtp.android.library.InboxId
import uniffi.xmtpv3.FfiInboxState

class InboxState(private val ffiInboxState: FfiInboxState) {
    val inboxId: InboxId
        get() = InboxId(ffiInboxState.inboxId)
    val identities: List<Identity>
        get() = ffiInboxState.accountIdentities.map { Identity(it) }

    val installations: List<Installation>
        get() = ffiInboxState.installations.map { Installation(it) }

    val recoveryIdentity: Identity
        get() = Identity(ffiInboxState.recoveryIdentity)
}
