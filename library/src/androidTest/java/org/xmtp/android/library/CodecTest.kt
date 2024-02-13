package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.protobuf.kotlin.toByteStringUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.Crypto.Companion.calculateMac
import org.xmtp.android.library.Crypto.Companion.verifyHmacSignature
import org.xmtp.android.library.codecs.CompositeCodec
import org.xmtp.android.library.codecs.ContentCodec
import org.xmtp.android.library.codecs.ContentTypeId
import org.xmtp.android.library.codecs.ContentTypeIdBuilder
import org.xmtp.android.library.codecs.DecodedComposite
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.messages.InvitationV1
import org.xmtp.android.library.messages.MessageV2Builder
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.PrivateKeyBundleV1
import org.xmtp.android.library.messages.SealedInvitationBuilder
import org.xmtp.android.library.messages.createDeterministic
import org.xmtp.android.library.messages.generate
import org.xmtp.android.library.messages.getPublicKeyBundle
import org.xmtp.android.library.messages.toV2
import org.xmtp.android.library.messages.walletAddress
import java.security.Key
import java.time.Instant
import java.util.Date

data class NumberCodec(
    override var contentType: ContentTypeId = ContentTypeIdBuilder.builderFromAuthorityId(
        authorityId = "example.com",
        typeId = "number",
        versionMajor = 1,
        versionMinor = 1,
    ),
) : ContentCodec<Double> {
    override fun encode(content: Double): EncodedContent {
        return EncodedContent.newBuilder().also {
            it.type = ContentTypeIdBuilder.builderFromAuthorityId(
                authorityId = "example.com",
                typeId = "number",
                versionMajor = 1,
                versionMinor = 1,
            )
            it.content = mapOf(Pair("number", content)).toString().toByteStringUtf8()
        }.build()
    }

    override fun decode(content: EncodedContent): Double =
        content.content.toStringUtf8().filter { it.isDigit() || it == '.' }.toDouble()

    override fun shouldPush(content: Double): Boolean = false

    override fun fallback(content: Double): String? {
        return "Error: This app does not support numbers."
    }
}

@RunWith(AndroidJUnit4::class)
class CodecTest {

    @Test
    fun testCanRoundTripWithCustomContentType() {
        Client.register(codec = NumberCodec())
        val fixtures = fixtures()
        val aliceClient = fixtures.aliceClient
        val aliceConversation =
            aliceClient.conversations.newConversation(fixtures.bob.walletAddress)
        aliceConversation.send(
            content = 3.14,
            options = SendOptions(contentType = NumberCodec().contentType),
        )
        val messages = aliceConversation.messages()
        assertEquals(messages.size, 1)
        if (messages.size == 1) {
            val content: Double? = messages[0].content()
            assertEquals(3.14, content)
            assertEquals("Error: This app does not support numbers.", messages[0].fallbackContent)
        }
    }

    @Test
    fun testCompositeCodecOnePart() {
        Client.register(codec = CompositeCodec())
        val fixtures = fixtures()
        val aliceClient = fixtures.aliceClient
        val aliceConversation =
            aliceClient.conversations.newConversation(fixtures.bob.walletAddress)
        val textContent = TextCodec().encode(content = "hiya")
        val source = DecodedComposite(encodedContent = textContent)
        aliceConversation.send(
            content = source,
            options = SendOptions(contentType = CompositeCodec().contentType),
        )
        val messages = aliceConversation.messages()
        val decoded: DecodedComposite? = messages[0].content()
        assertEquals("hiya", decoded?.content())
    }

