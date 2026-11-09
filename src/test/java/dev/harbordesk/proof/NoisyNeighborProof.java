package dev.harbordesk.proof;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantInfo;
import dev.harbordesk.support.Api;
import dev.harbordesk.support.HarborTestBase;
import dev.harbordesk.support.ProofReport;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tenant A (free) floods at 500 req/s while tenant B (pro) sends normal load. B's p95 is measured with and without the flood. */
@Tag("proof")
class NoisyNeighborProof extends HarborTestBase {
    static final int SECONDS = 10;
    static final int FLOOD_RPS = 500;
    static final int B_RPS = 20;

    record Stats(int ok, int failed, double p50, double p95, double max) {
    }

    @Test
    void floodOfOneTenantDoesNotHurtTheOther() throws Exception {
        Api api = api();
        TenantInfo a = newTenant("noisy-a", Plan.FREE);
        TenantInfo b = newTenant("noisy-b", Plan.PRO);
        String ta = token(a);
        String tb = token(b);
        for (int i = 0; i < 20; i++) {
            api.post("/api/tickets", tb, "{\"title\":\"seed " + i + "\"}");
        }
        runB(api, tb, 5, null);   // warm up the JIT and connection pools

        Stats baseline = runB(api, tb, SECONDS, null);
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger limited = new AtomicInteger();
        AtomicInteger served = new AtomicInteger();
        Stats withFlood = runB(api, tb, SECONDS, scheduler -> {
            HttpRequest flood = api.get("/api/tickets?limit=20", ta);
            scheduler.scheduleAtFixedRate(() -> {
                sent.incrementAndGet();
                api.sendAsync(flood).thenAccept(r -> {
                    if (r.status() == 429) {
                        limited.incrementAndGet();
                    } else if (r.status() == 200) {
                        served.incrementAndGet();
                    }
                });
            }, 0, 1_000_000_000L / FLOOD_RPS, TimeUnit.NANOSECONDS);
        });
        Thread.sleep(500);   // let the last flood answers arrive

        double share = 100.0 * limited.get() / sent.get();
        assertThat(baseline.failed()).isZero();
        assertThat(withFlood.failed()).as("B must never be rejected").isZero();
        assertThat(share).isGreaterThan(90.0);

        StringBuilder md = new StringBuilder();
        md.append("## 3. Noisy neighbor\n\n");
        md.append("Result: **PASS**\n\n");
        md.append("Tenant A (free, limit 20 req/s) floods `GET /api/tickets` at ").append(FLOOD_RPS).append(" req/s for ").append(SECONDS)
                .append(" s. Tenant B (pro, limit 100 req/s) sends ").append(B_RPS).append(" req/s the whole time (4 reads, 1 ticket create). ")
                .append("Client, server and database run on the same laptop, so absolute numbers are pessimistic and the run is noisy. A 5 s warm-up runs first, the run without flood comes before the run with flood.\n\n");
        md.append("| | B without flood | B during flood |\n|---|---|---|\n");
        md.append(String.format(java.util.Locale.ROOT, "| Requests answered OK | %d | %d |\n", baseline.ok(), withFlood.ok()));
        md.append(String.format(java.util.Locale.ROOT, "| Failed or rejected | %d | %d |\n", baseline.failed(), withFlood.failed()));
        md.append(String.format(java.util.Locale.ROOT, "| Latency p50 | %.1f ms | %.1f ms |\n", baseline.p50(), withFlood.p50()));
        md.append(String.format(java.util.Locale.ROOT, "| **Latency p95** | **%.1f ms** | **%.1f ms** |\n", baseline.p95(), withFlood.p95()));
        md.append(String.format(java.util.Locale.ROOT, "| Latency max | %.1f ms | %.1f ms |\n\n", baseline.max(), withFlood.max()));
        md.append(String.format(java.util.Locale.ROOT, "Tenant A: %d requests sent, %d answered 429 with `Retry-After` (**%.1f %%**), %d served. ",
                sent.get(), limited.get(), share, served.get()));
        md.append("The 429 answers come from an in-memory token bucket in front of the controllers, so they cost no database work.\n");
        ProofReport.write("noisy-neighbor", md.toString());
    }

    /** B's load: B_RPS requests per second for the given seconds, optionally with extra load started on the same scheduler. */
    private Stats runB(Api api, String token, int seconds, java.util.function.Consumer<ScheduledExecutorService> extra) throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
        List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger n = new AtomicInteger();
        if (extra != null) {
            extra.accept(scheduler);
        }
        scheduler.scheduleAtFixedRate(() -> {
            boolean create = n.incrementAndGet() % 5 == 0;
            HttpRequest req = create ? api.json("POST", "/api/tickets", token, "{\"title\":\"load\"}")
                    : api.get("/api/tickets?limit=20", token);
            long t0 = System.nanoTime();
            api.sendAsync(req).thenAccept(r -> {
                if (r.status() == 200 || r.status() == 201) {
                    latencies.add((System.nanoTime() - t0) / 1e6);
                } else {
                    failed.incrementAndGet();
                }
            });
        }, 0, 1_000_000_000L / B_RPS, TimeUnit.NANOSECONDS);
        Thread.sleep(seconds * 1000L);
        scheduler.shutdownNow();
        Thread.sleep(500);
        List<Double> sorted;
        synchronized (latencies) {
            sorted = new ArrayList<>(latencies);
        }
        Collections.sort(sorted);
        if (sorted.isEmpty()) {
            return new Stats(0, failed.get(), 0, 0, 0);
        }
        return new Stats(sorted.size(), failed.get(), sorted.get(sorted.size() / 2),
                sorted.get((int) Math.min(sorted.size() - 1, Math.ceil(sorted.size() * 0.95) - 1)), sorted.get(sorted.size() - 1));
    }
}
