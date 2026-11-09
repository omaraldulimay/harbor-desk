package dev.harbordesk.persistence;

import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantInfo;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** The tenant registry lives in the pool database. Routing reads it, results are cached (tenants do not change plan here). */
@Component
public class TenantDirectory {
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ConcurrentHashMap<UUID, TenantInfo> cache = new ConcurrentHashMap<>();

    public TenantDirectory(@Qualifier("poolDataSource") DataSource pool) {
        this.jdbc = JdbcClient.create(pool);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(pool));
    }

    public Optional<TenantInfo> find(UUID id) {
        TenantInfo cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        // the registry has RLS too: ask with the id we look for as the tenant
        Optional<TenantInfo> found = tx.execute(status -> {
            jdbc.sql("select set_config('app.tenant_id', :t, true)").param("t", id.toString())
                    .query(String.class).single();
            return jdbc.sql("select id, slug, plan, db_name from tenants where id = :id").param("id", id)
                    .query((rs, n) -> new TenantInfo(rs.getObject("id", UUID.class), rs.getString("slug"),
                            Plan.parse(rs.getString("plan")), rs.getString("db_name")))
                    .optional();
        });
        found.ifPresent(t -> cache.put(id, t));
        return found;
    }
}
