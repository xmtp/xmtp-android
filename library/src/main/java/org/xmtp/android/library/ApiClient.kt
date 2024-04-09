package org.xmtp.android.library

import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.TlsChannelCredentials
import kotlinx.coroutines.flow.Flow
import org.xmtp.android.library.messages.Pagination
import org.xmtp.android.library.messages.Topic
import org.xmtp.proto.message.api.v1.MessageApiGrpcKt
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.BatchQueryRequest
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.BatchQueryResponse
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.Cursor
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.Envelope
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.PublishRequest
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.PublishResponse
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.QueryRequest
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.QueryResponse
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.SubscribeRequest
import uniffi.xmtpv3.FfiEnvelope
import uniffi.xmtpv3.FfiPublishRequest
import uniffi.xmtpv3.FfiV2ApiClient
import java.io.Closeable
import java.util.concurrent.TimeUnit

interface ApiClient {
    val environment: XMTPEnvironment
    fun setAuthToken(token: String)
    suspend fun query(
        topic: String,
        pagination: Pagination? = null,
        cursor: Cursor? = null,
    ): QueryResponse

    suspend fun queryTopic(topic: Topic, pagination: Pagination? = null): QueryResponse
    suspend fun batchQuery(requests: List<QueryRequest>): BatchQueryResponse
    suspend fun envelopes(topic: String, pagination: Pagination? = null): List<Envelope>
    suspend fun publish(envelopes: List<Envelope>)
    suspend fun subscribe(request: Flow<SubscribeRequest>): Flow<Envelope>
}

data class GRPCApiClient(
    override val environment: XMTPEnvironment,
    val secure: Boolean = true,
    val appVersion: String? = null,
    val rustV2Client: FfiV2ApiClient,
) :
    ApiClient, Closeable {
    companion object {
        val AUTHORIZATION_HEADER_KEY: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

        val CLIENT_VERSION_HEADER_KEY: Metadata.Key<String> =
            Metadata.Key.of("X-Client-Version", Metadata.ASCII_STRING_MARSHALLER)

        val APP_VERSION_HEADER_KEY: Metadata.Key<String> =
            Metadata.Key.of("X-App-Version", Metadata.ASCII_STRING_MARSHALLER)

        fun makeQueryRequest(
            topic: String,
            pagination: Pagination? = null,
            cursor: Cursor? = null,
        ): QueryRequest =
            QueryRequest.newBuilder()
                .addContentTopics(topic).also {
                    if (pagination != null) {
                        it.pagingInfo = pagination.pagingInfo
                    }
                    if (pagination?.before != null) {
                        it.endTimeNs = pagination.before.time * 1_000_000
                        it.pagingInfo = it.pagingInfo.toBuilder().also { info ->
                            info.direction = pagination.direction
                        }.build()
                    }
                    if (pagination?.after != null) {
                        it.startTimeNs = pagination.after.time * 1_000_000
                        it.pagingInfo = it.pagingInfo.toBuilder().also { info ->
                            info.direction = pagination.direction
                        }.build()
                    }
                    if (cursor != null) {
                        it.pagingInfo = it.pagingInfo.toBuilder().also { info ->
                            info.cursor = cursor
                        }.build()
                    }
                }.build()

        fun makeSubscribeRequest(
            topics: List<String>,
        ): SubscribeRequest = SubscribeRequest.newBuilder().addAllContentTopics(topics).build()
    }

    private var authToken: String? = null

    override fun setAuthToken(token: String) {
        authToken = token
    }

    override suspend fun query(
        topic: String,
        pagination: Pagination?,
        cursor: Cursor?,
    ): QueryResponse {
        val request = makeQueryRequest(topic, pagination, cursor)
        val headers = Metadata()

        authToken?.let { token ->
            headers.put(AUTHORIZATION_HEADER_KEY, "Bearer $token")
        }
        headers.put(CLIENT_VERSION_HEADER_KEY, Constants.VERSION)
        if (appVersion != null) {
            headers.put(APP_VERSION_HEADER_KEY, appVersion)
        }
        return rustV2Client.query(request, headers = headers)
    }

    /**
     * This is a helper for paginating through a full query.
     * It yields all the envelopes in the query using the paging info
     * from the prior response to fetch the next page.
     */
    override suspend fun envelopes(topic: String, pagination: Pagination?): List<Envelope> {
        var envelopes: MutableList<Envelope> = mutableListOf()
        var hasNextPage = true
        var cursor: Cursor? = null
        while (hasNextPage) {
            val response = query(topic = topic, pagination = pagination, cursor = cursor)
            envelopes.addAll(response.envelopesList)
            cursor = response.pagingInfo.cursor
            hasNextPage = response.envelopesList.isNotEmpty() && response.pagingInfo.hasCursor()
            if (pagination?.limit != null && envelopes.size >= pagination.limit) {
                envelopes = envelopes.take(pagination.limit).toMutableList()
                break
            }
        }
        return envelopes
    }

    override suspend fun queryTopic(topic: Topic, pagination: Pagination?): QueryResponse {
        return query(topic.description, pagination)
    }

    override suspend fun batchQuery(
        requests: List<QueryRequest>,
    ): BatchQueryResponse {
        val batchRequest = BatchQueryRequest.newBuilder().addAllRequests(requests).build()
        val headers = Metadata()

        authToken?.let { token ->
            headers.put(AUTHORIZATION_HEADER_KEY, "Bearer $token")
        }
        headers.put(CLIENT_VERSION_HEADER_KEY, Constants.VERSION)
        if (appVersion != null) {
            headers.put(APP_VERSION_HEADER_KEY, appVersion)
        }
        return rustV2Client.batchQuery(batchRequest, headers = headers)
    }

    override suspend fun publish(envelopes: List<Envelope>) {
        val ffiEnvelopes = envelopes.map { envelopeToFFi(it) }
        val request = FfiPublishRequest(envelopes = ffiEnvelopes)

        rustV2Client.publish(request = request, authToken = authToken ?: "")
    }

    override suspend fun subscribe(request: Flow<SubscribeRequest>): Flow<Envelope> {
        val headers = Metadata()

        headers.put(CLIENT_VERSION_HEADER_KEY, Constants.VERSION)
        if (appVersion != null) {
            headers.put(APP_VERSION_HEADER_KEY, appVersion)
        }

        return rustV2Client.subscribe2(request, headers)
    }

    override fun close() {
        rustV2Client.close()
    }

    private fun envelopeToFFi(envelope: Envelope): FfiEnvelope {
        FfiEnvelope(
            contentTopic = envelope.contentTopic,
            timestampNs = envelope.timestampNs.toULong(),
            message = envelope.message.toByteArray()
        )
    }
}
