package com.sankalp.fairexit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void keepsASafeIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("abc-123");
    }

    @Test
    void replacesAnUnsafeRequestId() {
        String id = RequestLoggingFilter.resolveRequestId("bad\nid injected");
        assertThat(id).doesNotContain("\n").hasSize(36); // a fresh UUID
    }

    @Test
    void createsAnIdWhenNoneIsSent() {
        assertThat(RequestLoggingFilter.resolveRequestId(null)).hasSize(36);
    }

    @Test
    void idIsInTheLogContextDuringTheRequestAndClearedAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "req-1");
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen.set(MDC.get(RequestLoggingFilter.MDC_KEY)));

        assertThat(seen.get()).isEqualTo("req-1");
        assertThat(MDC.get(RequestLoggingFilter.MDC_KEY)).isNull();
    }
}
