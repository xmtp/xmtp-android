package org.xmtp.android.library

import org.junit.*
import org.junit.Assert.*
import org.xmtp.android.library.codecs.CompositeCodec
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.ContentTypeID
import org.xmtp.android.library.codecs.DecodedComposite
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.TextCodec

data class NumberCodec: ContentCodec {
    typealias T = Double
    val contentType: XMTP.ContentTypeID
        get() = ContentTypeID(authorityID = "example.com", typeID = "number", versionMajor = 1, versionMinor = 1)

    fun encode(content: Double) : XMTP.EncodedContent {
        var encodedContent = EncodedContent()
        encodedContent.type = ContentTypeID(authorityID = "example.com", typeID = "number", versionMajor = 1, versionMinor = 1)
        encodedContent.content = JSONEncoder().encode(content)
        return encodedContent
    }

    fun decode(content: XMTP.EncodedContent) : Double =
        JSONDecoder().decode(Double.self, from = content.content)
}

class CodecTest {

    @Test
    fun testCanRoundTripWithCustomContentType() {
        Client.register(codec = NumberCodec())
        val fixtures = fixtures()
        val aliceClient = fixtures.aliceClient!!
        val aliceConversation = aliceClient.conversations.newConversation(with = fixtures.bob.address)
        aliceConversation.send(content = 3.14, options = .init(contentType = NumberCodec().contentType))
        val messages = aliceConversation.messages()
        assertEquals(messages.size, 1)
        if (messages.size == 1) {
            val content: Double = messages[0].content()
            assertEquals(3.14, content)
        }
    }

    @Test
    fun testFallsBackToFallbackContentWhenCannotDecode() {
        val fixtures = fixtures()
        val aliceClient = fixtures.aliceClient!!
        val aliceConversation = aliceClient.conversations.newConversation(with = fixtures.bob.address)
        aliceConversation.send(content = 3.14, options = .init(contentType = NumberCodec().contentType, contentFallback = "pi"))
        // Remove number codec from regis
        Client.codecRegis.codecs.removeValue(forKey = NumberCodec().id)
        val messages = aliceConversation.messages()
        assertEquals(messages.size, 1)
        val content: Double? = messages[0].content()
        assertEquals(null, content)
        assertEquals("pi", messages[0].fallbackContent)
    }

    @Test
    fun testCompositeCodecOnePart() {
        Client.register(codec = CompositeCodec())
        val fixtures = fixtures()
        val aliceClient = fixtures.aliceClient!!
        val aliceConversation = aliceClient.conversations.newConversation(with = fixtures.bob.address)
        val textContent = TextCodec().encode(content = "hiya")
        val source = DecodedComposite(encodedContent = textContent)
        aliceConversation.send(content = source, options = .init(contentType = CompositeCodec().contentType))
        val messages = aliceConversation.messages()
        val decoded: DecodedComposite = messages[0].content()
        assertEquals("hiya", decoded.content())
    }

    @Test
    fun testCompositeCodecCanHaveParts() {
        Client.register(codec = CompositeCodec())
        Client.register(codec = NumberCodec())
        val fixtures = fixtures()
        val aliceClient = fixtures.aliceClient!!
        val aliceConversation = aliceClient.conversations.newConversation(with = fixtures.bob.address)
        val textContent = TextCodec().encode(content = "sup")
        val numberContent = NumberCodec().encode(content = 3.14)
        val source = DecodedComposite(parts = listOf(DecodedComposite(encodedContent = textContent), DecodedComposite(parts = listOf(DecodedComposite(encodedContent = numberContent)))))
        aliceConversation.send(content = source, options = .init(contentType = CompositeCodec().contentType))
        val messages = aliceConversation.messages()
        val decoded: DecodedComposite = messages[0].content()
        val part1 = decoded.parts[0]
        val part2 = decoded.parts[1].parts[0]
        assertEquals("sup", part1.content())
        assertEquals(3.14, part2.content())
    }
}
