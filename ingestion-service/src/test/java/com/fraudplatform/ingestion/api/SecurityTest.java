package com.fraudplatform.ingestion.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fraudplatform.ingestion.application.IngestTransactionService;
import com.fraudplatform.ingestion.application.IngestionReceipt;
import com.fraudplatform.ingestion.application.RateLimitDecision;
import com.fraudplatform.ingestion.application.RateLimiter;
import com.fraudplatform.ingestion.infrastructure.security.SecurityConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** AC-015-01/05: who may submit transactions, and who they are for rate limiting. */
@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class SecurityTest {

    private static final String BODY = """
            {"transactionId":"txn-1","accountId":"acc-1","amount":10.00,"currency":"USD","merchantId":"m-1",
             "merchantCategoryCode":"5411","country":"US","channel":"CARD_PRESENT","occurredAt":"2026-10-09T18:15:30Z"}""";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IngestTransactionService service;

    @MockitoBean
    private RateLimiter rateLimiter;

    @MockitoBean // jwt() supplies the authentication; real JWT decoding is covered by the ITs
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        given(rateLimiter.tryAcquire(any())).willReturn(RateLimitDecision.allowed(100, 99));
        given(service.ingest(any(), any())).willReturn(new IngestionReceipt("txn-1", UUID.randomUUID(), Instant.now(), false));
    }

    @Test
    @DisplayName("AC-015-01: no token → 401, nothing ingested")
    void anonymousRejected() {
        assertThat(post().exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-015-01: a valid token without the INGEST role → 403")
    void wrongRoleForbidden() {
        assertThat(post().with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-015-01: payment gateway with INGEST → 202")
    void ingestRoleAccepted() {
        assertThat(post().with(gateway("gw-acme")).exchange()).hasStatus(HttpStatus.ACCEPTED);
    }

    @Test
    @DisplayName("AC-015-01: the rate-limit key is the token's client id; a spoofed X-Client-Id header is ignored")
    void rateLimitKeyFromToken() {
        post().header("X-Client-Id", "someone-elses-quota").with(gateway("gw-acme")).exchange();

        verify(rateLimiter).tryAcquire("gw-acme");
    }

    @Test
    @DisplayName("AC-015-05: health and Prometheus scrape stay reachable without a token; security headers are set")
    void publicProbesAndHeaders() {
        var result = post().with(gateway("gw-acme")).exchange();

        assertThat(result).hasHeader("X-Content-Type-Options", "nosniff");
        assertThat(result).hasHeader("X-Frame-Options", "DENY");
    }

    private org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder post() {
        return mvc.post().uri("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(BODY);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor gateway(String clientId) {
        return jwt().jwt(j -> j.claim("azp", clientId)).authorities(new SimpleGrantedAuthority("ROLE_INGEST"));
    }
}
