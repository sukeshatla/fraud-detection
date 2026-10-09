package com.fraudplatform.alerts.infrastructure.kafka;

import com.fraudplatform.alerts.application.AlertResolution;
import com.fraudplatform.alerts.application.AlertResolutionPublisher;
import com.fraudplatform.contracts.EventHeaders;
import com.fraudplatform.contracts.Topics;
import com.fraudplatform.contracts.events.AlertResolvedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Announces resolved alerts. Runs <i>after</i> the DB commit, so a failure here cannot be rolled
 * back: the analyst's decision stands, and the event is logged + counted.
 *
 * <p>This is the <b>dual-write problem</b> (DB + Kafka without a shared transaction). Feature 010
 * closes the gap with a transactional outbox.
 */
public class KafkaAlertResolutionPublisher implements AlertResolutionPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAlertResolutionPublisher.class);

    private final KafkaTemplate<String, String> template;
    private final JsonMapper mapper;
    private final Duration timeout;
    private final Counter failures;

    public KafkaAlertResolutionPublisher(KafkaTemplate<String, String> template, JsonMapper mapper, Duration timeout,
            MeterRegistry meters) {
        this.template = template;
        this.mapper = mapper;
        this.timeout = timeout;
        this.failures = Counter.builder("alert_resolution_publish_failures_total").register(meters);
    }

    @Override
    public void publish(AlertResolution r) {
        AlertResolvedEvent event = new AlertResolvedEvent(AlertResolvedEvent.SCHEMA_VERSION,
                UUID.nameUUIDFromBytes(("resolved:" + r.alertId() + ":" + r.resolution()).getBytes(StandardCharsets.UTF_8)),
                r.alertId(), r.transactionId(), r.accountId(), r.resolution().name(), r.resolvedBy(), r.resolvedAt());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(Topics.ALERT_RESOLUTIONS, r.accountId(), mapper.writeValueAsString(event));
        record.headers()
                .add(EventHeaders.EVENT_TYPE, AlertResolvedEvent.EVENT_TYPE.getBytes(StandardCharsets.UTF_8))
                .add(EventHeaders.SCHEMA_VERSION, "1".getBytes(StandardCharsets.UTF_8));
        try {
            template.send(record).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed(r, e);
        } catch (Exception e) {
            failed(r, e);
        }
    }

    private void failed(AlertResolution r, Exception e) {
        failures.increment();
        log.error("Alert {} resolved as {} but the event was not published (dual-write gap, see Feature 010)",
                r.alertId(), r.resolution(), e);
    }
}
