package com.fraudplatform.load;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OAuth2 client-credentials tokens for the load generator, cached per client and refreshed
 * shortly before they expire, like a real gateway's token manager: one token request per
 * client per lifetime, never one per business request.
 */
final class Tokens {

    /** Dev-only secrets from infra/keycloak/fraud-realm.json. */
    private static final Map<String, String> SECRETS = Map.of(
            "gw-premium", "dev-only-not-a-secret-load",
            "gw-standard", "dev-only-not-a-secret-load",
            "payment-gateway", "dev-only-not-a-secret-gateway");
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");

    private record Cached(String value, Instant refreshAt) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private Tokens() {}

    static String bearer(String clientId) {
        Cached cached = CACHE.get(clientId);
        if (cached == null || Instant.now().isAfter(cached.refreshAt())) {
            // compute() serializes refreshes per client: concurrent virtual users wait for ONE fetch
            cached = CACHE.compute(clientId, (id, current) ->
                    current != null && Instant.now().isBefore(current.refreshAt()) ? current : fetch(id));
        }
        return "Bearer " + cached.value();
    }

    private static Cached fetch(String clientId) {
        String form = "grant_type=client_credentials&client_id=" + clientId + "&client_secret="
                + URLEncoder.encode(SECRETS.getOrDefault(clientId, ""), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(Requests.BASE_URL + "/realms/fraud/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            String body = HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
            Matcher token = ACCESS_TOKEN.matcher(body);
            Matcher expires = EXPIRES_IN.matcher(body);
            if (!token.find() || !expires.find()) {
                throw new IllegalStateException("No token for " + clientId + ": " + body);
            }
            return new Cached(token.group(1), Instant.now().plusSeconds(Long.parseLong(expires.group(1))).minus(REFRESH_MARGIN));
        } catch (IOException e) {
            throw new IllegalStateException("Token endpoint unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
