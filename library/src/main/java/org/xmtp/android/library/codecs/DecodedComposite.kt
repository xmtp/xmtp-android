package org.xmtp.android.library.codecs

public data class DecodedComposite(
    var parts: List<DecodedComposite> = listOf(),
    var encodedContent: EncodedContent? = null) {

    constructor(parts: List<DecodedComposite> = listOf(), encodedContent: EncodedContent? = null) {
        this.parts = parts
        this.encodedContent = encodedContent
    }

    fun <T> content() : T? =
        encodedContent?.decoded()
}

