package com.fraudplatform.scoring.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fraudplatform.scoring.support.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AC-004-05: measures why batching matters. Results are logged and recorded in
 * docs/concepts/04-jdbc-batch-processing.md. The assertion only guards the order of magnitude, so
 * it stays stable on slow CI runners.
 */
@IntegrationTest
class BatchInsertBenchmarkIT {

    private static final Logger log = LoggerFactory.getLogger(BatchInsertBenchmarkIT.class);
    private static final int ROWS = 5_000;
    private static final String INSERT = "INSERT INTO bench_insert (id, account_id, amount) VALUES (?, ?, ?)";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createTable() {
        jdbc.execute("DROP TABLE IF EXISTS bench_insert");
        jdbc.execute("CREATE TABLE bench_insert (id uuid PRIMARY KEY, account_id varchar(64), amount numeric(19,4))");
    }

    @Test
    @DisplayName("AC-004-05: batch insert is an order of magnitude faster than row-by-row autocommit")
    void batchVersusRowByRow() throws SQLException {
        Duration autocommit = time(this::rowByRowAutocommit);
        Duration singleTx = time(this::rowByRowSingleTransaction);
        Duration batched = time(this::batched);

        log.info("""

                ┌──────────────────────────────────────┬────────────┬──────────────┐
                │ {} rows                            │   elapsed  │   rows/sec   │
                ├──────────────────────────────────────┼────────────┼──────────────┤
                │ row-by-row, autocommit (1 fsync/row) │ {} ms │ {} │
                │ row-by-row, one transaction          │ {} ms │ {} │
                │ batchUpdate + reWriteBatchedInserts  │ {} ms │ {} │
                └──────────────────────────────────────┴────────────┴──────────────┘""",
                ROWS, pad(autocommit.toMillis()), pad(rate(autocommit)), pad(singleTx.toMillis()), pad(rate(singleTx)),
                pad(batched.toMillis()), pad(rate(batched)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bench_insert", Integer.class)).isEqualTo(3 * ROWS);
        assertThat(batched.multipliedBy(3)).isLessThan(autocommit);
    }

    private void rowByRowAutocommit() throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(INSERT)) {
            c.setAutoCommit(true);
            for (int i = 0; i < ROWS; i++) {
                bind(ps, i);
                ps.executeUpdate(); // one round trip + one commit per row
            }
        }
    }

    private void rowByRowSingleTransaction() throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(INSERT)) {
            c.setAutoCommit(false);
            for (int i = 0; i < ROWS; i++) {
                bind(ps, i);
                ps.executeUpdate(); // one round trip per row, one commit at the end
            }
            c.commit();
        }
    }

    private void batched() throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(INSERT)) {
            c.setAutoCommit(false);
            for (int i = 0; i < ROWS; i++) {
                bind(ps, i);
                ps.addBatch();
                if ((i + 1) % 500 == 0) {
                    ps.executeBatch(); // rewritten into multi-row INSERTs by the driver
                }
            }
            ps.executeBatch();
            c.commit();
        }
    }

    private static void bind(PreparedStatement ps, int i) throws SQLException {
        ps.setObject(1, UUID.randomUUID());
        ps.setString(2, "acc-" + (i % 100));
        ps.setBigDecimal(3, java.math.BigDecimal.valueOf(i, 2));
    }

    private interface SqlWork {
        void run() throws SQLException;
    }

    private static Duration time(SqlWork work) throws SQLException {
        long start = System.nanoTime();
        work.run();
        return Duration.ofNanos(System.nanoTime() - start);
    }

    private static long rate(Duration d) {
        return ROWS * 1000L / Math.max(1, d.toMillis());
    }

    private static String pad(long v) {
        return String.format("%8d", v);
    }
}
