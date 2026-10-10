package com.fraudplatform.load;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Simulation;
import java.time.Duration;

/**
 * AC-013-03: one misbehaving gateway suddenly sends far beyond its contracted quota (default
 * 100 rps sustained, 200 burst). Expected behaviour, which is the point:
 * <ul>
 *   <li>during the spike the platform sheds load with <b>429</b> (rate limiter, Feature 002),
 *       never with 5xx or timeouts;
 *   <li>after the spike it <b>recovers</b>: normal traffic is accepted again within SLO.
 * </ul>
 */
public class SpikeSimulation extends Simulation {

    static final int SPIKE_RATE = Requests.intProp("spikeRate", 2000);
    static final int NORMAL_RATE = Requests.intProp("rate", 50);

    {
        var spike = scenario("spike").exec(feed(new TransactionFeeder()), Requests.submitAs("submit (spike)", "gw-standard", 202, 429))
                .injectOpen(rampUsersPerSec(0).to(SPIKE_RATE).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(SPIKE_RATE).during(Duration.ofSeconds(10)));
        var recovery = scenario("recovery").exec(feed(new TransactionFeeder()), Requests.submit("submit (recovery)"))
                .injectOpen(constantUsersPerSec(NORMAL_RATE).during(Duration.ofSeconds(30)));

        setUp(spike.andThen(recovery))
                .protocols(Requests.protocol())
                .assertions(
                        details("submit (spike)").failedRequests().percent().lt(1.0),   // 429 is an answer, not a failure
                        details("submit (recovery)").failedRequests().percent().lt(0.1),
                        details("submit (recovery)").responseTime().percentile(99.0).lt(Requests.intProp("p99Ms", 250)));
    }
}
