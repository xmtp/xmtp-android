package org.xmtp.android.library

import org.junit.Assert.assertEquals
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.Topic

data class FakeWallet(var key: PrivateKey): SigningKey {
    companion object {

        fun generate() : FakeWallet {
            val key = PrivateKey.generate()
            return FakeWallet(key)
        }
    }

    val address: String
        get() = key.walletAddress

    fun sign(data: ByteArray) : XMTP.Signature {
        val signature = key.sign(data)
        return signature
    }

    fun sign(message: String) : XMTP.Signature {
        val signature = key.sign(message = message)
        return signature
    }

    constructor(key: PrivateKey) {
        this.key = key
    }
}
enum class FakeApiClientError (val rawValue: String) : Error {
    noResponses("noResponses"), queryAssertionFailure("queryAssertionFailure");

    companion object {
        operator fun invoke(rawValue: String) = FakeApiClientError.values().firstOrNull { it.rawValue == rawValue }
    }
}

class FakeApiClient: ApiClient {
    var environment: XMTPEnvironment
    var authToken: String = ""
    private var responses: Map<String, List<Envelope>> = mapOf()
    private var stream = FakeStreamHolder()
    var published: List<Envelope> = listOf()
    var cancellable: AnyCancellable? = null
    var forbiddingQueries = false

    deinit {
        cancellable?.cancel()
    }

    fun assertNoPublish(callback: () -> Unit) {
        val oldCount = published.size
        callback()
        assertEquals(oldCount, published.size, "Published messages: ${String(describing = try { published[oldCount - 1 until published.size].map { it.jsonString() } } catch (e: Throwable) { null })}")
    }

    fun assertNoQuery(callback: () -> Unit) {
        forbiddingQueries = true
        callback()
        forbiddingQueries = false
    }

    constructor() {
        environment = .local
    }

    fun send(envelope: Envelope) {
        stream.send(envelope = envelope)
    }

    fun findPublishedEnvelope(topic: Topic) : Envelope? =
        findPublishedEnvelope(topic.description)

    fun findPublishedEnvelope(topic: String) : Envelope? {
        for (envelope in published.reversed()) {
            if (envelope.contentTopic == topic.description) {
                return envelope
            }
        }
        return null
    }

    // MARK: ApiClient conformance
    required constructor(environment: XMTP.XMTPEnvironment, _: Boolean) {
        this.environment = environment
    }

    fun setAuthToken(token: String) {
        authToken = token
    }

    fun query(topics: List<String>) : XMTP.QueryResponse {
        if (forbiddingQueries) {
            XCTFail("Attempted to query ${topics}")
            throw FakeApiClientError.queryAssertionFailure
        }
        var result: List<Envelope> = listOf()
        for (topic in topics) {
            val response = responses.removeValue(forKey = topic)
            if (response != null) {
                result.append(contentsOf = response)
            }
            result.append(contentsOf = published.filter { it.contentTopic == topic }.reversed())
        }
        var queryResponse = QueryResponse()
        queryResponse.envelopes = result
        return queryResponse
    }

    suspend fun query(topics: List<XMTP.Topic>) : XMTP.QueryResponse =
        query(topics = topics.map(\.description), pagination = pagination)

    fun publish(envelopes: List<XMTP.Envelope>) : XMTP.PublishResponse {
        for (envelope in envelopes) {
            send(envelope = envelope)
        }
        published.append(contentsOf = envelopes)
        return PublishResponse()
    }
}

data class Fixtures(
    lateinit var fakeApiClient: FakeApiClient,
    lateinit var alice: PrivateKey,
    lateinit var aliceClient: Client,
    lateinit var bob: PrivateKey,
    lateinit var bobClient: Client) {

    constructor() {
        alice = PrivateKey.generate()
        bob = PrivateKey.generate()
        fakeApiClient = FakeApiClient()
        aliceClient = Client.create(account = alice, apiClient = fakeApiClient)
        bobClient = Client.create(account = bob, apiClient = fakeApiClient)
    }
}

fun fixtures() : Fixtures =
    Fixtures()
