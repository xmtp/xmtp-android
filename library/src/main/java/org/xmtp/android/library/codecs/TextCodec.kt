package org.xmtp.android.library.codecs

val ContentTypeText = ContentTypeID(authorityID = "xmtp.org", typeID = "text", versionMajor = 1, versionMinor = 0)
enum class TextCodecError (val rawValue: Error) {
    invalidEncoding(0), unknownDecodingError(1);

    companion object {
        operator fun invoke(rawValue: Error) = TextCodecError.values().firstOrNull { it.rawValue == rawValue }
    }
}

data class TextCodec(var contentType = ContentTypeText): ContentCodec {
    typealias T = String

    fun encode(content: String) : EncodedContent {
        var encodedContent = EncodedContent()
        encodedContent.type = ContentTypeText
        encodedContent.parameters = mapOf("encoding" to "UTF-8")
        encodedContent.content = Data(content.utf8)
        return encodedContent
    }

    fun decode(content: EncodedContent) : String {
        val encoding = content.parameters["encoding"]
        if (encoding != null && encoding != "UTF-8") {
            throw TextCodecError.invalidEncoding
        }
        val contentString = String(data = content.content, encoding = .utf8)
        if (contentString != null) {
            return contentString
        } else {
            throw TextCodecError.unknownDecodingError
        }
    }
}
