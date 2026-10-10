package com.fraudplatform.load;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/** Shared protocol and request definitions. Every knob is a -D system property. */
final class Requests {

    static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    private Requests() {}

    static HttpProtocolBuilder protocol() {
        return http.baseUrl(BASE_URL)
                .contentTypeHeader("application/json")
                .acceptHeader("application/json")
                .shareConnections(); // like a gateway's connection pool, not a browser per user
    }

    /**
     * POST one transaction. Client ids are spread over 50 "gateways" so per-client quotas reflect a
     * realistic tenant mix; each request has an Idempotency-Key like a well-behaved client.
     */
    static ChainBuilder submit(String name, Integer... acceptableStatuses) {
        return submitAs(name, null, acceptableStatuses);
    }

    /** @param clientId fixed X-Client-Id (one tenant), or null to spread over 50 gateways */
    static ChainBuilder submitAs(String name, String clientId, Integer... acceptableStatuses) {
        return io.gatling.javaapi.core.CoreDsl.exec(http(name)
                .post("/api/v1/transactions")
                .header("X-Client-Id", session -> clientId != null ? clientId : "gw-" + Math.floorMod(session.userId(), 50))
                .header("Idempotency-Key", "#{transactionId}")
                .body(StringBody("""
                        {"transactionId":"#{transactionId}","accountId":"#{accountId}","amount":#{amount},
                         "currency":"USD","merchantId":"m-#{mcc}","merchantCategoryCode":"#{mcc}",
                         "country":"#{country}","channel":"#{channel}","occurredAt":"#{occurredAt}"}"""))
                .check(status().in(acceptableStatuses.length == 0 ? new Integer[] {202} : acceptableStatuses)));
    }

    static int intProp(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
    }
}
