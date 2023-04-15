package com.xmtp.message_api.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * RPC
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: message_api/v1/message_api.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class MessageApiGrpc {

  private MessageApiGrpc() {}

  public static final String SERVICE_NAME = "xmtp.message_api.v1.MessageApi";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.xmtp.message_api.v1.PublishRequest,
      com.xmtp.message_api.v1.PublishResponse> getPublishMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Publish",
      requestType = com.xmtp.message_api.v1.PublishRequest.class,
      responseType = com.xmtp.message_api.v1.PublishResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.xmtp.message_api.v1.PublishRequest,
      com.xmtp.message_api.v1.PublishResponse> getPublishMethod() {
    io.grpc.MethodDescriptor<com.xmtp.message_api.v1.PublishRequest, com.xmtp.message_api.v1.PublishResponse> getPublishMethod;
    if ((getPublishMethod = MessageApiGrpc.getPublishMethod) == null) {
      synchronized (MessageApiGrpc.class) {
        if ((getPublishMethod = MessageApiGrpc.getPublishMethod) == null) {
          MessageApiGrpc.getPublishMethod = getPublishMethod =
              io.grpc.MethodDescriptor.<com.xmtp.message_api.v1.PublishRequest, com.xmtp.message_api.v1.PublishResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Publish"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.PublishRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.PublishResponse.getDefaultInstance()))
              .build();
        }
      }
    }
    return getPublishMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.xmtp.message_api.v1.SubscribeRequest,
      com.xmtp.message_api.v1.Envelope> getSubscribeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Subscribe",
      requestType = com.xmtp.message_api.v1.SubscribeRequest.class,
      responseType = com.xmtp.message_api.v1.Envelope.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.xmtp.message_api.v1.SubscribeRequest,
      com.xmtp.message_api.v1.Envelope> getSubscribeMethod() {
    io.grpc.MethodDescriptor<com.xmtp.message_api.v1.SubscribeRequest, com.xmtp.message_api.v1.Envelope> getSubscribeMethod;
    if ((getSubscribeMethod = MessageApiGrpc.getSubscribeMethod) == null) {
      synchronized (MessageApiGrpc.class) {
        if ((getSubscribeMethod = MessageApiGrpc.getSubscribeMethod) == null) {
          MessageApiGrpc.getSubscribeMethod = getSubscribeMethod =
              io.grpc.MethodDescriptor.<com.xmtp.message_api.v1.SubscribeRequest, com.xmtp.message_api.v1.Envelope>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Subscribe"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.SubscribeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.Envelope.getDefaultInstance()))
              .build();
        }
      }
    }
    return getSubscribeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.xmtp.message_api.v1.SubscribeAllRequest,
      com.xmtp.message_api.v1.Envelope> getSubscribeAllMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SubscribeAll",
      requestType = com.xmtp.message_api.v1.SubscribeAllRequest.class,
      responseType = com.xmtp.message_api.v1.Envelope.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.xmtp.message_api.v1.SubscribeAllRequest,
      com.xmtp.message_api.v1.Envelope> getSubscribeAllMethod() {
    io.grpc.MethodDescriptor<com.xmtp.message_api.v1.SubscribeAllRequest, com.xmtp.message_api.v1.Envelope> getSubscribeAllMethod;
    if ((getSubscribeAllMethod = MessageApiGrpc.getSubscribeAllMethod) == null) {
      synchronized (MessageApiGrpc.class) {
        if ((getSubscribeAllMethod = MessageApiGrpc.getSubscribeAllMethod) == null) {
          MessageApiGrpc.getSubscribeAllMethod = getSubscribeAllMethod =
              io.grpc.MethodDescriptor.<com.xmtp.message_api.v1.SubscribeAllRequest, com.xmtp.message_api.v1.Envelope>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SubscribeAll"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.SubscribeAllRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.Envelope.getDefaultInstance()))
              .build();
        }
      }
    }
    return getSubscribeAllMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.xmtp.message_api.v1.QueryRequest,
      com.xmtp.message_api.v1.QueryResponse> getQueryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Query",
      requestType = com.xmtp.message_api.v1.QueryRequest.class,
      responseType = com.xmtp.message_api.v1.QueryResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.xmtp.message_api.v1.QueryRequest,
      com.xmtp.message_api.v1.QueryResponse> getQueryMethod() {
    io.grpc.MethodDescriptor<com.xmtp.message_api.v1.QueryRequest, com.xmtp.message_api.v1.QueryResponse> getQueryMethod;
    if ((getQueryMethod = MessageApiGrpc.getQueryMethod) == null) {
      synchronized (MessageApiGrpc.class) {
        if ((getQueryMethod = MessageApiGrpc.getQueryMethod) == null) {
          MessageApiGrpc.getQueryMethod = getQueryMethod =
              io.grpc.MethodDescriptor.<com.xmtp.message_api.v1.QueryRequest, com.xmtp.message_api.v1.QueryResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Query"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.QueryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.QueryResponse.getDefaultInstance()))
              .build();
        }
      }
    }
    return getQueryMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.xmtp.message_api.v1.BatchQueryRequest,
      com.xmtp.message_api.v1.BatchQueryResponse> getBatchQueryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "BatchQuery",
      requestType = com.xmtp.message_api.v1.BatchQueryRequest.class,
      responseType = com.xmtp.message_api.v1.BatchQueryResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.xmtp.message_api.v1.BatchQueryRequest,
      com.xmtp.message_api.v1.BatchQueryResponse> getBatchQueryMethod() {
    io.grpc.MethodDescriptor<com.xmtp.message_api.v1.BatchQueryRequest, com.xmtp.message_api.v1.BatchQueryResponse> getBatchQueryMethod;
    if ((getBatchQueryMethod = MessageApiGrpc.getBatchQueryMethod) == null) {
      synchronized (MessageApiGrpc.class) {
        if ((getBatchQueryMethod = MessageApiGrpc.getBatchQueryMethod) == null) {
          MessageApiGrpc.getBatchQueryMethod = getBatchQueryMethod =
              io.grpc.MethodDescriptor.<com.xmtp.message_api.v1.BatchQueryRequest, com.xmtp.message_api.v1.BatchQueryResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "BatchQuery"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.BatchQueryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  com.xmtp.message_api.v1.BatchQueryResponse.getDefaultInstance()))
              .build();
        }
      }
    }
    return getBatchQueryMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static MessageApiStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MessageApiStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MessageApiStub>() {
        @java.lang.Override
        public MessageApiStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MessageApiStub(channel, callOptions);
        }
      };
    return MessageApiStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static MessageApiBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MessageApiBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MessageApiBlockingStub>() {
        @java.lang.Override
        public MessageApiBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MessageApiBlockingStub(channel, callOptions);
        }
      };
    return MessageApiBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static MessageApiFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<MessageApiFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<MessageApiFutureStub>() {
        @java.lang.Override
        public MessageApiFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new MessageApiFutureStub(channel, callOptions);
        }
      };
    return MessageApiFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * RPC
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Publish messages to the network
     * </pre>
     */
    default void publish(com.xmtp.message_api.v1.PublishRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.PublishResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPublishMethod(), responseObserver);
    }

    /**
     * <pre>
     * Subscribe to a stream of new envelopes matching a predicate
     * </pre>
     */
    default void subscribe(com.xmtp.message_api.v1.SubscribeRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.Envelope> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubscribeMethod(), responseObserver);
    }

    /**
     * <pre>
     * Subscribe to a stream of all messages
     * </pre>
     */
    default void subscribeAll(com.xmtp.message_api.v1.SubscribeAllRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.Envelope> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubscribeAllMethod(), responseObserver);
    }

    /**
     * <pre>
     * Query the store for messages
     * </pre>
     */
    default void query(com.xmtp.message_api.v1.QueryRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.QueryResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getQueryMethod(), responseObserver);
    }

    /**
     * <pre>
     * BatchQuery containing a set of queries to be processed
     * </pre>
     */
    default void batchQuery(com.xmtp.message_api.v1.BatchQueryRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.BatchQueryResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getBatchQueryMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service MessageApi.
   * <pre>
   * RPC
   * </pre>
   */
  public static abstract class MessageApiImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return MessageApiGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service MessageApi.
   * <pre>
   * RPC
   * </pre>
   */
  public static final class MessageApiStub
      extends io.grpc.stub.AbstractAsyncStub<MessageApiStub> {
    private MessageApiStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MessageApiStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MessageApiStub(channel, callOptions);
    }

    /**
     * <pre>
     * Publish messages to the network
     * </pre>
     */
    public void publish(com.xmtp.message_api.v1.PublishRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.PublishResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Subscribe to a stream of new envelopes matching a predicate
     * </pre>
     */
    public void subscribe(com.xmtp.message_api.v1.SubscribeRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.Envelope> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getSubscribeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Subscribe to a stream of all messages
     * </pre>
     */
    public void subscribeAll(com.xmtp.message_api.v1.SubscribeAllRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.Envelope> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getSubscribeAllMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Query the store for messages
     * </pre>
     */
    public void query(com.xmtp.message_api.v1.QueryRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.QueryResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getQueryMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * BatchQuery containing a set of queries to be processed
     * </pre>
     */
    public void batchQuery(com.xmtp.message_api.v1.BatchQueryRequest request,
        io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.BatchQueryResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getBatchQueryMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service MessageApi.
   * <pre>
   * RPC
   * </pre>
   */
  public static final class MessageApiBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<MessageApiBlockingStub> {
    private MessageApiBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MessageApiBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MessageApiBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Publish messages to the network
     * </pre>
     */
    public com.xmtp.message_api.v1.PublishResponse publish(com.xmtp.message_api.v1.PublishRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPublishMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Subscribe to a stream of new envelopes matching a predicate
     * </pre>
     */
    public java.util.Iterator<com.xmtp.message_api.v1.Envelope> subscribe(
        com.xmtp.message_api.v1.SubscribeRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getSubscribeMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Subscribe to a stream of all messages
     * </pre>
     */
    public java.util.Iterator<com.xmtp.message_api.v1.Envelope> subscribeAll(
        com.xmtp.message_api.v1.SubscribeAllRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getSubscribeAllMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Query the store for messages
     * </pre>
     */
    public com.xmtp.message_api.v1.QueryResponse query(com.xmtp.message_api.v1.QueryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getQueryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * BatchQuery containing a set of queries to be processed
     * </pre>
     */
    public com.xmtp.message_api.v1.BatchQueryResponse batchQuery(com.xmtp.message_api.v1.BatchQueryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getBatchQueryMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service MessageApi.
   * <pre>
   * RPC
   * </pre>
   */
  public static final class MessageApiFutureStub
      extends io.grpc.stub.AbstractFutureStub<MessageApiFutureStub> {
    private MessageApiFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected MessageApiFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new MessageApiFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Publish messages to the network
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.xmtp.message_api.v1.PublishResponse> publish(
        com.xmtp.message_api.v1.PublishRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPublishMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Query the store for messages
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.xmtp.message_api.v1.QueryResponse> query(
        com.xmtp.message_api.v1.QueryRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getQueryMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * BatchQuery containing a set of queries to be processed
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.xmtp.message_api.v1.BatchQueryResponse> batchQuery(
        com.xmtp.message_api.v1.BatchQueryRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getBatchQueryMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_PUBLISH = 0;
  private static final int METHODID_SUBSCRIBE = 1;
  private static final int METHODID_SUBSCRIBE_ALL = 2;
  private static final int METHODID_QUERY = 3;
  private static final int METHODID_BATCH_QUERY = 4;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_PUBLISH:
          serviceImpl.publish((com.xmtp.message_api.v1.PublishRequest) request,
              (io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.PublishResponse>) responseObserver);
          break;
        case METHODID_SUBSCRIBE:
          serviceImpl.subscribe((com.xmtp.message_api.v1.SubscribeRequest) request,
              (io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.Envelope>) responseObserver);
          break;
        case METHODID_SUBSCRIBE_ALL:
          serviceImpl.subscribeAll((com.xmtp.message_api.v1.SubscribeAllRequest) request,
              (io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.Envelope>) responseObserver);
          break;
        case METHODID_QUERY:
          serviceImpl.query((com.xmtp.message_api.v1.QueryRequest) request,
              (io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.QueryResponse>) responseObserver);
          break;
        case METHODID_BATCH_QUERY:
          serviceImpl.batchQuery((com.xmtp.message_api.v1.BatchQueryRequest) request,
              (io.grpc.stub.StreamObserver<com.xmtp.message_api.v1.BatchQueryResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getPublishMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.xmtp.message_api.v1.PublishRequest,
              com.xmtp.message_api.v1.PublishResponse>(
                service, METHODID_PUBLISH)))
        .addMethod(
          getSubscribeMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.xmtp.message_api.v1.SubscribeRequest,
              com.xmtp.message_api.v1.Envelope>(
                service, METHODID_SUBSCRIBE)))
        .addMethod(
          getSubscribeAllMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.xmtp.message_api.v1.SubscribeAllRequest,
              com.xmtp.message_api.v1.Envelope>(
                service, METHODID_SUBSCRIBE_ALL)))
        .addMethod(
          getQueryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.xmtp.message_api.v1.QueryRequest,
              com.xmtp.message_api.v1.QueryResponse>(
                service, METHODID_QUERY)))
        .addMethod(
          getBatchQueryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.xmtp.message_api.v1.BatchQueryRequest,
              com.xmtp.message_api.v1.BatchQueryResponse>(
                service, METHODID_BATCH_QUERY)))
        .build();
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (MessageApiGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .addMethod(getPublishMethod())
              .addMethod(getSubscribeMethod())
              .addMethod(getSubscribeAllMethod())
              .addMethod(getQueryMethod())
              .addMethod(getBatchQueryMethod())
              .build();
        }
      }
    }
    return result;
  }
}
