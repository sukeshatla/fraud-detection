package com.fraudplatform.scoring.application;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Decision;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RuleEngine;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.Transaction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScoreTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T18:15:31Z");

    @Mock
    private ProcessedEventStore processed;

    @Mock
    private AccountActivityStore activityStore;

    @Mock
    private AlertPublisher alerts;

    private final Transaction tx = aTransaction().build();

    private ScoreTransactionService serviceWithRuleWeight(int weight) {
        FraudRule rule = new FraudRule() {
            @Override
            public String code() {
                return "TEST";
            }

            @Override
            public Optional<RuleHit> evaluate(Transaction t, AccountActivity a) {
                return weight == 0 ? Optional.empty() : Optional.of(new RuleHit("TEST", weight, "test"));
            }
        };
        return new ScoreTransactionService(processed, activityStore, new RuleEngine(List.of(rule)), alerts,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void freshEvent() {
        given(processed.isProcessed(tx.eventId())).willReturn(false);
    }

    @Test
    @DisplayName("AC-003-10: REVIEW/DECLINE publishes an alert carrying score, decision and hits")
    void publishesAlertForRiskyTransaction() {
        given(activityStore.recordAndGet(tx)).willReturn(AccountActivity.none());

        RiskAssessment assessment = serviceWithRuleWeight(80).score(tx).orElseThrow();

        assertThat(assessment.decision()).isEqualTo(Decision.DECLINE);
        assertThat(assessment.riskScore()).isEqualTo(80);
        assertThat(assessment.scoredAt()).isEqualTo(NOW);
        verify(alerts).publish(assessment);
    }

    @Test
    @DisplayName("AC-003-10: APPROVE raises no alert")
    void noAlertForCleanTransaction() {
        given(activityStore.recordAndGet(tx)).willReturn(AccountActivity.none());

        assertThat(serviceWithRuleWeight(0).score(tx)).get().extracting(RiskAssessment::decision).isEqualTo(Decision.APPROVE);
        verify(alerts, never()).publish(any());
    }

    @Test
    @DisplayName("AC-003-08: already-processed eventId is skipped entirely")
    void skipsDuplicateDelivery() {
        given(processed.isProcessed(tx.eventId())).willReturn(true);

        assertThat(serviceWithRuleWeight(80).score(tx)).isEmpty();
        verifyNoInteractions(activityStore, alerts);
    }

    @Test
    @DisplayName("AC-003-08: event is marked processed only AFTER the alert is published (at-least-once)")
    void marksProcessedAfterSideEffects() {
        given(activityStore.recordAndGet(tx)).willReturn(AccountActivity.none());

        serviceWithRuleWeight(80).score(tx);

        InOrder order = inOrder(activityStore, alerts, processed);
        order.verify(activityStore).recordAndGet(tx);
        order.verify(alerts).publish(any());
        order.verify(processed).markProcessed(tx.eventId());
    }

    @Test
    @DisplayName("AC-003-08: failure before completion leaves the event unmarked so redelivery retries it")
    void doesNotMarkOnFailure() {
        given(activityStore.recordAndGet(tx)).willReturn(AccountActivity.none());
        doThrow(new IllegalStateException("kafka down")).when(alerts).publish(any());

        assertThatThrownBy(() -> serviceWithRuleWeight(80).score(tx)).isInstanceOf(IllegalStateException.class);
        verify(processed, never()).markProcessed(any());
    }
}
