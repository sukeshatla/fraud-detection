package com.fraudplatform.security;

/**
 * PII handling for logs (AC-015-04): account identifiers are personal data. Log statements use
 * {@link #maskAccount} so the last 4 characters remain for correlation while the rest is hidden.
 */
public final class Pii {

    private Pii() {}

    /** {@code acc-1001-7788} → {@code acc-****7788}; short or null values are fully masked. */
    public static String maskAccount(String accountId) {
        if (accountId == null || accountId.length() <= 4) {
            return "****";
        }
        String prefix = accountId.startsWith("acc-") ? "acc-" : "";
        return prefix + "****" + accountId.substring(accountId.length() - 4);
    }
}
