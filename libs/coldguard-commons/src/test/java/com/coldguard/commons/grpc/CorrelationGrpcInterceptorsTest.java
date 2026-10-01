package com.coldguard.commons.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.correlation.CorrelationContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises both interceptors over a real (in-process) gRPC transport, not mocks. */
class CorrelationGrpcInterceptorsTest {

  private static final MethodDescriptor.Marshaller<String> UTF8 =
      new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
          return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
          try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
          } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
          }
        }
      };

  private static final MethodDescriptor<String, String> ECHO =
      MethodDescriptor.<String, String>newBuilder()
          .setType(MethodDescriptor.MethodType.UNARY)
          .setFullMethodName(MethodDescriptor.generateFullMethodName("test.Echo", "Echo"))
          .setRequestMarshaller(UTF8)
          .setResponseMarshaller(UTF8)
          .build();

  private final AtomicReference<String> correlationSeenByServer = new AtomicReference<>();
  private Server server;
  private ManagedChannel channel;

  @BeforeEach
  void startServer() throws Exception {
    String name = "correlation-test-" + UUID.randomUUID();
    ServerServiceDefinition service =
        ServerServiceDefinition.builder("test.Echo")
            .addMethod(
                ECHO,
                ServerCalls.asyncUnaryCall(
                    (request, observer) -> {
                      correlationSeenByServer.set(CorrelationContext.current().orElse(null));
                      observer.onNext(request);
                      observer.onCompleted();
                    }))
            .build();
    server =
        InProcessServerBuilder.forName(name)
            .addService(ServerInterceptors.intercept(service, new CorrelationServerInterceptor()))
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).build();
  }

  @AfterEach
  void stop() {
    CorrelationContext.clear();
    channel.shutdownNow();
    server.shutdownNow();
  }

  private String call(Channel callChannel) {
    return ClientCalls.blockingUnaryCall(callChannel, ECHO, CallOptions.DEFAULT, "ping");
  }

  @Test
  void clientCorrelationId_reachesServerHandler() {
    CorrelationContext.set("corr-from-client");

    String reply = call(ClientInterceptors.intercept(channel, new CorrelationClientInterceptor()));

    assertThat(reply).isEqualTo("ping");
    assertThat(correlationSeenByServer.get()).isEqualTo("corr-from-client");
  }

  @Test
  void noCorrelationOnClient_serverGeneratesOne() {
    call(ClientInterceptors.intercept(channel, new CorrelationClientInterceptor()));

    assertThat(correlationSeenByServer.get()).isNotBlank().matches("[A-Za-z0-9._-]{1,100}");
  }

  @Test
  void unsafeMetadataValue_isReplacedByServer() {
    ClientInterceptor sendsUnsafeValue =
        new ClientInterceptor() {
          @Override
          public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
              MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, options)) {
              @Override
              public void start(Listener<RespT> listener, Metadata headers) {
                headers.put(CorrelationClientInterceptor.KEY, "evil value\nwith newline");
                super.start(listener, headers);
              }
            };
          }
        };

    call(ClientInterceptors.intercept(channel, sendsUnsafeValue));

    assertThat(correlationSeenByServer.get())
        .doesNotContain("evil")
        .matches("[A-Za-z0-9._-]{1,100}");
  }

  @Test
  void serverMdcDoesNotLeakIntoCallingThread() {
    CorrelationContext.set("caller-owned");

    call(ClientInterceptors.intercept(channel, new CorrelationClientInterceptor()));

    // The server ran on another thread; the caller's own MDC must be untouched.
    assertThat(CorrelationContext.current()).contains("caller-owned");
  }
}
