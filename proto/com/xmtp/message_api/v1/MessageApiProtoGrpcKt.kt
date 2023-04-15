package com.xmtp.message_api.v1

import com.xmtp.message_api.v1.MessageApiGrpc.getServiceDescriptor
import io.grpc.CallOptions
import io.grpc.CallOptions.DEFAULT
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.ServerServiceDefinition.builder
import io.grpc.ServiceDescriptor
import io.grpc.Status
import io.grpc.Status.UNIMPLEMENTED
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls
import io.grpc.kotlin.ClientCalls.serverStreamingRpc
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls
import io.grpc.kotlin.ServerCalls.serverStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow

/**
 * Holder for Kotlin coroutine-based client and server APIs for xmtp.message_api.v1.MessageApi.
 */
public object MessageApiGrpcKt {
  public const val SERVICE_NAME: String = MessageApiGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = MessageApiGrpc.getServiceDescriptor()

  public val publishMethod: MethodDescriptor<PublishRequest, PublishResponse>
    @JvmStatic
    get() = MessageApiGrpc.getPublishMethod()

  public val subscribeMethod: MethodDescriptor<SubscribeRequest, Envelope>
    @JvmStatic
    get() = MessageApiGrpc.getSubscribeMethod()

  public val subscribeAllMethod: MethodDescriptor<SubscribeAllRequest, Envelope>
    @JvmStatic
    get() = MessageApiGrpc.getSubscribeAllMethod()

  public val queryMethod: MethodDescriptor<QueryRequest, QueryResponse>
    @JvmStatic
    get() = MessageApiGrpc.getQueryMethod()

  public val batchQueryMethod: MethodDescriptor<BatchQueryRequest, BatchQueryResponse>
    @JvmStatic
    get() = MessageApiGrpc.getBatchQueryMethod()

  /**
   * A stub for issuing RPCs to a(n) xmtp.message_api.v1.MessageApi service as suspending
   * coroutines.
   */
  @StubFor(MessageApiGrpc::class)
  public class MessageApiCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<MessageApiCoroutineStub>(channel, callOptions) {
    public override fun build(channel: Channel, callOptions: CallOptions): MessageApiCoroutineStub =
        MessageApiCoroutineStub(channel, callOptions)

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun publish(request: PublishRequest, headers: Metadata = Metadata()):
        PublishResponse = unaryRpc(
      channel,
      MessageApiGrpc.getPublishMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun subscribe(request: SubscribeRequest, headers: Metadata = Metadata()): Flow<Envelope>
        = serverStreamingRpc(
      channel,
      MessageApiGrpc.getSubscribeMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun subscribeAll(request: SubscribeAllRequest, headers: Metadata = Metadata()):
        Flow<Envelope> = serverStreamingRpc(
      channel,
      MessageApiGrpc.getSubscribeAllMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun query(request: QueryRequest, headers: Metadata = Metadata()): QueryResponse =
        unaryRpc(
      channel,
      MessageApiGrpc.getQueryMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun batchQuery(request: BatchQueryRequest, headers: Metadata = Metadata()):
        BatchQueryResponse = unaryRpc(
      channel,
      MessageApiGrpc.getBatchQueryMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the xmtp.message_api.v1.MessageApi service based on Kotlin
   * coroutines.
   */
  public abstract class MessageApiCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for xmtp.message_api.v1.MessageApi.Publish.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun publish(request: PublishRequest): PublishResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method xmtp.message_api.v1.MessageApi.Publish is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for xmtp.message_api.v1.MessageApi.Subscribe.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open fun subscribe(request: SubscribeRequest): Flow<Envelope> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method xmtp.message_api.v1.MessageApi.Subscribe is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for xmtp.message_api.v1.MessageApi.SubscribeAll.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open fun subscribeAll(request: SubscribeAllRequest): Flow<Envelope> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method xmtp.message_api.v1.MessageApi.SubscribeAll is unimplemented"))

    /**
     * Returns the response to an RPC for xmtp.message_api.v1.MessageApi.Query.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun query(request: QueryRequest): QueryResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method xmtp.message_api.v1.MessageApi.Query is unimplemented"))

    /**
     * Returns the response to an RPC for xmtp.message_api.v1.MessageApi.BatchQuery.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun batchQuery(request: BatchQueryRequest): BatchQueryResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method xmtp.message_api.v1.MessageApi.BatchQuery is unimplemented"))

    public final override fun bindService(): ServerServiceDefinition =
        builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = MessageApiGrpc.getPublishMethod(),
      implementation = ::publish
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = MessageApiGrpc.getSubscribeMethod(),
      implementation = ::subscribe
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = MessageApiGrpc.getSubscribeAllMethod(),
      implementation = ::subscribeAll
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = MessageApiGrpc.getQueryMethod(),
      implementation = ::query
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = MessageApiGrpc.getBatchQueryMethod(),
      implementation = ::batchQuery
    )).build()
  }
}
