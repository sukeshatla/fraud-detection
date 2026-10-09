package com.fraudplatform.scoring.api;

import com.fraudplatform.scoring.application.AccountRiskService;
import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Account risk API, served from the Redis cache (cache-aside over PostgreSQL). */
@RestController
@RequestMapping("/api/v1/accounts")
class AccountRiskController {

    record RiskResponse(String accountId, boolean highRisk, Integer riskScore, String reason, Instant flaggedAt) {

        static RiskResponse from(RiskStatus status) {
            return switch (status) {
                case RiskStatus.Flagged f -> new RiskResponse(f.accountId(), true, f.account().riskScore(),
                        f.account().reason(), f.account().flaggedAt());
                case RiskStatus.Clear c -> new RiskResponse(c.accountId(), false, null, null, null);
            };
        }
    }

    private final AccountRiskService risk;

    AccountRiskController(AccountRiskService risk) {
        this.risk = risk;
    }

    @GetMapping("/{accountId}/risk")
    RiskResponse riskOf(@PathVariable String accountId) {
        return RiskResponse.from(risk.riskOf(accountId));
    }

    @DeleteMapping("/{accountId}/risk")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear(@PathVariable String accountId) {
        risk.clear(accountId);
    }

    @GetMapping("/high-risk")
    List<HighRiskAccount> listFlagged(@RequestParam(defaultValue = "100") int limit) {
        return risk.listFlagged(Math.clamp(limit, 1, 1000));
    }
}
