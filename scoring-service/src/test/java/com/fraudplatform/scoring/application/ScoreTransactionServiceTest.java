package com.fraudplatform.scoring.application;

import static com.fraudplatform.scoring.domain.TransactionBuilder.aTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fraudplatform.scoring.domain.AccountActivity;
import com.fraudplatform.scoring.domain.Decision;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RiskAssessment;
import com.fraudplatform.scoring.domain.RuleEngine;
import com.fraudplatform.scoring.domain.RuleHit;
import com.fraudplatform.scoring.domain.HighRiskAccount;
import com.fraudplatform.scoring.domain.RiskStatus;
import com.fraudplatform.scoring.domain.Transaction;
import com.fraudplatform.scoring.domain.ml.MlPrediction;
import com.fraudplatform.scoring.domain.ml.ScoreBlender;
import com.fraudplatform.scoring.domain.rules.KnownHighRiskAccountRule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private AssessmentRepository repository;

    @Mock
    private AlertPublisher alerts;

    private final FakeHighRiskAccountCache riskCache = new FakeHighRiskAccountCache();

    /** Default: model unavailable (rules only); individual tests stub a prediction. */
    private MlScorer ml = (tx, activity) -> Optional.empty();

    private ScoreTransactionService service;

    /** Test rule: amounts ≥ 1000 score 80 (DECLINE), everything else 0 (APPROVE). */
    private static final FraudRule BIG_IS_BAD = new FraudRule() {
        @Override
        public String code() {
            return "BIG";
        }

        @Override
        public Optional<RuleHit> evaluate(Transaction t, AccountActivity a) {
            return t.amount().compareTo(new BigDecimal("1000")) >= 0 ? Optional.of(new RuleHit("BIG", 80, "big")) : Optional.empty();
        }
    };

    private final Transaction risky = aTransaction().eventId(UUID.randomUUID()).transactionId("t-risky").amount("5000").build();
    private final Transaction clean = aTransaction().eventId(UUID.randomUUID()).transactionId("t-clean").accountId("acc-clean").amount("10").build();

    @BeforeEach
    void setUp() {
        service = new ScoreTransactionService(processed, activityStore,
                new RuleEngine(List.of(BIG_IS_BAD, new KnownHighRiskAccountRule())), repository, riskCache, ml,
                new ScoreBlender(0.6), alerts,
                Clock.fixed(NOW, ZoneOffset.UTC), 8);
        lenient().when(activityStore.recordAndGet(any())).thenReturn(AccountActivity.none());
    }

    @Test
    @DisplayName("AC-004-02: the whole batch is persisted with ONE repository call")
    void persistsBatchOnce() {
        List<RiskAssessment> result = service.scoreBatch(List.of(risky, clean));

        assertThat(result).extracting(RiskAssessment::decision).containsExactly(Decision.DECLINE, Decision.APPROVE);
        verify(repository, times(1)).saveAll(result);
    }

    @Test
    @DisplayName("AC-003-10: only REVIEW/DECLINE assessments raise alerts")
    void alertsOnlyForRisky() {
        service.scoreBatch(List.of(risky, clean));

        ArgumentCaptor<RiskAssessment> captor = ArgumentCaptor.forClass(RiskAssessment.class);
        verify(alerts).publish(captor.capture());
        assertThat(captor.getValue().transaction()).isEqualTo(risky);
        assertThat(captor.getValue().scoredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC-003-08: already-processed events are skipped; nothing to do → no DB call")
    void skipsProcessed() {
        given(processed.isProcessed(risky.eventId())).willReturn(true);

        assertThat(service.scoreBatch(List.of(risky))).isEmpty();
        verifyNoInteractions(activityStore, repository, alerts);
    }

    @Test
    @DisplayName("AC-003-08: the same eventId twice in one batch is scored once")
    void dedupesWithinBatch() {
        assertThat(service.scoreBatch(List.of(risky, risky))).hasSize(1);
        verify(alerts, times(1)).publish(any());
    }

    @Test
    @DisplayName("Order: persist → alert → mark processed (at-least-once, idempotent replays)")
    void ordering() {
        service.scoreBatch(List.of(risky));

        InOrder order = inOrder(repository, alerts, processed);
        order.verify(repository).saveAll(anyList());
        order.verify(alerts).publish(any());
        order.verify(processed).markProcessed(risky.eventId());
    }

    @Test
    @DisplayName("DB failure → no alerts, nothing marked; the whole poll is redelivered")
    void dbFailureStopsTheBatch() {
        doThrow(new IllegalStateException("db down")).when(repository).saveAll(anyList());

        assertThatThrownBy(() -> service.scoreBatch(List.of(risky))).isInstanceOf(IllegalStateException.class);
        verify(alerts, never()).publish(any());
        verify(processed, never()).markProcessed(any());
    }

    @Test
    @DisplayName("Alert failure → events not marked processed, so redelivery retries them")
    void alertFailureLeavesEventsUnmarked() {
        doThrow(new IllegalStateException("kafka down")).when(alerts).publish(any());

        assertThatThrownBy(() -> service.scoreBatch(List.of(risky))).isInstanceOf(IllegalStateException.class);
        verify(processed, never()).markProcessed(any());
    }

    @Test
    @DisplayName("AC-005-02: account already flagged in the cache gets KNOWN_HIGH_RISK")
    void flaggedAccountGetsKnownHighRisk() {
        riskCache.put(new RiskStatus.Flagged(new HighRiskAccount(clean.accountId(), 90, "X", NOW)));

        RiskAssessment assessment = service.scoreBatch(List.of(clean)).getFirst();

        assertThat(assessment.hits()).extracting(RuleHit::code).containsExactly("KNOWN_HIGH_RISK");
        assertThat(assessment.decision()).isEqualTo(Decision.REVIEW);
    }

    @Test
    @DisplayName("AC-005-01: a DECLINE flags the account in the cache, after the DB write")
    void declineFlagsAccountAfterPersist() {
        service.scoreBatch(List.of(risky));

        assertThat(riskCache.get(risky.accountId())).get().satisfies(status -> {
            assertThat(status.isHighRisk()).isTrue();
            assertThat(((RiskStatus.Flagged) status).account().riskScore()).isEqualTo(80);
        });
    }

    @Test
    @DisplayName("AC-005-08: a later record of a just-declined account in the SAME poll counts as high-risk")
    void flagPropagatesWithinBatch() {
        Transaction sameAccountLater = aTransaction().eventId(UUID.randomUUID()).transactionId("t-later")
                .accountId(risky.accountId()).amount("10").build();

        List<RiskAssessment> result = service.scoreBatch(List.of(risky, sameAccountLater));

        assertThat(result.get(1).hits()).extracting(RuleHit::code).containsExactly("KNOWN_HIGH_RISK");
    }

    @Test
    @DisplayName("AC-005-01: DB failure → the cache is NOT flagged (cache reflects committed state only)")
    void noCacheWriteWhenPersistFails() {
        doThrow(new IllegalStateException("db down")).when(repository).saveAll(anyList());

        assertThatThrownBy(() -> service.scoreBatch(List.of(risky))).isInstanceOf(IllegalStateException.class);
        assertThat(riskCache.get(risky.accountId())).isEmpty();
    }

    @Test
    @DisplayName("AC-006-03: the model escalates a rule-clean transaction; probability + version are kept")
    void modelEscalatesRuleCleanTransaction() {
        ml = (tx, activity) -> Optional.of(new MlPrediction(0.97, "lr-test"));
        setUp();

        RiskAssessment assessment = service.scoreBatch(List.of(clean)).getFirst();

        assertThat(assessment.ruleScore()).isZero();
        assertThat(assessment.riskScore()).isEqualTo(39); // round(0.4 · 97)
        assertThat(assessment.mlPrediction()).contains(new MlPrediction(0.97, "lr-test"));
    }

    @Test
    @DisplayName("AC-006-03: the model never dilutes a rule decision")
    void modelNeverDilutesRules() {
        ml = (tx, activity) -> Optional.of(new MlPrediction(0.01, "lr-test"));
        setUp();

        assertThat(service.scoreBatch(List.of(risky)).getFirst().decision()).isEqualTo(Decision.DECLINE);
    }
}
