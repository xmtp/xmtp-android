package org.xmtp.android.library


import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.web3j.crypto.Hash
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.extensions.millisecondsSinceEpoch
import org.xmtp.android.library.messages.ContactBundle
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.EnvelopeBuilder
import org.xmtp.android.library.messages.InvitationV1
import org.xmtp.android.library.messages.MessageBuilder
import org.xmtp.android.library.messages.MessageHeaderV2Builder
import org.xmtp.android.library.messages.MessageV1
import org.xmtp.android.library.messages.MessageV1Builder
import org.xmtp.android.library.messages.MessageV2Builder
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.SealedInvitation
import org.xmtp.android.library.messages.SealedInvitationBuilder
import org.xmtp.android.library.messages.SignedContent
import org.xmtp.android.library.messages.SignedContentBuilder
import org.xmtp.android.library.messages.SignedPublicKeyBundle
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.createRandom
import org.xmtp.android.library.messages.getPublicKeyBundle
import org.xmtp.android.library.messages.toPublicKeyBundle
import org.xmtp.android.library.messages.toV2
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.message.contents.Contact
import java.util.Date

class ConversationTests {
    lateinit var fakeApiClient: FakeApiClient
    lateinit var aliceWallet: PrivateKeyBuilder
    lateinit var bobWallet: PrivateKeyBuilder
    lateinit var alice: PrivateKey
    lateinit var aliceClient: Client
    lateinit var bob: PrivateKey
    lateinit var bobClient: Client

    @Before
    fun setUp() {
        aliceWallet = PrivateKeyBuilder()
        alice = aliceWallet.getPrivateKey()
        bobWallet = PrivateKeyBuilder()
        bob = bobWallet.getPrivateKey()
        fakeApiClient = FakeApiClient()
        aliceClient = Client().create(account = aliceWallet, apiClient = fakeApiClient)
        bobClient = Client().create(account = bobWallet, apiClient = fakeApiClient)
    }

    @Test
    fun testDoesNotAllowConversationWithSelf() {
        val client = Client().create(account = aliceWallet)
        runBlocking { client.conversations.newConversation(alice.walletAddress) }
    }

    @Test
    fun testDoesNotIncludeSelfConversationsInList() {
        val convos = aliceClient.conversations.conversations
        assert(convos.isEmpty())
        val recipient = aliceClient.privateKeyBundleV1?.toPublicKeyBundle()
        val invitation = InvitationV1.newBuilder().build().createRandom()
        val created = Date()
        val sealedInvitation = SealedInvitationBuilder.buildFromV1(
            sender = aliceClient.keys,
            recipient = SignedPublicKeyBundle(recipient),
            created = created,
            invitation = invitation
        )
        val peerAddress = recipient?.walletAddress
        runBlocking {
            aliceClient.publish(
                envelopes = listOf(
                    EnvelopeBuilder.buildFromTopic(
                        topic = Topic.userInvite(
                            aliceClient.address
                        ), timestamp = created, message = sealedInvitation.toByteArray()
                    ),
                    EnvelopeBuilder.buildFromTopic(
                        Topic.userInvite(peerAddress),
                        timestamp = created,
                        message = sealedInvitation.toByteArray()
                    )
                )
            )
        }
        val newConvos = aliceClient.conversations.conversations
        assert(newConvos.isEmpty())
    }

    @Test
    fun testCanUseCachedConversation() {
        val convo = runBlocking { bobClient.conversations.newConversation(alice.walletAddress) }
        assert(Contact.ContactBundle.VersionCase.V2 != convo.conversationId)

        fakeApiClient.assertNoQuery {
            if (!case v2 = bobClient . conversations . newConversation (with =
                    alice.walletAddress)
            ) {
                XCTFail("Did not get a v2 convo")
                return@assertNoQuery
            }
        }
    }

