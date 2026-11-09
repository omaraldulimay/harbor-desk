package dev.harbordesk.persistence;

import dev.harbordesk.domain.TenantContext;
import dev.harbordesk.domain.TenantInfo;
import java.util.function.Function;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The only way to touch tenant data. Opens a transaction on the tenant's database (routing happens in
 * TenantRoutingDataSource) and sets app.tenant_id for that transaction, which is what the RLS policies read.
 */
@Component
public class TenantTx {
    private final TransactionTemplate tx;
    private final JdbcClient jdbc;

    public TenantTx(TransactionTemplate tx, JdbcClient jdbc) {
        this.tx = tx;
        this.jdbc = jdbc;
    }

    public <T> T run(Function<TenantSession, T> work) {
        TenantInfo tenant = TenantContext.require();
        return tx.execute(status -> {
            jdbc.sql("select set_config('app.tenant_id', :t, true)").param("t", tenant.id().toString())
                    .query(String.class).single();
            return work.apply(new TenantSession(tenant, jdbc));
        });
    }
}
