package uniffi.xmtp_dh.org.xmtp.android.library.libxmtp

import android.util.Log
import uniffi.xmtp_dh.FfiLogger

class XMTPLogger : FfiLogger {
    override fun log(level: UInt, levelLabel: String, message: String) {
        Log.i("$level $levelLabel", message)
    }
}
