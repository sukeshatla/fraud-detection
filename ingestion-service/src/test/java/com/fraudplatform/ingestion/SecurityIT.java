package com.fraudplatform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.ingestion.support.IntegrationTest;
import com.fraudplatform.ingestion.support.TransactionJson;
import com.fraudplatform.testing.TestJwts;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** AC-015-01: real signed tokens through the real filter chain (decoding, signature, expiry, roles). */
@IntegrationTest
class SecurityIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("AC-015-01: signed gateway token → 202; tampered token → 401; analyst token → 403")
    void tokenValidation() {
        String good = TestJwts.client("gw-acme", "INGEST");
        String tampered = good.substring(0, good.lastIndexOf('.') + 1) + "AAAA" + good.substring(good.lastIndexOf('.') + 5);

        assertThat(post(good)).isEqualTo(HttpStatus.ACCEPTED.value());
        assertThat(post(tampered)).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(post(TestJwts.user("analyst", "ANALYST"))).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("AC-015-05: health probe and Prometheus scrape need no token; other actuator endpoints need OPS")
    void actuatorAccess() {
        assertThat(mvc.get().uri("/actuator/health/readiness").exchange()).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/actuator/prometheus").exchange()).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/actuator/metrics").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/metrics").header("Authorization", TestJwts.bearer(TestJwts.user("ops", "OPS")))
                .exchange()).hasStatus(HttpStatus.OK);
    }

    private int post(String token) {
        return mvc.post().uri("/api/v1/transactions").header("Authorization", TestJwts.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TransactionJson.valid("txn-" + UUID.randomUUID(), "acc-sec"))
                .exchange().getResponse().getStatus();
    }
}
