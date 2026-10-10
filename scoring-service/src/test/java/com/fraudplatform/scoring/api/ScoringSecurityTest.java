package com.fraudplatform.scoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fraudplatform.scoring.application.AccountHistoryService;
import com.fraudplatform.scoring.application.AccountRiskService;
import com.fraudplatform.scoring.application.DeadLetterReplay;
import com.fraudplatform.scoring.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** AC-015-02: the role matrix for scoring endpoints, one row per (endpoint, role). */
@Import(SecurityConfig.class)
@WebMvcTest({AccountHistoryController.class, AccountRiskController.class, AdminController.class})
class ScoringSecurityTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AccountHistoryService history;

    @MockitoBean
    private AccountRiskService risk;

    @MockitoBean
    private DeadLetterReplay replay;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @ParameterizedTest(name = "{0} {1} as {2} → {3}")
    @DisplayName("AC-015-02: role matrix")
    @CsvSource({
            "GET,    /api/v1/accounts/acc-1/transactions, NONE,       401",
            "GET,    /api/v1/accounts/acc-1/transactions, INGEST,     403",
            "GET,    /api/v1/accounts/acc-1/transactions, ANALYST,    200",
            "GET,    /api/v1/accounts/high-risk,          SUPERVISOR, 200",
            "DELETE, /api/v1/accounts/acc-1/risk,         ANALYST,    403",
            "DELETE, /api/v1/accounts/acc-1/risk,         SUPERVISOR, 204",
            "POST,   /admin/dlt/replay,                   SUPERVISOR, 403",
            "POST,   /admin/dlt/replay,                   OPS,        200",
    })
    void roleMatrix(String method, String uri, String role, int expected) {
        var request = mvc.method(HttpMethod.valueOf(method)).uri(uri);
        if (!"NONE".equals(role)) {
            request = request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        assertThat(request.exchange().getResponse().getStatus()).isEqualTo(expected);
    }
}
