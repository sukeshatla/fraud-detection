package com.fraudplatform.scoring.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.scoring.application.AccountRiskService;
import com.fraudplatform.scoring.application.InvalidEventException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AlertResolutionListenerTest {

    @Mock
    private AccountRiskService risk;

    private AlertResolutionListener listener() {
        return new AlertResolutionListener(risk, JsonMapper.builder().build());
    }

    private static String event(String resolution) {
        return """
                {"schemaVersion":1,"eventId":"3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d","alertId":"7d9c2f4e-1a2b-4c3d-8e9f-0a1b2c3d4e5f",
                 "transactionId":"txn-1","accountId":"acc-1","resolution":"%s","resolvedBy":"analyst-1",
                 "resolvedAt":"2026-10-09T19:02:11.500Z"}""".formatted(resolution);
    }

    @Test
    @DisplayName("AC-007-09 / AC-005-05: FALSE_POSITIVE clears the account's high-risk flag")
    void falsePositiveClearsAccount() {
        listener().onResolution(event("FALSE_POSITIVE"));

        verify(risk).clear("acc-1");
    }

    @Test
    @DisplayName("CONFIRMED_FRAUD keeps the account flagged")
    void confirmedFraudKeepsFlag() {
        listener().onResolution(event("CONFIRMED_FRAUD"));

        verifyNoInteractions(risk);
    }

    @Test
    void garbageIsNonRetryable() {
        assertThatThrownBy(() -> listener().onResolution("{")).isInstanceOf(InvalidEventException.class);
    }
}