    @Test
    fun testCanInitiateV2Conversation() {
        val existingConversations = aliceClient.conversations.list()
        assert(existingConversations.isEmpty())
        if (!case let is v2 = bobClient . conversations . newConversation (with =
                alice.walletAddress)
        ) {
            XCTFail("Did not get a v2 convo")
            return
        }
        val aliceInviteMessage =
            fakeApiClient.findPublishedEnvelope(Topic.userInvite(alice.walletAddress))
        val bobInviteMessage = fakeApiClient.findPublishedEnvelope(Topic.userInvite(bob.walletAddress))
        assert(aliceInviteMessage != null)
        assert(bobInviteMessage != null)
        assertEquals(conversation.peerAddress, alice.walletAddress)
        val newConversations = aliceClient.conversations.conversations
        assertEquals("already had conversations somehow", 1, newConversations.size)
    }

    @Test
    fun testCanFindExistingV1Conversation() {
        val encoder = TextCodec()
        val encodedContent = encoder.encode(content = "hi alice")
        // Get a date that's roughly two weeks ago to test with
        val someTimeAgo = Date().advanced(by = -2_000_000)
        val messageV1 = MessageV1Builder.buildEncode(
            sender = bobClient.privateKeyBundleV1,
            recipient = aliceClient.privateKeyBundleV1.toPublicKeyBundle(),
            message = encodedContent.toByteArray(),
            timestamp = someTimeAgo
        )
        // Overwrite contact as legacy
        bobClient.publishUserContact(legacy = true)
        aliceClient.publishUserContact(legacy = true)
        bobClient.publish(
            envelopes = listOf(
                EnvelopeBuilder.buildFromTopic(topic = Topic.userIntro(bob.walletAddress),
                timestamp = someTimeAgo,
                message = MessageBuilder.buildFromMessageV1(v1 = messageV1).toByteArray()
            ),
            EnvelopeBuilder.buildFromTopic(topic = Topic.userIntro(alice.walletAddress),
            timestamp = someTimeAgo,
            message = MessageBuilder.buildFromMessageV1(v1 = messageV1).toByteArray()
        ), Envelope(topic = Topic.directMessageV1(bob.walletAddress, alice.walletAddress), timestamp = someTimeAgo, message = MessageBuilder.buildFromMessageV1(v1 = messageV1).serializedData())))
        if (!case let is v1 = aliceClient.conversations.newConversation(bob.walletAddress)) {
            XCTFail("Did not have a convo with bob")
            return
        }
        assertEquals(conversation.peerAddress, bob.walletAddress)
        assertEquals(
            Int(conversation.sentAt.timeIntervalSince1970),
            Int(someTimeAgo.timeIntervalSince1970)
        )
        val existingMessages = fakeApiClient.published.size
        if (!case let is v1 = bobClient . conversations . newConversation (with =
                alice.walletAddress)
        ) {
            XCTFail("Did not have a convo with alice")
            return
        }
        assertEquals(
            "published more messages when we shouldn't have",
            existingMessages,
            fakeApiClient.published.size
        )
        assertEquals(conversation.peerAddress, alice.walletAddress)
        assertEquals(
            Int(conversation.sentAt.timeIntervalSince1970),
            Int(someTimeAgo.timeIntervalSince1970)
        )
    }

    @Test
    fun testCanFindExistingV2Conversation() {
        if (!case let is v2 = bobClient . conversations . newConversation (with =
                alice.walletAddress, context = .init(conversationID = "http://example.com/2"))) {
            XCTFail("Did not create existing conversation with alice")
            return
        }
        fakeApiClient.assertNoPublish {
            if (!case let is v2 = bobClient . conversations . newConversation (with =
                    alice.walletAddress, context = .init(conversationID = "http://example.com/2"))) {
            XCTFail("Did not get conversation with bob")
            return@assertNoPublish
        }
            assertEquals(
                "made new conversation instead of using existing one",
                conversation.topic,
                existingConversation.topic
            )
        }
    }

    fun publishLegacyContact(client: Client) {
        val contactBundle = ContactBundle.newBuilder().apply {
            v1Builder.keyBundle = client.privateKeyBundleV1?.toPublicKeyBundle()
        }.build()
        val envelope = Envelope.newBuilder().apply {
            contentTopic = Topic.contact(client.address).description
            timestampNs = (Date().millisecondsSinceEpoch * 1_000_000).toLong()
            message = contactBundle.toByteString()
        }.build()

        runBlocking { client.publish(envelopes = listOf(envelope)) }
    }

