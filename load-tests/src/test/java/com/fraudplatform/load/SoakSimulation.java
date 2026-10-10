package com.fraudplatform.load;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * AC-013-05: moderate load for a long time. The interesting output is not in Gatling but in
 * Grafana: heap after GC, Hikari connections, consumer lag and outbox backlog must stay FLAT. A
 * slow upward trend is a leak that a 5-minute test never shows.
 * <pre>-Drate=100 -DdurationMinutes=30</pre>
 */
public class SoakSimulation extends Simulation {

    {
        setUp(scenario("soak").exec(feed(new TransactionFeeder()), Requests.submit("submit"))
                .injectOpen(constantUsersPerSec(Requests.intProp("rate", 100))
                        .during(Duration.ofMinutes(Requests.intProp("durationMinutes", 30)))))
                .protocols(Requests.protocol())
                .assertions(global().failedRequests().percent().lt(0.1));
    }
}
