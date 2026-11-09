package dev.harbordesk.persistence;

import dev.harbordesk.domain.ConflictException;
import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantInfo;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Onboarding with the owner role: registry row, and for enterprise a new database that is migrated right away. */
@Component
public class TenantProvisioner {
    private final DataSource admin;
    private final DbProperties props;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public TenantProvisioner(@Qualifier("adminDataSource") DataSource admin, DbProperties props) {
        this.admin = admin;
        this.props = props;
        this.jdbc = JdbcClient.create(admin);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(admin));
    }

    public TenantInfo provision(String slug, String name, Plan plan) {
        boolean taken = jdbc.sql("select count(*) from tenants where slug = :s").param("s", slug)
                .query(Long.class).single() > 0;
        if (taken) {
            throw new ConflictException("slug already exists");
        }
        UUID id = UUID.randomUUID();
        String dbName = plan.hasSilo() ? "harbor_silo_" + slug.replace('-', '_') : null;
        if (dbName != null) {
            createDatabase(dbName);
            try (var silo = DatabaseConfig.hikari(props.urlFor(dbName), props.adminUser(), props.adminPassword(), 3)) {
                DatabaseConfig.migrate(silo);
            }
        }
        tx.executeWithoutResult(status -> {
            jdbc.sql("select set_config('app.tenant_id', :t, true)").param("t", id.toString())
                    .query(String.class).single();
            jdbc.sql("insert into tenants (id, slug, name, plan, db_name) values (:id, :slug, :name, :plan, :db)")
                    .param("id", id).param("slug", slug).param("name", name)
                    .param("plan", plan.id()).param("db", dbName).update();
        });
        return new TenantInfo(id, slug, plan, dbName);
    }

    private void createDatabase(String dbName) {
        // CREATE DATABASE cannot run in a transaction; the name is generated from a validated slug
        try (Connection c = admin.getConnection(); Statement st = c.createStatement()) {
            st.execute("create database \"" + dbName + "\"");
        } catch (SQLException e) {
            throw new ConflictException("database " + dbName + " could not be created: " + e.getMessage());
        }
    }
}
