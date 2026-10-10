package com.fraudplatform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class StreamTokenResolverTest {

    private final StreamTokenResolver resolver = new StreamTokenResolver(Set.of("/api/v1/alerts/stream"));

    @Test
    @DisplayName("AC-015-02: the SSE path accepts ?access_token= (EventSource can't send headers)")
    void queryParamOnStreamPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alerts/stream");
        request.setParameter("access_token", "abc");

        assertThat(resolver.resolve(request)).isEqualTo("abc");
    }

    @Test
    @DisplayName("Every other path ignores query-string tokens (they leak into logs and history)")
    void queryParamIgnoredElsewhere() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alerts");
        request.setParameter("access_token", "abc");

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void headerAlwaysWorks() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alerts");
        request.addHeader("Authorization", "Bearer xyz");

        assertThat(resolver.resolve(request)).isEqualTo("xyz");
    }
}
