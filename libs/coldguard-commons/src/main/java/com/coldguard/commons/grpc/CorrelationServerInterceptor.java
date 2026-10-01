package com.coldguard.commons.grpc;

import com.coldguard.commons.correlation.CorrelationContext;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Restores the correlation id received as gRPC metadata into the MDC for every listener callback of
 * the call. Callbacks may run on different threads, so the MDC is set and cleared around each.
 */
public class CorrelationServerInterceptor implements ServerInterceptor {

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    String correlationId =
        CorrelationContext.sanitizeOrGenerate(headers.get(CorrelationClientInterceptor.KEY));
    return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
        next.startCall(call, headers)) {
      @Override
      public void onMessage(ReqT message) {
        within(() -> super.onMessage(message));
      }

      @Override
      public void onHalfClose() {
        within(super::onHalfClose);
      }

      @Override
      public void onCancel() {
        within(super::onCancel);
      }

      @Override
      public void onComplete() {
        within(super::onComplete);
      }

      @Override
      public void onReady() {
        within(super::onReady);
      }

      private void within(Runnable action) {
        CorrelationContext.set(correlationId);
        try {
          action.run();
        } finally {
          CorrelationContext.clear();
        }
      }
    };
  }
}
