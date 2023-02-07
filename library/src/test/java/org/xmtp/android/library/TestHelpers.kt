package org.xmtp.android.library

import org.junit.Assert.assertEquals
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.Topic
import org.xmtp.proto.message.api.v1.MessageApiOuterClass

class FakeApiClient : ApiClient {
    override val environment: XMTPEnvironment = XMTPEnvironment.LOCAL
    private var authToken: String? = null
    private var responses: Map<String, List<Envelope>> = mapOf()
    var published: List<Envelope> = listOf()
    var forbiddingQueries = false

    fun assertNoPublish(callback: () -> Unit) {
        val oldCount = published.size
        callback()
        assertEquals(oldCount, published.size)
    }

    fun assertNoQuery(callback: () -> Unit) {
        forbiddingQueries = true
        callback()
        forbiddingQueries = false
    }

    fun findPublishedEnvelope(topic: Topic): Envelope? =
        findPublishedEnvelope(topic.description)

    fun findPublishedEnvelope(topic: String): Envelope? {
        for (envelope in published.reversed()) {
            if (envelope.contentTopic == topic) {
                return envelope
            }
        }
        return null
    }

    override fun setAuthToken(token: String) {
        authToken = token
    }

    override suspend fun query(topics: List<Topic>): MessageApiOuterClass.QueryResponse {
        return queryStrings(topics = topics.map { it.description })
    }

    override suspend fun queryStrings(topics: List<String>): MessageApiOuterClass.QueryResponse {
        var result: List<Envelope> = listOf()
        for (topic in topics) {
            val response = responses.toMutableMap().remove(topic)
            if (response != null) {
                result.toMutableList().addAll(response)
            }
            result.toMutableList().addAll(published.filter { it.contentTopic == topic }.reversed())
        }
        return QueryResponse.newBuilder().also {
            it.envelopesList.addAll(result)
        }.build()
    }

    override suspend fun publish(envelopes: List<MessageApiOuterClass.Envelope>): MessageApiOuterClass.PublishResponse {
        for (envelope in envelopes) {
//            send(envelope = envelope)
        }
        published.toMutableList().addAll(envelopes)
        return PublishResponse.newBuilder().build()
    }
}

data class Fixtures(val aliceAccount: PrivateKeyBuilder, val bobAccount: PrivateKeyBuilder) {
    lateinit var fakeApiClient: FakeApiClient
    lateinit var alice: PrivateKey
    lateinit var aliceClient: Client
    lateinit var bob: PrivateKey
    lateinit var bobClient: Client

    constructor() : this(aliceAccount = PrivateKeyBuilder(), bobAccount = PrivateKeyBuilder()) {
        alice = aliceAccount.getPrivateKey()
        bob = bobAccount.getPrivateKey()
        fakeApiClient = FakeApiClient()
        aliceClient = Client().create(account = aliceAccount, apiClient = fakeApiClient)
        bobClient = Client().create(account = bobAccount, apiClient = fakeApiClient)
    }
}

fun fixtures(): Fixtures =
    Fixtures()
