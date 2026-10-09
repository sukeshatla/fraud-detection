package com.fraudplatform.alerts.infrastructure.redis;

import com.fraudplatform.alerts.application.AlertChange;
import com.fraudplatform.alerts.application.AlertChangeBus;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fan-out of alert changes over Redis pub/sub.
 *
 * <p>Kafka consumer groups <i>split</i> records across instances, which is right for processing.
 * Pub/sub <i>broadcasts</i> to every subscriber, which is right for "push to whoever is connected".
 * Fire-and-forget: a subscriber that is down misses messages, which is fine here because the queue
 * in PostgreSQL is the source of truth and the dashboard refetches on reconnect.
 */
public class RedisAlertChangeBus implements AlertChangeBus {

    static final String CHANNEL = "alerts:changes";

    private static final Logger log = LoggerFactory.getLogger(RedisAlertChangeBus.class);

    private final StringRedisTemplate redis;
    private final JsonMapper mapper;
    private final List<Consumer<AlertChange>> subscribers = new CopyOnWriteArrayList<>();

    public RedisAlertChangeBus(StringRedisTemplate redis, RedisMessageListenerContainer container, JsonMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
        container.addMessageListener((message, pattern) -> dispatch(message.getBody()), new ChannelTopic(CHANNEL));
    }

    @Override
    public void publish(AlertChange change) {
        try {
            redis.convertAndSend(CHANNEL, mapper.writeValueAsString(change));
        } catch (RuntimeException e) {
            log.warn("Could not broadcast alert change {} (dashboards will catch up on refetch): {}",
                    change.alert().id(), e.toString());
        }
    }

    @Override
    public Runnable subscribe(Consumer<AlertChange> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    private void dispatch(byte[] body) {
        AlertChange change;
        try {
            change = mapper.readValue(body, AlertChange.class);
        } catch (RuntimeException e) {
            log.warn("Ignoring malformed alert change message: {}", e.toString());
            return;
        }
        for (Consumer<AlertChange> subscriber : subscribers) {
            try {
                subscriber.accept(change);
            } catch (RuntimeException e) {
                log.warn("Alert change subscriber failed: {}", e.toString());
            }
        }
    }
}
