package com.fraudplatform.scoring.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fraudplatform.scoring.application.AccountRiskService;
import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(AccountRiskController.class)
class AccountRiskControllerTest {

    private static final HighRiskAccount FLAG =
            new HighRiskAccount("acc-1", 85, "HIGH_AMOUNT,GEO_VELOCITY", Instant.parse("2026-10-09T18:00:00Z"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AccountRiskService risk;

    @Test
    @DisplayName("AC-005-03: flagged account → highRisk true with score, reason, flaggedAt")
    void flagged() {
        given(risk.riskOf("acc-1")).willReturn(new RiskStatus.Flagged(FLAG));

        MvcTestResult result = mvc.get().uri("/api/v1/accounts/acc-1/risk").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.highRisk").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.riskScore").isEqualTo(85);
        assertThat(result).bodyJson().extractingPath("$.reason").isEqualTo("HIGH_AMOUNT,GEO_VELOCITY");
    }

    @Test
    @DisplayName("AC-005-03: clean account → highRisk false")
    void clear() {
        given(risk.riskOf("acc-2")).willReturn(new RiskStatus.Clear("acc-2"));

        assertThat(mvc.get().uri("/api/v1/accounts/acc-2/risk").exchange())
                .bodyJson().extractingPath("$.highRisk").isEqualTo(false);
    }

    @Test
    @DisplayName("AC-005-05: DELETE clears the account → 204")
    void clearAccount() {
        assertThat(mvc.delete().uri("/api/v1/accounts/acc-1/risk").exchange()).hasStatus(HttpStatus.NO_CONTENT);
        verify(risk).clear("acc-1");
    }

    @Test
    @DisplayName("AC-005-07: lists flagged accounts")
    void listFlagged() {
        given(risk.listFlagged(100)).willReturn(List.of(FLAG));

        assertThat(mvc.get().uri("/api/v1/accounts/high-risk").exchange())
                .bodyJson().extractingPath("$[0].accountId").isEqualTo("acc-1");
    }
}
