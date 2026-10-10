package com.fraudplatform.ingestion.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("AC-012-05: incoming X-Request-Id is kept, put in the MDC during the request, echoed, then cleared")
    void keepsIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "rid-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInMdc.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        });

        assertThat(seenInMdc).hasValue("rid-123");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("rid-123");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull(); // no leak to the next request on this thread
    }

    @Test
    @DisplayName("Missing or oversized X-Request-Id → a fresh id is minted")
    void mintsIdWhenAbsentOrInvalid() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
        assertThat(response.getHeader("X-Request-Id")).hasSize(36);

        MockHttpServletRequest hostile = new MockHttpServletRequest();
        hostile.addHeader("X-Request-Id", "x".repeat(500) + "\n injected");
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(hostile, second, new MockFilterChain());
        assertThat(second.getHeader("X-Request-Id")).hasSize(36);
    }
}
