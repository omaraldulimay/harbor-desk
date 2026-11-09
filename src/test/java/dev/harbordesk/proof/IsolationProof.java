package dev.harbordesk.proof;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantContext;
import dev.harbordesk.domain.TenantInfo;
import dev.harbordesk.domain.TicketService;
import dev.harbordesk.persistence.CommentRepository;
import dev.harbordesk.persistence.RawSql;
import dev.harbordesk.persistence.TenantTx;
import dev.harbordesk.persistence.TicketRepository;
import dev.harbordesk.support.HarborTestBase;
import dev.harbordesk.support.ProofReport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 3 tenants, 10,000 random attempts to read or write another tenant's rows. Expected leaks: 0. */
@Tag("proof")
class IsolationProof extends HarborTestBase {
    static final int ATTEMPTS = 10_000;
    static final int TICKETS_PER_TENANT = 20;

    @Autowired
    TicketService tickets;
    @Autowired
    TicketRepository ticketRepo;
    @Autowired
    CommentRepository commentRepo;
    @Autowired
    TenantTx tx;

    @Test
    void tenDThousandForeignAttemptsLeakNothing() {
        List<TenantInfo> ts = List.of(newTenant("iso-a", Plan.FREE), newTenant("iso-b", Plan.PRO), newTenant("iso-c", Plan.PRO));
        Map<UUID, List<Long>> ids = new HashMap<>();
        for (TenantInfo t : ts) {
            List<Long> own = new ArrayList<>();
            TenantContext.runAs(t, () -> {
                for (int i = 0; i < TICKETS_PER_TENANT; i++) {
                    own.add(tickets.create("ticket " + i, "private to " + t.slug()).id());
                }
                tickets.comment(own.get(0), "ann", "private comment");
                return null;
            });
            ids.put(t.id(), own);
        }
        JdbcClient owner = JdbcClient.create(admin);
        String before = snapshot(owner, ts);

        Random rnd = new Random(42);
        Map<String, Integer> attempts = new java.util.TreeMap<>();
        int leaks = 0;
        long start = System.nanoTime();
        for (int i = 0; i < ATTEMPTS; i++) {
            TenantInfo me = ts.get(rnd.nextInt(ts.size()));
            TenantInfo other = ts.stream().filter(t -> !t.equals(me)).toList().get(rnd.nextInt(ts.size() - 1));
            long foreign = ids.get(other.id()).get(rnd.nextInt(TICKETS_PER_TENANT));
            String op = switch (rnd.nextInt(5)) {
                case 0 -> "read foreign ticket by id";
                case 1 -> "update status of foreign ticket";
                case 2 -> "comment on foreign ticket";
                case 3 -> "insert row with foreign tenant_id";
                default -> "list without filter";
            };
            attempts.merge(op, 1, Integer::sum);
            boolean leaked = TenantContext.runAs(me, () -> attempt(op, me, other, foreign));
            if (leaked) {
                leaks++;
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        String after = snapshot(owner, ts);
        assertThat(after).as("foreign data must be unchanged").isEqualTo(before);
        assertThat(leaks).isZero();

        // forgotten WHERE: no tenant filter at all, still only own rows
        TenantInfo a = ts.get(0);
        long ownRows = TenantContext.runAs(a, () -> RawSql.run(tx, j -> j.sql("select count(*) from tickets").query(Long.class).single()));
        long foreignRows = TenantContext.runAs(a, () -> RawSql.run(tx,
                j -> j.sql("select count(*) from tickets where tenant_id <> :t").param("t", a.id()).query(Long.class).single()));
        long totalAsOwner = owner.sql("select count(*) from tickets where tenant_id in (:ids)")
                .param("ids", ts.stream().map(TenantInfo::id).toList()).query(Long.class).single();
        assertThat(ownRows).isEqualTo(TICKETS_PER_TENANT);
        assertThat(foreignRows).isZero();

        StringBuilder md = new StringBuilder();
        md.append("## 1. Data separation (most important)\n\n");
        md.append("Result: **").append(leaks == 0 ? "PASS, 0 leaks" : "FAIL, " + leaks + " leaks").append("**\n\n");
        md.append("3 tenants share one database (pool). Each has ").append(TICKETS_PER_TENANT)
                .append(" tickets and a comment. ").append(ATTEMPTS).append(" random attempts ran as one tenant against the ids of another, ")
                .append("through the real repositories and with hand written SQL, in ").append(ms).append(" ms.\n\n");
        md.append("| Attempt | Count | Leaks |\n|---|---|---|\n");
        attempts.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" | 0 |\n"));
        md.append("| **Total** | **").append(ATTEMPTS).append("** | **").append(leaks).append("** |\n\n");
        md.append("Reads returned no row, updates changed 0 rows, a comment on a foreign ticket and an insert with a foreign `tenant_id` were rejected by the database. ")
                .append("A snapshot of all tickets and comments (ids, status, text) taken with the owner role before and after is identical.\n\n");
        md.append("**Query without `WHERE tenant_id`:** `select count(*) from tickets` as tenant A returns **").append(ownRows)
                .append("** rows. The same table holds **").append(totalAsOwner).append("** rows of these three tenants (owner view). ")
                .append("Rows of other tenants visible to A: **").append(foreignRows).append("**.\n\n");
        md.append("Settings checked in `RlsTest`: app role has no superuser and no BYPASSRLS and owns no table, ")
                .append("every table has RLS enabled and forced, an unset `app.tenant_id` shows no rows.\n");
        ProofReport.write("isolation", md.toString());
    }

    /** Returns true if the attempt leaked. */
    private boolean attempt(String op, TenantInfo me, TenantInfo other, long foreign) {
        switch (op) {
            case "read foreign ticket by id":
                return tx.run(s -> ticketRepo.find(s, foreign)).isPresent();
            case "update status of foreign ticket":
                return tx.run(s -> ticketRepo.updateStatus(s, foreign, "closed")).isPresent();
            case "comment on foreign ticket":
                try {
                    tx.run(s -> commentRepo.insert(s, foreign, "mallory", "injected"));
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            case "insert row with foreign tenant_id":
                try {
                    RawSql.run(tx, j -> j.sql("insert into tickets (tenant_id, title) values (:t, 'injected')")
                            .param("t", other.id()).update());
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            default:
                return tx.run(s -> ticketRepo.list(s, null, 200)).stream().anyMatch(t -> !t.tenantId().equals(me.id()));
        }
    }

    private static String snapshot(JdbcClient owner, List<TenantInfo> ts) {
        List<UUID> ids = ts.stream().map(TenantInfo::id).toList();
        String t = owner.sql("select string_agg(id || ':' || tenant_id || ':' || status || ':' || title || ':' || body, '|' order by id) from tickets where tenant_id in (:ids)")
                .param("ids", ids).query(String.class).single();
        String c = owner.sql("select coalesce(string_agg(id || ':' || tenant_id || ':' || body, '|' order by id), '') from comments where tenant_id in (:ids)")
                .param("ids", ids).query(String.class).single();
        return t + "#" + c;
    }
}
