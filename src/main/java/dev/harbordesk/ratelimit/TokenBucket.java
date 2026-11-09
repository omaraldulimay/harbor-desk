package dev.harbordesk.ratelimit;

import java.util.function.LongSupplier;

/** Token bucket: refills at ratePerSecond, holds at most one second worth of tokens. */
public final class TokenBucket {
    private final double ratePerNano;
    private final double capacity;
    private final LongSupplier nanoClock;
    private double tokens;
    private long last;

    public TokenBucket(int ratePerSecond, LongSupplier nanoClock) {
        this.ratePerNano = ratePerSecond / 1_000_000_000.0;
        this.capacity = ratePerSecond;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.last = nanoClock.getAsLong();
    }

    /** Returns 0 if a token was taken, otherwise the nanoseconds until one is available. */
    public synchronized long tryAcquire() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - last) * ratePerNano);
        last = now;
        if (tokens >= 1) {
            tokens -= 1;
            return 0;
        }
        return (long) Math.ceil((1 - tokens) / ratePerNano);
    }
}
