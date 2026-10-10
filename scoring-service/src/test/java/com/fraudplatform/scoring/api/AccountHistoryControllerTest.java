package com.fraudplatform.scoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.BDDMockito.given;

import com.fraudplatform.scoring.application.AccountHistoryService;
import com.fraudplatform.scoring.application.TransactionHistoryEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fraudplatform.scoring.infrastructure.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Import(SecurityConfig.class)
@WebMvcTest(AccountHistoryController.class)
class AccountHistoryControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AccountHistoryService history;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("AC-004-07: returns the account's recent transactions with score and decision")
    void returnsHistory() {
        given(history.recent("acc-1", 20)).willReturn(List.of(new TransactionHistoryEntry(
                "txn-1", new BigDecimal("42.00"), "USD", "m-1", "5411", "US", "CARD_PRESENT",
                Instant.parse("2026-10-09T18:15:30Z"), 0, "APPROVE")));

        MvcTestResult result = mvc.get().uri("/api/v1/accounts/acc-1/transactions").with(asAnalyst).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[0].transactionId").isEqualTo("txn-1");
        assertThat(result).bodyJson().extractingPath("$[0].decision").isEqualTo("APPROVE");
    }

    @Test
    @DisplayName("AC-004-07: limit is passed through")
    void customLimit() {
        given(history.recent("acc-1", 5)).willReturn(List.of());

        assertThat(mvc.get().uri("/api/v1/accounts/acc-1/transactions?limit=5").with(asAnalyst).exchange())
                .hasStatus(HttpStatus.OK).bodyJson().isEqualTo("[]");
    }

    private static final org.springframework.test.web.servlet.request.RequestPostProcessor asAnalyst =
            jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"));
}
