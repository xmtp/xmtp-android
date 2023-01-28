package org.xmtp.android.library


import org.junit.*
import org.junit.Assert.*
import org.xmtp.android.library.codecs.TextCodec
import org.xmtp.android.library.messages.ContactBundle
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.InvitationV1
import org.xmtp.android.library.messages.MessageV1
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.SealedInvitation
import org.xmtp.android.library.messages.SignedPublicKeyBundle
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.toPublicKeyBundle
import java.util.Date

class ConversationTests {
    lateinit var fakeApiClient: FakeApiClient
    lateinit var alice: PrivateKey
    lateinit var aliceClient: Client
    lateinit var bob: PrivateKey
    lateinit var bobClient: Client

    @Before
    fun setUp() {
        alice = PrivateKeyBuilder()
        bob = PrivateKeyBuilder()
        fakeApiClient = FakeApiClient()
        aliceClient = Client().create(account = alice, apiClient = fakeApiClient)
        bobClient = Client().create(account = bob, apiClient = fakeApiClient)
    }

    @Test
    fun testDoesNotAllowConversationWithSelf() {
        val expectation = expectation(description = "convo with self throws")
        val client = Client().create(account = alice)
        do {
            client.conversations.newConversation(with = alice.walletAddress)
        } catch {
            expectation.fulfill()
        }
        wait(for = listOf(expectation), timeout = 0.1)
    }

    @Test
    fun testDoesNotIncludeSelfConversationsInList() {
        val convos = aliceClient.conversations.list()
        assert(convos.isEmpty())
        val recipient = aliceClient.privateKeyBundleV1.toPublicKeyBundle()
        val invitation = InvitationV1.createRandom()
        val created = Date()
        val sealedInvitation = SealedInvitation.createV1(sender = aliceClient.keys, recipient = SignedPublicKeyBundle(recipient), created = created, invitation = invitation)
        val peerAddress = recipient.walletAddress
        aliceClient.publish(envelopes = listOf(Envelope(topic = userInvite(aliceClient.address), timestamp = created, message = sealedInvitation.serializedData()), Envelope(topic = .userInvite(peerAddress), timestamp = created, message = sealedInvitation.serializedData())))
        val newConvos = aliceClient.conversations.list()
        assert(newConvos.isEmpty())
    }

    @Test
    fun testCanStreamConversationsV1() {
        // Overwrite contact as legacy
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        val expectation = expectation(description = "got a conversation")
        Task(priority = .userInitiated) { for (conversation in aliceClient.conversations.stream()) {
            if (conversation.peerAddress == bob.walletAddress) {
                expectation.fulfill()
            }
        } }
        if (!case let is v1 = bobClient.conversations.newConversation(with = alice.walletAddress)) {
            XCTFail("Did not create a v1 convo")
            return
        }
        conversation.send(content = "hi")
        // Remove known introduction from contacts to test de-duping
        bobClient.contacts.hasIntroduced.removeAll()
        conversation.send(content = "hi again")
        waitForExpectations(timeout = 5)
    }

    @Test
    fun testCanStreamConversationsV2() {
        val expectation1 = expectation(description = "got a conversation")
        expectation1.expectedFulfillmentCount = 2
        Task(priority = .userInitiated) { for (conversation in bobClient.conversations.stream()) {
            expectation1.fulfill()
        } }
        if (!case let is v2 = bobClient.conversations.newConversation(with = alice.walletAddress)) {
            XCTFail("Did not create a v2 convo")
            return
        }
        conversation.send(content = "hi")
        if (!case let is v2 = bobClient.conversations.newConversation(with = alice.walletAddress)) {
            XCTFail("Did not create a v2 convo")
            return
        }
        conversation.send(content = "hi again")
        val newWallet = PrivateKey.generate()
        val newClient = Client.create(account = newWallet, apiClient = fakeApiClient)
        if (!case let is v2 = bobClient.conversations.newConversation(with = newWallet.walletAddress)) {
            XCTFail("Did not create a v2 convo")
            return
        }
        conversation2.send(content = "hi from new wallet")
        waitForExpectations(timeout = 3)
    }

    @Test
    fun testCanUseCachedConversation() {
        if (!case v2 = bobClient.conversations.newConversation(with = alice.walletAddress)) {
            XCTFail("Did not get a v2 convo")
            return
        }
        fakeApiClient.assertNoQuery { if (!case v2 = bobClient.conversations.newConversation(with = alice.walletAddress)) {
            XCTFail("Did not get a v2 convo")
            return@assertNoQuery
        } }
    }

