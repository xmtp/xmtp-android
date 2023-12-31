package org.xmtp.android.library

import com.google.protobuf.kotlin.toByteStringUtf8
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.ContentTypeId
import org.xmtp.android.library.codecs.ContentTypeIdBuilder
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.messages.walletAddress

data class NumberPair(val a: Double, val b: Double, var result: Double = 0.0)

data class MultiplyNumberCodec(
    override var contentType: ContentTypeId = ContentTypeIdBuilder.builderFromAuthorityId(
        authorityId = "your.domain",
        typeId = "multiply-number",
        versionMajor = 1,
        versionMinor = 0
    )
) : ContentCodec<NumberPair> {
    override fun encode(content: NumberPair): EncodedContent {
        val jsonObject = JSONObject()
        jsonObject.put("a", content.a)
        jsonObject.put("b", content.b)
        return EncodedContent.newBuilder().also {
            it.type = contentType
            it.content = jsonObject.toString().toByteStringUtf8()
        }.build()
    }

    override fun decode(content: EncodedContent): NumberPair {
        val jsonObject = JSONObject(content.content.toStringUtf8())
        val a = jsonObject.getDouble("a")
        val b = jsonObject.getDouble("b")
        val numberPair = NumberPair(a, b, a * b)
        return numberPair
    }

    override fun fallback(content: NumberPair): String? {
        return "Error: This app does not support numbers."
    }
}

class Test {
    @Test
    fun testSendingAndReceivingMultipliedNumber() {
        Client.register(codec = MultiplyNumberCodec())
        val fixtures = fixtures()
        val aliceClient = fixtures.aliceClient
        val aliceConversation =
            aliceClient.conversations.newConversation(fixtures.bob.walletAddress)
        val numberPair = NumberPair(3.0, 2.0)
        aliceConversation.send(
            content = numberPair,
            options = SendOptions(contentType = MultiplyNumberCodec().contentType)
        )
        val messages = aliceConversation.messages()
        assertEquals(messages.size, 1)
        if (messages.size == 1) {
            val content: NumberPair? = messages[0].content()
            assertEquals(6.0, content?.result)
        }
    }
}
