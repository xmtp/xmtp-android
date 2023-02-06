package org.xmtp.android.library.codecs

import com.google.protobuf.kotlin.toByteString
import org.bouncycastle.asn1.cms.CMSAttributes.contentType
import org.xmtp.android.library.Client
import org.xmtp.android.library.EncodedContentCompression
import org.xmtp.proto.message.contents.Content

typealias EncodedContent = org.xmtp.proto.message.contents.Content.EncodedContent

fun <T> EncodedContent.decoded(): T? {
    val codec = Client().codecRegistry.find(type)
    var encodedContent = this
    if (hasCompression()) {
        encodedContent = decompressContent()
    }
    return codec.decode(content = encodedContent) as? T
}

fun EncodedContent.compress(compression: EncodedContentCompression): EncodedContent {
    val copy = this.toBuilder()
    when (compression) {
        EncodedContentCompression.deflate -> {
            copy.also {
                it.compression = Content.Compression.COMPRESSION_DEFLATE
            }
        }
        EncodedContentCompression.gzip -> {
            copy.also {
                it.compression = Content.Compression.COMPRESSION_GZIP
            }
        }
    }
    copy.also {
        it.content = compression.compress(content.toByteArray()).toByteString()
    }
    return copy.build()
}

fun EncodedContent.decompressContent(): EncodedContent {
    if (!hasCompression()) {
        return this
    }
    var copy = this
    when (compression) {
        Content.Compression.COMPRESSION_DEFLATE -> {
            copy = copy.toBuilder().also {
                it.content =
                    EncodedContentCompression.deflate.decompress(content = content.toByteArray())
                        .toByteString()
            }.build()
        }
        Content.Compression.COMPRESSION_GZIP -> {
            copy = copy.toBuilder().also {
                it.content =
                    EncodedContentCompression.gzip.decompress(content = content.toByteArray())
                        .toByteString()
            }.build()
        }
        else -> return copy
    }
    return copy
}

interface ContentCodec<T> {
    val contentType: ContentTypeId
    fun encode(content: T): EncodedContent
    fun decode(content: EncodedContent): T
}

val id: String
    get() = contentType.id

//public fun ContentCodec.Companion.==(lhs: Self, rhs: Self) : Boolean =
//    lhs.contentType.authorityID == rhs.contentType.authorityID && lhs.contentType.typeID == rhs.contentType.typeID
//
//public fun ContentCodec.hash(hasher: inout Hasher) {
//    hasher.combine(id)
//}
