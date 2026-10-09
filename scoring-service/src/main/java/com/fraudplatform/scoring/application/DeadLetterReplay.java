package com.fraudplatform.scoring.application;

/** Outbound port: re-publish dead-lettered transactions once the cause is fixed. */
public interface DeadLetterReplay {

    /** @return how many dead letters were re-published (at most {@code max}) */
    int replay(int max);
}