    @Test
    fun testCompositeCodecCanHaveParts() {
        Client.register(codec = CompositeCodec())
        Client.register(codec = NumberCodec())
        val fixtures = fixtures()
        val aliceClient = fixtures.aliceClient!!
        val aliceConversation =
            aliceClient.conversations.newConversation(fixtures.bob.walletAddress)
        val textContent = TextCodec().encode(content = "sup")
        val numberContent = NumberCodec().encode(content = 3.14)
        val source = DecodedComposite(
            parts = listOf(
                DecodedComposite(encodedContent = textContent),
                DecodedComposite(parts = listOf(DecodedComposite(encodedContent = numberContent))),
            ),
        )
        aliceConversation.send(
            content = source,
            options = SendOptions(contentType = CompositeCodec().contentType),
        )
        val messages = aliceConversation.messages()
        val decoded: DecodedComposite? = messages[0].content()
        val part1 = decoded!!.parts[0]
        val part2 = decoded.parts[1].parts[0]
        assertEquals("sup", part1.content())
        assertEquals(3.14, part2.content())
    }

    @Test
    fun testCanGetPushInfoBeforeDecoded() {
        val codec = NumberCodec()
        Client.register(codec = codec)
        val fixtures = fixtures()
        val aliceClient = fixtures.aliceClient!!
        val aliceConversation =
            aliceClient.conversations.newConversation(fixtures.bob.walletAddress)
        aliceConversation.send(
            content = 3.14,
            options = SendOptions(contentType = codec.contentType),
        )
        val messages = aliceConversation.messages()
        assert(messages.isNotEmpty())

        val message = MessageV2Builder.buildEncode(
            client = aliceClient,
            encodedContent = messages[0].encodedContent,
            topic = aliceConversation.topic,
            keyMaterial = aliceConversation.keyMaterial!!,
            codec = codec,
        )

        assertEquals(false, message.shouldPush)
        assertEquals(true, message.senderHmac?.isNotEmpty())
        val keys = aliceClient.conversations.getHmacKeys()
    }

    @Test
    fun testReturnsAllHMACKeys() {
        val baseTime = Instant.now()
        val timestamps = List(5) { i -> baseTime.plusSeconds(i.toLong()) }
        val fixtures = fixtures()

        val invites = timestamps.map { createdAt ->
            val fakeWallet = FakeWallet.generate()
            val recipient = PrivateKeyBundleV1.newBuilder().build().generate(wallet = fakeWallet)
            InvitationV1.newBuilder().build().createDeterministic(
                sender = fixtures.aliceClient.privateKeyBundleV1.toV2(),
                recipient = recipient.toV2().getPublicKeyBundle()
            )
        }

        val thirtyDayPeriodsSinceEpoch = Instant.now().epochSecond / 60 / 60 / 24 / 30

        val periods = listOf(
            thirtyDayPeriodsSinceEpoch - 1,
            thirtyDayPeriodsSinceEpoch,
            thirtyDayPeriodsSinceEpoch + 1
        )

        val hmacKeys = fixtures.aliceClient.conversations.getHmacKeys()

        val topics = hmacKeys.hmacKeysMap.keys
        invites.forEach { invite ->
            assertTrue(topics.contains(invite.topic))
        }

        val topicHmacs = mutableMapOf<String, ByteArray>()
        val headerBytes = ByteArray(10)

        invites.map { invite ->
            val topic = invite.topic
            val payload = TextCodec().encode(content = "Hello, world!")

            val message = MessageV2Builder.buildEncode(
                client = fixtures.aliceClient,
                encodedContent = payload,
                topic = topic,
                keyMaterial = headerBytes,
                codec = TextCodec()
            )

            val conversation = fixtures.aliceClient.fetchConversation(topic)
            val keyMaterial = conversation?.keyMaterial
            val info = "$thirtyDayPeriodsSinceEpoch-${fixtures.aliceClient.address}"
            val hmac = Crypto.calculateMac(
                Crypto.deriveKey(keyMaterial!!, ByteArray(0), info.toByteArray()),
                headerBytes
            )

            topicHmacs[topic] = hmac
        }

        hmacKeys.hmacKeysMap.forEach { (topic, hmacData) ->
            hmacData.valuesList.forEachIndexed { idx, hmacKeyThirtyDayPeriod ->
                val valid = verifyHmacSignature(
                    hmacKeyThirtyDayPeriod.hmacKey.toByteArray(),
                    topicHmacs[topic]!!,
                    headerBytes
                )
                assertTrue(valid == (idx == 1))
            }
        }

    }
}
