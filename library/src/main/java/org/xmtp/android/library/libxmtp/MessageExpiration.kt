package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiMessageDisappearingSettings

class MessageExpiration(
    val expirationStartAtNs: Long,
    val expirationDurationInNs: Long,
)
{
    companion object {
        fun createFromFfi(ffiSettings: FfiMessageDisappearingSettings): MessageExpiration {
            return MessageExpiration(ffiSettings.fromNs, ffiSettings.inNs)
        }
    }
}

