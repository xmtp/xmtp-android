package org.xmtp.android.library.libxmtp

import android.util.Log
import uniffi.xmtpv3.FfiLogger

class XMTPLogger : FfiLogger {
    override fun log(level: UInt, levelLabel: String, message: String) {
        Log.i("$level $levelLabel", message)
    }
}
