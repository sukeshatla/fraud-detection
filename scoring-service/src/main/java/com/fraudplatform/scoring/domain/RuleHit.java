package com.fraudplatform.scoring.domain;

/** A rule that fired: what, how much it contributes, and a human-readable why. */
public record RuleHit(String code, int weight, String reason) {}
