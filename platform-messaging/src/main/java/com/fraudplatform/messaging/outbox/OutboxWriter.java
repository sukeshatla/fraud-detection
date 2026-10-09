package com.fraudplatform.messaging.outbox;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes messages to the {@code outbox} table <b>inside the caller's transaction</b>, so they commit
 * or roll back together with the business data. That's the whole point: refusing to run outside a
 * transaction turns a subtle data-loss bug into an immediate error.
 */
public class OutboxWriter {

    static final String INSERT = "INSERT INTO outbox (topic, message_key, payload, headers) VALUES (?, ?, ?, ?::jsonb)";

    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    public OutboxWriter(JdbcTemplate jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void append(List<OutboxMessage> messages) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxWriter must be called inside the business transaction");
        }
        if (messages.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT, messages, messages.size(), (ps, m) -> {
            ps.setString(1, m.topic());
            ps.setString(2, m.key());
            ps.setString(3, m.payload());
            ps.setString(4, mapper.writeValueAsString(m.headers()));
        });
    }
}
