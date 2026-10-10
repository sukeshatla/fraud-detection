package com.fraudplatform.alerts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.alerts.application.IngestAlertService;
import com.fraudplatform.alerts.application.InvalidEventException;
import com.fraudplatform.alerts.application.NewAlert;
import com.fraudplatform.alerts.domain.Severity;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class FraudAlertListenerTest {

    static final String EVENT = """
            {"schemaVersion":1,"alertEventId":"7d9c2f4e-1a2b-4c3d-8e9f-0a1b2c3d4e5f",
             "sourceEventId":"0b8e5a0e-6f4c-4c1e-9d55-3f0e2b1a7c11","transactionId":"txn-1","accountId":"acc-1",
             "amount":7500.00,"currency":"USD","merchantId":"m-1","merchantCategoryCode":"7995","country":"MT",
             "channel":"CARD_NOT_PRESENT","occurredAt":"2026-10-09T18:15:30Z","ruleScore":85,"riskScore":88,
             "decision":"DECLINE","ruleHits":[{"code":"HIGH_AMOUNT","weight":30,"reason":"big"}],
             "scoredAt":"2026-10-09T18:15:30.450Z","mlProbability":0.93,"modelVersion":"lr-v1"}
            """;

    @Mock
    private IngestAlertService service;

    @Test
    @DisplayName("AC-007-01: FraudAlertEvent → NewAlert (id = alertEventId, severity derived, ML fields kept)")
    void mapsEvent() {
        new FraudAlertListener(service, JsonMapper.builder().build()).onAlert(new org.apache.kafka.clients.consumer.ConsumerRecord<>("t", 0, 0, "acc-1", EVENT));

        ArgumentCaptor<NewAlert> captor = ArgumentCaptor.forClass(NewAlert.class);
        verify(service).ingest(captor.capture());
        NewAlert alert = captor.getValue();
        assertThat(alert.id()).isEqualTo(UUID.fromString("7d9c2f4e-1a2b-4c3d-8e9f-0a1b2c3d4e5f"));
        assertThat(alert.severity()).isEqualTo(Severity.HIGH);
        assertThat(alert.riskScore()).isEqualTo(88);
        assertThat(alert.mlProbability()).isEqualByComparingTo("0.93");
        assertThat(alert.ruleHits()).hasSize(1);
        assertThat(alert.createdAt()).hasToString("2026-10-09T18:15:30.450Z");
    }

    @Test
    @DisplayName("Malformed payload → InvalidEventException (non-retryable → DLT)")
    void rejectsGarbage() {
        assertThatThrownBy(() -> new FraudAlertListener(service, JsonMapper.builder().build()).onAlert(new org.apache.kafka.clients.consumer.ConsumerRecord<>("t", 0, 0, "acc-1", "nope")))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(service);
    }
}
