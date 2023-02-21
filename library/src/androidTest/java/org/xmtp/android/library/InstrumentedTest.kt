package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.InvitationV1ContextBuilder
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.PrivateKeyBundleBuilder
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.encrypted
import org.xmtp.android.library.messages.generate
import org.xmtp.android.library.messages.secp256K1Uncompressed
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.message.contents.PrivateKeyOuterClass
import java.util.Date

@RunWith(AndroidJUnit4::class)
class InstrumentedTest {
    @Test
    fun testPublishingAndFetchingContactBundlesWithWhileGeneratingKeys() {
        val aliceWallet = PrivateKeyBuilder()
        val alicePrivateKey = aliceWallet.getPrivateKey()
        val clientOptions =
            ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.LOCAL, isSecure = false))
        val client = Client().create(aliceWallet, clientOptions)
        assertEquals(XMTPEnvironment.LOCAL, client.apiClient.environment)
        runBlocking {
            client.publishUserContact()
        }
        val contact = client.getUserContact(peerAddress = alicePrivateKey.walletAddress)
        assert(
            contact?.v2?.keyBundle?.identityKey?.secp256K1Uncompressed?.bytes?.toByteArray()
                .contentEquals(client.privateKeyBundleV1?.identityKey?.publicKey?.secp256K1Uncompressed?.bytes?.toByteArray())
        )
        assert(contact?.v2?.keyBundle?.identityKey?.hasSignature() ?: false)
        assert(contact?.v2?.keyBundle?.preKey?.hasSignature() ?: false)
    }

    @Test
    fun testSaveKey() {
        val alice = PrivateKeyBuilder()
        val identity = PrivateKey.newBuilder().build().generate()
        val authorized = alice.createIdentity(identity)
        val authToken = authorized.createAuthToken()
        val api = GRPCApiClient(environment = XMTPEnvironment.LOCAL, secure = false)
        api.setAuthToken(authToken)
        val encryptedBundle = authorized.toBundle.encrypted(alice)
        val envelope = Envelope.newBuilder().also {
            it.contentTopic = Topic.userPrivateStoreKeyBundle(authorized.address).description
            it.timestampNs = Date().time * 1_000_000
            it.message = encryptedBundle.toByteString()
        }.build()
        runBlocking {
            api.publish(envelopes = listOf(envelope))
        }
        Thread.sleep(2_000)
        val result =
            runBlocking { api.queryTopic(topics = listOf(Topic.userPrivateStoreKeyBundle(authorized.address))) }
        assertEquals(result.envelopesList.size, 1)
    }

    @Test
    @FlakyTest
    fun testPublishingAndFetchingContactBundlesWithSavedKeys() {
        val aliceWallet = PrivateKeyBuilder()
        val alice = PrivateKeyOuterClass.PrivateKeyBundleV1.newBuilder().build()
            .generate(wallet = aliceWallet)
        // Save keys
        val identity = PrivateKeyBuilder().getPrivateKey()
        val authorized = aliceWallet.createIdentity(identity)
        val authToken = authorized.createAuthToken()
        val api = GRPCApiClient(environment = XMTPEnvironment.LOCAL, secure = false)
        api.setAuthToken(authToken)
        val encryptedBundle =
            PrivateKeyBundleBuilder.buildFromV1Key(v1 = alice).encrypted(aliceWallet)
        val envelope = Envelope.newBuilder().also {
            it.contentTopic = Topic.userPrivateStoreKeyBundle(authorized.address).description
            it.timestampNs = Date().time * 1_000_000
            it.message = encryptedBundle.toByteString()
        }.build()
        runBlocking {
            api.publish(envelopes = listOf(envelope))
        }

        // Done saving keys
        val clientOptions =
            ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.LOCAL, isSecure = false))
        val client = Client().create(account = aliceWallet, options = clientOptions)
        assertEquals(XMTPEnvironment.LOCAL, client.apiClient.environment)
        val contact = client.getUserContact(peerAddress = aliceWallet.address)
        assertEquals(
            contact?.v2?.keyBundle?.identityKey?.secp256K1Uncompressed,
            client.privateKeyBundleV1?.identityKey?.publicKey?.secp256K1Uncompressed
        )
        assert(contact!!.v2.keyBundle.identityKey.hasSignature())
        assert(contact.v2.keyBundle.preKey.hasSignature())
    }

    @Test
    fun testCanPaginateV2Messages() {
        val bob = PrivateKeyBuilder()
        val alice = PrivateKeyBuilder()
        val clientOptions =
            ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.LOCAL, isSecure = false))
        val bobClient = Client().create(bob, clientOptions)
        // Publish alice's contact
        Client().create(account = alice, clientOptions)
        val convo = bobClient.conversations.newConversation(
            alice.address,
            context = InvitationV1ContextBuilder.buildFromConversation("hi")
        )
        // Say this message is sent in the past
        val date = Date()
        date.time = date.time - 10000
        convo.send(text = "10 seconds ago", sentAt = date)
        convo.send(text = "now")
        val messages = convo.messages(limit = 1)
        assertEquals(1, messages.size)
        val nowMessage = messages[0]
        assertEquals("now", nowMessage.body)
        val messages2 = convo.messages(limit = 1, before = date)
        assertEquals(1, messages2.size)
        val tenSecondsAgoMessage = messages2[0]
        assertEquals("10 seconds ago", tenSecondsAgoMessage.body)
        val messages3 = convo.messages(limit = 1, after = tenSecondsAgoMessage.sent)
        assertEquals(1, messages3.size)
        val nowMessage2 = messages3[0]
        assertEquals("now", nowMessage2.body)
    }

    @Test
    fun testCanPaginateV1Messages() {
        val bob = PrivateKeyBuilder()
        val alice = PrivateKeyBuilder()
        val clientOptions =
            ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.LOCAL, isSecure = false))
        val bobClient = Client().create(bob, clientOptions)
        // Publish alice's contact
        Client().create(account = alice, clientOptions)
        val convo = ConversationV1(client = bobClient, peerAddress = alice.address, sentAt = Date())
        // Say this message is sent in the past
        val date = Date()
        date.time = date.time - 10000
        convo.send(text = "10 seconds ago", sentAt = date)
        convo.send(text = "now")
        val messages = convo.messages(limit = 1)
        assertEquals(1, messages.size)
        val nowMessage = messages[0]
        assertEquals("now", nowMessage.body)
        val messages2 = convo.messages(limit = 1, before = date)
        assertEquals(1, messages2.size)
        val tenSecondsAgoMessage = messages2[0]
        assertEquals("10 seconds ago", tenSecondsAgoMessage.body)
        val messages3 = convo.messages(limit = 1, after = tenSecondsAgoMessage.sent)
        assertEquals(1, messages3.size)
        val nowMessage2 = messages3[0]
        assertEquals("now", nowMessage2.body)
    }

    @Test
    fun testStreamMessagesInV1Conversation() {
        val alice = PrivateKeyBuilder()
        val bob = PrivateKeyBuilder()
        val clientOptions =
            ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.LOCAL, isSecure = false))
        val aliceClient = Client().create(account = alice, options = clientOptions)
        aliceClient.publishUserContact(legacy = true)
        val bobClient = Client().create(account = bob, options = clientOptions)
        bobClient.publishUserContact(legacy = true)
        val aliceConversation = aliceClient.conversations.newConversation(bob.address)
        aliceConversation.send(content = "greetings")