    @Test
    fun testCanInitiateV2Conversation() {
        val existingConversations = aliceClient.conversations.list()
        assert(existingConversations.isEmpty())
        if (!case let is v2 = bobClient.conversations.newConversation(with = alice.walletAddress)) {
            XCTFail("Did not get a v2 convo")
            return
        }
        val aliceInviteMessage = fakeApiClient.findPublishedEnvelope(userInvite(alice.walletAddress))
        val bobInviteMessage = fakeApiClient.findPublishedEnvelope(userInvite(bob.walletAddress))
        assert(aliceInviteMessage != null)
        assert(bobInviteMessage != null)
        assertEquals(conversation.peerAddress, alice.walletAddress)
        val newConversations = aliceClient.conversations.list()
        assertEquals("already had conversations somehow", 1, newConversations.size)
    }

    @Test
    fun testCanFindExistingV1Conversation() {
        val encoder = TextCodec()
        val encodedContent = encoder.encode(content = "hi alice")
        // Get a date that's roughly two weeks ago to test with
        val someTimeAgo = Date().advanced(by = -2_000_000)
        val messageV1 = MessageV1.encode(sender = bobClient.privateKeyBundleV1, recipient = aliceClient.privateKeyBundleV1.toPublicKeyBundle(), message = encodedContent.serializedData(), timestamp = someTimeAgo)
        // Overwrite contact as legacy
        bobClient.publishUserContact(legacy = true)
        aliceClient.publishUserContact(legacy = true)
        bobClient.publish(envelopes = listOf(Envelope(topic = .userIntro(bob.walletAddress), timestamp = someTimeAgo, message = Message(v1 = messageV1).serializedData()), Envelope(topic = .userIntro(alice.walletAddress), timestamp = someTimeAgo, message = Message(v1 = messageV1).serializedData()), Envelope(topic = .directMessageV1(bob.walletAddress, alice.walletAddress), timestamp = someTimeAgo, message = Message(v1 = messageV1).serializedData())))
        if (!case let is v1 = aliceClient.conversations.newConversation(with = bob.walletAddress)) {
            XCTFail("Did not have a convo with bob")
            return
        }
        assertEquals(conversation.peerAddress, bob.walletAddress)
        assertEquals(Int(conversation.sentAt.timeIntervalSince1970), Int(someTimeAgo.timeIntervalSince1970))
        val existingMessages = fakeApiClient.published.size
        if (!case let is v1 = bobClient.conversations.newConversation(with = alice.walletAddress)) {
            XCTFail("Did not have a convo with alice")
            return
        }
        assertEquals("published more messages when we shouldn't have", existingMessages, fakeApiClient.published.size)
        assertEquals(conversation.peerAddress, alice.walletAddress)
        assertEquals(Int(conversation.sentAt.timeIntervalSince1970), Int(someTimeAgo.timeIntervalSince1970))
    }

    @Test
    fun testCanFindExistingV2Conversation() {
        if (!case let is v2 = bobClient.conversations.newConversation(with = alice.walletAddress, context = .init(conversationID = "http://example.com/2"))) {
            XCTFail("Did not create existing conversation with alice")
            return
        }
        fakeApiClient.assertNoPublish {
            if (!case let is v2 = bobClient.conversations.newConversation(with = alice.walletAddress, context = .init(conversationID = "http://example.com/2"))) {
            XCTFail("Did not get conversation with bob")
            return@assertNoPublish
        }
            assertEquals("made new conversation instead of using existing one", conversation.topic, existingConversation.topic)
        }
    }

    fun publishLegacyContact(client: Client) {
        var contactBundle = ContactBundle()
        contactBundle.v1.keyBundle = client.privateKeyBundleV1.toPublicKeyBundle()
        var envelope = Envelope()
        envelope.contentTopic = Topic.contact(client.address).description
        envelope.timestampNs = UInt64(Date().millisecondsSinceEpoch * 1_000_000)
        envelope.message = contactBundle.serializedData()
        client.publish(envelopes = listOf(envelope))
    }

