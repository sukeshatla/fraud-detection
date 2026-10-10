package com.fraudplatform.load;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/** AC-013-07: short, gentle run for CI against a freshly started stack. */
public class SmokeSimulation extends Simulation {

    {
        setUp(scenario("smoke").exec(feed(new TransactionFeeder()), Requests.submit("submit"))
                .injectOpen(constantUsersPerSec(20).during(Duration.ofSeconds(30))))
                .protocols(Requests.protocol())
                .assertions(
                        global().successfulRequests().percent().gt(99.0),
                        global().responseTime().percentile(95.0).lt(Requests.intProp("p95Ms", 500)));
    }
}
