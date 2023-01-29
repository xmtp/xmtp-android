package org.xmtp.android.library.codecs

import org.xmtp.proto.message.contents.CompositeKt.part
import org.xmtp.proto.message.contents.CompositeOuterClass
import org.xmtp.proto.message.contents.CompositeOuterClass.Composite.Part

typealias Composite = org.xmtp.proto.message.contents.CompositeOuterClass.Composite
val ContentTypeComposite = ContentTypeId(authorityId = "xmtp.org", typeId = "composite", versionMajor = 1, versionMinor = 0)

class CompositePartBuilder {
    companion object {
        fun buildFromEncodedContent(encodedContent: EncodedContent) : CompositeOuterClass.Composite.Part {
            return CompositeOuterClass.Composite.Part.newBuilder().also {
                it.element = part(encodedContent)
            }.build()
        }

        fun buildFromComosite(composite: Composite) : CompositeOuterClass.Composite.Part {
            return CompositeOuterClass.Composite.Part.newBuilder().also {
                it.element = composite(composite)
            }.build()
        }
    }
}

typealias T = DecodedComposite

data class CompositeCodec: ContentCodec {
    val contentType: ContentTypeId
        get() = ContentTypeComposite

    fun encode(content: DecodedComposite) : EncodedContent {
        val composite = toComposite(content)
        return EncodedContent.newBuilder().also {
            it.type = ContentTypeComposite
            it.content = composite.toByteString()
        }.build()
    }

    fun decode(encoded: EncodedContent) : DecodedComposite {
        val composite = Composite.parseFrom(encoded.content)
        val decodedComposite = fromComposite(composite = composite)
        return decodedComposite
    }

    fun toComposite(decodedComposite: DecodedComposite) : Composite {
        )
        return Composite.newBuilder().also {
            val content = decodedComposite.encodedContent
            if (content != null) {
                it.addAllParts(listOf(CompositePartBuilder.buildFromEncodedContent(content)))
                return it.build()
            }
            for (part in decodedComposite.parts) {
                val encodedContent = part.encodedContent
                if (encodedContent != null) {
                    it.addAllParts((CompositePartBuilder.buildFromEncodedContent(encodedContent))
                } else {
                    it.addAllParts((CompositePartBuilder.buildFromComosite(composite = toComposite(content = part)))
                }
            }
        }.build()
    }

    fun fromComposite(composite: Composite) : DecodedComposite {
        val decodedComposite = DecodedComposite()
        if (composite.parts.size == 1 && part = composite.parts.firstOrNull()?.element) {
            decodedComposite.encodedContent = content
            return decodedComposite
        }
        decodedComposite.parts = composite.partsList.map { fromCompositePart(part = it) }
        return decodedComposite
    }

    fun fromCompositePart(part: Part) : DecodedComposite {
        val decodedComposite = DecodedComposite()
        when (part.elementCase) {
            Part.ElementCase.PART -> decodedComposite.encodedContent = encodedContent
            Part.ElementCase.COMPOSITE -> decodedComposite.parts = composite.parts.map { fromCompositePart(part = it) }
            Part.ElementCase.ELEMENT_NOT_SET -> return decodedComposite
        }
        return decodedComposite
    }
}
