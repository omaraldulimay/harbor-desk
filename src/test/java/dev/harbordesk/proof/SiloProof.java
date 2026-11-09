package dev.harbordesk.proof;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harbordesk.domain.Plan;
import dev.harbordesk.domain.TenantInfo;
import dev.harbordesk.support.Api;
import dev.harbordesk.support.HarborTestBase;
import dev.harbordesk.support.ProofReport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The enterprise tenant is created through the admin API, written to over HTTP, and its rows are found in its own database only. */
@Tag("proof")
class SiloProof extends HarborTestBase {

    @Test
    void enterpriseDataLivesInItsOwnDatabase() throws Exception {
        Api api = api();
        String slug = "silo" + Long.toHexString(System.nanoTime());
        Api.Resp created = api.admin("/admin/tenants", "{\"slug\":\"" + slug + "\",\"name\":\"Silo Test\",\"plan\":\"enterprise\"}", ADMIN_KEY);
        assertThat(created.status()).isEqualTo(201);
        String db = created.string("database");
        String tenantId = created.string("id");
        assertThat(db).startsWith("harbor_silo_");
        String silo = api.getResp("/dev/token?tenant=" + tenantId, null).string("token");

        TenantInfo poolTenant = newTenant("silo-neighbor", Plan.PRO);
        String pool = token(poolTenant);

        int n = 25;
        for (int i = 0; i < n; i++) {
            assertThat(api.post("/api/tickets", silo, "{\"title\":\"silo ticket " + i + "\"}").status()).isEqualTo(201);
        }
        for (int i = 0; i < 5; i++) {
            assertThat(api.post("/api/tickets", pool, "{\"title\":\"pool ticket " + i + "\"}").status()).isEqualTo(201);
        }
        assertThat(api.getResp("/api/tickets?limit=200", silo).body()).doesNotContain("pool ticket");

        // look directly into the databases with the owner role
        String siloUrl = dbUrl.substring(0, dbUrl.lastIndexOf('/') + 1) + db;
        long inSilo = count(siloUrl, "select count(*) from tickets where tenant_id = '" + tenantId + "'");
        long siloTotal = count(siloUrl, "select count(*) from tickets");
        long poolPoolTenantInSilo = count(siloUrl, "select count(*) from tickets where tenant_id = '" + poolTenant.id() + "'");
        long inPool = count(dbUrl, "select count(*) from tickets where tenant_id = '" + tenantId + "'");
        long poolTenantInPool = count(dbUrl, "select count(*) from tickets where tenant_id = '" + poolTenant.id() + "'");
        long registryRow = count(dbUrl, "select count(*) from tenants where id = '" + tenantId + "' and db_name = '" + db + "'");
        long siloRls = count(siloUrl, "select count(*) from pg_class where relname in ('tickets','comments') and relrowsecurity and relforcerowsecurity");

        assertThat(inSilo).isEqualTo(n);
        assertThat(siloTotal).isEqualTo(n);
        assertThat(inPool).isZero();
        assertThat(poolPoolTenantInSilo).isZero();
        assertThat(poolTenantInPool).isEqualTo(5);
        assertThat(registryRow).isEqualTo(1);
        assertThat(siloRls).isEqualTo(2);

        StringBuilder md = new StringBuilder();
        md.append("## 2. Silo\n\n");
        md.append("Result: **PASS**\n\n");
        md.append("An enterprise tenant was created through `POST /admin/tenants`. The service created the database `").append(db)
                .append("`, migrated it and registered the route. Then ").append(n).append(" tickets were written for it and 5 for a pro tenant over HTTP. ")
                .append("Row counts, read directly with the owner role:\n\n");
        md.append("| Question | Rows |\n|---|---|\n");
        md.append("| Enterprise tickets in the silo database | ").append(inSilo).append(" |\n");
        md.append("| Total tickets in the silo database | ").append(siloTotal).append(" |\n");
        md.append("| Enterprise tickets in the shared pool database | ").append(inPool).append(" |\n");
        md.append("| Pro tenant tickets in the silo database | ").append(poolPoolTenantInSilo).append(" |\n");
        md.append("| Pro tenant tickets in the shared pool database | ").append(poolTenantInPool).append(" |\n");
        md.append("| Tables with forced RLS in the silo (defense in depth) | ").append(siloRls).append(" of 2 |\n");

        ProofReport.write("silo", md.toString());
        dropSilo(db);
    }

    private long count(String url, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, "harbor", "harbor");
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void dropSilo(String db) {
        // the app keeps a pool for the silo, force closes its sessions
        try (Connection c = admin.getConnection(); Statement st = c.createStatement()) {
            st.execute("drop database if exists \"" + db + "\" with (force)");
        } catch (SQLException ignored) {
            // cleanup only
        }
    }
}
