package org.xmtp.android.library.frames

import ProxyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmtp.android.library.frames.Constants.OPEN_FRAMES_PROXY_URL

class OpenFramesProxy(private val inner: ProxyClient = ProxyClient(OPEN_FRAMES_PROXY_URL)) {

    suspend fun readMetadata(url: String): GetMetadataResponse = withContext(Dispatchers.IO) {
        inner.readMetadata(url)
    }

    suspend fun post(url: String, payload: FramePostPayload): GetMetadataResponse = withContext(Dispatchers.IO) {
        inner.post(url, payload)
    }

    suspend fun postRedirect(url: String, payload: FramePostPayload): FramesApiRedirectResponse = withContext(Dispatchers.IO) {
        inner.postRedirect(url, payload)
    }

    suspend fun mediaUrl(url: String): String = withContext(Dispatchers.IO) {
        if (url.startsWith("data:")) {
            url
        } else {
            inner.mediaUrl(url)
        }
    }
}
