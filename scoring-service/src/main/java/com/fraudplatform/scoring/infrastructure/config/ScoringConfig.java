package com.fraudplatform.scoring.infrastructure.config;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.scoring.application.AccountActivityStore;
import com.fraudplatform.scoring.application.AccountHistoryService;
import com.fraudplatform.scoring.application.AccountRiskService;
import com.fraudplatform.scoring.application.AssessmentRepository;
import com.fraudplatform.scoring.application.HighRiskAccountCache;
import com.fraudplatform.scoring.application.MlScorer;
import com.fraudplatform.scoring.application.ProcessedEventStore;
import com.fraudplatform.scoring.application.ScoreTransactionService;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RuleEngine;
import com.fraudplatform.scoring.domain.ml.FeatureExtractor;
import com.fraudplatform.scoring.domain.ml.ScoreBlender;
import com.fraudplatform.scoring.domain.rules.CardTestingRule;
import com.fraudplatform.scoring.domain.rules.GeoVelocityRule;
import com.fraudplatform.scoring.domain.rules.HighAmountRule;
import com.fraudplatform.scoring.domain.rules.HighRiskMccRule;
import com.fraudplatform.scoring.domain.rules.KnownHighRiskAccountRule;
import com.fraudplatform.scoring.domain.rules.VelocityRule;
import com.fraudplatform.messaging.dlt.DltReplayer;
import com.fraudplatform.messaging.outbox.OutboxRelay;
import com.fraudplatform.messaging.outbox.OutboxRelayRunner;
import com.fraudplatform.messaging.outbox.OutboxWriter;
import com.fraudplatform.scoring.application.DeadLetterReplay;
import com.fraudplatform.scoring.infrastructure.kafka.FraudAlertEvents;
import com.fraudplatform.scoring.infrastructure.kafka.KafkaDeadLetterReplay;
import com.fraudplatform.scoring.infrastructure.ml.CircuitBreakerMlScorer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import com.fraudplatform.scoring.application.ScoringMetrics;
import com.fraudplatform.scoring.infrastructure.metrics.MicrometerScoringMetrics;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import com.fraudplatform.scoring.infrastructure.ml.LogisticRegressionMlScorer;
import com.fraudplatform.scoring.infrastructure.ml.ModelLoader;
import com.fraudplatform.scoring.infrastructure.ml.SemaphoreBulkheadMlScorer;
import com.fraudplatform.scoring.infrastructure.persistence.JdbcAssessmentRepository;
import com.fraudplatform.scoring.infrastructure.persistence.JdbcHighRiskAccountSource;
import com.fraudplatform.scoring.infrastructure.redis.RedisAccountActivityStore;
import com.fraudplatform.scoring.infrastructure.redis.RedisHighRiskAccountCache;
import com.fraudplatform.scoring.infrastructure.redis.RedisProcessedEventStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationPredicate;
import java.time.Clock;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Composition root for scoring: rules, engine, use case and adapters. */
@Configuration(proxyBeanMethods = false)
class ScoringConfig {

