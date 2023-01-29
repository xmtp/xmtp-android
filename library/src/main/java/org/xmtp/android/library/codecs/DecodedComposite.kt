package org.xmtp.android.library.codecs

data class DecodedComposite(
    var parts: List<DecodedComposite> = listOf(),
    var encodedContent: EncodedContent? = null) {

    fun <String> content() : String? =
        encodedContent?.decoded()
}

