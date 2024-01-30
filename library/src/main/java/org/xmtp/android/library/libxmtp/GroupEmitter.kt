package uniffi.xmtpv3.org.xmtp.android.library.libxmtp

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import uniffi.xmtpv3.FfiConversationCallback
import uniffi.xmtpv3.FfiGroup

class GroupEmitter {
    private val _groups = MutableSharedFlow<FfiGroup>()
    val groups = _groups.asSharedFlow()

    val callback: FfiConversationCallback = object : FfiConversationCallback {
        override fun onConversation(conversation: FfiGroup) {
            _groups.tryEmit(conversation)
        }
    }
}
