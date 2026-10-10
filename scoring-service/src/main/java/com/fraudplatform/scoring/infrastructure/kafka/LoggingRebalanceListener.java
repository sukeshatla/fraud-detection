package com.fraudplatform.scoring.infrastructure.kafka;

import java.util.Collection;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;

/**
 * Logs partition movements. With the cooperative-sticky assignor a rebalance only revokes the
 * partitions that actually move, so these lines show exactly what each instance gained or lost
 * when replicas scale up/down or crash.
 */
public class LoggingRebalanceListener implements ConsumerAwareRebalanceListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingRebalanceListener.class);

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        if (!partitions.isEmpty()) {
            log.info("Rebalance: assigned {} (now own {})", partitions, consumer.assignment().size());
        }
    }

    @Override
    public void onPartitionsRevokedAfterCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        if (!partitions.isEmpty()) {
            log.info("Rebalance: revoked {} (offsets committed first)", partitions);
        }
    }

    @Override
    public void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log.warn("Rebalance: LOST {} (session timed out; another member may already own them)", partitions);
    }
}
