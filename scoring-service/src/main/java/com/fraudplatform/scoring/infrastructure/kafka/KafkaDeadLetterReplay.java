package com.fraudplatform.scoring.infrastructure.kafka;

import com.fraudplatform.contracts.Topics;
import com.fraudplatform.messaging.dlt.DltReplayer;
import com.fraudplatform.scoring.application.DeadLetterReplay;

/** Replays {@code transactions.received.v1.DLT} back into {@code transactions.received.v1}. */
public class KafkaDeadLetterReplay implements DeadLetterReplay {

    private final DltReplayer replayer;

    public KafkaDeadLetterReplay(DltReplayer replayer) {
        this.replayer = replayer;
    }

    @Override
    public int replay(int max) {
        return replayer.replay(Topics.TRANSACTIONS_RECEIVED + ".DLT", Topics.TRANSACTIONS_RECEIVED, max);
    }
}
