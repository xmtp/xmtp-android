package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.protobuf.kotlin.toByteStringUtf8
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.ContentTypeReaction
import org.xmtp.android.library.codecs.EncodedContent
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.codecs.ReactionCodec
import org.xmtp.android.library.codecs.ReactionSchema
import org.xmtp.android.library.messages.walletAddress

@RunWith(AndroidJUnit4::class)
class ReactionTest {

    @Test
    fun testCanDecodeLegacyForm() {
        val codec = ReactionCodec()

        // This is how clients send reactions now.
        val canonicalEncoded = EncodedContent.newBuilder().also {
            it.type = ContentTypeReaction
            it.content = """
                {
                    "action": "added",
                    "content": "smile",
                    "reference": "abc123",
                    "schema": "shortcode"
                }
            """.trimIndent().toByteStringUtf8()
        }.build()

        // Previously, some clients sent reactions like this.
        // So we test here to make sure we can still decode them.
        val legacyEncoded = EncodedContent.newBuilder().also {
            it.type = ContentTypeReaction
            it.putAllParameters(
                mapOf(
                    "action" to "added",
                    "reference" to "abc123",
                    "schema" to "shortcode",
                ),
            )
            it.content = "smile".toByteStringUtf8()
        }.build()

        val canonical = codec.decode(canonicalEncoded)
        val legacy = codec.decode(legacyEncoded)

        assertEquals(ReactionAction.Added, canonical.action)
        assertEquals(ReactionAction.Added, legacy.action)
        assertEquals("smile", canonical.content)
        assertEquals("smile", legacy.content)
        assertEquals("abc123", canonical.reference)
        assertEquals("abc123", legacy.reference)
        assertEquals(ReactionSchema.Shortcode, canonical.schema)
        assertEquals(ReactionSchema.Shortcode, legacy.schema)
    }

    @Test
    fun testCanUseReactionCodec() {
        Client.register(codec = ReactionCodec())

        val fixtures = fixtures()
        val aliceClient = fixtures.alixClient
        val aliceConversation = runBlocking {
            aliceClient.conversations.newConversation(fixtures.bo.walletAddress)
        }

        runBlocking { aliceConversation.send(text = "hey alice 2 bob") }

        val messageToReact = runBlocking { aliceConversation.messages()[0] }

        val attachment = Reaction(
            reference = messageToReact.id,
            action = ReactionAction.Added,
            content = "U+1F603",
            schema = ReactionSchema.Unicode,
        )

        runBlocking {
            aliceConversation.send(
                content = attachment,
                options = SendOptions(contentType = ContentTypeReaction),
            )
        }
        val messages = runBlocking { aliceConversation.messages() }
        assertEquals(messages.size, 3)
        if (messages.size == 3) {
            val content: Reaction? = messages.first().content()
            assertEquals("U+1F603", content?.content)
            assertEquals(messageToReact.id, content?.reference)
            assertEquals(ReactionAction.Added, content?.action)
            assertEquals(ReactionSchema.Unicode, content?.schema)
        }
    }
}
