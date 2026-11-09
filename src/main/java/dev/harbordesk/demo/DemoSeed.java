package dev.harbordesk.demo;

import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantContext;
import dev.harbordesk.domain.TenantInfo;
import dev.harbordesk.domain.TenantService;
import dev.harbordesk.domain.TicketService;
import dev.harbordesk.security.JwtService;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Demo profile: three invented tenants from different industries, a few tickets each, and a token per tenant in the log. */
@Component
@ConditionalOnProperty("harbor.demo.seed")
public class DemoSeed implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    private record Demo(String slug, String name, Plan plan, List<String> tickets) {
    }

    private final TenantService tenants;
    private final TicketService tickets;
    private final JwtService jwt;

    public DemoSeed(TenantService tenants, TicketService tickets, JwtService jwt) {
        this.tenants = tenants;
        this.tickets = tickets;
        this.jwt = jwt;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Demo> demos = List.of(
                new Demo("norddock-freight", "Norddock Freight", Plan.PRO,
                        List.of("Pallet 4411 missing at depot", "Invoice shows wrong tonnage", "Driver app does not sync")),
                new Demo("kestrel-mutual", "Kestrel Mutual Insurance", Plan.ENTERPRISE,
                        List.of("Claim 88231 stuck in review", "Policy PDF has wrong address")),
                new Demo("bramble-goods", "Bramble Goods", Plan.FREE,
                        List.of("Refund not received", "Parcel marked delivered but not here")));
        for (Demo d : demos) {
            TenantInfo t;
            try {
                t = tenants.onboard(d.slug(), d.name(), d.plan());
            } catch (dev.harbordesk.domain.ConflictException e) {
                log.info("demo tenant {} exists already (delete the demo database with scripts/local-db.sh and rerun for fresh data)", d.slug());
                continue;
            }
            TenantContext.runAs(t, () -> {
                d.tickets().forEach(title -> tickets.create(title, ""));
                return null;
            });
            log.info("demo tenant {} ({}, {}) id={}", d.name(), d.plan().id(), t.dbName() == null ? "pool" : t.dbName(), t.id());
            log.info("  token: {}", jwt.issue(t.id(), "demo-user", Duration.ofHours(8)));
        }
    }
}
