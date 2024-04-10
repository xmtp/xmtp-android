package org.xmtp.android.library

import com.google.protobuf.kotlin.toByteString
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.TlsChannelCredentials
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.xmtp.android.library.messages.Pagination
import org.xmtp.android.library.messages.Topic
import org.xmtp.proto.message.api.v1.MessageApiGrpcKt
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.BatchQueryRequest
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.BatchQueryResponse
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.Cursor
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.Envelope
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.PagingInfo
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.PublishRequest
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.PublishResponse
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.QueryRequest
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.QueryResponse
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.SortDirection
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.SubscribeRequest
import org.xmtp.proto.message.api.v1.queryResponse
import uniffi.xmtpv3.FfiCursor
import uniffi.xmtpv3.FfiEnvelope
import uniffi.xmtpv3.FfiPagingInfo
import uniffi.xmtpv3.FfiPublishRequest
import uniffi.xmtpv3.FfiSortDirection
import uniffi.xmtpv3.FfiV2ApiClient
import uniffi.xmtpv3.FfiV2BatchQueryRequest
import uniffi.xmtpv3.FfiV2BatchQueryResponse
import uniffi.xmtpv3.FfiV2QueryRequest
import uniffi.xmtpv3.FfiV2QueryResponse
import uniffi.xmtpv3.FfiV2SubscribeRequest
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

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
    suspend fun subscribe(topics: List<String>): Flow<Envelope>
}

data class GRPCApiClient(
    override val environment: XMTPEnvironment,
    val appVersion: String? = null,
    val rustV2Client: FfiV2ApiClient,
) :
    ApiClient, Closeable {
    companion object {
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
        return queryResponseFromFFi(rustV2Client.query(queryRequestToFFi(request)))
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
        val batchRequest = requests.map { queryRequestToFFi(it) }
        return batchQueryResponseFromFFi(rustV2Client.batchQuery(FfiV2BatchQueryRequest(requests = batchRequest)))
    }

    override suspend fun publish(envelopes: List<Envelope>) {
        val ffiEnvelopes = envelopes.map { envelopeToFFi(it) }
        val request = FfiPublishRequest(envelopes = ffiEnvelopes)

        rustV2Client.publish(request = request, authToken = authToken ?: "")
    }

    override suspend fun subscribe(topics: List<String>): Flow<Envelope> = flow {
        try {
            val subscription = rustV2Client.subscribe(FfiV2SubscribeRequest(topics))
            try {
                while (true) {
                    val nextEnvelope = subscription.next()
                    emit(envelopeFromFFi(nextEnvelope))
                }
            } catch (e: Exception) {
                throw e
            } finally {
                subscription.end()
            }
        } catch (e: Exception) {
            throw e
        }
    }
    override fun close() {
        rustV2Client.close()
    }

    private fun envelopeToFFi(envelope: Envelope): FfiEnvelope {
        return FfiEnvelope(
            contentTopic = envelope.contentTopic,
            timestampNs = envelope.timestampNs.toULong(),
            message = envelope.message.toByteArray()
        )
    }

    private fun envelopeFromFFi(envelope: FfiEnvelope): Envelope {
        return Envelope.newBuilder().also {
            it.contentTopic = envelope.contentTopic
            it.timestampNs = envelope.timestampNs.toLong()
            it.message = envelope.message.toByteString()
        }.build()
    }

    private fun queryRequestToFFi(request: QueryRequest): FfiV2QueryRequest {
        return FfiV2QueryRequest(
            contentTopics = request.contentTopicsList,
            startTimeNs = request.startTimeNs.toULong(),
            endTimeNs = request.endTimeNs.toULong(),
            pagingInfo = pagingInfoToFFi(request.pagingInfo)
        )
    }

    private fun queryResponseFromFFi(response: FfiV2QueryResponse): QueryResponse {
        return QueryResponse.newBuilder().also { queryResponse ->
            queryResponse.addAllEnvelopes(response.envelopes.map { envelopeFromFFi(it) })
            response.pagingInfo?.let {
                queryResponse.pagingInfo = pagingInfoFromFFi(it)
            }
        }.build()
    }

    private fun batchQueryResponseFromFFi(response: FfiV2BatchQueryResponse): BatchQueryResponse {
        return BatchQueryResponse.newBuilder().also { queryResponse ->
            queryResponse.addAllResponses(response.responses.map { queryResponseFromFFi(it) })
        }.build()
    }

    private fun pagingInfoFromFFi(info: FfiPagingInfo): PagingInfo {
        return PagingInfo.newBuilder().also {
            it.limit = info.limit.toInt()
            info.cursor?.let { cursor ->
                it.cursor = cursorFromFFi(cursor)
            }
            it.direction = directionFromFfi(info.direction)
        }.build()
    }

    private fun pagingInfoToFFi(info: PagingInfo): FfiPagingInfo {
        return FfiPagingInfo(
            limit = info.limit.toUInt(),
            cursor = cursorToFFi(info.cursor),
            direction = directionToFfi(info.direction)
        )
    }

    private fun directionToFfi(direction: SortDirection): FfiSortDirection {
        return when (direction) {
            SortDirection.SORT_DIRECTION_ASCENDING -> FfiSortDirection.ASCENDING
            SortDirection.SORT_DIRECTION_DESCENDING -> FfiSortDirection.DESCENDING
            else -> FfiSortDirection.UNSPECIFIED
        }
    }

    private fun directionFromFfi(direction: FfiSortDirection): SortDirection {
        return when (direction) {
            FfiSortDirection.ASCENDING -> SortDirection.SORT_DIRECTION_ASCENDING
            FfiSortDirection.DESCENDING -> SortDirection.SORT_DIRECTION_DESCENDING
            else -> SortDirection.SORT_DIRECTION_UNSPECIFIED
        }
    }

    private fun cursorToFFi(cursor: Cursor): FfiCursor {
        return FfiCursor(
            digest = cursor.index.digest.toByteArray(),
            senderTimeNs = cursor.index.senderTimeNs.toULong()
        )
    }

    private fun cursorFromFFi(cursor: FfiCursor): Cursor {
        return Cursor.newBuilder().also {
            it.index.toBuilder().also { index ->
                index.digest = cursor.digest.toByteString()
                index.senderTimeNs = cursor.senderTimeNs.toLong()
            }.build()
        }.build()
    }
}
