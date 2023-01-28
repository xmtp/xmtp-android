package org.xmtp.android.library.codecs

import org.xmtp.android.library.Client
import org.xmtp.android.library.EncodedContentCompression

enum class CodecError (val rawValue: String) : Error {
    invalidContent("invalidContent"), codecNotFound("codecNotFound");

    companion object {
        operator fun invoke(rawValue: String) = CodecError.values().firstOrNull { it.rawValue == rawValue }
    }
}
public typealias EncodedContent = org.xmtp.proto.message.contents.Content.EncodedContent

fun <EncodedContent.T> decoded() : T {
    val codec = Client.codecRegistry.find(for = type)
    var encodedContent = this
    if (hasCompression) {
        encodedContent = decompressContent()
    }
    val content = codec.decode(content = encodedContent) as? T
    if (content != null) {
        return content
    }
    throw CodecError.invalidContent
}

fun EncodedContent.compress(compression: EncodedContentCompression) : EncodedContent {
    var copy = this
    when (compression) {
        deflate -> copy.compression = .deflate
                gzip -> copy.compression = .gzip
    }
    copy.content = compression.compress(content = content)
    return copy
}

fun EncodedContent.decompressContent() : EncodedContent {
    if (!hasCompression) {
        return this
    }
    var copy = this
    when (compression) {
        gzip -> copy.content = EncodedContentCompression.gzip.decompress(content = content)
        deflate -> copy.content = EncodedContentCompression.deflate.decompress(content = content)
        else -> return copy
    }
    return copy
}

public interface ContentCodec: Hashable, Equatable {
    associatedtype T
    val contentType: ContentTypeID
    fun encode(content: T) : EncodedContent
    fun decode(content: EncodedContent) : T
}

public fun ContentCodec.Companion.==(lhs: Self, rhs: Self) : Boolean =
    lhs.contentType.authorityID == rhs.contentType.authorityID && lhs.contentType.typeID == rhs.contentType.typeID
public val ContentCodec.id: String
    get() = contentType.id

public fun ContentCodec.hash(hasher: inout Hasher) {
    hasher.combine(id)
}
