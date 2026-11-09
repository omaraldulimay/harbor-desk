package dev.harbordesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantContext;
import dev.harbordesk.domain.TenantInfo;
import dev.harbordesk.domain.TicketService;
import dev.harbordesk.persistence.RawSql;
import dev.harbordesk.persistence.TenantTx;
import dev.harbordesk.support.HarborTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class RlsTest extends HarborTestBase {
    @Autowired
    TicketService tickets;
    @Autowired
    TenantTx tx;
    @Autowired
    JdbcClient app;   // routed, uses the app role

    @Test
    void appRoleIsNotPrivileged() {
        // run as the app role, with no tenant in context (pool database)
        Map<String, Object> role = app.sql("select rolsuper, rolbypassrls, rolcreaterole, rolcreatedb from pg_roles where rolname = current_user")
                .query().singleRow();
        assertThat(role.values()).containsOnly(false);
        long owned = app.sql("select count(*) from pg_tables where schemaname = 'public' and tableowner = current_user")
                .query(Long.class).single();
        assertThat(owned).isZero();
    }

    @Test
    void everyTableHasForcedRls() {
        List<String> notForced = app.sql("""
                select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relkind = 'r' and c.relname <> 'flyway_schema_history'
                  and not (c.relrowsecurity and c.relforcerowsecurity)""").query(String.class).list();
        assertThat(notForced).isEmpty();
    }

    @Test
    void everyTableHasTenantColumn() {
        List<String> without = app.sql("""
                select t.table_name from information_schema.tables t
                where t.table_schema = 'public' and t.table_type = 'BASE TABLE' and t.table_name <> 'flyway_schema_history'
                  and t.table_name <> 'tenants'
                  and not exists (select 1 from information_schema.columns c where c.table_schema = 'public'
                                  and c.table_name = t.table_name and c.column_name = 'tenant_id')""")
                .query(String.class).list();
        assertThat(without).isEmpty();
    }

    @Test
    void queryWithoutWhereOnlyReturnsOwnRows() {
        TenantInfo a = newTenant("rls-a", Plan.PRO);
        TenantInfo b = newTenant("rls-b", Plan.PRO);
        TenantContext.runAs(a, () -> tickets.create("a1", ""));
        TenantContext.runAs(a, () -> tickets.create("a2", ""));
        TenantContext.runAs(b, () -> tickets.create("b1", ""));

        // deliberately no WHERE tenant_id
        List<UUID> seenByA = TenantContext.runAs(a,
                () -> RawSql.run(tx, j -> j.sql("select tenant_id from tickets").query(UUID.class).list()));
        assertThat(seenByA).hasSize(2).containsOnly(a.id());

        long totalInTable = JdbcClient.create(admin)
                .sql("select count(*) from tickets").query(Long.class).single();
        assertThat(totalInTable).isGreaterThanOrEqualTo(3);   // the owner sees everything, the app does not
    }

    @Test
    void withoutTenantSettingNoRowIsVisible() {
        TenantInfo a = newTenant("rls-none", Plan.PRO);
        TenantContext.runAs(a, () -> tickets.create("x", ""));
        // plain query on the app role, no set_config in this transaction
        long visible = app.sql("select count(*) from tickets").query(Long.class).single();
        assertThat(visible).isZero();
    }

    @Test
    void insertForAnotherTenantIsRejected() {
        TenantInfo a = newTenant("rls-w-a", Plan.PRO);
        TenantInfo b = newTenant("rls-w-b", Plan.PRO);
        assertThatThrownBy(() -> TenantContext.runAs(a, () -> RawSql.run(tx, j ->
                j.sql("insert into tickets (tenant_id, title) values (:t, 'stolen')").param("t", b.id()).update())))
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void repositoryNeedsTenantContext() {
        assertThatThrownBy(() -> tickets.list(null, 10)).isInstanceOf(dev.harbordesk.domain.NoTenantException.class);
    }
}
