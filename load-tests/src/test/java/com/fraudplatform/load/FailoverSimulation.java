package com.fraudplatform.load;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * AC-011-02: steady traffic while load-tests/failover.sh kills an ingestion replica mid-run.
 * With NGINX passive health checks + retry-on-next-upstream, clients should barely notice.
 */
public class FailoverSimulation extends Simulation {

    {
        setUp(scenario("failover").exec(feed(new TransactionFeeder()), Requests.submit("submit"))
                .injectOpen(constantUsersPerSec(Requests.intProp("rate", 100))
                        .during(Duration.ofSeconds(Requests.intProp("durationSeconds", 60)))))
                .protocols(Requests.protocol())
                .assertions(global().failedRequests().percent().lt(Double.parseDouble(System.getProperty("maxErrorPct", "0.1"))));
    }
}
