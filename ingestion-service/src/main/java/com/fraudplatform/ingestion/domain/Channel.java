package com.fraudplatform.ingestion.domain;

/** How the card was used. Card-not-present carries the highest fraud base rate. */
public enum Channel {
    CARD_PRESENT,
    CARD_NOT_PRESENT,
    CONTACTLESS,
    ATM
}
