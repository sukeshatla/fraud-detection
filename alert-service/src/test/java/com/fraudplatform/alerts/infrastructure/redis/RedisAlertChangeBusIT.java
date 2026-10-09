package com.fraudplatform.alerts.infrastructure.redis;

import static com.fraudplatform.alerts.application.AlertFixtures.alert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fraudplatform.alerts.application.AlertChange;
import com.fraudplatform.alerts.domain.AlertStatus;
import com.fraudplatform.testing.Containers;
import com.fraudplatform.testing.RedisTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/** AC-008-02: every instance sees every change, whichever instance consumed the Kafka record. */
@Testcontainers
class RedisAlertChangeBusIT {

    @Container
    static final GenericContainer<?> REDIS = Containers.redis();

    @Test
    @DisplayName("AC-008-02: a change published on instance A reaches subscribers on A and B")
    void fansOutToAllInstances() {
        RedisAlertChangeBus podA = bus();
        RedisAlertChangeBus podB = bus();
        List<AlertChange> seenByA = new CopyOnWriteArrayList<>();
        List<AlertChange> seenByB = new CopyOnWriteArrayList<>();
        podA.subscribe(seenByA::add);
        podB.subscribe(seenByB::add);
        AlertChange change = new AlertChange(AlertChange.Type.CREATED, alert(UUID.randomUUID(), AlertStatus.OPEN, 0));

        // subscriptions are established asynchronously: publish until both have seen it
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).until(() -> {
            podA.publish(change);
            return seenByA.contains(change) && seenByB.contains(change);
        });

        assertThat(seenByB.getFirst()).isEqualTo(change);
    }

    private static RedisAlertChangeBus bus() {
        StringRedisTemplate redis = RedisTestSupport.template(REDIS);
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redis.getRequiredConnectionFactory());
        container.afterPropertiesSet();
        container.start();
        return new RedisAlertChangeBus(redis, container, JsonMapper.builder().build());
    }
}