    @Test
    fun testCanLoadV1Messages() {
        // Overwrite contact as legacy so we can get v1
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = bobClient.conversations.newConversation(aliceWallet.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        if (!case let is v1 = aliceClient.conversations.newConversation(bobWallet.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        bobConversation.send(content = "hey alice")
        bobConversation.send(content = "hey alice again")
        val messages = aliceConversation.messages()
        assertEquals(2, messages.size)
        assertEquals("hey alice", messages[1].body)
        assertEquals(bobWallet.address, messages[1].senderAddress)
    }

    @Test
    fun testCanLoadV2Messages() {
        if (!case let is v2 = bobClient.conversations.newConversation(aliceWallet.address, InvitationV1ContextBuilder.buildFromConversation(conversationID = "hi"))) {

        }
        if (!case let is v2 = aliceClient.conversations.newConversation(bobWallet.address, InvitationV1ContextBuilder.buildFromConversation(conversationID = "hi"))) {

        }
        bobConversation.send(content = "hey alice")
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals("hey alice", messages[0].body)
        assertEquals(bobWallet.address, messages[0].senderAddress)
    }

    @Test
    fun testVerifiesV2MessageSignature() {
        if (!case let is v2 = aliceClient.conversations.newConversation(bobWallet.address, context = InvitationV1ContextBuilder.buildFromConversation(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        val codec = TextCodec()
        val originalContent = codec.encode(content = "hello")
        val tamperedContent = codec.encode(content = "this is a fake")
        val originalPayload = originalContent.toByteArray()
        val tamperedPayload = tamperedContent.toByteArray()
        val date = Date()
        val header = MessageHeaderV2Builder.buildFromTopic(aliceConversation.topic, created = date)
        val headerBytes = header.toByteArray()
        val digest = Hash.sha256(headerBytes + tamperedPayload)
        val preKey = aliceClient.keys?.preKeysList[0]
        val signature = preKey.sign(digest)
        val bundle = aliceClient.privateKeyBundleV1?.toV2().getPublicKeyBundle()
        val signedContent =
            SignedContentBuilder.builderFromPayload(payload = originalPayload, sender = bundle, signature = signature)
        val signedBytes = signedContent.toByteArray()
        val ciphertext =
            Crypto.encrypt(aliceConversation.keyMaterial, signedBytes, additionalData = headerBytes)
        val tamperedMessage = MessageV2Builder.buildFromCipherText(headerBytes = headerBytes, ciphertext = ciphertext)
        aliceClient.publish(
            envelopes = listOf(
                EnvelopeBuilder.buildFromTopic(
                    topic = aliceConversation.topic,
                    timestamp = Date(),
                    message = MessageBuilder.buildFromMessageV2(v2 = tamperedMessage).toByteArray()
                )
            )
        )
        if (!case let is v2 = bobClient.conversations.newConversation(aliceWallet.address, InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        val messages = bobConversation.messages()
        assertEquals("did not filter out tampered message", 0, messages.size)
    }

    @Test
    fun testV1ConversationCodable() {
        // Overwrite contact as legacy
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = aliceClient.conversations.newConversation(bob.walletAddress)
        ) {
            XCTFail("Did not have a v1 convo with bob")
            return
        }
        conversation.send(content = "hi")
        val envelope =
            fakeApiClient.published.firstOrNull()(where = { it.contentTopic.hasPrefix("/xmtp/0/dm-") })!!
        val container = Conversation.v1(conversation).encodedContainer
        fakeApiClient.assertNoQuery {
            val decodedConversation = container.decode(with = aliceClient)
            val decodedMessage = decodedConversation.decode(envelope)
            assertEquals(decodedMessage.body, "hi")
        }
    }

    @Test
    fun testV2ConversationCodable() {
        if (!case let is v2 = aliceClient . conversations . newConversation (with =
                bob.walletAddress)
        ) {
            XCTFail("Did not have a v2 convo with bob")
            return
        }
        conversation.send(content = "hi")
        val envelope =
            fakeApiClient.published.firstOrNull()(where = { it.contentTopic.hasPrefix("/xmtp/0/m-") })!!
        val container = Conversation.v2(conversation).encodedContainer
        fakeApiClient.assertNoQuery {
            val decodedConversation = container.decode(with = aliceClient)
            val decodedMessage = decodedConversation.decode(envelope)
            assertEquals(decodedMessage.body, "hi")
        }
    }

    @Test
    fun testDecodeSingleV1Message() {
        // Overwrite contact as legacy
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = aliceClient.conversations.newConversation(bob.walletAddress)
        ) {
            XCTFail("Did not have a convo with bob")
            return
        }
        conversation.send(content = "hi")
        val message =
            fakeApiClient.published.firstOrNull()(where = { it.contentTopic.hasPrefix("/xmtp/0/dm-") })!!
        val decodedMessage = conversation.decode(envelope = message)
        assertEquals("hi", decodedMessage.body)
        val decodedMessage2 = Conversation.v1(conversation).decode(message)
        assertEquals("hi", decodedMessage2.body)
    }

    @Test
    fun testDecodeSingleV2Message() {
        if (!case let is v2 = aliceClient . conversations . newConversation (with =
                bob.walletAddress)
        ) {
            XCTFail("Did not have a convo with bob")
            return
        }
        conversation.send(content = "hi")
        val message =
            fakeApiClient.published.firstOrNull()(where = { it.contentTopic.hasPrefix("/xmtp/0/m-") })!!
        val decodedMessage = conversation.decode(envelope = message)
        assertEquals("hi", decodedMessage.body)
        val decodedMessage2 = Conversation.v2(conversation).decode(message)
        assertEquals("hi", decodedMessage2.body)
    }

    @Test
    fun testCanSendGzipCompressedV1Messages() {
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = bobClient.conversations.newConversation(aliceWallet.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        if (!case let is v1 = aliceClient.conversations.newConversation(bobWallet.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        bobConversation.send(
            content = Array(repeating = "A", count = 1000).joined(),
            options = . init (compression = . gzip))
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals(Array(repeating = "A", count = 1000).joined(), messages[0].content())
    }

    @Test
    fun testCanSendDeflateCompressedV1Messages() {
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = bobClient.conversations.newConversation(aliceWallet.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        if (!case let is v1 = aliceClient.conversations.newConversation(bobWallet.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        bobConversation.send(
            content = Array(repeating = "A", count = 1000).joined(),
            options = . init (compression = . deflate))
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals(Array(repeating = "A", count = 1000).joined(), messages[0].content())
    }

    @Test
    fun testCanSendGzipCompressedV2Messages() {
        if (!case let is v2 = bobClient.conversations.newConversation(aliceWallet.address, InvitationV1ContextBuilder.buildFromConversation(conversationId = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        if (!case let is v2 = aliceClient.conversations.newConversation(bobWallet.address, InvitationV1ContextBuilder.buildFromConversation(conversationId = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        bobConversation.send(
            content = Array(repeating = "A", count = 1000).joined(),
            options = . init (compression = . gzip))
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals(Array(repeating = "A", count = 1000).joined(), messages[0].body)
        assertEquals(bobWallet.address, messages[0].senderAddress)
    }

    @Test
    fun testCanSendDeflateCompressedV2Messages() {
        if (!case let is v2 = bobClient.conversations.newConversation (aliceWallet.address, InvitationV1ContextBuilder.buildFromConversation(conversationId = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        if (!case let is v2 = aliceClient.conversations.newConversation(bobWallet.address, InvitationV1ContextBuilder.buildFromConversation(conversationId = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        bobConversation.send(
            content = Array(repeating = "A", count = 1000).joined(),
            options = . init (compression = . deflate))
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals(Array(repeating = "A", count = 1000).joined(), messages[0].body)
        assertEquals(bobWallet.address, messages[0].senderAddress)
    }
}
