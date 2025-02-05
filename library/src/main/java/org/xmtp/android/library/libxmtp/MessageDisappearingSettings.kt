package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiMessageDisappearingSettings

class MessageDisappearingSettings(
    val disappearStartingAtNs: Long,
    val disappearDurationInNs: Long,
)
{
    companion object {
        fun createFromFfi(ffiSettings: FfiMessageDisappearingSettings): MessageDisappearingSettings {
            return MessageDisappearingSettings(ffiSettings.fromNs, ffiSettings.inNs)
        }
    }
}

