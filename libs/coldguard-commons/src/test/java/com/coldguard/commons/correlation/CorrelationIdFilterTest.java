package com.coldguard.commons.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void validHeader_isPropagatedToMdcAndEchoedInResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationContext.HTTP_HEADER, "corr-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seenInChain = new AtomicReference<>();
    FilterChain chain = (req, res) -> seenInChain.set(CorrelationContext.current().orElse(null));

    filter.doFilter(request, response, chain);

    assertThat(seenInChain.get()).isEqualTo("corr-123");
    assertThat(response.getHeader(CorrelationContext.HTTP_HEADER)).isEqualTo("corr-123");
  }

  @Test
  void missingHeader_generatesIdVisibleInChainAndResponse() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seenInChain = new AtomicReference<>();
    FilterChain chain = (req, res) -> seenInChain.set(CorrelationContext.current().orElse(null));

    filter.doFilter(new MockHttpServletRequest(), response, chain);

    String generated = response.getHeader(CorrelationContext.HTTP_HEADER);
    assertThat(generated).isNotBlank();
    assertThat(seenInChain.get()).isEqualTo(generated);
  }

  @Test
  void unsafeHeader_isReplacedAndNeverEchoed() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationContext.HTTP_HEADER, "bad id;<x>");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(CorrelationContext.HTTP_HEADER))
        .isNotEqualTo("bad id;<x>")
        .matches("[A-Za-z0-9._-]{1,100}");
  }

  @Test
  void mdcIsClearedAfterSuccessfulRequest() throws Exception {
    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (r, s) -> {});

    assertThat(CorrelationContext.current()).isEmpty();
  }

  @Test
  void mdcIsClearedWhenChainThrows() {
    FilterChain failing =
        (req, res) -> {
          throw new IllegalStateException("boom");
        };

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), failing))
        .isInstanceOf(IllegalStateException.class);
    assertThat(CorrelationContext.current()).isEmpty();
  }

  @Test
  void runsFirst() {
    assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }
}