    @Test
    fun testStreamingMessagesFromV1Conversation() {
        // Overwrite contact as legacy
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = aliceClient.conversations.newConversation(with = bob.walletAddress)) {
            XCTFail("Did not have a convo with bob")
            return
        }
        val expectation = expectation(description = "got a message")
        Task(priority = .userInitiated) { for (_ in conversation.streamMessages()) {
            expectation.fulfill()
        } }
        val encoder = TextCodec()
        val encodedContent = encoder.encode(content = "hi alice")
        val date = Date().advanced(by = -1_000_000)
        // Stream a message
        fakeApiClient.send(envelope = Envelope(topic = conversation.topic, timestamp = Date(), message = Message(v1 = MessageV1.encode(sender = bobClient.privateKeyBundleV1, recipient = aliceClient.privateKeyBundleV1.toPublicKeyBundle(), message = encodedContent.serializedData(), timestamp = date)).serializedData()))
        waitForExpectations(timeout = 3)
    }

    @Test
    fun testStreamingMessagesFromV2Conversations() {
        if (!case let is v2 = aliceClient.conversations.newConversation(with = bob.walletAddress)) {
            XCTFail("Did not get a v2 convo")
            return
        }
        val expectation = expectation(description = "got a message")
        Task(priority = .userInitiated) { for (_ in conversation.streamMessages()) {
            expectation.fulfill()
        } }
        val encoder = TextCodec()
        val encodedContent = encoder.encode(content = "hi alice")
        // Stream a message
        fakeApiClient.send(envelope = Envelope(topic = conversation.topic, timestamp = Date(), message = Message(v2 = MessageV2.encode(client = bobClient, content = encodedContent, topic = conversation.topic, keyMaterial = conversation.keyMaterial)).serializedData()))
        waitForExpectations(timeout = 3)
    }

    @Test
    fun testCanLoadV1Messages() {
        // Overwrite contact as legacy so we can get v1
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = bobClient.conversations.newConversation(with = alice.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        if (!case let is v1 = aliceClient.conversations.newConversation(with = bob.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        bobConversation.send(content = "hey alice")
        bobConversation.send(content = "hey alice again")
        val messages = aliceConversation.messages()
        assertEquals(2, messages.size)
        assertEquals("hey alice", messages[1].body)
        assertEquals(bob.address, messages[1].senderAddress)
    }

    @Test
    fun testCanLoadV2Messages() {
        if (!case let is v2 = bobClient.conversations.newConversation(with = alice.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        if (!case let is v2 = aliceClient.conversations.newConversation(with = bob.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        bobConversation.send(content = "hey alice")
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals("hey alice", messages[0].body)
        assertEquals(bob.address, messages[0].senderAddress)
    }

    @Test
    fun testVerifiesV2MessageSignature() {
        if (!case let is v2 = aliceClient.conversations.newConversation(with = bob.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        val codec = TextCodec()
        val originalContent = codec.encode(content = "hello")
        val tamperedContent = codec.encode(content = "this is a fake")
        val originalPayload = originalContent.serializedData()
        val tamperedPayload = tamperedContent.serializedData()
        val date = Date()
        val header = MessageHeaderV2(topic = aliceConversation.topic, created = date)
        val headerBytes = header.serializedData()
        val digest = SHA256.hash(data = headerBytes + tamperedPayload)
        val preKey = aliceClient.keys.preKeys[0]
        val signature = preKey.sign(Data(digest))
        val bundle = aliceClient.privateKeyBundleV1.toV2().getPublicKeyBundle()
        val signedContent = SignedContent(payload = originalPayload, sender = bundle, signature = signature)
        val signedBytes = signedContent.serializedData()
        val ciphertext = Crypto.encrypt(aliceConversation.keyMaterial, signedBytes, additionalData = headerBytes)
        val tamperedMessage = MessageV2(headerBytes = headerBytes, ciphertext = ciphertext)
        aliceClient.publish(envelopes = listOf(Envelope(topic = aliceConversation.topic, timestamp = Date(), message = Message(v2 = tamperedMessage).serializedData())))
        if (!case let is v2 = bobClient.conversations.newConversation(with = alice.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        val messages = bobConversation.messages()
        assertEquals("did not filter out tampered message", 0, messages.size)
    }

    @Test
    fun testCanPaginateV1Messages() {
        // Overwrite contact as legacy so we can get v1
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = bobClient.conversations.newConversation(with = alice.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        if (!case let is v1 = aliceClient.conversations.newConversation(with = bob.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        bobConversation.send(content = "hey alice 1", sentAt = Date().addingTimeInterval(-10))
        bobConversation.send(content = "hey alice 2", sentAt = Date().addingTimeInterval(-5))
        bobConversation.send(content = "hey alice 3", sentAt = Date())
        val messages = aliceConversation.messages(limit = 1)
        assertEquals(1, messages.size)
        assertEquals("hey alice 3", messages[0].body)
        val messages2 = aliceConversation.messages(limit = 1, before = messages[0].sent)
        assertEquals(1, messages2.size)
        assertEquals("hey alice 2", messages2[0].body)
    }

    @Test
    fun testCanPaginateV2Messages() {
        if (!case let is v2 = bobClient.conversations.newConversation(with = alice.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        if (!case let is v2 = aliceClient.conversations.newConversation(with = bob.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        bobConversation.send(content = "hey alice 1", sentAt = Date().addingTimeInterval(-10))
        bobConversation.send(content = "hey alice 2", sentAt = Date().addingTimeInterval(-5))
        bobConversation.send(content = "hey alice 3", sentAt = Date())
        val messages = aliceConversation.messages(limit = 1)
        assertEquals(1, messages.size)
        assertEquals("hey alice 3", messages[0].body)
        val messages2 = aliceConversation.messages(limit = 1, before = messages[0].sent)
        assertEquals(1, messages2.size)
        assertEquals("hey alice 2", messages2[0].body)
    }

    @Test
    fun testV1ConversationCodable() {
        // Overwrite contact as legacy
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = aliceClient.conversations.newConversation(with = bob.walletAddress)) {
            XCTFail("Did not have a v1 convo with bob")
            return
        }
        conversation.send(content = "hi")
        val envelope = fakeApiClient.published.firstOrNull()(where = { it.contentTopic.hasPrefix("/xmtp/0/dm-") })!!
        val container = Conversation.v1(conversation).encodedContainer
        fakeApiClient.assertNoQuery {
            val decodedConversation = container.decode(with = aliceClient)
            val decodedMessage = decodedConversation.decode(envelope)
            assertEquals(decodedMessage.body, "hi")
        }
    }

    @Test
    fun testV2ConversationCodable() {
        if (!case let is v2 = aliceClient.conversations.newConversation(with = bob.walletAddress)) {
            XCTFail("Did not have a v2 convo with bob")
            return
        }
        conversation.send(content = "hi")
        val envelope = fakeApiClient.published.firstOrNull()(where = { it.contentTopic.hasPrefix("/xmtp/0/m-") })!!
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
        if (!case let is v1 = aliceClient.conversations.newConversation(with = bob.walletAddress)) {
            XCTFail("Did not have a convo with bob")
            return
        }
        conversation.send(content = "hi")
        val message = fakeApiClient.published.firstOrNull()(where = { it.contentTopic.hasPrefix("/xmtp/0/dm-") })!!
        val decodedMessage = conversation.decode(envelope = message)
        assertEquals("hi", decodedMessage.body)
        val decodedMessage2 = Conversation.v1(conversation).decode(message)
        assertEquals("hi", decodedMessage2.body)
    }

    @Test
    fun testDecodeSingleV2Message() {
        if (!case let is v2 = aliceClient.conversations.newConversation(with = bob.walletAddress)) {
            XCTFail("Did not have a convo with bob")
            return
        }
        conversation.send(content = "hi")
        val message = fakeApiClient.published.firstOrNull()(where = { it.contentTopic.hasPrefix("/xmtp/0/m-") })!!
        val decodedMessage = conversation.decode(envelope = message)
        assertEquals("hi", decodedMessage.body)
        val decodedMessage2 = Conversation.v2(conversation).decode(message)
        assertEquals("hi", decodedMessage2.body)
    }

    @Test
    fun testCanSendGzipCompressedV1Messages() {
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = bobClient.conversations.newConversation(with = alice.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        if (!case let is v1 = aliceClient.conversations.newConversation(with = bob.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        bobConversation.send(content = Array(repeating = "A", count = 1000).joined(), options = .init(compression = .gzip))
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals(Array(repeating = "A", count = 1000).joined(), messages[0].content())
    }

    @Test
    fun testCanSendDeflateCompressedV1Messages() {
        publishLegacyContact(client = bobClient)
        publishLegacyContact(client = aliceClient)
        if (!case let is v1 = bobClient.conversations.newConversation(with = alice.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        if (!case let is v1 = aliceClient.conversations.newConversation(with = bob.address)) {
            XCTFail("did not get a v1 conversation for alice")
            return
        }
        bobConversation.send(content = Array(repeating = "A", count = 1000).joined(), options = .init(compression = .deflate))
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals(Array(repeating = "A", count = 1000).joined(), messages[0].content())
    }

    @Test
    fun testCanSendGzipCompressedV2Messages() {
        if (!case let is v2 = bobClient.conversations.newConversation(with = alice.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        if (!case let is v2 = aliceClient.conversations.newConversation(with = bob.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        bobConversation.send(content = Array(repeating = "A", count = 1000).joined(), options = .init(compression = .gzip))
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals(Array(repeating = "A", count = 1000).joined(), messages[0].body)
        assertEquals(bob.address, messages[0].senderAddress)
    }

    @Test
    fun testCanSendDeflateCompressedV2Messages() {
        if (!case let is v2 = bobClient.conversations.newConversation(with = alice.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        if (!case let is v2 = aliceClient.conversations.newConversation(with = bob.address, context = InvitationV1.Context(conversationID = "hi"))) {
            XCTFail("did not get a v2 conversation for alice")
            return
        }
        bobConversation.send(content = Array(repeating = "A", count = 1000).joined(), options = .init(compression = .deflate))
        val messages = aliceConversation.messages()
        assertEquals(1, messages.size)
        assertEquals(Array(repeating = "A", count = 1000).joined(), messages[0].body)
        assertEquals(bob.address, messages[0].senderAddress)
    }
}
