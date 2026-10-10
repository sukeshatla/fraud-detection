package com.fraudplatform.alerts.api;

import com.fraudplatform.alerts.application.IngestAlertService;
import com.fraudplatform.alerts.application.InvalidEventException;
import com.fraudplatform.alerts.application.NewAlert;
import com.fraudplatform.alerts.application.RuleHitView;
import com.fraudplatform.alerts.domain.Severity;
import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.Topics;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import com.fraudplatform.contracts.events.FraudAlertEvent;
import java.math.BigDecimal;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Inbound adapter: fraud.alerts.v1 → alert queue. */
@Component
class FraudAlertListener {

    private final IngestAlertService service;
    private final JsonMapper mapper;

    FraudAlertListener(IngestAlertService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @KafkaListener(id = "alerts", topics = Topics.FRAUD_ALERTS, groupId = "alerts")
    void onAlert(ConsumerRecord<String, String> record) {
        Header requestId = record.headers().lastHeader(EventHeaders.REQUEST_ID);
        if (requestId != null) {
            MDC.put("requestId", new String(requestId.value(), StandardCharsets.UTF_8)); // correlate logs with the original request
        }
        try {
            service.ingest(toNewAlert(record.value()));
        } finally {
            MDC.remove("requestId");
        }
    }

    private NewAlert toNewAlert(String payload) {
        try {
            FraudAlertEvent e = mapper.readValue(payload, FraudAlertEvent.class);
            return new NewAlert(e.alertEventId(), e.transactionId(), e.accountId(), e.amount(), e.currency(),
                    e.merchantId(), e.merchantCategoryCode(), e.country(), e.channel(), e.occurredAt(), e.ruleScore(),
                    e.riskScore(), e.mlProbability() == null ? null : BigDecimal.valueOf(e.mlProbability()),
                    e.modelVersion(), e.decision(), Severity.of(e.decision(), e.riskScore()),
                    e.scoredAt(), // created_at = scoring time: deterministic on replay
                    e.ruleHits().stream().map(h -> new RuleHitView(h.code(), h.weight(), h.reason())).toList());
        } catch (JacksonException | NullPointerException e) {
            throw new InvalidEventException("Unprocessable FraudAlertEvent", e);
        }
    }
}
