package dev.harbordesk.persistence;

import dev.harbordesk.domain.TenantContext;
import dev.harbordesk.domain.TenantInfo;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Pool tenants use the shared database, tenants with a silo use their own database. The target is chosen from the
 * tenant context when a connection is taken, silo pools are created on first use.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    private final DataSource pool;
    private final Function<String, DataSource> siloFactory;
    private final Map<String, DataSource> silos = new ConcurrentHashMap<>();

    public TenantRoutingDataSource(DataSource pool, Function<String, DataSource> siloFactory) {
        this.pool = pool;
        this.siloFactory = siloFactory;
        setTargetDataSources(Map.of());
        setDefaultTargetDataSource(pool);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        TenantInfo t = TenantContext.current();
        return t == null ? null : t.dbName();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        Object key = determineCurrentLookupKey();
        if (key == null) {
            return pool;
        }
        return silos.computeIfAbsent((String) key, siloFactory);
    }
}