    /** pg_advisory_lock key electing the single active outbox relay among scoring instances. */
    static final long OUTBOX_LOCK_KEY = 0x5C0_0001L;
    static final int OUTBOX_BATCH = 500;

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Don't trace health checks / scrapes: every few seconds per replica, they'd drown real traces. */
    @Bean
    ObservationPredicate skipActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getRequestURI().startsWith("/actuator"));
    }

    @Bean
    HighAmountRule highAmountRule(ScoringProperties props) {
        return new HighAmountRule(props.rules().highAmountUsd(), props.rules().usdRates());
    }

    @Bean
    VelocityRule velocityRule(ScoringProperties props) {
        return new VelocityRule(props.rules().velocityMaxPerMinute());
    }

    @Bean
    GeoVelocityRule geoVelocityRule(ScoringProperties props) {
        return new GeoVelocityRule(props.rules().geoVelocityWindow());
    }

    @Bean
    HighRiskMccRule highRiskMccRule(ScoringProperties props) {
        return new HighRiskMccRule(props.rules().highRiskMccs());
    }

    @Bean
    CardTestingRule cardTestingRule(ScoringProperties props) {
        return new CardTestingRule(props.rules().cardTestingAmount(), props.rules().cardTestingMinCount());
    }

    @Bean
    KnownHighRiskAccountRule knownHighRiskAccountRule() {
        return new KnownHighRiskAccountRule();
    }

    /** Spring injects every {@link FraudRule} bean: a new rule is one new class + one @Bean. */
    @Bean
    RuleEngine ruleEngine(List<FraudRule> rules) {
        return new RuleEngine(rules);
    }

    @Bean
    AccountActivityStore accountActivityStore(StringRedisTemplate redis, ScoringProperties props) {
        return new RedisAccountActivityStore(redis, props.rules().cardTestingAmount(), props.activityRetention());
    }

    @Bean
    ProcessedEventStore processedEventStore(StringRedisTemplate redis, ScoringProperties props) {
        return new RedisProcessedEventStore(redis, props.processedTtl());
    }

    // --- Transactional outbox (Feature 010) -------------------------------------------------

    @Bean
    OutboxWriter outboxWriter(JdbcTemplate jdbc, JsonMapper mapper) {
        return new OutboxWriter(jdbc, mapper);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, ProducerFactory<String, String> producers,
            JsonMapper mapper, ScoringProperties props, MeterRegistry meters) {
        // A NON-observed template: the outbox rows already carry the trace context of the transaction
        // that caused them. An observed template would stamp the relay's own (unrelated) span instead.
        return new OutboxRelay(jdbc, tx, new KafkaTemplate<>(producers), mapper, OUTBOX_LOCK_KEY, OUTBOX_BATCH,
                props.publishTimeout(), meters);
    }

    /** SmartLifecycle: starts with the context, drains its in-flight batch on shutdown. */
    @Bean
    OutboxRelayRunner outboxRelayRunner(OutboxRelay relay) {
        return new OutboxRelayRunner(relay, OUTBOX_BATCH, Duration.ofMillis(100), Duration.ofSeconds(1));
    }

    @Bean
    DeadLetterReplay deadLetterReplay(ConsumerFactory<String, String> consumers, KafkaTemplate<String, String> kafka) {
        return new KafkaDeadLetterReplay(new DltReplayer(consumers, kafka, "scoring-dlt-replay"));
    }

    @Bean
    JdbcAssessmentRepository assessmentRepository(JdbcTemplate jdbc, TransactionTemplate tx, OutboxWriter outbox,
            JsonMapper mapper, ScoringProperties props) {
        return new JdbcAssessmentRepository(jdbc, tx, outbox, new FraudAlertEvents(mapper, props.alertTopic()));
    }

    @Bean
    AccountHistoryService accountHistoryService(JdbcAssessmentRepository repository) {
        return new AccountHistoryService(repository);
    }

    @Bean
    HighRiskAccountCache highRiskAccountCache(StringRedisTemplate redis, MeterRegistry meters, ScoringProperties props) {
        ScoringProperties.RiskCache c = props.riskCache();
        return new RedisHighRiskAccountCache(redis, meters, c.ttl(), c.clearTtl(), c.jitter(), c.lockTtl());
    }

    @Bean
    AccountRiskService accountRiskService(HighRiskAccountCache cache, JdbcTemplate jdbc, Clock clock, ScoringProperties props) {
        ScoringProperties.RiskCache c = props.riskCache();
        return new AccountRiskService(cache, new JdbcHighRiskAccountSource(jdbc, clock, c.flagWindow()), clock,
                c.lockWait(), Duration.ofMillis(20));
    }

    @Bean
    MlScorer mlScorer(ScoringProperties props, JsonMapper mapper, ResourceLoader resources, MeterRegistry meters) {
        ScoringProperties.Ml ml = props.ml();
        if (!ml.enabled()) {
            return (tx, activity) -> Optional.empty();
        }
        var loaded = new ModelLoader(mapper).load(resources.getResource(ml.model()));
        var extractor = new FeatureExtractor(props.rules().usdRates(), props.rules().highRiskMccs());
        // bulkhead( circuitBreaker( model ) ): cap concurrency, and stop calling a failing/slow model
        CircuitBreaker breaker = CircuitBreaker.of("ml-model", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(20)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofMillis(100))
                .slowCallRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(5)
                .build());
        MlScorer model = new LogisticRegressionMlScorer(extractor, loaded.model());
        return new SemaphoreBulkheadMlScorer(new CircuitBreakerMlScorer(model, breaker, meters),
                ml.maxConcurrent(), ml.acquireTimeout(), meters);
    }

    @Bean
    ScoringMetrics scoringMetrics(MeterRegistry meters) {
        return new MicrometerScoringMetrics(meters);
    }

    @Bean
    ScoreTransactionService scoreTransactionService(ProcessedEventStore processed, AccountActivityStore activity,
            RuleEngine engine, AssessmentRepository repository, HighRiskAccountCache riskCache, MlScorer mlScorer,
            ScoringMetrics metrics, Clock clock, ScoringProperties props) {
        return new ScoreTransactionService(processed, activity, engine, repository, riskCache, mlScorer,
                new ScoreBlender(props.ml().ruleWeight()), metrics, clock, props.maxConcurrentAccounts());
    }

    @Bean
    NewTopic transactionsReceivedTopic(ScoringProperties props) {
        return TopicBuilder.name(Topics.TRANSACTIONS_RECEIVED).partitions(props.partitions()).replicas(props.replicationFactor()).build();
    }

    @Bean
    NewTopic transactionsReceivedDlt(ScoringProperties props) {
        return TopicBuilder.name(KafkaErrorHandlingConfig.DLT).partitions(1).replicas(props.replicationFactor()).build();
    }

    @Bean
    NewTopic alertResolutionsTopic(ScoringProperties props) {
        return TopicBuilder.name(Topics.ALERT_RESOLUTIONS).partitions(6).replicas(props.replicationFactor()).build();
    }

    @Bean
    NewTopic fraudAlertsTopic(ScoringProperties props) {
        return TopicBuilder.name(props.alertTopic()).partitions(6).replicas(props.replicationFactor()).build();
    }
}
