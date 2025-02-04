package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiMessageDisappearingSettings

class MessageExpiration(
    private val libXMTPMessageExpiration: FfiMessageDisappearingSettings,
) {

    val expirationStartAtNs: Long
        get() = libXMTPMessageExpiration.fromNs

    val expirationDurationInNs: Long
        get() = libXMTPMessageExpiration.inNs

}

