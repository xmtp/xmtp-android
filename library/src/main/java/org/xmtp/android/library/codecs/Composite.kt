package org.xmtp.android.library.codecs

typealias Composite = org.xmtp.proto.message.contents.CompositeOuterClass.Composite
val ContentTypeComposite = ContentTypeID(authorityID = "xmtp.org", typeID = "composite", versionMajor = 1, versionMinor = 0)

constructor(Composite.Part.encodedContent: EncodedContent) : this() {    element = .part(encodedContent)
}

constructor(Composite.Part.composite: Composite) : this() {    element = .composite(composite)
}

data class CompositeCodec: ContentCodec {
    public typealias T = DecodedComposite
    public val contentType: ContentTypeID
        get() = ContentTypeComposite

    public fun encode(content: DecodedComposite) : EncodedContent {
        val composite = toComposite(content = content)
        var encoded = EncodedContent()
        encoded.type = ContentTypeComposite
        encoded.content = composite.serializedData()
        return encoded
    }

    public fun decode(encoded: EncodedContent) : DecodedComposite {
        val composite = Composite(serializedData = encoded.content)
        val decodedComposite = fromComposite(composite = composite)
        return decodedComposite
    }

    fun toComposite(decodedComposite: DecodedComposite) : Composite {
        var composite = Composite()
        val content = decodedComposite.encodedContent
        if (content != null) {
            composite.parts = listOf(Composite.Part(encodedContent = content))
            return composite
        }
        for (part in decodedComposite.parts) {
            val encodedContent = part.encodedContent
            if (encodedContent != null) {
                composite.parts.append(Composite.Part(encodedContent = encodedContent))
            } else {
                composite.parts.append(Composite.Part(composite = toComposite(content = part)))
            }
        }
        return composite
    }

    fun fromComposite(composite: Composite) : DecodedComposite {
        var decodedComposite = DecodedComposite()
        if (composite.parts.size == 1 && case let is part = composite.parts.firstOrNull()?.element) {
            decodedComposite.encodedContent = content
            return decodedComposite
        }
        decodedComposite.parts = composite.parts.map { fromCompositePart(part = it) }
        return decodedComposite
    }

    fun fromCompositePart(part: Composite.Part) : DecodedComposite {
        var decodedComposite = DecodedComposite()
        when (part.element) {
            let is part -> decodedComposite.encodedContent = encodedContent
            let is composite -> decodedComposite.parts = composite.parts.map { fromCompositePart(part = it) }
            none -> return decodedComposite
        }
        return decodedComposite
    }
}
