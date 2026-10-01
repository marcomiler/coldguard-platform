package com.coldguard.commons.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads (or generates) the correlation id for every HTTP request, exposes it through the MDC and
 * echoes it in the response, including error responses produced by later filters.
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId =
        CorrelationContext.sanitizeOrGenerate(request.getHeader(CorrelationContext.HTTP_HEADER));
    CorrelationContext.set(correlationId);
    response.setHeader(CorrelationContext.HTTP_HEADER, correlationId);
    try {
      chain.doFilter(request, response);
    } finally {
      CorrelationContext.clear();
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
