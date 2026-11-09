package dev.harbordesk.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketTest {
    @Test
    void allowsBurstUpToRateThenRejectsAndRefills() {
        AtomicLong now = new AtomicLong();
        TokenBucket bucket = new TokenBucket(20, now::get);
        for (int i = 0; i < 20; i++) {
            assertThat(bucket.tryAcquire()).isZero();
        }
        assertThat(bucket.tryAcquire()).isEqualTo(50_000_000L);   // one token at 20/s takes 50 ms

        now.addAndGet(50_000_000L);
        assertThat(bucket.tryAcquire()).isZero();
        assertThat(bucket.tryAcquire()).isPositive();

        now.addAndGet(10_000_000_000L);             // a long pause refills at most one second worth
        for (int i = 0; i < 20; i++) {
            assertThat(bucket.tryAcquire()).isZero();
        }
        assertThat(bucket.tryAcquire()).isPositive();
    }
}
