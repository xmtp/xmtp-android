package notifications.v1

import com.google.protobuf.Empty
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
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import notifications.v1.NotificationsGrpc.getServiceDescriptor

/**
 * Holder for Kotlin coroutine-based client and server APIs for notifications.v1.Notifications.
 */
public object NotificationsGrpcKt {
  public const val SERVICE_NAME: String = NotificationsGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = NotificationsGrpc.getServiceDescriptor()

  public val registerInstallationMethod:
      MethodDescriptor<Service.RegisterInstallationRequest, Service.RegisterInstallationResponse>
    @JvmStatic
    get() = NotificationsGrpc.getRegisterInstallationMethod()

  public val deleteInstallationMethod: MethodDescriptor<Service.DeleteInstallationRequest, Empty>
    @JvmStatic
    get() = NotificationsGrpc.getDeleteInstallationMethod()

  public val subscribeMethod: MethodDescriptor<Service.SubscribeRequest, Empty>
    @JvmStatic
    get() = NotificationsGrpc.getSubscribeMethod()

  public val unsubscribeMethod: MethodDescriptor<Service.UnsubscribeRequest, Empty>
    @JvmStatic
    get() = NotificationsGrpc.getUnsubscribeMethod()

  /**
   * A stub for issuing RPCs to a(n) notifications.v1.Notifications service as suspending
   * coroutines.
   */
  @StubFor(NotificationsGrpc::class)
  public class NotificationsCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<NotificationsCoroutineStub>(channel, callOptions) {
    public override fun build(channel: Channel, callOptions: CallOptions):
        NotificationsCoroutineStub = NotificationsCoroutineStub(channel, callOptions)

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
    public suspend fun registerInstallation(request: Service.RegisterInstallationRequest,
        headers: Metadata = Metadata()): Service.RegisterInstallationResponse = unaryRpc(
      channel,
      NotificationsGrpc.getRegisterInstallationMethod(),
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
    public suspend fun deleteInstallation(request: Service.DeleteInstallationRequest,
        headers: Metadata = Metadata()): Empty = unaryRpc(
      channel,
      NotificationsGrpc.getDeleteInstallationMethod(),
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
    public suspend fun subscribe(request: Service.SubscribeRequest, headers: Metadata = Metadata()):
        Empty = unaryRpc(
      channel,
      NotificationsGrpc.getSubscribeMethod(),
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
    public suspend fun unsubscribe(request: Service.UnsubscribeRequest, headers: Metadata =
        Metadata()): Empty = unaryRpc(
      channel,
      NotificationsGrpc.getUnsubscribeMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the notifications.v1.Notifications service based on Kotlin
   * coroutines.
   */
  public abstract class NotificationsCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for notifications.v1.Notifications.RegisterInstallation.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun registerInstallation(request: Service.RegisterInstallationRequest):
        Service.RegisterInstallationResponse = throw
        StatusException(UNIMPLEMENTED.withDescription("Method notifications.v1.Notifications.RegisterInstallation is unimplemented"))

    /**
     * Returns the response to an RPC for notifications.v1.Notifications.DeleteInstallation.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun deleteInstallation(request: Service.DeleteInstallationRequest): Empty =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method notifications.v1.Notifications.DeleteInstallation is unimplemented"))

    /**
     * Returns the response to an RPC for notifications.v1.Notifications.Subscribe.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun subscribe(request: Service.SubscribeRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method notifications.v1.Notifications.Subscribe is unimplemented"))

    /**
     * Returns the response to an RPC for notifications.v1.Notifications.Unsubscribe.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun unsubscribe(request: Service.UnsubscribeRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method notifications.v1.Notifications.Unsubscribe is unimplemented"))

    public final override fun bindService(): ServerServiceDefinition =
        builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = NotificationsGrpc.getRegisterInstallationMethod(),
      implementation = ::registerInstallation
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = NotificationsGrpc.getDeleteInstallationMethod(),
      implementation = ::deleteInstallation
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = NotificationsGrpc.getSubscribeMethod(),
      implementation = ::subscribe
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = NotificationsGrpc.getUnsubscribeMethod(),
      implementation = ::unsubscribe
    )).build()
  }
}
