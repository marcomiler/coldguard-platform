package com.coldguard.gateway.config;

import com.coldguard.commons.correlation.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects a request body larger than {@code coldguard.gateway.max-request-body-size} with {@code
 * 413} before anything parses it. A declared length is checked up front; a body without one
 * (chunked) is read, bounded, and replayed to the application. Runs right after the correlation
 * filter so the problem carries the correlation id, and before security.
 */
@Component
class RequestBodyLimitFilter extends OncePerRequestFilter implements Ordered {

  private final long maxBytes;

  RequestBodyLimitFilter(
      @Value("${coldguard.gateway.max-request-body-size:1MB}") DataSize maxRequestBodySize) {
    this.maxBytes = maxRequestBodySize.toBytes();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long declared = request.getContentLengthLong();
    if (declared > maxBytes) {
      reject(response);
      return;
    }
    if (declared < 0 && isChunked(request)) {
      byte[] body =
          request.getInputStream().readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
      if (body.length > maxBytes) {
        reject(response);
        return;
      }
      chain.doFilter(new ReplayedBody(request, body), response);
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean isChunked(HttpServletRequest request) {
    String encoding = request.getHeader("Transfer-Encoding");
    return encoding != null && encoding.toLowerCase().contains("chunked");
  }

  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    String correlation =
        CorrelationContext.current().map(id -> ",\"correlationId\":\"" + id + "\"").orElse("");
    response
        .getWriter()
        .write(
            "{\"title\":\"Content Too Large\",\"status\":413,"
                + "\"detail\":\"The request body is too large\","
                + "\"code\":\"REQUEST_TOO_LARGE\""
                + correlation
                + "}");
  }

  /** The bytes already read, offered again to whoever reads the body. */
  private static final class ReplayedBody extends HttpServletRequestWrapper {

    private final byte[] body;

    ReplayedBody(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return in.read();
        }

        @Override
        public boolean isFinished() {
          return in.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("Asynchronous reads are not supported");
        }
      };
    }
  }
}
