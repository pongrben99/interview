package com.example.alerting.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock that tests move by hand, so time-dependent logic is tested without sleeping.
 */
public final class MutableClock extends Clock {

    private volatile Instant now;

    /**
     * Creates a clock standing at the given instant.
     *
     * @param start where the clock starts
     */
    public MutableClock(Instant start) {
        this.now = start;
    }

    /**
     * Sets the time.
     *
     * @param instant the new time
     */
    public void set(Instant instant) {
        this.now = instant;
    }

    /**
     * Moves the time forward.
     *
     * @param duration how far to move
     */
    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
