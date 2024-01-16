package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.messages.EnvelopeBuilder
import org.xmtp.android.library.messages.InvitationV1
import org.xmtp.android.library.messages.MessageBuilder
import org.xmtp.android.library.messages.MessageV1Builder
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.SealedInvitationBuilder
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.createDeterministic
import org.xmtp.android.library.messages.getPublicKeyBundle
import org.xmtp.android.library.messages.toPublicKeyBundle
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.keystore.api.v1.Keystore
import java.lang.Thread.sleep
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ConversationsTest {

    @Test
    fun testCanGetConversationFromIntroEnvelope() {
        val fixtures = fixtures()
        val client = fixtures.aliceClient
        val created = Date()
        val newWallet = PrivateKeyBuilder()
        val newClient = Client().create(account = newWallet, apiClient = fixtures.fakeApiClient)
        val message = MessageV1Builder.buildEncode(
            sender = newClient.privateKeyBundleV1,
            recipient = fixtures.aliceClient.v1keys.toPublicKeyBundle(),
            message = TextCodec().encode(content = "hello").toByteArray(),
            timestamp = created
        )
        val envelope = EnvelopeBuilder.buildFromTopic(
            topic = Topic.userIntro(client.address),
            timestamp = created,
            message = MessageBuilder.buildFromMessageV1(v1 = message).toByteArray()
        )
        val conversation = client.conversations.fromIntro(envelope = envelope)
        assertEquals(conversation.peerAddress, newWallet.address)
        assertEquals(conversation.createdAt.time, created.time)
    }

    @Test
    fun testCanGetConversationFromInviteEnvelope() {
        val fixtures = fixtures()
        val client = fixtures.aliceClient
        val created = Date()
        val newWallet = PrivateKeyBuilder()
        val newClient = Client().create(account = newWallet, apiClient = fixtures.fakeApiClient)
        val invitation = InvitationV1.newBuilder().build().createDeterministic(
            sender = newClient.keys,
            recipient = client.keys.getPublicKeyBundle()
        )
        val sealed = SealedInvitationBuilder.buildFromV1(
            sender = newClient.keys,
            recipient = client.keys.getPublicKeyBundle(),
            created = created,
            invitation = invitation
        )
        val peerAddress = fixtures.alice.walletAddress
        val envelope = EnvelopeBuilder.buildFromTopic(
            topic = Topic.userInvite(peerAddress),
            timestamp = created,
            message = sealed.toByteArray()
        )
        val conversation = client.conversations.fromInvite(envelope = envelope)
        assertEquals(conversation.peerAddress, newWallet.address)
        assertEquals(conversation.createdAt.time, created.time)
    }

    @Test
    @Ignore("CI Issues")
    fun testStreamAllMessages() {
        val bo = PrivateKeyBuilder()
        val alix = PrivateKeyBuilder()
        val clientOptions =
            ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.LOCAL, isSecure = false))
        val boClient = Client().create(bo, clientOptions)
        val alixClient = Client().create(alix, clientOptions)
        val boConversation = boClient.conversations.newConversation(alixClient.address)

        // Record message stream across all conversations
        val allMessages = mutableListOf<DecodedMessage>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                alixClient.conversations.streamAllMessages().collect { message ->
                    allMessages.add(message)
                }
            } catch (e: Exception) {}
        }
        sleep(2500)

        for (i in 0 until 5) {
            boConversation.send(text = "Message $i")
            sleep(1000)
        }
        assertEquals(allMessages.size, 5)

        val caro = PrivateKeyBuilder()
        val caroClient = Client().create(caro, clientOptions)
        val caroConversation = caroClient.conversations.newConversation(alixClient.address)
        sleep(2500)

        for (i in 0 until 5) {
            caroConversation.send(text = "Message $i")
            sleep(1000)
        }

        assertEquals(allMessages.size, 10)

        job.cancel()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                alixClient.conversations.streamAllMessages().collect { message ->
                    allMessages.add(message)
                }
            } catch (e: Exception) {
            }
        }
        sleep(2500)

        for (i in 0 until 5) {
            boConversation.send(text = "Message $i")
            sleep(1000)
        }

        assertEquals(allMessages.size, 15)
    }

    @Test
    fun testLoadConvos() {
        // Build from [8,54,32,15,250,250,23,163,203,139,84,242,45,106,250,96,177,61,164,135,38,84,50,65,173,197,194,80,219,176,224,205]
        // or in hex 0836200ffafa17a3cb8b54f22d6afa60b13da48726543241adc5c250dbb0e0cd
        // aka 2k many convos test wallet
        // Create a ByteArray with the 32 bytes above
        val privateKeyData = listOf(
            0x08,
            0x36,
            0x20,
            0x0f,
            0xfa,
            0xfa,
            0x17,
            0xa3,
            0xcb,
            0x8b,
            0x54,
            0xf2,
            0x2d,
            0x6a,
            0xfa,
            0x60,
            0xb1,
            0x3d,
            0xa4,
            0x87,
            0x26,
            0x54,
            0x32,
            0x41,
            0xad,
            0xc5,
            0xc2,
            0x50,
            0xdb,
            0xb0,
            0xe0,
            0xcd
        )
            .map { it.toByte() }
            .toByteArray()
        // Use hardcoded privateKey
        val privateKey = PrivateKeyBuilder.buildFromPrivateKeyData(privateKeyData)
        val privateKeyBuilder = PrivateKeyBuilder(privateKey)
        val options =
            ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.DEV))
        val client = Client().create(account = privateKeyBuilder, options = options)

        val start = Date()
        val conversations = client.conversations.list()
        val end = Date()
        println("Loaded ${conversations.size} conversations in ${(end.time - start.time) / 1000.0}s")

        val start2 = Date()
        val conversations2 = client.conversations.list()
        val end2 = Date()
        println("Second time loaded ${conversations2.size} conversations in ${(end2.time - start2.time) / 1000.0}s")

        val last500Topics = conversations.takeLast(500).map { it.toTopicData().toByteString() }
        val client2 = Client().create(account = privateKeyBuilder, options = options)
        for (topic in last500Topics) {
            client2.conversations.importTopicData(Keystore.TopicMap.TopicData.parseFrom(topic))
        }

        val start3 = Date()
        val conversations3 = client2.conversations.list()
        val end3 = Date()
        println("Loaded ${conversations3.size} conversations in ${(end3.time - start3.time) / 1000.0}s")

        val start4 = Date()
        val conversations4 = client2.conversations.list()
        val end4 = Date()
        println("Second time loaded ${conversations4.size} conversations in ${(end4.time - start4.time) / 1000.0}s")
    }
}
