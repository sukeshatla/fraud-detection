package com.fraudplatform.ingestion.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fraudplatform.ingestion.application.IngestTransactionService;
import com.fraudplatform.ingestion.application.IngestionReceipt;
import com.fraudplatform.ingestion.application.RateLimitDecision;
import com.fraudplatform.ingestion.application.RateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fraudplatform.ingestion.infrastructure.security.SecurityConfig;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class RateLimitInterceptorTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IngestTransactionService service;

    @MockitoBean
    private RateLimiter rateLimiter;

    @MockitoBean // jwt() supplies the authentication; real JWT decoding is covered by the ITs
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    @DisplayName("AC-002-01: empty bucket → 429 problem+json with Retry-After and RateLimit-* headers")
    void rejectsWhenBucketEmpty() {
        given(rateLimiter.tryAcquire("gw-1")).willReturn(RateLimitDecision.rejected(100, Duration.ofMillis(1500)));

        MvcTestResult result = submit("gw-1");

        assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result).hasHeader("Retry-After", "2"); // rounded UP to whole seconds
        assertThat(result).hasHeader("RateLimit-Limit", "100");
        assertThat(result).hasHeader("RateLimit-Remaining", "0");
        assertThat(result).bodyJson().extractingPath("$.type").asString().endsWith("rate-limited");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("AC-002-01: allowed request carries RateLimit-* headers and reaches the controller")
    void allowsAndAdvertisesRemainingQuota() {
        given(rateLimiter.tryAcquire("gw-1")).willReturn(RateLimitDecision.allowed(100, 42));
        given(service.ingest(any(), any()))
                .willReturn(new IngestionReceipt("txn-1", UUID.randomUUID(), Instant.now(), false));

        MvcTestResult result = submit("gw-1");

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).hasHeader("RateLimit-Limit", "100");
        assertThat(result).hasHeader("RateLimit-Remaining", "42");
    }

    private MvcTestResult submit(String clientId) {
        return mvc.post().uri("/api/v1/transactions")
                .with(jwt().jwt(j -> j.claim("azp", clientId)).authorities(new SimpleGrantedAuthority("ROLE_INGEST")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionId":"txn-1","accountId":"acc-1","amount":10.00,"currency":"USD",
                         "merchantId":"m-1","merchantCategoryCode":"5411","country":"US",
                         "channel":"CARD_PRESENT","occurredAt":"2026-10-09T18:15:30Z"}
                        """)
                .exchange();
    }
}
