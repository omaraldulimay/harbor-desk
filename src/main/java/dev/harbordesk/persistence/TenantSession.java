package dev.harbordesk.persistence;

import dev.harbordesk.domain.TenantInfo;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Proof that code runs inside a tenant transaction. Only TenantTx can create one (an ArchUnit test checks it),
 * and every repository method needs one.
 */
public final class TenantSession {
    private final TenantInfo tenant;
    private final JdbcClient jdbc;

    TenantSession(TenantInfo tenant, JdbcClient jdbc) {
        this.tenant = tenant;
        this.jdbc = jdbc;
    }

    public TenantInfo tenant() {
        return tenant;
    }

    JdbcClient jdbc() {
        return jdbc;
    }

}
