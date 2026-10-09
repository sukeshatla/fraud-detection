package com.fraudplatform.scoring.domain;

import java.util.Optional;

/**
 * Strategy interface for one fraud rule. Implementations must be pure and thread-safe: no I/O,
 * no mutable state. Everything a rule needs is in its arguments.
 *
 * <p>Adding a rule means adding one class and one bean; the engine never changes (open/closed).
 */
public interface FraudRule {

    String code();

    Optional<RuleHit> evaluate(Transaction transaction, AccountActivity activity);
}
