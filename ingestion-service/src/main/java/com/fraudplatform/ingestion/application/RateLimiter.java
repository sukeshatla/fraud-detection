package com.fraudplatform.ingestion.application;

/** Outbound port: a quota shared by every instance of the service. */
public interface RateLimiter {

    RateLimitDecision tryAcquire(String clientId);
}
