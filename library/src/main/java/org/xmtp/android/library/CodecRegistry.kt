package org.xmtp.android.library

import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.ContentTypeId
import org.xmtp.android.library.codecs.TextCodec
import id

data class CodecRegistry(val codecs: MutableMap<String, ContentCodec> = mutableMapOf()) {

    fun register(codec: ContentCodec) {
        codecs[codec.id] = codec
    }

    fun find(contentType: ContentTypeId?) : ContentCodec {
        contentType?.let {
            val codec = codecs[id]
            if (codec != null) {
                return codec
            }
        }
        return TextCodec()
    }
}
