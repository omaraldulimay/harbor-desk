package dev.harbordesk.persistence;

import java.util.function.Function;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Test helper in the persistence package: runs hand written SQL inside a tenant transaction. */
public final class RawSql {
    private RawSql() {
    }

    public static <T> T run(TenantTx tx, Function<JdbcClient, T> work) {
        return tx.run(s -> work.apply(s.jdbc()));
    }
}
