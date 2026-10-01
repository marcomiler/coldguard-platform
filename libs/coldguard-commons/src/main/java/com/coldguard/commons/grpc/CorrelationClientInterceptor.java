package com.coldguard.commons.grpc;

import com.coldguard.commons.correlation.CorrelationContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/** Copies the current correlation id into the metadata of every outgoing gRPC call. */
public class CorrelationClientInterceptor implements ClientInterceptor {

  static final Metadata.Key<String> KEY =
      Metadata.Key.of(CorrelationContext.GRPC_METADATA_KEY, Metadata.ASCII_STRING_MARSHALLER);

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        CorrelationContext.current().ifPresent(id -> headers.put(KEY, id));
        super.start(responseListener, headers);
      }
    };
  }
}
