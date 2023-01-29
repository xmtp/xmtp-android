package org.xmtp.android.library.codecs

import com.google.protobuf.kotlin.toByteString
import org.xmtp.android.library.Client
import org.xmtp.android.library.EncodedContentCompression
import org.xmtp.proto.message.contents.Content

typealias EncodedContent = org.xmtp.proto.message.contents.Content.EncodedContent

fun <T> EncodedContent.decoded() : T? {
    val codec = Client.codecRegistry.find(for = type)
    var encodedContent = this
    if (hasCompression) {
        encodedContent = decompressContent()
    }
    val content = codec.decode(content = encodedContent) as? T
    return content
}

fun EncodedContent.compress(compression: EncodedContentCompression) : EncodedContent {
    val copy = this.toBuilder()
    when (compression) {
        EncodedContentCompression.deflate -> {
            copy.also {
                it.compression = deflate
            }
        }
        EncodedContentCompression.gzip -> {
            copy.also {
                it.compression = gzip
            }
        }
    }
    copy.also {
        it.content = compression.compress(content = content)
    }
    return copy.build()
}

fun EncodedContent.decompressContent() : EncodedContent {
    if (!hasCompression()) {
        return this
    }
    var copy = this
    when (compression) {
        Content.Compression.COMPRESSION_DEFLATE -> {
            copy = copy.toBuilder().also {
                it.content = EncodedContentCompression.deflate.decompress(content = content.toByteArray()).toByteString()
            }.build()
        }
        Content.Compression.COMPRESSION_GZIP -> {
            copy = copy.toBuilder().also {
                it.content = EncodedContentCompression.gzip.decompress(content = content.toByteArray()).toByteString()
            }.build()
        }
        else -> return copy
    }
    return copy
}

public interface ContentCodec: Hashable, Equatable {
    val contentType: ContentTypeId
    fun encode(content: String) : EncodedContent
    fun decode(content: EncodedContent) : String
}

public fun ContentCodec.Companion.==(lhs: Self, rhs: Self) : Boolean =
    lhs.contentType.authorityID == rhs.contentType.authorityID && lhs.contentType.typeID == rhs.contentType.typeID
public val ContentCodec.id: String
    get() = contentType.id

public fun ContentCodec.hash(hasher: inout Hasher) {
    hasher.combine(id)
}
