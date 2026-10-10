package com.fraudplatform.load;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * AC-013-02: expected peak load, OPEN workload model: arrivals happen at a fixed rate whether or
 * not the system keeps up, like real payment traffic. (A closed model, N users each waiting for
 * their response, slows down with the system and hides latency: coordinated omission.)
 *
 * <pre>-Drate=1000 -DrampSeconds=120 -DholdSeconds=300 -Dp99Ms=50</pre>
 * Defaults are sized for a laptop running the whole stack.
 */
public class BaselineSimulation extends Simulation {

    static final int RATE = Requests.intProp("rate", 200);
    static final int RAMP = Requests.intProp("rampSeconds", 30);
    static final int HOLD = Requests.intProp("holdSeconds", 60);
    static final int P99_MS = Requests.intProp("p99Ms", 100);

    {
        setUp(scenario("baseline").exec(feed(new TransactionFeeder()), Requests.submit("submit"))
                .injectOpen(
                        rampUsersPerSec(5).to(RATE).during(Duration.ofSeconds(RAMP)),
                        constantUsersPerSec(RATE).during(Duration.ofSeconds(HOLD))))
                .protocols(Requests.protocol())
                .assertions(
                        global().responseTime().percentile(99.0).lt(P99_MS),
                        global().failedRequests().percent().lt(0.1));
    }
}