//        val expectation = expectation(description = "bob gets a streamed message")
        val bobConversation = bobClient.conversations.newConversation(alice.address)
        assertEquals(bobConversation.topic, aliceConversation.topic)
        bobConversation.streamMessages()
        aliceConversation.send(content = "hi bob")
        bobConversation.send(content = "hi alice")
//        waitForExpectations(timeout = 3)
    }

    @Test
    fun testStreamMessagesInV2Conversation() {
        val alice = PrivateKeyBuilder()
        val bob = PrivateKeyBuilder()
        val clientOptions =
            ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.LOCAL, isSecure = false))
        val aliceClient = Client().create(account = alice, options = clientOptions)
        val bobClient = Client().create(account = bob, options = clientOptions)
        val aliceConversation = aliceClient.conversations.newConversation(bob.address,
            context = InvitationV1ContextBuilder.buildFromConversation(conversationId = "https://example.com/3"))
        val bobConversation = bobClient.conversations.newConversation(alice.address,
            context = InvitationV1ContextBuilder.buildFromConversation(conversationId = "https://example.com/3"))
        assertEquals(bobConversation.topic, aliceConversation.topic)
        val conversation = runBlocking {  bobConversation.streamMessages().first() }
        aliceConversation.send(text = "hi bob")
        assertEquals(bobConversation, conversation)
    }

    @Test
    fun testCanStreamV2Conversations() {
        val alice = PrivateKeyBuilder()
        val bob = PrivateKeyBuilder()
        val clientOptions =
            ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.LOCAL, isSecure = false))
        val aliceClient = Client().create(account = alice, options = clientOptions)
        val bobClient = Client().create(account = bob, options = clientOptions)
//        val expectation1 = expectation(description = "got a conversation")
//        expectation1.expectedFulfillmentCount = 2
        bobClient.conversations.stream()
        var conversation = bobClient.conversations.newConversation(alice.address)
        conversation.send(content = "hi")
        conversation = bobClient.conversations.newConversation(alice.address)
        conversation.send(content = "hi again")
        val newWallet = PrivateKeyBuilder()
        val newClient = Client().create(account = newWallet, options = clientOptions)
        val conversation2 = bobClient.conversations.newConversation(newWallet.address)
        conversation2.send(content = "hi from new wallet")
//        waitForExpectations(timeout = 3)
    }

}
