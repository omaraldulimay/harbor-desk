package dev.harbordesk.persistence;

import dev.harbordesk.domain.Ticket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL. Reads do not filter by tenant on purpose: the RLS policy does, so a forgotten filter is harmless.
 * Inserts carry tenant_id because the policy checks it (WITH CHECK).
 */
@Repository
public class TicketRepository {
    static final RowMapper<Ticket> MAPPER = (rs, n) -> new Ticket(rs.getLong("id"), rs.getObject("tenant_id", UUID.class),
            rs.getString("title"), rs.getString("body"), rs.getString("status"),
            rs.getTimestamp("created_at").toInstant());

    private static final String COLUMNS = "id, tenant_id, title, body, status, created_at";

    public Ticket insert(TenantSession s, String title, String body) {
        return s.jdbc().sql("insert into tickets (tenant_id, title, body) values (:t, :title, :body) returning " + COLUMNS)
                .param("t", s.tenant().id()).param("title", title).param("body", body)
                .query(MAPPER).single();
    }

    public Optional<Ticket> find(TenantSession s, long id) {
        return s.jdbc().sql("select " + COLUMNS + " from tickets where id = :id").param("id", id)
                .query(MAPPER).optional();
    }

    public List<Ticket> list(TenantSession s, String status, int limit) {
        return s.jdbc().sql("select " + COLUMNS + " from tickets where (:status::text is null or status = :status) "
                        + "order by id desc limit :limit")
                .param("status", status).param("limit", limit).query(MAPPER).list();
    }

    public Optional<Ticket> updateStatus(TenantSession s, long id, String status) {
        return s.jdbc().sql("update tickets set status = :status where id = :id returning " + COLUMNS)
                .param("status", status).param("id", id).query(MAPPER).optional();
    }

    public long count(TenantSession s) {
        return s.jdbc().sql("select count(*) from tickets").query(Long.class).single();
    }

    /** Transaction scoped advisory lock keyed by tenant, used for the quota check. */
    public void lockQuota(TenantSession s) {
        s.jdbc().sql("select pg_advisory_xact_lock(hashtextextended(:t, 0))").param("t", s.tenant().id().toString())
                .query().singleRow();
    }
}
