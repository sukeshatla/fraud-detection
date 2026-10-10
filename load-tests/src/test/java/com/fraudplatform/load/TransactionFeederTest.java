package com.fraudplatform.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionFeederTest {

    @Test
    @DisplayName("AC-013-04: ~95% normal traffic, the rest fraud patterns emitted back-to-back per account")
    void realisticMix() {
        TransactionFeeder feeder = new TransactionFeeder();
        List<Map<String, Object>> rows = IntStream.range(0, 20_000).mapToObj(i -> feeder.next()).toList();

        Map<Object, Long> byKind = rows.stream().collect(Collectors.groupingBy(r -> r.get("kind"), Collectors.counting()));
        double normalShare = byKind.get("normal") / 20_000.0;

        assertThat(normalShare).isBetween(0.70, 0.90); // 5% of *patterns*, each pattern is 2–8 rows
        assertThat(byKind).containsKeys("velocity", "card-testing", "impossible-travel");
        assertThat(rows).allSatisfy(r -> assertThat(r).containsKeys("transactionId", "accountId", "amount", "mcc", "country"));

        int firstVelocity = rows.indexOf(rows.stream().filter(r -> r.get("kind").equals("velocity")).findFirst().orElseThrow());
        assertThat(rows.subList(firstVelocity, firstVelocity + 8)).extracting(r -> r.get("accountId")).containsOnly(rows.get(firstVelocity).get("accountId"));
    }
}
