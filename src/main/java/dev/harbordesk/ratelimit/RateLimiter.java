package dev.harbordesk.ratelimit;

import dev.harbordesk.domain.TenantInfo;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/** One in-memory bucket per tenant, sized by the plan. Per instance, see ADR 0004. */
@Component
public class RateLimiter {
    private final ConcurrentHashMap<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RateLimiter() {
        this(System::nanoTime);
    }

    public RateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    /** Returns 0 if the request may pass, otherwise the seconds to wait (at least 1). */
    public long retryAfterSeconds(TenantInfo tenant) {
        TokenBucket bucket = buckets.computeIfAbsent(tenant.id(),
                id -> new TokenBucket(tenant.plan().requestsPerSecond(), clock));
        long wait = bucket.tryAcquire();
        return wait == 0 ? 0 : Math.max(1, (wait + 999_999_999L) / 1_000_000_000L);
    }
}
