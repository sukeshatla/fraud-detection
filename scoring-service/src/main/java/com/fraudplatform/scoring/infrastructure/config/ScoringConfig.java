package com.fraudplatform.scoring.infrastructure.config;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.scoring.application.AccountActivityStore;
import com.fraudplatform.scoring.application.AccountHistoryService;
import com.fraudplatform.scoring.application.AccountRiskService;
import com.fraudplatform.scoring.application.AlertPublisher;
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
import com.fraudplatform.scoring.infrastructure.kafka.KafkaAlertPublisher;
import com.fraudplatform.scoring.infrastructure.ml.LogisticRegressionMlScorer;
import com.fraudplatform.scoring.infrastructure.ml.ModelLoader;
import com.fraudplatform.scoring.infrastructure.ml.SemaphoreBulkheadMlScorer;
import com.fraudplatform.scoring.infrastructure.persistence.JdbcAssessmentRepository;
import com.fraudplatform.scoring.infrastructure.persistence.JdbcHighRiskAccountSource;
import com.fraudplatform.scoring.infrastructure.redis.RedisAccountActivityStore;
import com.fraudplatform.scoring.infrastructure.redis.RedisHighRiskAccountCache;
import com.fraudplatform.scoring.infrastructure.redis.RedisProcessedEventStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
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

    @Bean
    Clock clock() {
        return Clock.systemUTC();
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

    @Bean
    AlertPublisher alertPublisher(KafkaTemplate<String, String> template, JsonMapper mapper, ScoringProperties props) {
        return new KafkaAlertPublisher(template, mapper, props.alertTopic(), props.publishTimeout());
    }

    @Bean
    JdbcAssessmentRepository assessmentRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        return new JdbcAssessmentRepository(jdbc, tx);
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
        return new SemaphoreBulkheadMlScorer(new LogisticRegressionMlScorer(extractor, loaded.model()),
                ml.maxConcurrent(), ml.acquireTimeout(), meters);
    }

    @Bean
    ScoreTransactionService scoreTransactionService(ProcessedEventStore processed, AccountActivityStore activity,
            RuleEngine engine, AssessmentRepository repository, HighRiskAccountCache riskCache, MlScorer mlScorer,
            AlertPublisher alerts, Clock clock, ScoringProperties props) {
        return new ScoreTransactionService(processed, activity, engine, repository, riskCache, mlScorer,
                new ScoreBlender(props.ml().ruleWeight()), alerts, clock);
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
    NewTopic fraudAlertsTopic(ScoringProperties props) {
        return TopicBuilder.name(props.alertTopic()).partitions(6).replicas(props.replicationFactor()).build();
    }
}
