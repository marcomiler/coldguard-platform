package com.coldguard.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

class RequestBodyLimitFilterTest {

  private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(DataSize.ofBytes(100));

  private static MockHttpServletRequest post(byte[] body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x");
    request.setContent(body);
    return request;
  }

  @Test
  void aBodyWithinTheLimitPasses() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<HttpServletRequest> seen = new AtomicReference<>();

    filter.doFilter(
        post(new byte[100]), response, (req, res) -> seen.set((HttpServletRequest) req));

    assertThat(seen.get()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void aDeclaredLengthOverTheLimitIsRejectedWith413AndNeverReachesTheApplication()
      throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<HttpServletRequest> seen = new AtomicReference<>();

    filter.doFilter(
        post(new byte[101]), response, (req, res) -> seen.set((HttpServletRequest) req));

    assertThat(seen.get()).isNull();
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
  }

  @Test
  void aChunkedBodyOverTheLimitIsRejectedToo() throws Exception {
    MockHttpServletRequest chunked =
        new MockHttpServletRequest("POST", "/api/v1/x") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    chunked.addHeader("Transfer-Encoding", "chunked");
    chunked.setContent(new byte[500]);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(chunked, response, (req, res) -> {});

    assertThat(response.getStatus()).isEqualTo(413);
  }

  @Test
  void aChunkedBodyWithinTheLimitIsReplayedToTheApplication() throws Exception {
    MockHttpServletRequest chunked =
        new MockHttpServletRequest("POST", "/api/v1/x") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    chunked.addHeader("Transfer-Encoding", "chunked");
    chunked.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
    AtomicReference<String> body = new AtomicReference<>();

    filter.doFilter(
        chunked,
        new MockHttpServletResponse(),
        (req, res) ->
            body.set(
                new String(
                    ((HttpServletRequest) req).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8)));

    assertThat(body.get()).isEqualTo("{\"a\":1}");
  }
}
