package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiInboxState

class InboxState(private val ffiInboxState: FfiInboxState) {
    val inboxId: String
        get() = ffiInboxState.inboxId
    val identities: List<Identity>
        get() = ffiInboxState.accountIdentities.map { Identity(it, null) }

    val installations: List<Installation>
        get() = ffiInboxState.installations.map { Installation(it) }

    val recoveryIdentity: Identity
        get() = Identity(ffiInboxState.recoveryIdentity, null)
}
