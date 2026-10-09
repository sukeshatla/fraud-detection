package com.fraudplatform.scoring.infrastructure.config;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.scoring.application.AccountActivityStore;
import com.fraudplatform.scoring.application.AlertPublisher;
import com.fraudplatform.scoring.application.ProcessedEventStore;
import com.fraudplatform.scoring.application.ScoreTransactionService;
import com.fraudplatform.scoring.domain.FraudRule;
import com.fraudplatform.scoring.domain.RuleEngine;
import com.fraudplatform.scoring.domain.rules.CardTestingRule;
import com.fraudplatform.scoring.domain.rules.GeoVelocityRule;
import com.fraudplatform.scoring.domain.rules.HighAmountRule;
import com.fraudplatform.scoring.domain.rules.HighRiskMccRule;
import com.fraudplatform.scoring.domain.rules.VelocityRule;
import com.fraudplatform.scoring.infrastructure.kafka.KafkaAlertPublisher;
import com.fraudplatform.scoring.infrastructure.redis.RedisAccountActivityStore;
import com.fraudplatform.scoring.infrastructure.redis.RedisProcessedEventStore;
import java.time.Clock;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
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
    ScoreTransactionService scoreTransactionService(ProcessedEventStore processed, AccountActivityStore activity,
            RuleEngine engine, AlertPublisher alerts, Clock clock) {
        return new ScoreTransactionService(processed, activity, engine, alerts, clock);
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
